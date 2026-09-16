package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paper版独自の補助機能。Just Leveling本体の進行値は変更せず、
 * プロフィール・スキル進捗・Otherworld単位ランキング・解放通知を提供する。
 */
public final class LevelingExtras {
    private final LevelingSystemModule leveling;
    private final Map<String, Integer> observedLevels = new HashMap<>();

    public LevelingExtras(Loader plugin, LevelingSystemModule leveling) {
        this.leveling = leveling;
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanSkillUnlocks, 20L, 20L);
    }

    public int totalLevel(Player player) {
        int total = 0;
        for (LevelingAptitude aptitude : LevelingAptitude.values()) {
            total += leveling.getLevel(player, aptitude);
        }
        return total;
    }

    public long unlockedSkillCount(Player player) {
        return List.of(LevelingSkill.values()).stream()
                .filter(skill -> leveling.getLevel(player, skill.aptitude()) >= skill.requiredLevel())
                .count();
    }

    public void sendProfile(Player player) {
        String scope = leveling.dataScope(player);
        player.sendMessage("§6========== §dLeveling Profile §6==========");
        player.sendMessage("§7World group: §b" + scope);
        player.sendMessage("§7Total Level: §a" + totalLevel(player) + "§7/§a" + (LevelingAptitude.MAX_LEVEL * LevelingAptitude.values().length));
        player.sendMessage("§7Skills: §e" + unlockedSkillCount(player) + "§7/§e" + LevelingSkill.values().length);
        for (LevelingAptitude aptitude : LevelingAptitude.values()) {
            int level = leveling.getLevel(player, aptitude);
            player.sendMessage("§f" + aptitude.abbreviation() + " §7- §e" + aptitude.displayName()
                    + "§7: §aLv." + level + " §8(" + aptitude.rank(level) + ")");
        }
        LevelingSkill next = nextSkill(player);
        if (next != null) {
            int current = leveling.getLevel(player, next.aptitude());
            player.sendMessage("§7Next Skill: §d" + next.displayName() + " §7- "
                    + next.aptitude().abbreviation() + " Lv." + next.requiredLevel()
                    + " §8(" + Math.max(0, next.requiredLevel() - current) + " levels left)");
        } else {
            player.sendMessage("§6All skills unlocked!");
        }
    }

    public void sendSkills(Player player) {
        player.sendMessage("§6========== §dLeveling Skills §6==========");
        for (LevelingAptitude aptitude : LevelingAptitude.values()) {
            int level = leveling.getLevel(player, aptitude);
            player.sendMessage("§e" + aptitude.displayName() + " §7Lv." + level);
            for (LevelingSkill skill : LevelingSkill.values()) {
                if (skill.aptitude() != aptitude) continue;
                boolean unlocked = level >= skill.requiredLevel();
                player.sendMessage((unlocked ? "§a✔ " : "§c✖ ") + "§f" + skill.displayName()
                        + " §7(Lv." + skill.requiredLevel() + ")");
            }
        }
    }

    public void sendTop(Player viewer) {
        String scope = leveling.dataScope(viewer);
        List<Player> ranking = Bukkit.getOnlinePlayers().stream()
                .filter(player -> leveling.dataScope(player).equals(scope))
                .sorted(Comparator.comparingInt(this::totalLevel).reversed()
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(10)
                .toList();

        viewer.sendMessage("§6========== §dLeveling TOP §7[§b" + scope + "§7] §6==========");
        if (ranking.isEmpty()) {
            viewer.sendMessage("§7ランキング対象のプレイヤーがいません。");
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            Player player = ranking.get(i);
            String mark = player.getUniqueId().equals(viewer.getUniqueId()) ? " §a← You" : "";
            viewer.sendMessage("§e#" + (i + 1) + " §f" + player.getName()
                    + " §7- Total §a" + totalLevel(player) + mark);
        }
        viewer.sendMessage("§8※ 同じOtherworldグループ内のオンラインプレイヤーのみ集計");
    }

    private void scanSkillUnlocks() {
        if (!leveling.isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String scope = leveling.dataScope(player);
            for (LevelingAptitude aptitude : LevelingAptitude.values()) {
                int current = leveling.getLevel(player, aptitude);
                String key = scope + ":" + player.getUniqueId() + ":" + aptitude.key();
                Integer previous = observedLevels.put(key, current);
                if (previous == null || current <= previous) continue;

                for (LevelingSkill skill : LevelingSkill.values()) {
                    if (skill.aptitude() != aptitude) continue;
                    if (skill.requiredLevel() > previous && skill.requiredLevel() <= current) {
                        player.sendMessage("§6✦ New Skill Unlocked: §d" + skill.displayName()
                                + " §7(" + aptitude.abbreviation() + " Lv." + skill.requiredLevel() + ")");
                        player.sendActionBar("§dSkill Unlocked: §f" + skill.displayName());
                    }
                }
            }
        }
    }

    private LevelingSkill nextSkill(Player player) {
        return List.of(LevelingSkill.values()).stream()
                .filter(skill -> leveling.getLevel(player, skill.aptitude()) < skill.requiredLevel())
                .min(Comparator.comparingInt(skill -> skill.requiredLevel() - leveling.getLevel(player, skill.aptitude())))
                .orElse(null);
    }
}
