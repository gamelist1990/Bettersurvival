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

    public void requestCleanup() {
        if (!closed) {
            io.execute(this::cleanupSafely);
        }
    }

    public long getDatabaseSizeBytes() {
        return getMainDatabaseSizeBytes() + getWalSizeBytes() + getShmSizeBytes();
    }

    public long getMainDatabaseSizeBytes() {
        return databaseFile.exists() ? databaseFile.length() : 0L;
    }

    public long getWalSizeBytes() {
        File wal = new File(databaseFile.getPath() + "-wal");
        return wal.exists() ? wal.length() : 0L;
    }

    public long getShmSizeBytes() {
        File shm = new File(databaseFile.getPath() + "-shm");
        return shm.exists() ? shm.length() : 0L;
    }

    public long getDiskUsableSpaceBytes() {
        File dir = databaseFile.getParentFile();
        return dir == null ? 0L : dir.getUsableSpace();
    }

    public long getDiskTotalSpaceBytes() {
        File dir = databaseFile.getParentFile();
        return dir == null ? 0L : dir.getTotalSpace();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getQueueCapacity() {
        return MAX_QUEUE;
    }

    public boolean isReady() {
        return !closed && connection != null;
    }

    public String getDatabaseFileName() {
        return databaseFile.getName();
    }

    public CompletableFuture<StatusSnapshot> getStatusSnapshot() {
        CompletableFuture<StatusSnapshot> future = new CompletableFuture<>();
        io.execute(() -> {
            try {
                flushBatch();
                long total = 0L;
                long last24h = 0L;
                long rolledBack = 0L;
                if (connection != null) {
                    try (Statement statement = connection.createStatement();
                         ResultSet rs = statement.executeQuery("""
                                 SELECT
                                     COUNT(*) AS total,
                                     SUM(CASE WHEN time_ms >= (CAST(strftime('%s','now') AS INTEGER) * 1000 - 86400000)
                                              THEN 1 ELSE 0 END) AS last_24h,
                                     SUM(CASE WHEN rolled_back = 1 THEN 1 ELSE 0 END) AS rolled_back
                                 FROM protect_events
                                 """)) {
                        if (rs.next()) {
                            total = rs.getLong("total");
                            last24h = rs.getLong("last_24h");
                            rolledBack = rs.getLong("rolled_back");
                        }
                    }
                }
                future.complete(new StatusSnapshot(
                        total,
                        last24h,
                        rolledBack,
                        getMainDatabaseSizeBytes(),
                        getWalSizeBytes(),
                        getShmSizeBytes(),
                        getDiskUsableSpaceBytes(),
                        getDiskTotalSpaceBytes(),
                        getQueueSize(),
                        getQueueCapacity(),
                        getDroppedRecords(),
                        isReady()));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public record StatusSnapshot(
            long totalRecords,
            long last24hRecords,
            long rolledBackRecords,
            long databaseBytes,
            long walBytes,
            long shmBytes,
            long diskUsableBytes,
            long diskTotalBytes,
            int queueSize,
            int queueCapacity,
            long droppedRecords,
            boolean ready) {
        public long totalStorageBytes() {
            return databaseBytes + walBytes + shmBytes;
        }

        public double queueUsagePercent() {
            return queueCapacity <= 0 ? 0.0 : (queueSize * 100.0) / queueCapacity;
        }
    }

    public CompletableFuture<ResetResult> resetDatabase() {
        CompletableFuture<ResetResult> future = new CompletableFuture<>();
        io.execute(() -> {
            try {
                if (connection == null) {
                    future.completeExceptionally(new IllegalStateException("Protect database is not ready"));
                    return;
                }

                // 初期化要求以前にまだDBへ入っていないイベントは破棄する。
                // このclear以降にenqueueされたイベントは初期化完了後の新しい履歴として残る。
                int clearedQueue = queue.size();
                queue.clear();

                int deleted;
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    deleted = statement.executeUpdate("DELETE FROM protect_events");
                    statement.executeUpdate("DELETE FROM sqlite_sequence WHERE name = 'protect_events'");
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }

                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    statement.execute("VACUUM");
                    statement.execute("PRAGMA optimize");
                }

                droppedRecords.set(0L);
                future.complete(new ResetResult(deleted, clearedQueue));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public record ResetResult(int deletedRecords, int clearedQueuedRecords) {
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
                        safeActions, safeLimit, safeOffset, -1));
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
            Set<ProtectAction> actions,
            int limit) {
        return queryReplayCandidates(
                worldUuid, x, y, z, radius, sinceMs, actorName, actions, limit, 0);
    }

    public CompletableFuture<List<ProtectRecord>> queryRestore(
            String worldUuid,
            int x,
            int y,
            int z,
            int radius,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int limit) {
        return queryReplayCandidates(
                worldUuid, x, y, z, radius, sinceMs, actorName, actions, limit, 1);
    }

    private CompletableFuture<List<ProtectRecord>> queryReplayCandidates(
            String worldUuid,
            int x,
            int y,
            int z,
            int radius,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int limit,
            int rolledBackState) {
        CompletableFuture<List<ProtectRecord>> future = new CompletableFuture<>();
        int safeRadius = Math.max(0, Math.min(256, radius));
        int safeLimit = Math.max(1, Math.min(QUERY_LIMIT_MAX, limit));
        Set<ProtectAction> reversible = EnumSet.noneOf(ProtectAction.class);
        Set<ProtectAction> requested = actions == null || actions.isEmpty()
                ? EnumSet.allOf(ProtectAction.class)
                : EnumSet.copyOf(actions);
        for (ProtectAction action : requested) {
            if (action.reversible()) {
                reversible.add(action);
            }
        }
        if (reversible.isEmpty()) {
            future.complete(List.of());
            return future;
        }

        io.execute(() -> {
            try {
                flushBatch();
                List<ProtectRecord> initial = runNearbyQuery(
                        worldUuid, x, y, z, safeRadius, sinceMs, actorName,
                        reversible, safeLimit, 0, rolledBackState);
                future.complete(expandOperationGroups(initial, rolledBackState));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<List<ProtectRecord>> queryLastRollback(String rollbackActor, int limit) {
        CompletableFuture<List<ProtectRecord>> future = new CompletableFuture<>();
        int safeLimit = Math.max(1, Math.min(QUERY_LIMIT_MAX, limit));
        io.execute(() -> {
            try {
                flushBatch();
                if (connection == null) {
                    future.complete(List.of());
                    return;
                }
                String sql = """
                        SELECT id, time_ms, actor_uuid, actor_name, world_uuid, world_name,
                               x, y, z, action, block_before, block_after, slot,
                               item_before, item_after, detail, operation_id, rolled_back
                        FROM protect_events
                        WHERE rolled_back = 1
                          AND rollback_actor = ? COLLATE NOCASE
                          AND rollback_time_ms = (
                              SELECT MAX(rollback_time_ms)
                              FROM protect_events
                              WHERE rolled_back = 1 AND rollback_actor = ? COLLATE NOCASE
                          )
                        ORDER BY time_ms ASC, id ASC
                        LIMIT ?
                        """;
                List<ProtectRecord> result = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, rollbackActor);
                    ps.setString(2, rollbackActor);
                    ps.setInt(3, safeLimit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) result.add(readRecord(rs));
                    }
                }
                future.complete(result);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<List<ProtectRecord>> queryByIds(Collection<Long> ids) {
        CompletableFuture<List<ProtectRecord>> future = new CompletableFuture<>();
        if (ids == null || ids.isEmpty()) {
            future.complete(List.of());
            return future;
        }
        List<Long> copy = ids.stream().filter(java.util.Objects::nonNull).limit(QUERY_LIMIT_MAX).toList();
        io.execute(() -> {
            try {
                flushBatch();
                if (connection == null || copy.isEmpty()) {
                    future.complete(List.of());
                    return;
                }
                StringBuilder sql = new StringBuilder("""
                        SELECT id, time_ms, actor_uuid, actor_name, world_uuid, world_name,
                               x, y, z, action, block_before, block_after, slot,
                               item_before, item_after, detail, operation_id, rolled_back
                        FROM protect_events WHERE id IN (
                        """);
                for (int i = 0; i < copy.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append('?');
                }
                sql.append(") ORDER BY time_ms DESC, id DESC");
                List<ProtectRecord> result = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                    int index = 1;
                    for (Long id : copy) ps.setLong(index++, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) result.add(readRecord(rs));
                    }
                }
                future.complete(result);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<Integer> purgeOlderThan(long cutoffMs, String actorName) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        io.execute(() -> {
            try {
                flushBatch();
                if (connection == null) {
                    future.complete(0);
                    return;
                }
                String sql = actorName == null || actorName.isBlank()
                        ? "DELETE FROM protect_events WHERE time_ms < ?"
                        : "DELETE FROM protect_events WHERE time_ms < ? AND actor_name = ? COLLATE NOCASE";
                int deleted;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, cutoffMs);
                    if (actorName != null && !actorName.isBlank()) ps.setString(2, actorName);
                    deleted = ps.executeUpdate();
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA optimize");
                }
                future.complete(deleted);
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

    public void markRestored(Collection<Long> ids) {
        if (ids == null || ids.isEmpty() || closed) {
            return;
        }
        List<Long> copy = List.copyOf(ids);
        io.execute(() -> {
            if (connection == null) return;
            String sql = """
                    UPDATE protect_events
                    SET rolled_back = 0, rollback_actor = NULL, rollback_time_ms = NULL
                    WHERE id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Long id : copy) {
                    if (id == null) continue;
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Protect restore mark failed", e);
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
                            operation_id TEXT,
                            rolled_back INTEGER NOT NULL DEFAULT 0,
                            rollback_actor TEXT,
                            rollback_time_ms INTEGER
                        )
                        """);
                ensureColumn(statement, "protect_events", "operation_id", "TEXT");
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_protect_operation
                        ON protect_events(operation_id)
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

    private void ensureColumn(
            Statement statement,
            String table,
            String column,
            String definition) throws SQLException {
        boolean exists = false;
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
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
                    item_before, item_after, detail, operation_id, rolled_back
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        ps.setString(16, record.operationId());
        ps.setInt(17, record.rolledBack() ? 1 : 0);
    }

    private List<ProtectRecord> expandOperationGroups(
            List<ProtectRecord> initial,
            int rolledBackState) throws SQLException {
        if (connection == null || initial == null || initial.isEmpty()) {
            return initial == null ? List.of() : initial;
        }

        Set<String> operationIds = new java.util.LinkedHashSet<>();
        java.util.LinkedHashMap<Long, ProtectRecord> merged = new java.util.LinkedHashMap<>();
        for (ProtectRecord record : initial) {
            merged.put(record.id(), record);
            if (record.operationId() != null && !record.operationId().isBlank()) {
                operationIds.add(record.operationId());
            }
        }
        if (operationIds.isEmpty()) {
            return new ArrayList<>(merged.values());
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, time_ms, actor_uuid, actor_name, world_uuid, world_name,
                       x, y, z, action, block_before, block_after, slot,
                       item_before, item_after, detail, operation_id, rolled_back
                FROM protect_events
                WHERE operation_id IN (
                """);
        for (int i = 0; i < operationIds.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');
        if (rolledBackState == 0) {
            sql.append(" AND rolled_back = 0");
        } else if (rolledBackState == 1) {
            sql.append(" AND rolled_back = 1");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (String operationId : operationIds) {
                ps.setString(index++, operationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProtectRecord record = readRecord(rs);
                    if (record.reversible()) {
                        merged.put(record.id(), record);
                    }
                }
            }
        }
        return new ArrayList<>(merged.values());
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
            int rolledBackState) throws SQLException {
        if (connection == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, time_ms, actor_uuid, actor_name, world_uuid, world_name,
                       x, y, z, action, block_before, block_after, slot,
                       item_before, item_after, detail, operation_id, rolled_back
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
        if (rolledBackState == 0) {
            sql.append(" AND rolled_back = 0");
        } else if (rolledBackState == 1) {
            sql.append(" AND rolled_back = 1");
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
                rs.getString("operation_id"),
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
            if (totalDeleted >= 50_000 && !closed) {
                io.schedule(this::cleanupSafely, 1L, TimeUnit.SECONDS);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Protect retention cleanup failed", e);
        }
    }
}
