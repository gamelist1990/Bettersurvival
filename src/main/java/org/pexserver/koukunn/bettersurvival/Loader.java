package org.pexserver.koukunn.bettersurvival;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandBlockManager;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandManager;
import org.pexserver.koukunn.bettersurvival.Core.Command.GlobalCommandFilter;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Listeners.CommandBlockerListener;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleListener;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.TreeMine.TreeMineModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFeed.AutoFeedModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFishing.AutoFishingModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AnythingFeed.AnythingFeedModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant.AutoPlantModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine.OreMineModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort.ChestSortModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DeathChest.DeathChestModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Home.HomeModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin.BedrockSkinModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.BetterMenu.BetterMenuModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.EnchantmentSplit.EnchantmentSplitModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.SharedStorageModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.CopperGolemModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.GeyserWorkbench.GeyserWorkbenchModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ParallelFurnace.ParallelFurnaceModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Recycler.RecyclerModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.AirDash.AirDashModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.CustomEnchantTableModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WarpStone.WarpStoneModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.KeepAliveGuard.KeepAliveGuardModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.JpCh.JpChModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.ui.PartyMenu;
import org.pexserver.koukunn.bettersurvival.Commands.help.HelpCommand;
import org.pexserver.koukunn.bettersurvival.Commands.command.CommandCommand;
import org.pexserver.koukunn.bettersurvival.Commands.discord.DiscordCommand;
import org.pexserver.koukunn.bettersurvival.Commands.home.HomeCommand;
import org.pexserver.koukunn.bettersurvival.Commands.toggle.ToggleCommand;
import org.pexserver.koukunn.bettersurvival.Commands.chest.ChestCommand;
import org.pexserver.koukunn.bettersurvival.Commands.rename.RenameCommand;
import org.pexserver.koukunn.bettersurvival.Commands.tpa.TpaCommand;
import org.pexserver.koukunn.bettersurvival.Commands.invsee.InvseeCommand;
import org.pexserver.koukunn.bettersurvival.Commands.list.ListCommand;
import org.pexserver.koukunn.bettersurvival.Commands.ping.PingCommand;
import org.pexserver.koukunn.bettersurvival.Commands.hotp.HotpCommand;
import org.pexserver.koukunn.bettersurvival.Commands.webservice.WebServiceCommand;
import org.pexserver.koukunn.bettersurvival.Commands.offline.OfflineCommand;
import org.pexserver.koukunn.bettersurvival.Commands.w.WhitelistCommand;
import org.pexserver.koukunn.bettersurvival.Commands.party.PartyCommand;
import org.pexserver.koukunn.bettersurvival.Commands.land.LandCommand;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa.TpaModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeListener;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeOfflineData;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebServiceModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChunkLoader.ChunkLoaderModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess.OfflineAccessModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature;

public final class Loader extends JavaPlugin {

    private CommandManager commandManager;
    private ConfigManager configManager;
    private CommandBlockManager commandBlockManager;
    private ToggleModule toggleModule;
    private TpaModule tpaModule;
    private DiscordWebhookModule discordWebhookModule;
    private DiscordBotModule discordBotModule;
    private HomeModule homeModule;
    private PendingWhitelistModule pendingWhitelistModule;
    private EnchantmentSplitModule enchantmentSplitModule;
    private ItemCombineModule itemCombineModule;
    private GeyserWorkbenchModule geyserWorkbenchModule;
    private ChestSortModule chestSortModule;
    private ChestLockModule chestLockModule;
    private SharedStorageModule sharedStorageModule;
    private ChestShopModule chestShopModule;
    private BetterMenuModule betterMenuModule;
    private CopperGolemModule copperGolemModule;
    private WebServiceModule webServiceModule;
    private WebMapModule webMapModule;
    private org.pexserver.koukunn.bettersurvival.Modules.Feature.Performance.PerformanceModule performanceModule;
    private org.pexserver.koukunn.bettersurvival.Modules.Feature.Motd.MotdModule motdModule;
    private ChunkLoaderModule chunkLoaderModule;
    private PartyModule partyModule;
    private PartyMenu partyMenu;
    private LandProtectionModule landProtectionModule;
    private ParallelFurnaceModule parallelFurnaceModule;
    private RecyclerModule recyclerModule;
    private AirDashModule airDashModule;
    private CustomEnchantTableModule customEnchantTableModule;
    private WarpStoneModule warpStoneModule;
    private KeepAliveGuardModule keepAliveGuardModule;
    private org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule ringModule;
    private org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.TournamentModule tournamentModule;
    private OfflineAccessModule offlineAccessModule;

