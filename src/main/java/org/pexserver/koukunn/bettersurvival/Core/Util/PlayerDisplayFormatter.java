package org.pexserver.koukunn.bettersurvival.Core.Util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.Party;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Party と Leveling 称号を一つの表示名へ安全に合成する共通サービス。 */
public final class PlayerDisplayFormatter {
    private static volatile PlayerDisplayFormatter instance;

    private final Loader plugin;
    private final Map<UUID, String> levelingTitles = new ConcurrentHashMap<>();

    private PlayerDisplayFormatter(Loader plugin) {
        this.plugin = plugin;
    }

    public static PlayerDisplayFormatter get(Loader plugin) {
        PlayerDisplayFormatter current = instance;
        if (current != null) return current;
        synchronized (PlayerDisplayFormatter.class) {
            if (instance == null) instance = new PlayerDisplayFormatter(plugin);
            return instance;
        }
    }

    public void setLevelingTitle(Player player, String title) {
        if (player == null) return;
        if (title == null || title.isBlank()) levelingTitles.remove(player.getUniqueId());
        else levelingTitles.put(player.getUniqueId(), title);
        refresh(player);
    }

    public void clearLevelingTitle(Player player) {
        if (player == null) return;
        levelingTitles.remove(player.getUniqueId());
        refresh(player);
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;

        StringBuilder legacy = new StringBuilder();
        String title = levelingTitles.get(player.getUniqueId());
        if (title != null && !title.isBlank()) {
            legacy.append("§7[§6").append(title).append("§7] ");
        }

        Party party = plugin.getPartyModule() == null ? null : plugin.getPartyModule().getPartyOf(player.getUniqueId());
        if (party != null) {
            String color = party.isNameTagColor() ? party.getColor().getLegacyCode() : "§f";
            legacy.append(color);
            if (party.isNameTagPrefix()) legacy.append("[").append(party.getName()).append("] ");
        } else {
            legacy.append("§f");
        }
        legacy.append(player.getName());

        Component component = ComponentUtils.legacy(legacy.toString());
        player.displayName(component);
        player.playerListName(component);
    }
}
