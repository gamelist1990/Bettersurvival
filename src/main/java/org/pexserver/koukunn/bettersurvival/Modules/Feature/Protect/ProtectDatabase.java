package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Protect の SQLite 永続化層。
 *
 * Bukkit/Paper のメインスレッドからはキューへ積むだけにし、SQLite は専用I/Oスレッド
 * 1本からのみ触る。WAL + NORMAL synchronous + バッチINSERTでtickへの影響を抑える。
 */
public final class ProtectDatabase {
    private static final int MAX_QUEUE = 20_000;
    private static final int MAX_BATCH = 256;
    private static final int QUERY_LIMIT_MAX = 10_000;

    private final Loader plugin;
    private final ProtectSettings settings;
    private final ArrayBlockingQueue<ProtectRecord> queue = new ArrayBlockingQueue<>(MAX_QUEUE);
    private final ScheduledExecutorService io;
    private final AtomicLong droppedRecords = new AtomicLong();
    private final File databaseFile;

    private Connection connection;
    private volatile boolean closed;

    public ProtectDatabase(Loader plugin, ProtectSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        File dir = new File(plugin.getDataFolder(), "Protect");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.databaseFile = new File(dir, "protect.db");
        this.io = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BetterSurvival-Protect-DB");
            thread.setDaemon(true);
            return thread;
        });

        io.execute(this::initialize);
        io.scheduleWithFixedDelay(this::flushSafely, 250L, 250L, TimeUnit.MILLISECONDS);
        io.scheduleWithFixedDelay(this::cleanupSafely, 60L, 6L * 60L * 60L, TimeUnit.SECONDS);
    }

    public void enqueue(ProtectRecord record) {
        if (record == null || closed) {
            return;
        }
        if (!queue.offer(record)) {
            long dropped = droppedRecords.incrementAndGet();
            if (dropped == 1L || dropped % 1000L == 0L) {
                plugin.getLogger().warning("Protect DB queue overflow: dropped=" + dropped);
            }
        }
    }

    public long getDroppedRecords() {
        return droppedRecords.get();
    }

    public long getDatabaseSizeBytes() {
        long size = databaseFile.exists() ? databaseFile.length() : 0L;
        File wal = new File(databaseFile.getPath() + "-wal");
        if (wal.exists()) {
            size += wal.length();
        }
        return size;
    }

    public CompletableFuture<Long> countRecords() {
        CompletableFuture<Long> future = new CompletableFuture<>();
        io.execute(() -> {
            try {
                flushBatch();
                if (connection == null) {
                    future.complete(0L);
                    return;
                }
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM protect_events")) {
                    future.complete(rs.next() ? rs.getLong(1) : 0L);
                }
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<List<ProtectRecord>> queryNearby(
            String worldUuid,
            int x,
            int y,
            int z,
            int radius,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int limit,
            int offset) {
        CompletableFuture<List<ProtectRecord>> future = new CompletableFuture<>();
        int safeRadius = Math.max(0, Math.min(256, radius));
        int safeLimit = Math.max(1, Math.min(QUERY_LIMIT_MAX, limit));
        int safeOffset = Math.max(0, offset);
        Set<ProtectAction> safeActions = actions == null || actions.isEmpty()
                ? EnumSet.allOf(ProtectAction.class)
                : EnumSet.copyOf(actions);

        io.execute(() -> {
            try {
                flushBatch();
                future.complete(runNearbyQuery(
                        worldUuid, x, y, z, safeRadius, sinceMs, actorName,
                        safeActions, safeLimit, safeOffset, false));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<List<ProtectRecord>> queryRollback(
            String worldUuid,
            int x,
            int y,
            int z,
            int radius,
            long sinceMs,
            String actorName,
            int limit) {
        CompletableFuture<List<ProtectRecord>> future = new CompletableFuture<>();
        int safeRadius = Math.max(0, Math.min(256, radius));
        int safeLimit = Math.max(1, Math.min(QUERY_LIMIT_MAX, limit));
        Set<ProtectAction> reversible = EnumSet.of(
                ProtectAction.BLOCK_BREAK,
                ProtectAction.BLOCK_PLACE,
                ProtectAction.CONTAINER_CHANGE);

        io.execute(() -> {
            try {
                flushBatch();
                future.complete(runNearbyQuery(
                        worldUuid, x, y, z, safeRadius, sinceMs, actorName,
                        reversible, safeLimit, 0, true));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public void markRolledBack(Collection<Long> ids, String actorName) {
        if (ids == null || ids.isEmpty() || closed) {
            return;
        }
        List<Long> copy = List.copyOf(ids);
        io.execute(() -> {
            if (connection == null) {
                return;
            }
            String sql = """
                    UPDATE protect_events
                    SET rolled_back = 1, rollback_actor = ?, rollback_time_ms = ?
                    WHERE id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (Long id : copy) {
                    if (id == null) {
                        continue;
                    }
                    ps.setString(1, actorName);
                    ps.setLong(2, now);
                    ps.setLong(3, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Protect rollback mark failed", e);
            }
        });
    }

    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        io.execute(() -> {
            flushSafely();
            if (connection != null) {
                try {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    }
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Protect DB close failed", e);
                }
                connection = null;
            }
        });
        io.shutdown();
        try {
            if (!io.awaitTermination(5L, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }
    }

    private void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA temp_store=MEMORY");
                statement.execute("PRAGMA busy_timeout=3000");
                statement.execute("PRAGMA wal_autocheckpoint=1000");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS protect_events (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            time_ms INTEGER NOT NULL,
                            actor_uuid TEXT,
                            actor_name TEXT,
                            world_uuid TEXT NOT NULL,
                            world_name TEXT NOT NULL,
                            x INTEGER NOT NULL,
                            y INTEGER NOT NULL,
                            z INTEGER NOT NULL,
                            action TEXT NOT NULL,
                            block_before TEXT,
                            block_after TEXT,
                            slot INTEGER,
                            item_before BLOB,
                            item_after BLOB,
                            detail TEXT,
                            rolled_back INTEGER NOT NULL DEFAULT 0,
                            rollback_actor TEXT,
                            rollback_time_ms INTEGER
                        )
                        """);
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_protect_location_time
                        ON protect_events(world_uuid, x, y, z, time_ms DESC)
                        """);
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_protect_actor_time
                        ON protect_events(actor_name, time_ms DESC)
                        """);
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_protect_action_time
                        ON protect_events(action, time_ms DESC)
                        """);
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_protect_time
                        ON protect_events(time_ms)
                        """);
            }
        } catch (Throwable throwable) {
            connection = null;
            plugin.getLogger().log(Level.SEVERE, "Protect DB initialization failed", throwable);
        }
    }

    private void flushSafely() {
        try {
            flushBatch();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Protect DB flush failed", throwable);
        }
    }

    private void flushBatch() throws SQLException {
        if (connection == null || queue.isEmpty()) {
            return;
        }

        List<ProtectRecord> batch = new ArrayList<>(MAX_BATCH);
        queue.drainTo(batch, MAX_BATCH);
        if (batch.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO protect_events (
                    time_ms, actor_uuid, actor_name, world_uuid, world_name,
                    x, y, z, action, block_before, block_after, slot,
                    item_before, item_after, detail, rolled_back
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (ProtectRecord record : batch) {
                bindInsert(ps, record);
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            for (ProtectRecord record : batch) {
                if (!queue.offer(record)) {
                    droppedRecords.incrementAndGet();
                }
            }
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void bindInsert(PreparedStatement ps, ProtectRecord record) throws SQLException {
        ps.setLong(1, record.timeMs());
        ps.setString(2, record.actorUuid());
        ps.setString(3, record.actorName());
        ps.setString(4, record.worldUuid());
        ps.setString(5, record.worldName());
        ps.setInt(6, record.x());
        ps.setInt(7, record.y());
        ps.setInt(8, record.z());
        ps.setString(9, record.action().name());
        ps.setString(10, record.blockBefore());
        ps.setString(11, record.blockAfter());
        if (record.slot() == null) {
            ps.setNull(12, java.sql.Types.INTEGER);
        } else {
            ps.setInt(12, record.slot());
        }
        ps.setBytes(13, record.itemBefore());
        ps.setBytes(14, record.itemAfter());
        ps.setString(15, record.detail());
        ps.setInt(16, record.rolledBack() ? 1 : 0);
    }

    private List<ProtectRecord> runNearbyQuery(
            String worldUuid,
            int x,
            int y,
            int z,
            int radius,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int limit,
            int offset,
            boolean onlyNotRolledBack) throws SQLException {
        if (connection == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, time_ms, actor_uuid, actor_name, world_uuid, world_name,
                       x, y, z, action, block_before, block_after, slot,
                       item_before, item_after, detail, rolled_back
                FROM protect_events
                WHERE world_uuid = ?
                  AND time_ms >= ?
                  AND x BETWEEN ? AND ?
                  AND y BETWEEN ? AND ?
                  AND z BETWEEN ? AND ?
                """);

        if (actorName != null && !actorName.isBlank()) {
            sql.append(" AND actor_name = ? COLLATE NOCASE");
        }
        if (onlyNotRolledBack) {
            sql.append(" AND rolled_back = 0");
        }
        sql.append(" AND action IN (");
        int actionCount = 0;
        for (int ignored = 0; ignored < actions.size(); ignored++) {
            if (actionCount++ > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(") ORDER BY time_ms DESC, id DESC LIMIT ? OFFSET ?");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setString(index++, worldUuid);
            ps.setLong(index++, sinceMs);
            ps.setInt(index++, x - radius);
            ps.setInt(index++, x + radius);
            ps.setInt(index++, y - radius);
            ps.setInt(index++, y + radius);
            ps.setInt(index++, z - radius);
            ps.setInt(index++, z + radius);
            if (actorName != null && !actorName.isBlank()) {
                ps.setString(index++, actorName);
            }
            for (ProtectAction action : actions) {
                ps.setString(index++, action.name());
            }
            ps.setInt(index++, limit);
            ps.setInt(index, offset);

            List<ProtectRecord> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readRecord(rs));
                }
            }
            return result;
        }
    }

    private ProtectRecord readRecord(ResultSet rs) throws SQLException {
        Object slotValue = rs.getObject("slot");
        Integer slot = slotValue instanceof Number number ? number.intValue() : null;
        return new ProtectRecord(
                rs.getLong("id"),
                rs.getLong("time_ms"),
                rs.getString("actor_uuid"),
                rs.getString("actor_name"),
                rs.getString("world_uuid"),
                rs.getString("world_name"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                ProtectAction.valueOf(rs.getString("action")),
                rs.getString("block_before"),
                rs.getString("block_after"),
                slot,
                rs.getBytes("item_before"),
                rs.getBytes("item_after"),
                rs.getString("detail"),
                rs.getInt("rolled_back") != 0);
    }

    private void cleanupSafely() {
        if (connection == null) {
            return;
        }
        long cutoff = System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(Math.max(1, settings.getRetentionDays()));
        String sql = """
                DELETE FROM protect_events
                WHERE id IN (
                    SELECT id FROM protect_events
                    WHERE time_ms < ?
                    ORDER BY id
                    LIMIT 10000
                )
                """;
        try {
            int totalDeleted = 0;
            for (int i = 0; i < 5; i++) {
                int deleted;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, cutoff);
                    deleted = ps.executeUpdate();
                }
                totalDeleted += deleted;
                if (deleted < 10_000) {
                    break;
                }
            }
            if (totalDeleted > 0) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA optimize");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Protect retention cleanup failed", e);
        }
    }
}
