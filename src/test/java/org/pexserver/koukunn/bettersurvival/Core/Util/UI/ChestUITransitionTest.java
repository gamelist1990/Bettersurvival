package org.pexserver.koukunn.bettersurvival.Core.Util.UI;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChestUITransitionTest {
    @Test
    void bedrockTransitionsAllowTimeForThePreviousContainerToClose() {
        assertEquals(10, ChestUI.transitionDelayTicks(true, true, 1));
        assertEquals(14, ChestUI.transitionDelayTicks(true, true, 14));
        assertEquals(1, ChestUI.transitionDelayTicks(false, true, 0));
        assertEquals(1, ChestUI.transitionDelayTicks(true, false, 1));
    }
}
