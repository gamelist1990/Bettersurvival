package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保護エリアごとの動作カスタム設定。
 * 部外者（オーナー・パーティー・ホワイトリスト以外）への制限と、
 * エリア侵入時の通知方法を保持する。
 */
public class ClaimSettings {

    /** 侵入通知の方法 */
    public enum NotifyMode {
        TITLE("§eタイトル+サブタイトル"),
        ACTIONBAR("§eアクションバー"),
        NONE("§7通知しない");

        private final String displayName;

        NotifyMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public NotifyMode next() {
            NotifyMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static final String PLACEHOLDER_OWNER = "%owner%";

    private boolean blockContainers = true;
    private boolean blockDoors = true;
    private boolean blockSwitches = true;
    private boolean blockPlace = true;
    private boolean blockBreak = true;
    private NotifyMode notifyMode = NotifyMode.ACTIONBAR;
    private String titleText = "§c%owner%§fの保護エリア";
    private String subtitleText = "§7許可なく操作はできません";
    private String actionbarText = "§e%owner%の保護エリアに入りました";

    public boolean isBlockContainers() {
        return blockContainers;
    }

    public void setBlockContainers(boolean blockContainers) {
        this.blockContainers = blockContainers;
    }

    public boolean isBlockDoors() {
        return blockDoors;
    }

    public void setBlockDoors(boolean blockDoors) {
        this.blockDoors = blockDoors;
    }

    public boolean isBlockSwitches() {
        return blockSwitches;
    }

    public void setBlockSwitches(boolean blockSwitches) {
        this.blockSwitches = blockSwitches;
    }

    public boolean isBlockPlace() {
        return blockPlace;
    }

    public void setBlockPlace(boolean blockPlace) {
        this.blockPlace = blockPlace;
    }

    public boolean isBlockBreak() {
        return blockBreak;
    }

    public void setBlockBreak(boolean blockBreak) {
        this.blockBreak = blockBreak;
    }

    public NotifyMode getNotifyMode() {
        return notifyMode;
    }

    public void setNotifyMode(NotifyMode notifyMode) {
        this.notifyMode = notifyMode == null ? NotifyMode.NONE : notifyMode;
    }

    public String getTitleText() {
        return titleText;
    }

    public void setTitleText(String titleText) {
        this.titleText = sanitizeText(titleText);
    }

    public String getSubtitleText() {
        return subtitleText;
    }

    public void setSubtitleText(String subtitleText) {
        this.subtitleText = sanitizeText(subtitleText);
    }

    public String getActionbarText() {
        return actionbarText;
    }

    public void setActionbarText(String actionbarText) {
        this.actionbarText = sanitizeText(actionbarText);
    }

    /** 保存フォーマットを壊す制御文字を除去する。 */
    private static String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    // ================= 永続化 =================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("blockContainers", blockContainers);
        map.put("blockDoors", blockDoors);
        map.put("blockSwitches", blockSwitches);
        map.put("blockPlace", blockPlace);
        map.put("blockBreak", blockBreak);
        map.put("notifyMode", notifyMode.name());
        map.put("titleText", titleText);
        map.put("subtitleText", subtitleText);
        map.put("actionbarText", actionbarText);
        return map;
    }

    public static ClaimSettings fromMap(Map<String, Object> map) {
        ClaimSettings settings = new ClaimSettings();
        if (map == null) {
            return settings;
        }
        settings.blockContainers = readBool(map.get("blockContainers"), true);
        settings.blockDoors = readBool(map.get("blockDoors"), true);
        settings.blockSwitches = readBool(map.get("blockSwitches"), true);
        settings.blockPlace = readBool(map.get("blockPlace"), true);
        settings.blockBreak = readBool(map.get("blockBreak"), true);
        settings.notifyMode = readMode(map.get("notifyMode"));
        if (map.get("titleText") instanceof String s) {
            settings.titleText = s;
        }
        if (map.get("subtitleText") instanceof String s) {
            settings.subtitleText = s;
        }
        if (map.get("actionbarText") instanceof String s) {
            settings.actionbarText = s;
        }
        return settings;
    }

    /** アイテムPDCへ格納するためのコンパクト文字列に変換する。 */
    public String toCompact() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : toMap().entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(entry.getKey()).append('\t').append(entry.getValue());
        }
        return sb.toString();
    }

    public static ClaimSettings fromCompact(String compact) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (compact != null && !compact.isEmpty()) {
            for (String line : compact.split("\n")) {
                int idx = line.indexOf('\t');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx);
                String value = line.substring(idx + 1);
                if ("true".equals(value) || "false".equals(value)) {
                    map.put(key, Boolean.parseBoolean(value));
                } else {
                    map.put(key, value);
                }
            }
        }
        return fromMap(map);
    }

    private static boolean readBool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private static NotifyMode readMode(Object value) {
        if (value instanceof String s) {
            try {
                return NotifyMode.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return NotifyMode.ACTIONBAR;
    }
}
