package org.pexserver.koukunn.bettersurvival.Commands.protect;

import org.junit.jupiter.api.Test;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectAction;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProtectQueryTest {

    @Test
    void parsesCompoundDuration() {
        assertEquals(
                TimeUnit.DAYS.toMillis(9) + TimeUnit.HOURS.toMillis(3) + TimeUnit.MINUTES.toMillis(15),
                ProtectQuery.parseDuration("1w2d3h15m"));
    }

    @Test
    void parsesCoreProtectStyleFilters() {
        ProtectQuery query = ProtectQuery.parse(new String[]{
                "rollback",
                "user:Steve",
                "time:2h",
                "radius:100",
                "action:break,place,explosion",
                "limit:5000",
                "page:2",
                "world:world",
                "x:12",
                "y:-20",
                "z:30",
                "preview:true"
        }, 1, TimeUnit.HOURS.toMillis(24));

        assertEquals("Steve", query.user());
        assertEquals(TimeUnit.HOURS.toMillis(2), query.durationMs());
        assertEquals(100, query.radius());
        assertEquals(5000, query.limit());
        assertEquals(2, query.page());
        assertTrue(query.preview());
        assertEquals("world", query.world());
        assertEquals(12, query.x());
        assertEquals(-20, query.y());
        assertEquals(30, query.z());
        assertEquals(
                java.util.Set.of(
                        ProtectAction.BLOCK_BREAK,
                        ProtectAction.BLOCK_PLACE,
                        ProtectAction.EXPLOSION),
                query.actions());
    }

    @Test
    void expandsActionPresets() {
        ProtectQuery query = ProtectQuery.parse(
                new String[]{"lookup", "action:liquid,fire"},
                1,
                TimeUnit.HOURS.toMillis(1));

        assertTrue(query.actions().contains(ProtectAction.LIQUID_PLACE));
        assertTrue(query.actions().contains(ProtectAction.LIQUID_REMOVE));
        assertTrue(query.actions().contains(ProtectAction.LIQUID_FLOW));
        assertTrue(query.actions().contains(ProtectAction.FIRE_IGNITE));
        assertTrue(query.actions().contains(ProtectAction.FIRE_BURN));
        assertTrue(query.actions().contains(ProtectAction.FIRE_FADE));
    }

    @Test
    void rejectsPartialCoordinatesAndInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                ProtectQuery.parse(new String[]{"lookup", "x:1", "y:2"}, 1, 1000L));
        assertThrows(IllegalArgumentException.class, () -> ProtectQuery.parseDuration("2hours"));
        assertThrows(IllegalArgumentException.class, () -> ProtectQuery.parseDuration("0m"));
    }

    @Test
    void normalizesAllUserToNoFilter() {
        ProtectQuery query = ProtectQuery.parse(
                new String[]{"lookup", "user:*"},
                1,
                TimeUnit.HOURS.toMillis(1));
        assertNull(query.user());
    }
}
