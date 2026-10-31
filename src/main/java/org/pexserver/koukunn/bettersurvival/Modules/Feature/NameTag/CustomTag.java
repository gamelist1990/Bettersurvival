package org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag;

/** Validation for a player's literal, at most four-code-point custom tag. */
public record CustomTag(String value) {
    public CustomTag {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 4
                || value.codePoints().anyMatch(c -> c == '§' || Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.SURROGATE)) {
            throw new IllegalArgumentException("タグは制御文字を含まない1〜4文字で指定してください");
        }
    }
}
