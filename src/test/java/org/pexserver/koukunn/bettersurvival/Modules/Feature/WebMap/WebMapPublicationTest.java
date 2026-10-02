package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import org.junit.jupiter.api.Test;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WebMapPublicationTest {
    @Test
    void selectedGroupsCanExcludeDefaultAndIncludeMultipleOtherworlds() {
        WebMapSettings settings = new WebMapSettings();
        settings.setPublicationMode("selected");
        settings.setPublicationGroups(Set.of("survival", "creative"));
        assertFalse(settings.isGroupPublished("default"));
        assertTrue(settings.isGroupPublished("SURVIVAL"));
        assertTrue(settings.isGroupPublished("creative"));
        assertFalse(settings.isGroupPublished("private"));
    }

    @Test
    void singleGroupDoesNotPublishOtherGroups() {
        WebMapSettings settings = new WebMapSettings();
        settings.setPublicationMode("group");
        settings.setPublicationGroup("survival");
        assertTrue(settings.isGroupPublished("survival"));
        assertFalse(settings.isGroupPublished("default"));
        assertFalse(settings.isGroupPublished("creative"));
    }

    @Test
    void lobbyIsExcludedEvenFromAllOrExplicitSelection() {
        WebMapSettings settings = new WebMapSettings();
        for (String mode : List.of("all", "group", "selected")) {
            settings.setPublicationMode(mode);
            settings.setPublicationGroup("selection-lobby");
            settings.setPublicationGroups(Set.of("selection-lobby"));
            assertFalse(settings.isGroupPublished("selection-lobby"));
        }
        settings.setPublicationMode("all");
        assertTrue(settings.isGroupPublished("survival"));
        assertFalse(settings.isGroupPublished(null));
    }

    @Test
    void legacySelectionRetainsPreviouslyImplicitDefault() {
        PEXConfig config = new PEXConfig();
        config.put("publicationMode", "selected");
        config.put("publicationGroups", List.of("survival"));
        WebMapSettings settings = new WebMapStore(null).toSettings(config);
        assertTrue(settings.isGroupPublished("default"));
        assertTrue(settings.isGroupPublished("survival"));
    }

    @Test
    void explicitSelectionAndPlayerPrivacySurviveSettingsReload() {
        PEXConfig config = new PEXConfig();
        config.put("publicationVersion", 2);
        config.put("publicationMode", "selected");
        config.put("publicationGroups", List.of("survival"));
        config.put("dimensions", Map.of("minecraft:survival", Map.of("visible", true, "showPlayers", false)));
        WebMapSettings settings = new WebMapStore(null).toSettings(config);
        assertFalse(settings.isGroupPublished("default"));
        assertTrue(settings.isGroupPublished("survival"));
        assertTrue(settings.getDimensions().get("minecraft:survival").isVisible());
        assertFalse(settings.getDimensions().get("minecraft:survival").isShowPlayers());
    }

    @Test
    void emptyExplicitSelectionPublishesNoGroupsAfterReload() {
        PEXConfig config = new PEXConfig();
        config.put("publicationVersion", 2);
        config.put("publicationMode", "selected");
        config.put("publicationGroups", List.of());
        WebMapSettings settings = new WebMapStore(null).toSettings(config);
        assertFalse(settings.isGroupPublished("default"));
        assertFalse(settings.isGroupPublished("survival"));
    }

    @Test
    void friendlyOtherworldLabelsPreserveExistingApiIdentifiers() {
        WebMapHttpSnapshot.WorldView world = new WebMapHttpSnapshot.WorldView(
                "otherworld:generated_abcd", "generated_abcd", "生活 / ネザー", "u_751f_6d3b", "生活",
                false, "nether", "NETHER", 0, 0, true, true);
        WebMapHttpSnapshot snapshot = new WebMapHttpSnapshot(List.of(world),
                WebMapHttpSnapshot.indexWorlds(List.of(world)), List.of(), 20, "Server", "", "", 0);
        assertSame(world, snapshot.findWorld("generated_abcd"));
        assertSame(world, snapshot.findWorld("otherworld:generated_abcd"));
        assertEquals("生活 / ネザー", snapshot.findWorld("generated_abcd").displayName());
        assertEquals("生活", snapshot.findWorld("generated_abcd").groupDisplayName());
        assertFalse(snapshot.findWorld("generated_abcd").showPlayers());
    }
}
