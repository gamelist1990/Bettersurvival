package org.pexserver.koukunn.bettersurvival.Modules.Feature.Motd;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;
import org.pexserver.koukunn.bettersurvival.Core.Config.JsonUtils;
import org.pexserver.koukunn.bettersurvival.Loader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * プラグインフォルダ内の {@code motd/} フォルダを使って、サーバーリスト表示 (MOTD) と
 * サーバーアイコンをカスタマイズするモジュール。
 * <ul>
 *     <li>{@code motd/icon.png} (64x64 PNG) を置くと、自動でサーバーアイコンに設定される。</li>
 *     <li>{@code motd/motd.json} で MOTD の1行目・2行目を設定できる。{@code §} や {@code &} の色記号が使える。</li>
 *     <li>複数の MOTD を定義して {@code random: true} にすると、接続ごとにランダムで切り替わる。</li>
 * </ul>
 */
public class MotdModule implements Listener {
    private final Loader plugin;
    private final File folder;

    private volatile CachedServerIcon icon;
    private volatile MotdConfig config;

    public MotdModule(Loader plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "motd");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("[Motd] motd フォルダの作成に失敗しました: " + folder.getPath());
        }
        reload();
    }

    /** icon.png と motd.json を読み込み直す。 */
    public void reload() {
        loadIcon();
        loadConfig();
    }

    private void loadIcon() {
        File iconFile = findIconFile();
        if (iconFile == null) {
            this.icon = null;
            return;
        }
        try {
            BufferedImage source = ImageIO.read(iconFile);
            if (source == null) {
                this.icon = null;
                plugin.getLogger().warning("[Motd] " + iconFile.getName() + " を画像として読み込めませんでした");
                return;
            }
            BufferedImage normalized = normalizeTo64(source);
            this.icon = plugin.getServer().loadServerIcon(normalized);
            if (source.getWidth() == 64 && source.getHeight() == 64) {
                plugin.getLogger().info("[Motd] サーバーアイコンを設定しました (motd/" + iconFile.getName() + ")");
            } else {
                plugin.getLogger().info("[Motd] サーバーアイコンを 64x64 にリサイズして設定しました (" + source.getWidth() + "x" + source.getHeight() + " → 64x64, motd/" + iconFile.getName() + ")");
            }
        } catch (Exception error) {
            this.icon = null;
            plugin.getLogger().warning("[Motd] アイコンの読み込みに失敗しました: " + error.getMessage());
        }
    }

    /** motd フォルダ内から使用可能なアイコン画像を探す。優先: icon.png → icon.jpg → icon.jpeg → 最初に見つかった対応画像。 */
    private File findIconFile() {
        String[] preferred = {"icon.png", "icon.jpg", "icon.jpeg"};
        for (String name : preferred) {
            File file = new File(folder, name);
            if (file.isFile()) {
                return file;
            }
        }
        File[] children = folder.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (!child.isFile()) continue;
            String lower = child.getName().toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return child;
            }
        }
        return null;
    }

    /** 任意サイズの画像を 64x64 の ARGB BufferedImage に整形する。既に 64x64 でも ARGB に統一しておく。 */
    private BufferedImage normalizeTo64(BufferedImage source) {
        BufferedImage target = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, 64, 64, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private void loadConfig() {
        File configFile = new File(folder, "motd.json");
        if (!configFile.isFile()) {
            this.config = writeDefaultConfig(configFile);
            return;
        }
        try {
            MotdConfig loaded = JsonUtils.fromJson(configFile, MotdConfig.class);
            if (loaded == null || loaded.motds == null) {
                this.config = writeDefaultConfig(configFile);
                return;
            }
            // null 行を空文字に正規化しておく。
            loaded.motds.removeIf(entry -> entry == null);
            for (MotdEntry entry : loaded.motds) {
                if (entry.line1 == null) entry.line1 = "";
                if (entry.line2 == null) entry.line2 = "";
            }
            this.config = loaded;
        } catch (IOException error) {
            plugin.getLogger().warning("[Motd] motd.json の読み込みに失敗しました: " + error.getMessage());
            this.config = new MotdConfig();
        }
    }

    private MotdConfig writeDefaultConfig(File configFile) {
        MotdConfig defaults = new MotdConfig();
        defaults.motds.add(new MotdEntry(
                "&6&lBetterSurvival &7» &fWelcome back!",
                "&7ゆったり遊べるサバイバルサーバー &a▸ &fjoin now"));
        defaults.motds.add(new MotdEntry(
                "&b&lBetterSurvival &7» &fさぁ、冒険を続けよう",
                "&8今日も良い一日を &7| &d§oランダムMOTD対応"));
        try {
            JsonUtils.toJson(configFile, defaults);
        } catch (IOException error) {
            plugin.getLogger().warning("[Motd] motd.json の初期生成に失敗しました: " + error.getMessage());
        }
        return defaults;
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        CachedServerIcon currentIcon = this.icon;
        if (currentIcon != null) {
            try {
                event.setServerIcon(currentIcon);
            } catch (Exception ignored) {
                // 一部の実装ではアイコン設定が未対応。MOTD 側は続行する。
            }
        }

        MotdConfig currentConfig = this.config;
        if (currentConfig == null || !currentConfig.enabled || currentConfig.motds == null || currentConfig.motds.isEmpty()) {
            return;
        }
        MotdEntry entry = pickEntry(currentConfig);
        if (entry == null) {
            return;
        }
        String line1 = colorize(entry.line1);
        String line2 = colorize(entry.line2);
        String legacy = line2.isEmpty() ? line1 : line1 + "\n" + line2;
        event.motd(LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    private MotdEntry pickEntry(MotdConfig currentConfig) {
        List<MotdEntry> list = currentConfig.motds;
        if (list.size() == 1 || !currentConfig.random) {
            return list.get(0);
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** {@code &} を色記号 {@code §} へ変換する。既に {@code §} で書かれている部分はそのまま使える。 */
    private String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /** motd.json のルート構造。 */
    public static class MotdConfig {
        public String _help = "line1/line2 で MOTD の1行目・2行目を設定します。§ または & で色記号が使えます (例: &6 は金色)。"
                + " motds に複数のブロックを定義して random=true にすると接続ごとにランダム切り替え、false なら先頭を常に使用します。"
                + " このフォルダに icon.png (または icon.jpg / icon.jpeg) を置くとサーバーアイコンになります。サイズが 64x64 でなくても自動でリサイズされます。";
        public boolean enabled = false;
        public boolean random = true;
        public List<MotdEntry> motds = new ArrayList<>();
    }

    /** MOTD の1エントリ (2行分)。 */
    public static class MotdEntry {
        public String line1 = "";
        public String line2 = "";

        public MotdEntry() {
        }

        public MotdEntry(String line1, String line2) {
            this.line1 = line1;
            this.line2 = line2;
        }
    }
}