    @Override
    public void onEnable() {
        // マネージャーを初期化
        commandManager = new CommandManager(this);
        configManager = new ConfigManager(this);
        ChestUI.register();
        DialogUI.register();
        pendingWhitelistModule = new PendingWhitelistModule(this, configManager);
        getServer().getPluginManager().registerEvents(pendingWhitelistModule, this);

        // CommandBlockManager を作成して CommandManager に渡す
        this.commandBlockManager = new CommandBlockManager(this);
        commandManager.setBlockManager(this.commandBlockManager);
        // リスナー登録
        getServer().getPluginManager().registerEvents(new CommandBlockerListener(this.commandBlockManager), this);

        // グローバルコマンドフィルタを適用（別プラグインのコマンドにも干渉）
        GlobalCommandFilter globalFilter = new GlobalCommandFilter(this, this.commandBlockManager);
        globalFilter.applyGlobalFilter();
        getServer().getPluginManager().registerEvents(globalFilter, this);

        toggleModule = new ToggleModule(this);
        if (!toggleModule.hasGlobal("offlineaccess")) {
            toggleModule.setGlobal("offlineaccess", false);
        }
        offlineAccessModule = new OfflineAccessModule(this, configManager, toggleModule);
        getServer().getPluginManager().registerEvents(offlineAccessModule, this);
        offlineAccessModule.inject();

        // コマンドを登録
        registerCommands();

        // Toggle module (GUI)
        getServer().getPluginManager().registerEvents(new ToggleListener(toggleModule), this);
        itemCombineModule = new ItemCombineModule(this);
        getServer().getPluginManager().registerEvents(itemCombineModule, this);

        // TreeMine モジュール登録
        TreeMineModule treemine = new TreeMineModule(toggleModule);
        getServer().getPluginManager().registerEvents(treemine, this);
        getServer().getPluginManager().registerEvents(new AutoFeedModule(toggleModule), this);
        getServer().getPluginManager().registerEvents(new AutoFishingModule(toggleModule), this);
        enchantmentSplitModule = new EnchantmentSplitModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(enchantmentSplitModule, this);
        // AnythingFeed module (allow edible items on non-breedable mobs)
        getServer().getPluginManager().registerEvents(new AnythingFeedModule(toggleModule), this);
        // AutoPlant モジュール登録
        getServer().getPluginManager().registerEvents(new AutoPlantModule(toggleModule), this);
        // OreMine モジュール登録
        OreMineModule oremine = new OreMineModule(this, toggleModule);
        getServer().getPluginManager().registerEvents(oremine, this);
        // ChestLock module registration
        chestLockModule = new ChestLockModule(toggleModule, configManager);
        getServer().getPluginManager().registerEvents(chestLockModule, this);
        // ChestShop module registration
        chestShopModule = new ChestShopModule(toggleModule, configManager, chestLockModule);
        getServer().getPluginManager().registerEvents(chestShopModule, this);
        sharedStorageModule = new SharedStorageModule(this, toggleModule, itemCombineModule, chestLockModule, chestShopModule);
        getServer().getPluginManager().registerEvents(sharedStorageModule, this);
        // ChestSort モジュール登録
        chestSortModule = new ChestSortModule(this, toggleModule, chestLockModule, chestShopModule);
        getServer().getPluginManager().registerEvents(chestSortModule, this);
        discordBotModule = new DiscordBotModule(this, configManager, pendingWhitelistModule, offlineAccessModule.getManager());
        discordWebhookModule = new DiscordWebhookModule(this, configManager);
        getServer().getPluginManager().registerEvents(discordWebhookModule, this);
        homeModule = new HomeModule(this);
        // BedrockSkin モジュール登録 (Floodgate/Geyser ユーザーのスキン自動適用)
        BedrockSkinModule bedrockSkin = new BedrockSkinModule(this, toggleModule, configManager);
        getServer().getPluginManager().registerEvents(bedrockSkin, this);
        // TPA モジュール登録 (テレポートリクエスト機能)
        tpaModule = new TpaModule(this);
        getServer().getPluginManager().registerEvents(tpaModule, this);
        betterMenuModule = new BetterMenuModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(betterMenuModule, this);
        copperGolemModule = new CopperGolemModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(copperGolemModule, this);
        geyserWorkbenchModule = new GeyserWorkbenchModule(this, toggleModule);
        getServer().getPluginManager().registerEvents(geyserWorkbenchModule, this);
        if (!toggleModule.hasGlobal("chunkloader")) {
            toggleModule.setGlobal("chunkloader", true);
        }
        chunkLoaderModule = new ChunkLoaderModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(chunkLoaderModule, this);
        // Party モジュール登録 (ギルド風パーティー機能)
        partyModule = new PartyModule(this, toggleModule);
        getServer().getPluginManager().registerEvents(partyModule, this);
        partyMenu = new PartyMenu(this, partyModule);
        // LandProtection モジュール登録 (Rust 風の土地保護コア)
        landProtectionModule = new LandProtectionModule(this, toggleModule, itemCombineModule, partyModule);
        getServer().getPluginManager().registerEvents(landProtectionModule, this);
        getServer().getPluginManager().registerEvents(new DeathChestModule(this, toggleModule, landProtectionModule), this);
        // ParallelFurnace モジュール登録 (かまど×石炭ブロックで作る並列稼働かまど)
        parallelFurnaceModule = new ParallelFurnaceModule(this, toggleModule, itemCombineModule, chestLockModule, landProtectionModule);
        getServer().getPluginManager().registerEvents(parallelFurnaceModule, this);
        // Recycler モジュール登録 (砥石×鉄ブロックで作るRust風リサイクラー)
        recyclerModule = new RecyclerModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(recyclerModule, this);
        // AirDash モジュール登録 (空中ジャンプ再入力でダッシュ / Apexのアッシュ風)
        airDashModule = new AirDashModule(this, toggleModule);
        getServer().getPluginManager().registerEvents(airDashModule, this);
        // CustomEnchantTable モジュール登録 (エンチャントテーブル×ラピスで作る特殊エンチャント台)
        customEnchantTableModule = new CustomEnchantTableModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(customEnchantTableModule, this);
        // WarpStone モジュール登録 (Waystones風ワープ + GTA風カメラ演出)
        warpStoneModule = new WarpStoneModule(this, toggleModule, itemCombineModule);
        getServer().getPluginManager().registerEvents(warpStoneModule, this);
        keepAliveGuardModule = new KeepAliveGuardModule(this, toggleModule);
        getServer().getPluginManager().registerEvents(keepAliveGuardModule, this);
        // JPCh モジュール登録 (ローマ字チャットを日本語へ自動変換)
        JpChModule jpChModule = new JpChModule(this, toggleModule);
        getServer().getPluginManager().registerEvents(jpChModule, this);
        // Ring モジュール登録 (土地保護内の闘技場リング / Duel / マッチング)
        ringModule = new org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule(this, landProtectionModule);
        landProtectionModule.setRingModule(ringModule);
        getServer().getPluginManager().registerEvents(ringModule, this);
        // Tournament モジュール登録 (頂点決定戦: リングを使ったトーナメント大会)
        tournamentModule = new org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.TournamentModule(this, toggleModule, ringModule);
        getServer().getPluginManager().registerEvents(tournamentModule, this);
        webServiceModule = new WebServiceModule(this);
        getServer().getPluginManager().registerEvents(webServiceModule, this);
        webMapModule = new WebMapModule(this);
        getServer().getPluginManager().registerEvents(webMapModule, this);
        // Performance モジュール登録 (省電力モード: 無人時に更新スレッドを停止)
        performanceModule = new org.pexserver.koukunn.bettersurvival.Modules.Feature.Performance.PerformanceModule(this);
        getServer().getPluginManager().registerEvents(performanceModule, this);
        // Motd モジュール登録 (motd/ フォルダの icon.png と motd.json でサーバー表示をカスタマイズ)
        motdModule = new org.pexserver.koukunn.bettersurvival.Modules.Feature.Motd.MotdModule(this);
        getServer().getPluginManager().registerEvents(motdModule, this);
        // InvSee イベントリスナー登録 (プレイヤーインベントリ閲覧・編集)
        InvseeOfflineData.initialize(this);
        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            InvseeOfflineData.saveSnapshot(onlinePlayer);
        }
        getServer().getPluginManager().registerEvents(new InvseeListener(this), this);
        // FenceLeash module registration (allow placing a leash knot on fence by
        // right-clicking while holding a lead)
        // Toggle 機能として登録
        toggleModule.registerFeature(
                new ToggleFeature("treemine", "TreeMine", "木を一括で伐採・破壊します(スニーク必須)", Material.DIAMOND_AXE));
        toggleModule.registerFeature(
                new ToggleFeature("oremine", "OreMine", "近接する鉱石を一括で破壊します（スニーク必須）", Material.DIAMOND_PICKAXE));
        toggleModule
                .registerFeature(new ToggleFeature("autofeed", "AutoFeed", "餌を与えると周辺の動物にも自動で餌を与えます", Material.WHEAT));
        toggleModule.registerFeature(
                new ToggleFeature("autofishing", "AutoFishing", "釣り開始後、動いたり視点を大きく変えるまで自動で釣りを続けます", Material.FISHING_ROD));
        toggleModule.registerFeature(
                new ToggleFeature("anythingfeed", "AnythingFeed", "非繁殖動物に任意の食料で反応するようにします", Material.APPLE));
        toggleModule.registerFeature(new ToggleFeature("autoplant", "AutoPlant",
                "オフハンドに植えたいアイテムを持ちながら耕した土の近くに行くと自動で植え・収穫します", Material.WHEAT_SEEDS));
        toggleModule.registerFeature(
                new ToggleFeature("chestlock", "ChestLock", "チェスト保護を有効/無効にします（破壊・移動・取得を制限）", Material.CHEST, false));
        toggleModule.registerFeature(
                new ToggleFeature("chestshop", "ChestShop", "看板でチェストをショップ化します(>>Shop 名前)", Material.OAK_SIGN, false));
        toggleModule
                .registerFeature(new ToggleFeature("chestsort", "ChestSort", "スニーク+木の棒でチェスト内を整理します", Material.STICK));
        toggleModule.registerFeature(
                new ToggleFeature("sharedstorage", "SharedStorage", "主チェストとsubチェストを使った共有ストレージを有効/無効にします", Material.CHEST, false));
        toggleModule.registerFeature(new ToggleFeature("deathchest", "DeathChest",
                "死亡時に所持品を死亡地点付近のラージチェストへ保管し座標を通知します", Material.TRAPPED_CHEST));
        toggleModule.registerFeature(new ToggleFeature("home", "Home",
                "/home で登録済みの家へ移動します。登録は最大3個までです", Material.RED_BED));
        toggleModule.registerFeature(
                new ToggleFeature("bedrockskin", "BedrockSkin", "BedrockユーザーのスキンをJavaクライアントに自動反映します", Material.PLAYER_HEAD, false));
        toggleModule.registerFeature(
                new ToggleFeature("tpa", "TPA", "テレポートリクエスト機能（無効にすると受信拒否）", Material.ENDER_PEARL));
        toggleModule.registerFeature(
                new ToggleFeature("enchantsplit", "EnchantSplit", "複数エンチャント本を分離できる専用砥石を有効/無効にします", Material.GRINDSTONE, false));
        toggleModule.registerFeature(
                new ToggleFeature("bettermenu", "BetterMenu", "木の斧GUIツールの生成・起動を有効/無効にします", Material.WOODEN_AXE, false));
        toggleModule.registerFeature(
            new ToggleFeature("coppergolem", "CopperGolem", "カッパーゴーレムの召喚と作物採取AIを有効/無効にします", Material.COPPER_BLOCK, false));
        toggleModule.registerFeature(
            new ToggleFeature("geyseranvil", "Geyser金床", "Geyser/Bedrock対応の金床UIを有効/無効にします", Material.ANVIL, false));
        toggleModule.registerFeature(
            new ToggleFeature("geysersmithing", "Geyser鍛冶台", "Geyser/Bedrock対応の鍛冶台UIを有効/無効にします", Material.SMITHING_TABLE, false));
        toggleModule.registerFeature(
            new ToggleFeature("webmap", "WebMap", "軽量な Web マップと ChunkGen を有効/無効にします", Material.FILLED_MAP, false));
        toggleModule.registerFeature(
            new ToggleFeature("webservice", "WebService", "ホームページ、ログイン、プロフィール機能を有効/無効にします", Material.BOOK, false));
        toggleModule.registerFeature(
            new ToggleFeature("chunkloader", "ChunkLoader", "コンパス+名札(chunkloader)で作るチャンクローダーを有効/無効にします", Material.CALIBRATED_SCULK_SENSOR, false));
        toggleModule.registerFeature(
            new ToggleFeature("party", "Party", "パーティー(ギルド)機能を有効/無効にします (/party, /p)", Material.WHITE_BANNER, false));
        toggleModule.registerFeature(
            new ToggleFeature("landprotect", "LandProtect", "ロデストーン+ダイヤで作る土地保護コアを有効/無効にします", Material.LODESTONE, false));
        toggleModule.registerFeature(
            new ToggleFeature("parallelfurnace", "ParallelFurnace", "かまど×石炭ブロックで作る並列かまどを有効/無効にします", Material.FURNACE, false));
        toggleModule.registerFeature(
            new ToggleFeature("recycler", "Recycler", "砥石×鉄ブロックで作るリサイクラー(不要品を素材へ分解/処分)を有効/無効にします", Material.GRINDSTONE, false));
        toggleModule.registerFeature(
            new ToggleFeature("airdash", "AirDash", "空中でジャンプキーを再入力するとエアダッシュします(クールダウンはActionBar表示)", Material.FEATHER));
        toggleModule.registerFeature(
            new ToggleFeature("customenchant", "CustomEnchant", "エンチャントテーブル×ラピスで作るカスタムエンチャント台を有効/無効にします", Material.ENCHANTING_TABLE, false));
        toggleModule.registerFeature(
            new ToggleFeature("warpstone", "WarpStone", "エンダーパール×石レンガで作るワープストーンを有効/無効にします", Material.LODESTONE, false));
        toggleModule.registerFeature(
            new ToggleFeature("keepaliveguard", "KeepAliveGuard", "OPのkeepalive timeout kickを可能な範囲でキャンセルします", Material.REPEATER, false));
        toggleModule.registerFeature(
            new ToggleFeature(JpChModule.FEATURE_KEY, "JPCh", "チャットのローマ字を検出して日本語へ自動翻訳します", Material.WRITABLE_BOOK, false));
        toggleModule.registerFeature(
            new ToggleFeature("tournament", "Tournament", "リングを使った頂点決定戦(トーナメント大会)を有効/無効にします (/tournament)", Material.GOLDEN_SWORD, false));        toggleModule.registerFeature(
                new ToggleFeature("offlineaccess", "OfflineAccess", "オフラインアカウントのログインを許可/拒否します", Material.COMPASS, false));        if (!toggleModule.hasGlobal("treemine")) {
            toggleModule.setGlobal("treemine", true);
        }
        if (!toggleModule.hasGlobal("oremine")) {
            toggleModule.setGlobal("oremine", true);
        }
        if (!toggleModule.hasGlobal("autofeed")) {
            toggleModule.setGlobal("autofeed", true);
        }
        if (!toggleModule.hasGlobal("autofishing")) {
            toggleModule.setGlobal("autofishing", false);
        }
        if (!toggleModule.hasGlobal("anythingfeed")) {
            toggleModule.setGlobal("anythingfeed", true);
        }
        if (!toggleModule.hasGlobal("autoplant")) {
            toggleModule.setGlobal("autoplant", true);
        }
        if (!toggleModule.hasGlobal("chestlock")) {
            toggleModule.setGlobal("chestlock", true);
        }
        if (!toggleModule.hasGlobal("chestsort")) {
            toggleModule.setGlobal("chestsort", true);
        }
        if (!toggleModule.hasGlobal("sharedstorage")) {
            toggleModule.setGlobal("sharedstorage", false);
        }
        if (!toggleModule.hasGlobal("chestshop")) {
            toggleModule.setGlobal("chestshop", true);
        }
        if (!toggleModule.hasGlobal("deathchest")) {
            toggleModule.setGlobal("deathchest", true);
        }
        if (!toggleModule.hasGlobal("home")) {
            toggleModule.setGlobal("home", true);
        }
        if (!toggleModule.hasGlobal("bedrockskin")) {
            toggleModule.setGlobal("bedrockskin", true);
        }
        if (!toggleModule.hasGlobal("tpa")) {
            toggleModule.setGlobal("tpa", true);
        }
        if (!toggleModule.hasGlobal("enchantsplit")) {
            toggleModule.setGlobal("enchantsplit", false);
        }
        if (!toggleModule.hasGlobal("bettermenu")) {
            toggleModule.setGlobal("bettermenu", true);
        }
        if (!toggleModule.hasGlobal("coppergolem")) {
            toggleModule.setGlobal("coppergolem", true);
        }
        if (!toggleModule.hasGlobal("geyseranvil")) {
            toggleModule.setGlobal("geyseranvil", true);
        }
        if (!toggleModule.hasGlobal("geysersmithing")) {
            toggleModule.setGlobal("geysersmithing", true);
        }
        if (!toggleModule.hasGlobal("webmap")) {
            toggleModule.setGlobal("webmap", false);
        }
        if (!toggleModule.hasGlobal("webservice")) {
            toggleModule.setGlobal("webservice", true);
        }
        if (!toggleModule.hasGlobal("party")) {
            toggleModule.setGlobal("party", true);
        }
        if (!toggleModule.hasGlobal("landprotect")) {
            toggleModule.setGlobal("landprotect", true);
        }
        if (!toggleModule.hasGlobal("parallelfurnace")) {
            toggleModule.setGlobal("parallelfurnace", true);
        }
        if (!toggleModule.hasGlobal("recycler")) {
            toggleModule.setGlobal("recycler", true);
        }
        if (!toggleModule.hasGlobal("airdash")) {
            toggleModule.setGlobal("airdash", true);
        }
        if (!toggleModule.hasGlobal("customenchant")) {
            toggleModule.setGlobal("customenchant", true);
        }
        if (!toggleModule.hasGlobal("warpstone")) {
            toggleModule.setGlobal("warpstone", true);
        }
        if (!toggleModule.hasGlobal("keepaliveguard")) {
            toggleModule.setGlobal("keepaliveguard", true);
        }
        if (!toggleModule.hasGlobal(JpChModule.FEATURE_KEY)) {
            toggleModule.setGlobal(JpChModule.FEATURE_KEY, true);
        }
        if (!toggleModule.hasGlobal("tournament")) {
            toggleModule.setGlobal("tournament", true);
        }
        getLogger().info("Better Survival Plugin が有効になりました");
    }

    /**
     * すべてのコマンドを登録
     */
    private void registerCommands() {
        // ヘルプコマンド
        commandManager.register(new HelpCommand(commandManager));
        // Toggle command
        commandManager.register(new ToggleCommand(this));
        // Chest command (chest lock & member management)
        commandManager.register(new ChestCommand(this));
        // Rename command: 手持ちアイテムの名前変更
        commandManager.register(new RenameCommand());
        // TPA command: テレポートリクエスト
        commandManager.register(new TpaCommand(this));
        // Home command: 登録済みHomeへの移動
        commandManager.register(new HomeCommand(this));
        // InvSee command: プレイヤーインベントリ閲覧・編集（OP専用）
        commandManager.register(new InvseeCommand(this));
        // List command: オンラインのプレイヤー一覧を表示
        commandManager.register(new ListCommand());
        // Ping command: サーバーとの通信遅延を計測
        commandManager.register(new PingCommand());
        // HOTP command: WebSite 登録用ワンタイムコード
        commandManager.register(new HotpCommand(this));
        // DiscordWebhook command: Discord通知設定UI（OP専用）
        commandManager.register(new DiscordCommand(this));
        // Pending whitelist command: 初回参加前ユーザーを接続待機登録
        commandManager.register(new WhitelistCommand(this));
        // WebService command: OP専用の WebService / WebMap 統合管理
        commandManager.register(new WebServiceCommand(this));
        // Status command: サーバー稼働状況の表示と統計リセット (/status reset)
        commandManager.register(new org.pexserver.koukunn.bettersurvival.Commands.status.StatusCommand(this));
        // Performance command: 省電力モードの有効/無効 (OP専用)
        commandManager.register(new org.pexserver.koukunn.bettersurvival.Commands.performance.PerformanceCommand(this));
        // Party command: パーティー(ギルド)管理 (/party と /p の両方で開ける)
        commandManager.register(new PartyCommand(this, "party"));
        commandManager.register(new PartyCommand(this, "p"));
        // Land command: 土地保護のデバッグ表示・情報
        commandManager.register(new LandCommand(this));
        // Tournament command: 頂点決定戦（トーナメント大会）の参加・管理
        commandManager.register(new org.pexserver.koukunn.bettersurvival.Commands.tournament.TournamentCommand(this));
        // OfflineAccess command: オフラインアカウントログイン許可リスト管理
        commandManager.register(new OfflineCommand(offlineAccessModule));
        // Command: グローバル無効化コマンド
        commandManager.register(new CommandCommand(this.commandBlockManager));
        // 他のコマンドはここに追加できます
    }

    /**
     * CommandManager を取得
     * 
     * @return CommandManager インスタンス
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ToggleModule getToggleModule() {
        return toggleModule;
    }

    public TpaModule getTpaModule() {
        return tpaModule;
    }

    public DiscordWebhookModule getDiscordWebhookModule() {
        return discordWebhookModule;
    }

    public DiscordBotModule getDiscordBotModule() {
        return discordBotModule;
    }

    public HomeModule getHomeModule() {
        return homeModule;
    }

    public PendingWhitelistModule getPendingWhitelistModule() {
        return pendingWhitelistModule;
    }

    public ItemCombineModule getItemCombineModule() {
        return itemCombineModule;
    }

    public ChestLockModule getChestLockModule() {
        return chestLockModule;
    }

    public SharedStorageModule getSharedStorageModule() {
        return sharedStorageModule;
    }

    public ChestShopModule getChestShopModule() {
        return chestShopModule;
    }

    public PartyModule getPartyModule() {
        return partyModule;
    }

    public PartyMenu getPartyMenu() {
        return partyMenu;
    }

    public LandProtectionModule getLandProtectionModule() {
        return landProtectionModule;
    }

    public ParallelFurnaceModule getParallelFurnaceModule() {
        return parallelFurnaceModule;
    }

    public AirDashModule getAirDashModule() {
        return airDashModule;
    }

    public CustomEnchantTableModule getCustomEnchantTableModule() {
        return customEnchantTableModule;
    }

    public WarpStoneModule getWarpStoneModule() {
        return warpStoneModule;
    }

    public org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule getRingModule() {
        return ringModule;
    }

    public org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.TournamentModule getTournamentModule() {
        return tournamentModule;
    }

    public WebMapModule getWebMapModule() {
        return webMapModule;
    }

    public org.pexserver.koukunn.bettersurvival.Modules.Feature.Performance.PerformanceModule getPerformanceModule() {
        return performanceModule;
    }

    public org.pexserver.koukunn.bettersurvival.Modules.Feature.Motd.MotdModule getMotdModule() {
        return motdModule;
    }

    public WebServiceModule getWebServiceModule() {
        return webServiceModule;
    }

    public OfflineAccessModule getOfflineAccessModule() {
        return offlineAccessModule;
    }

    @Override
    public void onDisable() {
        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            InvseeOfflineData.saveSnapshot(onlinePlayer);
        }
        if (webMapModule != null) {
            webMapModule.shutdown();
        }
        if (webServiceModule != null) {
            webServiceModule.shutdown();
        }
        if (discordWebhookModule != null) {
            discordWebhookModule.shutdown();
        }
        if (discordBotModule != null) {
            discordBotModule.shutdown();
        }
        if (enchantmentSplitModule != null) {
            enchantmentSplitModule.shutdown();
        }
        if (chestSortModule != null) {
            chestSortModule.shutdown();
        }
        if (copperGolemModule != null) {
            copperGolemModule.shutdown();
        }
        if (chunkLoaderModule != null) {
            chunkLoaderModule.shutdown();
        }
        if (tournamentModule != null) {
            tournamentModule.shutdown();
        }
        if (ringModule != null) {
            ringModule.shutdown();
        }
        if (landProtectionModule != null) {
            landProtectionModule.shutdown();
        }
        if (parallelFurnaceModule != null) {
            parallelFurnaceModule.shutdown();
        }
        if (airDashModule != null) {
            airDashModule.shutdown();
        }
        if (recyclerModule != null) {
            recyclerModule.shutdown();
        }
        if (customEnchantTableModule != null) {
            customEnchantTableModule.shutdown();
        }
        if (warpStoneModule != null) {
            warpStoneModule.shutdown();
        }
        getLogger().info("Better Survival Plugin が無効になりました");
    }
}
