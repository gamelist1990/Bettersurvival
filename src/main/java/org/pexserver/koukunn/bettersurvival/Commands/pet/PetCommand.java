package org.pexserver.koukunn.bettersurvival.Commands.pet;

import org.bukkit.command.CommandSender;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Pet.PetModule;

import java.util.List;
import java.util.Objects;

/** Pet化に使う合成素材を案内する。 */
public final class PetCommand extends BaseCommand {
    private final PetModule module;

    public PetCommand(PetModule module) {
        this.module = Objects.requireNonNull(module, "PetModule must be initialized before PetCommand");
    }

    @Override public String getName() { return "pet"; }
    @Override public String getDescription() { return "Petの対象モブと合成素材を確認"; }
    @Override public String getUsage() { return "/pet <list|recipe モブ名>"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§6Pet対応モブ: §f" + String.join(", ", module.supportedTypes()));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("recipe")) {
            String recipe = module.describeRecipe(args[1]);
            if (recipe == null) sendError(sender, "未対応のモブです。/pet list で一覧を確認してください");
            else sender.sendMessage("§6" + args[1] + "用 なかよし素材: §f" + recipe + "§7（2アイテムを近くに投げて合成）");
            return true;
        }
        sender.sendMessage("§e/pet list §7- 対応モブ一覧");
        sender.sendMessage("§e/pet recipe <モブ名> §7- 合成素材を表示");
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("list", "recipe");
        if (args.length == 2 && args[0].equalsIgnoreCase("recipe")) return module.supportedTypes();
        return List.of();
    }
}
