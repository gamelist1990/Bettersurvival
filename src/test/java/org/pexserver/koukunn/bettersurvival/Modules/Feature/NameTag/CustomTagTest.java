package org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomTagTest {
    @Test void acceptsJapaneseAndSupplementaryCharacters() {
        assertEquals("冒険者", new CustomTag("冒険者").value());
        assertEquals("😀😀😀😀", new CustomTag("😀😀😀😀").value());
    }

    @Test void rejectsTooLongEmptyAndFormatting() {
        for (String value : new String[]{"", " ", "12345", "😀😀😀😀😀", "§c", "a\nb", "\uD800"}) {
            assertThrows(IllegalArgumentException.class, () -> new CustomTag(value));
        }
    }
}
