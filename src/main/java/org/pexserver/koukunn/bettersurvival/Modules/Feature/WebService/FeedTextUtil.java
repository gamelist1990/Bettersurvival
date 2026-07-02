package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * フィード本文の共通テキスト処理。
 *
 * Web / Discord / Minecraft の 3 面で同じ見た目になるよう、
 * - 素の URL は「サイト名だけ表示してクリックで開く」形式
 * - [ラベル](URL) の Markdown リンクはラベル表示
 * - Minecraft ではクリック可能テキスト、Discord ではマスクリンクに変換
 * を一手に引き受ける。
 */
public final class FeedTextUtil {

    public static final String URL_REGEX = "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+";
    /** [label](url) 形式 or 素の URL */
    public static final Pattern LINK_OR_URL_PATTERN = Pattern.compile(
            "\\[([^\\]\\r\\n]+)]\\((" + URL_REGEX + ")\\)|(" + URL_REGEX + ")");
    /** インライン装飾 (太字/下線/打ち消し/斜体/コード) */
    private static final Pattern INLINE_STYLE_PATTERN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|__(.+?)__|~~(.+?)~~|\\*(.+?)\\*|`([^`\\r\\n]+)`");

    private FeedTextUtil() {
    }

    /** URL からホスト名だけを取り出す（www. は除去）。失敗時は URL のまま。 */
    public static String hostLabel(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return url;
            }
            return host.toLowerCase(Locale.ROOT).startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException error) {
            return url;
        }
    }

    // ================= Minecraft 表示 =================

    /**
     * フィード本文を Minecraft 向けのクリック可能な Component に変換する。
     * 素の URL はサイト名のみ表示（ホバーで完全な URL を表示）。
     */
    public static Component toMinecraftComponent(String text, NamedTextColor baseColor) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.empty();
        Matcher matcher = LINK_OR_URL_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                result = result.append(styledPlainText(text.substring(last, matcher.start()), baseColor));
            }
            String label = matcher.group(1);
            String markdownUrl = matcher.group(2);
            String plainUrl = matcher.group(3);
            String openUrl = markdownUrl != null ? markdownUrl : plainUrl;
            String displayText = label != null ? label : hostLabel(plainUrl);
            result = result.append(linkComponent(displayText, openUrl));
            last = matcher.end();
        }
        if (last < text.length()) {
            result = result.append(styledPlainText(text.substring(last), baseColor));
        }
        return result;
    }

    /** クリック可能なリンク表示（下線付き・ホバーで URL 表示）。 */
    public static Component linkComponent(String label, String url) {
        return Component.text(label)
                .color(NamedTextColor.BLUE)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY)));
    }

    /** [画像: 名前] のようなクリック可能な添付リンク。 */
    public static Component attachmentComponent(String label, String url) {
        return Component.text("[" + label + "]")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("クリックでWebで表示\n" + url, NamedTextColor.GRAY)));
    }

    /** リンク以外の部分に Markdown のインライン装飾（太字など）を適用する。 */
    private static Component styledPlainText(String text, NamedTextColor baseColor) {
        Component result = Component.empty();
        Matcher matcher = INLINE_STYLE_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                result = result.append(Component.text(text.substring(last, matcher.start()), baseColor));
            }
            if (matcher.group(1) != null) {
                result = result.append(Component.text(matcher.group(1), baseColor).decorate(TextDecoration.BOLD));
            } else if (matcher.group(2) != null) {
                result = result.append(Component.text(matcher.group(2), baseColor).decorate(TextDecoration.UNDERLINED));
            } else if (matcher.group(3) != null) {
                result = result.append(Component.text(matcher.group(3), baseColor).decorate(TextDecoration.STRIKETHROUGH));
            } else if (matcher.group(4) != null) {
                result = result.append(Component.text(matcher.group(4), baseColor).decorate(TextDecoration.ITALIC));
            } else if (matcher.group(5) != null) {
                result = result.append(Component.text(matcher.group(5), NamedTextColor.WHITE));
            }
            last = matcher.end();
        }
        if (last < text.length()) {
            result = result.append(Component.text(text.substring(last), baseColor));
        }
        return result;
    }

    // ================= Discord 表示 =================

    /**
     * フィード本文を Discord 向けに変換する。
     * 素の URL は [サイト名](URL) のマスクリンクにし、既存の Markdown はそのまま通す。
     */
    public static String toDiscordContent(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Matcher matcher = LINK_OR_URL_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            sb.append(text, last, matcher.start());
            String plainUrl = matcher.group(3);
            if (plainUrl != null) {
                sb.append('[').append(hostLabel(plainUrl)).append("](").append(plainUrl).append(')');
            } else {
                sb.append(matcher.group());
            }
            last = matcher.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    // ================= data URL =================

    /** data:image/...;base64,... をデコードする。画像以外・不正な場合は null。 */
    public static byte[] decodeImageDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/")) {
            return null;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).endsWith(";base64")) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    /** data URL の MIME タイプ（画像のみ許可）。不正な場合は null。 */
    public static String imageDataUrlMime(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/")) {
            return null;
        }
        int semicolon = dataUrl.indexOf(';');
        if (semicolon <= 5) {
            return null;
        }
        String mime = dataUrl.substring(5, semicolon).toLowerCase(Locale.ROOT);
        return mime.matches("image/(png|jpeg|jpg|gif|webp)") ? mime : null;
    }

    /** MIME タイプに対応する拡張子。 */
    public static String extensionForMime(String mime) {
        if (mime == null) {
            return "png";
        }
        return switch (mime) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    /** ファイル名として安全な形に整える。 */
    public static String sanitizeFileName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (cleaned.isBlank()) {
            return fallback;
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
