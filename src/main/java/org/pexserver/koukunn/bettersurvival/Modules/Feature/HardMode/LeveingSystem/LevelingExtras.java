package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Paper版独自のプロフィール・進捗・ランキング・解放通知。 */
public final class LevelingExtras {
    private final LevelingSystemModule leveling;
    private final Map<String, Integer> observedLevels = new HashMap<>();

    public LevelingExtras(Loader plugin, LevelingSystemModule leveling) {
        this.leveling = leveling;
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanSkillUnlocks, 20L, 20L);
    }

    public int totalLevel(Player player) {
        int total = 0;
        for (LevelingAptitude aptitude : LevelingAptitude.values()) total += leveling.getLevel(player, aptitude);
        return total;
    }

    public long unlockedSkillCount(Player player) {
        return List.of(LevelingSkill.values()).stream()
                .filter(skill -> leveling.getLevel(player, skill.aptitude()) >= skill.requiredLevel())
                .count();
    }

    public void sendProfile(Player player) {
        String scope = leveling.dataScope(player);
        player.sendMessage("§6========== §dレベリングプロフィール §6==========");
        player.sendMessage("§7ワールドグループ: §b" + scope);
        player.sendMessage("§7総合レベル: §a" + totalLevel(player) + "§7/§a" + (LevelingAptitude.MAX_LEVEL * LevelingAptitude.values().length));
        player.sendMessage("§7解放済みスキル: §e" + unlockedSkillCount(player) + "§7/§e" + LevelingSkill.values().length);
        for (LevelingAptitude aptitude : LevelingAptitude.values()) {
            int level = leveling.getLevel(player, aptitude);
            player.sendMessage("§f" + aptitude.abbreviation() + " §7- §e" + aptitude.displayName()
                    + "§7: §aLv." + level + " §8(" + aptitude.rank(level) + ")");
        }
        LevelingSkill next = nextSkill(player);
        if (next != null) {
            int current = leveling.getLevel(player, next.aptitude());
            player.sendMessage("§7次のスキル: §d" + next.displayName() + " §7- "
                    + next.aptitude().abbreviation() + " Lv." + next.requiredLevel()
                    + " §8(あと" + Math.max(0, next.requiredLevel() - current) + "レベル)");
        } else {
            player.sendMessage("§6全スキル解放済み！");
        }
    }

    public void sendSkills(Player player) {
        player.sendMessage("§6========== §dレベリングスキル §6==========");
        for (LevelingAptitude aptitude : LevelingAptitude.values()) {
            int level = leveling.getLevel(player, aptitude);
            player.sendMessage("§e" + aptitude.displayName() + " §7Lv." + level);
            for (LevelingSkill skill : LevelingSkill.values()) {
                if (skill.aptitude() != aptitude) continue;
                boolean unlocked = level >= skill.requiredLevel();
                player.sendMessage((unlocked ? "§a✔ " : "§c✖ ") + "§f" + skill.displayName()
                        + " §7(必要Lv." + skill.requiredLevel() + ")");
            }
        }
    }

    public void sendTop(Player viewer) {
        String scope = leveling.dataScope(viewer);
        List<? extends Player> ranking = Bukkit.getOnlinePlayers().stream()
                .filter(player -> leveling.dataScope(player).equals(scope))
                .sorted(Comparator.comparingInt(this::totalLevel).reversed()
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(10)
                .toList();

        viewer.sendMessage("§6========== §dレベリングランキング §7[§b" + scope + "§7] §6==========");
        if (ranking.isEmpty()) {
            viewer.sendMessage("§7ランキング対象のプレイヤーがいません。");
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            Player player = ranking.get(i);
            String mark = player.getUniqueId().equals(viewer.getUniqueId()) ? " §a← あなた" : "";
            viewer.sendMessage("§e#" + (i + 1) + " §f" + player.getName()
                    + " §7- 総合Lv. §a" + totalLevel(player) + mark);
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
                        player.sendMessage("§6✦ 新しいスキルを解放しました: §d" + skill.displayName()
                                + " §7(" + aptitude.abbreviation() + " Lv." + skill.requiredLevel() + ")");
                        player.sendActionBar(ComponentUtils.legacy("§dスキル解放: §f" + skill.displayName()));
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
