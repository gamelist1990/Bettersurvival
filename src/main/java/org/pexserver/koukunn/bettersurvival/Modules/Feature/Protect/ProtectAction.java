package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

/**
 * Protect が永続化する操作種別。
 */
public enum ProtectAction {
    BLOCK_BREAK(true),
    BLOCK_PLACE(true),
    BLOCK_PHYSICS(true),
    BLOCK_MOVE(true),
    LIQUID_PLACE(true),
    LIQUID_REMOVE(true),
    LIQUID_FLOW(true),
    FIRE_IGNITE(true),
    FIRE_BURN(true),
    FIRE_FADE(true),
    EXPLOSION(true),
    ENTITY_CHANGE(true),
    LEAF_DECAY(true),
    GROWTH(true),
    SCULK_SPREAD(true),
    PORTAL_CREATE(true),
    FARMLAND_TRAMPLE(true),
    NATURAL_FORM(true),
    POT_CHANGE(true),
    BRUSH(false),
    CUSTOM_BLOCK(true),

    CONTAINER_OPEN(false),
    CONTAINER_CLOSE(false),
    CONTAINER_CHANGE(true),
    CONTAINER_TRANSFER(false),

    ITEM_DROP(false),
    ITEM_PICKUP(false),
    ITEM_BREAK(false),
    ITEM_CRAFT(false),
    ITEM_SHOOT(false),
    ITEM_TRADE(false),
    ITEM_INTERACT(false);

    private final boolean reversible;

    ProtectAction(boolean reversible) {
        this.reversible = reversible;
    }

    public boolean reversible() {
        return reversible;
    }

    public boolean isBlockMutation() {
        return switch (this) {
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_PHYSICS, BLOCK_MOVE,
                    LIQUID_PLACE, LIQUID_REMOVE, LIQUID_FLOW,
                    FIRE_IGNITE, FIRE_BURN, FIRE_FADE,
                    EXPLOSION, ENTITY_CHANGE, LEAF_DECAY, GROWTH,
                    SCULK_SPREAD, PORTAL_CREATE, FARMLAND_TRAMPLE,
                    NATURAL_FORM, POT_CHANGE, CUSTOM_BLOCK -> true;
            default -> false;
        };
    }

    public boolean isContainerAction() {
        return this == CONTAINER_OPEN
                || this == CONTAINER_CLOSE
                || this == CONTAINER_CHANGE
                || this == CONTAINER_TRANSFER;
    }

    public boolean isItemAction() {
        return switch (this) {
            case ITEM_DROP, ITEM_PICKUP, ITEM_BREAK, ITEM_CRAFT,
                    ITEM_SHOOT, ITEM_TRADE, ITEM_INTERACT -> true;
            default -> false;
        };
    }
}
