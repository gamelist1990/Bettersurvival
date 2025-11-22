package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryAction;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.bukkit.Sound;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.Container;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChestShopModule implements Listener {

    private final ChestShopStore store;
    private final ToggleModule toggle;
    private final ChestLockModule chestLock;

    public ChestShopModule(ToggleModule toggle, ConfigManager manager, ChestLockModule chestLock) {
        this.toggle = toggle;
        this.store = new ChestShopStore(manager);
        this.chestLock = chestLock;
        // schedule periodic autosave for any open editors (covers edge cases where click/drag may be missed)
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Bettersurvival");
                if (plugin != null) {
                Bukkit.getLogger().info("[ChestShop] scheduling editor autosave tick task for plugin=" + plugin.getName());
                Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        try {
                            if (p == null) continue;
                            InventoryView view = null;
                            try { view = p.getOpenInventory(); } catch (Exception ignored) { }
                            if (view == null || view.getTitle() == null) continue;
                            String title = view.getTitle();
                            if (!title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) continue;
                            ResolveResult rr = resolveByTitle(title);
                            if (rr == null) continue;
                            Location loc = rr.loc;
                            ChestShop shop = rr.shop;
                            Inventory invEditor = view.getTopInventory();
                            if (invEditor == null) continue;
                            // compute snapshot hash to detect changes and avoid saving every tick
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < 26; i++) {
                                ItemStack it = invEditor.getItem(i);
                                if (it == null) { sb.append("-"); continue; }
                                String disp = null;
                                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) disp = it.getItemMeta().getDisplayName();
                                if (disp == null) disp = it.getType().name();
                                sb.append(it.getType().name()).append('|').append(disp).append('|');
                                if (it.hasItemMeta() && it.getItemMeta().hasLore()) sb.append(String.join("/", it.getItemMeta().getLore()));
                                sb.append(':').append(it.getAmount()).append(';');
                            }
                            String snapStr = sb.toString();
                            int snap = snapStr.hashCode();
                            UUID uid = p.getUniqueId();
                            Integer prevHash = editorSnapshotHash.get(uid);
                            long now = System.currentTimeMillis();
                            Long lastSaved = editorLastSavedTick.getOrDefault(uid, 0L);
                            // debug: show snapshot info when change detected or throttled
                            if (Objects.equals(prevHash, snap)) {
                                continue;
                            }
                            if ((now - lastSaved) < 200L) {
                                Bukkit.getLogger().info("[ChestShop] snapshot changed but throttled for player="+p.getName()+" snap="+snap+" prev="+prevHash+" delta_ms="+(now-lastSaved));
                                continue;
                            }
                            Bukkit.getLogger().info("[ChestShop] snapshot change detected for player="+p.getName()+" prev="+prevHash+" new="+snap+" preview='"+(snapStr.length()>200?snapStr.substring(0,200):snapStr)+"'");
                            Map<Integer, ShopListing> old = store.getListings(loc);
                            Map<Integer, ShopListing> updated = new LinkedHashMap<>();
                            for (int i = 0; i < 26; i++) {
                                ItemStack it = invEditor.getItem(i);
                                if (it == null) continue;
                                String rawDisplay = null;
                                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) rawDisplay = it.getItemMeta().getDisplayName();
                                if (rawDisplay == null) rawDisplay = it.getType().name();
                                ShopListing prev = old.get(i);
                                ShopListing sl = parseRawToListing(it, rawDisplay, prev);
                                if (prev != null) sl.setStock(prev.getStock());
                                updated.put(i, sl);
                            }
                            // detect any listings that were removed by the player (editor slot emptied)
                            // and return their physical items + stock back to the player.
                            if (old != null && !old.isEmpty()) {
                                for (Map.Entry<Integer, ShopListing> enOld : old.entrySet()) {
                                    int idx = enOld.getKey();
                                    if (updated.containsKey(idx)) continue; // still present
                                    ShopListing removed = enOld.getValue();
                                    if (removed == null) continue;
                                    if (wasRemovalRecentlyHandled(p, loc, idx)) continue;
                                    try {
                                        ItemStack giveBack = null;
                                        if (removed.getItemData() != null) {
                                            try { giveBack = ItemStack.deserialize(removed.getItemData()); } catch (Exception ignored) { giveBack = null; }
                                        }
                                        if (giveBack == null) {
                                            Material m = Material.matchMaterial(removed.getMaterial());
                                            giveBack = new ItemStack(m == null ? Material.PAPER : m);
                                        }
                                        // sanitize meta
                                        ItemMeta gm = giveBack.getItemMeta();
                                        if (gm != null) {
                                            if (gm.hasLore() && gm.getLore() != null) {
                                                List<String> newl = new ArrayList<>();
                                                for (String L : gm.getLore()) {
                                                    if (L == null) continue;
                                                    if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                                                    String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                                    if (cleaned.isEmpty()) continue;
                                                    newl.add(cleaned);
                                                }
                                                gm.setLore(newl.isEmpty() ? null : newl);
                                            }
                                            if (gm.hasDisplayName() && gm.getDisplayName() != null) {
                                                String name = gm.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                                                if (name.isEmpty()) gm.setDisplayName(null); else gm.setDisplayName(name);
                                            }
                                            giveBack.setItemMeta(gm);
                                        }

                                        int amountToGive = Math.max(1, 1 + Math.max(0, removed.getStock()));
                                        // avoid duplicating if player already has similar items
                                        giveBack = sanitizeReturnedItem(giveBack);
                                        ItemStack proto = giveBack.clone(); proto.setAmount(1);
                                        int existing = countSimilarInPlayer(p, proto);
                                        int toGive = Math.max(0, amountToGive - existing);
                                        if (toGive > 0) {
                                            giveBack.setAmount(toGive);
                                            giveOrMergeToPlayer(p, giveBack);
                                        }
                                        markRemovalHandled(p, loc, idx);
                                        p.sendMessage("§a出品をエディターから取り出しました、在庫を返却しました: " + giveBack.getType().toString() + " x" + amountToGive);
                                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                                    } catch (Exception ignored) {}
                                }
                            }

                            boolean ok = store.saveListings(loc, updated);
                            if (ok) {
                                Bukkit.getLogger().info("[ChestShop] Auto-saved editor for player=" + p.getName() + " shop=" + shop.getName() + " entries=" + updated.size());
                                editorSnapshotHash.put(uid, snap);
                                editorLastSavedTick.put(uid, now);
                                // note: editor item lore restoration removed to avoid interfering with player's editor items
                            } else {
                                // autosave logic: support owner UI autosave for currency changes
                                if (title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) {
                                    try {
                                        // attempt to resolve mapping if lost
                                        if (loc == null || shop == null) {
                                            try {
                                                String guess = title.substring(ChestShopUI.OWNER_TITLE_PREFIX.length()).trim();
                                                if (!guess.isEmpty()) {
                                                    Map<String, ChestShop> all = store.getAll();
                                                    for (Map.Entry<String, ChestShop> en : all.entrySet()) {
                                                        ChestShop cs = en.getValue();
                                                        if (cs == null) continue;
                                                        if (cs.getName() != null && cs.getName().equals(guess)) {
                                                            String key = en.getKey();
                                                            String[] parts = key.split(":" );
                                                            if (parts.length == 4) {
                                                                World w = Bukkit.getWorld(parts[0]);
                                                                if (w != null) {
                                                                    try {
                                                                        int x = Integer.parseInt(parts[1]);
                                                                        int y = Integer.parseInt(parts[2]);
                                                                        int z = Integer.parseInt(parts[3]);
                                                                        loc = new Location(w, x, y, z);
                                                                        shop = cs;
                                                                        break;
                                                                    } catch (Exception ex) { }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (Exception ignore) {}
                                        }
                                        if (loc == null || shop == null) continue;
                                        Inventory invOwner = view.getTopInventory();
                                        if (invOwner == null) continue;
                                        // only watch currency slot (12) for changes
                                        ItemStack cur = invOwner.getItem(12);
                                        String curMat = cur == null ? null : cur.getType().name();
                                        String prevCur = shop.getCurrency();
                                        if (!Objects.equals(curMat, prevCur)) {
                                            boolean saved = store.saveShopCurrency(loc, curMat);
                                            if (saved) {
                                                shop.setCurrency(curMat);
                                                editorSnapshotHash.put(uid, snap);
                                                editorLastSavedTick.put(uid, now);
                                                Bukkit.getLogger().info("[ChestShop] Auto-saved owner currency for player="+p.getName()+" shop="+shop.getName()+" currency="+curMat);
                                            } else {
                                                Bukkit.getLogger().info("[ChestShop] Auto-save owner currency failed for player="+p.getName()+" shop="+(shop==null?"(null)":shop.getName()));
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }, 1L, 1L);
            }
        } catch (Exception ignored) {}
    }

    // track last seen editor snapshot hashes to detect modifications without writing every tick
    private final Map<UUID, Integer> editorSnapshotHash = new ConcurrentHashMap<>();
    // throttle minimum ticks between saves per-player (avoid excessive I/O)
    private final Map<UUID, Long> editorLastSavedTick = new ConcurrentHashMap<>();

    // helper: resolve shop and canonical location from an inventory title string (title-based resolution is default)
    private static class ResolveResult { public final Location loc; public final ChestShop shop; ResolveResult(Location l, ChestShop s) { this.loc = l; this.shop = s; } }
    private ResolveResult resolveByTitle(String title) {
        if (title == null) return null;
        String guess = null;
        if (title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) guess = title.substring(ChestShopUI.EDITOR_TITLE_PREFIX.length()).trim();
        else if (title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) guess = title.substring(ChestShopUI.OWNER_TITLE_PREFIX.length()).trim();
        else if (title.startsWith(ChestShopUI.TITLE_PREFIX)) guess = title.substring(ChestShopUI.TITLE_PREFIX.length()).trim();
        if (guess == null || guess.isEmpty()) return null;
        Map<String, ChestShop> all = store.getAll();
        for (Map.Entry<String, ChestShop> en : all.entrySet()) {
            ChestShop cs = en.getValue();
            if (cs == null || cs.getName() == null) continue;
            if (!cs.getName().equals(guess)) continue;
            String key = en.getKey();
            String[] parts = key.split(":");
            if (parts.length != 4) continue;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                return new ResolveResult(new Location(w, x, y, z), cs);
            } catch (Exception ignored) {}
        }
        return null;
    }

    // players for whom we recently opened a UI — used to suppress handling close/open re-entrancy
    private final Set<UUID> suppressCloseHandling = ConcurrentHashMap.newKeySet();
    // prevent duplicate-return races: track recent removals (player+loc+slot)
    private final Map<String, Long> recentRemovalTimestamps = new ConcurrentHashMap<>();

    private String makeRemovalKey(Player p, Location loc, int slot) {
        if (p == null || loc == null) return null;
        return p.getUniqueId().toString() + ":" + loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ() + ":" + slot;
    }

    private void markRemovalHandled(Player p, Location loc, int slot) {
        String k = makeRemovalKey(p, loc, slot);
        if (k == null) return;
        recentRemovalTimestamps.put(k, System.currentTimeMillis());
    }

    private boolean wasRemovalRecentlyHandled(Player p, Location loc, int slot) {
        String k = makeRemovalKey(p, loc, slot);
        if (k == null) return false;
        Long t = recentRemovalTimestamps.get(k);
        if (t == null) return false;
        if (System.currentTimeMillis() - t < 2000L) return true;
        recentRemovalTimestamps.remove(k);
        return false;
    }

    private ShopListing parseRawToListing(ItemStack it, String raw, ShopListing prev) {
        int prevStock = prev != null ? prev.getStock() : 0;
        int prevPrice = prev != null ? prev.getPrice() : 0;
        String prevDesc = prev != null && prev.getDescription() != null ? prev.getDescription() : "";
        Map<String,Object> prevItemData = prev != null ? prev.getItemData() : null;
        Map<String,Integer> prevEnchants = prev != null ? prev.getEnchants() : null;
        int prevDamage = prev != null ? prev.getDamage() : 0;
        if (raw == null) raw = it.getType().name();
        try { Bukkit.getLogger().info("[ChestShop] parseRawToListing raw='"+raw+"' type="+it.getType().name()); } catch (Exception ignored) {}
        String desc = "";
        String left = raw.trim();
        int price = 0;

        // JSON-like block support: {cost:10,docs:説明}
        int jstart = left.indexOf('{');
        int jend = jstart >= 0 ? left.indexOf('}', jstart) : -1;
        if (jstart >= 0 && jend > jstart) {
            String json = left.substring(jstart + 1, jend).trim();
            left = (left.substring(0, jstart) + left.substring(jend + 1)).trim();
            // robust parsing: cost/price and docs/desc/description
            try {
                java.util.regex.Matcher mCost = java.util.regex.Pattern.compile("(?i)\\b(?:cost|price)\\s*:\\s*(\\d+)").matcher(json);
                if (mCost.find()) {
                    try { price = Integer.parseInt(mCost.group(1)); } catch (Exception ignored) {}
                }
                if (price != 0) Bukkit.getLogger().info("[ChestShop] detected cost="+price+" in json='"+json+"'");
                java.util.regex.Matcher mDocs = java.util.regex.Pattern.compile("(?i)\\b(?:docs|desc|description)\\s*:\\s*(.*)").matcher(json);
                if (mDocs.find()) {
                    desc = mDocs.group(1).trim();
                    // strip trailing commas if present
                    if (desc.endsWith(",")) desc = desc.substring(0, desc.length()-1).trim();
                    // remove surrounding quotes if used
                    if ((desc.startsWith("\"") && desc.endsWith("\"")) || (desc.startsWith("'") && desc.endsWith("'"))) {
                        desc = desc.substring(1, desc.length()-1);
                    }
                    Bukkit.getLogger().info("[ChestShop] detected docs='"+desc+"' in json='"+json+"'");
                }
            } catch (Exception ignored) {}
        }

        // legacy parsing: --desc and trailing -price
        if (desc.isEmpty() && left.contains("--")) {
            String[] ar = left.split("--", 2);
            left = ar[0].trim();
            desc = ar[1].trim();
        }

        if (price == 0) {
            try {
                java.util.regex.Pattern pr = java.util.regex.Pattern.compile("-(\\d+)$");
                java.util.regex.Matcher mm = pr.matcher(left);
                if (mm.find()) {
                    price = Integer.parseInt(mm.group(1));
                    left = left.substring(0, mm.start()).trim();
                }
            } catch (Exception ignored) {}
        }

        // If no price parsed from display, reuse previous listing's price if present
        if (price == 0 && prevPrice > 0) price = prevPrice;

        // normalize display name: strip embedded JSON blocks {..} which were used as inline metadata
        String display = raw == null ? null : raw.replaceAll("\\{[^}]*\\}", "").trim();
        Map<String,Integer> enchMap = new LinkedHashMap<>();
        int damage = 0;
        try {
                if (it.hasItemMeta()) {
                ItemMeta im = it.getItemMeta();
                if (im != null) {
                    if (im.hasEnchants()) {
                        for (Map.Entry<Enchantment, Integer> en : im.getEnchants().entrySet()) {
                            try {
                                String k = en.getKey().getKey().getKey();
                                enchMap.put(k, en.getValue());
                            } catch (Exception ignored) {}
                        }
                    }
                    if (im instanceof Damageable) {
                        damage = ((Damageable) im).getDamage();
                    }
                }
            }
        } catch (Exception ignored) {}
        if ((enchMap == null || enchMap.isEmpty()) && prevEnchants != null) {
            enchMap = new LinkedHashMap<>(prevEnchants);
        }

        Map<String,Object> itemData = null;
        try {
            if (it != null) {
                ItemStack copy = it.clone();
                try {
                    ItemMeta cim = copy.getItemMeta();
                    if (cim != null && cim.hasLore()) {
                        List<String> newLore = new ArrayList<>();
                        for (String L : cim.getLore()) {
                            if (L == null) continue;
                            if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                            String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                            if (cleaned.isEmpty()) continue;
                            newLore.add(cleaned);
                        }
                        cim.setLore(newLore.isEmpty() ? null : newLore);
                    }
                    // also sanitize display name in copied data to remove inline JSON blocks
                    try {
                        if (cim != null && cim.hasDisplayName() && cim.getDisplayName() != null) {
                            String clean = cim.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                            if (clean.isEmpty()) clean = null;
                            cim.setDisplayName(clean);
                        }
                    } catch (Exception ignored) {}
                    copy.setItemMeta(cim);
                } catch (Exception ignoredMeta) {}
                itemData = copy.serialize();
                // if we couldn't serialize meaningful data, fall back to previously stored item data
                if ((itemData == null || itemData.isEmpty()) && prevItemData != null) itemData = new LinkedHashMap<>(prevItemData);
            }
        } catch (Exception ignored) {}
        String matName = it == null ? Material.PAPER.name() : it.getType().name();
        // if description blank, keep previous description
        if ((desc == null || desc.isEmpty()) && prevDesc != null && !prevDesc.isEmpty()) desc = prevDesc;
        if (damage == 0 && prevDamage > 0) damage = prevDamage;
        ShopListing sl = new ShopListing(matName, display, price, desc, prevStock, enchMap, damage, itemData);
        return sl;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        Block b = e.getBlock();
        if (b == null) return;
        if (!(b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST || b.getType() == Material.BARREL)) {
            // if this is a sign attached to a shop chest, prevent non-owner/OP from breaking it
            try {
                if (b.getState() instanceof Sign) {
                    List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
                    for (BlockFace f : faces) {
                        Block nb = b.getRelative(f);
                        if (nb == null) continue;
                        if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                        Optional<ChestShop> optSign = store.get(nb.getLocation());
                        if (!optSign.isPresent()) continue;
                        ChestShop shopSign = optSign.get();
                        Player pBreak = e.getPlayer();
                        if (pBreak != null && (pBreak.isOp() || (shopSign.getOwner() != null && shopSign.getOwner().equals(pBreak.getUniqueId().toString())))) {
                            // owner/op can break sign
                            return;
                        }
                        e.setCancelled(true);
                        if (pBreak != null) pBreak.sendMessage("§cこの看板はショップに紐づいているため破壊できません");
                        return;
                    }
                }
            } catch (Exception ignored) {}
            return;
        }
        Location loc = b.getLocation();
        Optional<ChestShop> opt = store.get(loc);
        if (!opt.isPresent()) return;
        ChestShop shop = opt.get();
        if (e.getPlayer() == null) return;
        Player p = e.getPlayer();
        String pid = p.getUniqueId().toString();
        if (!p.isOp() && !pid.equals(shop.getOwner())) {
            // not owner - prevent breaking
            e.setCancelled(true);
            p.sendMessage("§cこのチェストはショップとして登録されています。オーナーだけが破壊できます。");
            return;
        }
        // owner or op: drop all listed items (sample + 在庫) for each linked chest, then remove shop entries
        for (Location l : ChestLockModule.getChestRelatedLocations(b)) {
            try {
                Map<Integer, ShopListing> listings = store.getListings(l);
                if (listings != null && !listings.isEmpty()) {
                    for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                        ShopListing sl = en.getValue();
                        if (sl == null) continue;
                        ItemStack toDrop = null;
                        if (sl.getItemData() != null) {
                            try { toDrop = ItemStack.deserialize(sl.getItemData()); } catch (Exception ignored) { toDrop = null; }
                        }
                        if (toDrop == null) {
                            Material m = Material.matchMaterial(sl.getMaterial());
                            toDrop = new ItemStack(m == null ? Material.PAPER : m);
                        }
                        // apply stored enchants if serialized data didn't include them
                        try {
                            if ((toDrop.getItemMeta() == null || toDrop.getItemMeta().getEnchants().isEmpty()) && sl.getEnchants() != null && !sl.getEnchants().isEmpty()) {
                                ItemMeta im = toDrop.getItemMeta();
                                if (im != null) {
                                    for (Map.Entry<String,Integer> en2 : sl.getEnchants().entrySet()) {
                                        try {
                                            Enchantment ec = Enchantment.getByKey(NamespacedKey.minecraft(en2.getKey()));
                                            if (ec != null && en2.getValue() != null) im.addEnchant(ec, en2.getValue(), true);
                                        } catch (Exception ignored) {}
                                    }
                                    toDrop.setItemMeta(im);
                                }
                            }
                        } catch (Exception ignored) {}

                        int amountToDrop = Math.max(1, 1 + Math.max(0, sl.getStock()));
                        toDrop.setAmount(amountToDrop);
                        // drop near the chest location
                        try {
                            l.getWorld().dropItemNaturally(l, sanitizeReturnedItem(toDrop));
                        } catch (Exception ignored) { try { l.getWorld().dropItemNaturally(l, toDrop); } catch (Exception ignored2) {} }
                    }
                }
            } catch (Exception ignored) {}
            store.remove(l);
        }
        p.sendMessage("§aショップを削除しました (チェスト破壊): " + shop.getName());
    }

    private Optional<Block> findAdjacentChest(Block b) {
        if (b == null) return Optional.empty();
        List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
        for (BlockFace f : faces) {
            Block nb = b.getRelative(f);
            if (nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL) return Optional.of(nb);
        }
        return Optional.empty();
    }

    private Optional<Block> findNearestAvailableChest(Block start, int radius) {
        if (start == null) return Optional.empty();
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = start.getWorld().getBlockAt(start.getX() + dx, start.getY() + dy, start.getZ() + dz);
                    if (b == null) continue;
                    if (!(b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST || b.getType() == Material.BARREL)) continue;
                    Location loc = b.getLocation();
                    // skip locked
                    if (chestLock != null && chestLock.getLock(loc).isPresent()) continue;
                    // check empty
                    BlockState st = b.getState();
                    if (st instanceof Container) {
                        Inventory inv = ((Container) st).getSnapshotInventory();
                        if (inv != null && !inv.isEmpty()) continue;
                    }
                    double dist = start.getLocation().distance(loc);
                    if (dist < bestDist) { best = b; bestDist = dist; }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        // protect interacting with shop signs
        try {
            Block clicked = e.getClickedBlock();
            if (clicked.getState() instanceof Sign) {
                List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
                for (BlockFace f : faces) {
                    Block nb = clicked.getRelative(f);
                    if (nb == null) continue;
                    if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                    Optional<ChestShop> maybeShop = store.get(nb.getLocation());
                    if (!maybeShop.isPresent()) continue;
                    ChestShop cs = maybeShop.get();
                    Player pl = (Player) e.getPlayer();
                    if (pl != null && (pl.isOp() || (cs.getOwner() != null && cs.getOwner().equals(pl.getUniqueId().toString())))) {
                        // owner/op allowed to interact
                        break;
                    }
                    e.setCancelled(true);
                    if (pl != null) pl.sendMessage("§cこのショップの看板は操作できません");
                    return;
                }
            }
        } catch (Exception ignored) {}
        if (!(e.getClickedBlock().getType() == Material.CHEST || e.getClickedBlock().getType() == Material.TRAPPED_CHEST || e.getClickedBlock().getType() == Material.BARREL)) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();
        Optional<ChestShop> opt = store.get(loc);
        if (!opt.isPresent()) return;
        ChestShop shop = opt.get();
        // If owner is sneaking (shift-clicking), open buyer UI instead of owner UI
        if (p.isSneaking() && p.getUniqueId().toString().equals(shop.getOwner())) {
            e.setCancelled(true);
            // create a shallow copy with a different owner id so openForPlayer treats player as non-owner
            ChestShop browseShop = new ChestShop("", shop.getOwnerName(), shop.getName(), shop.getCurrency());
            ChestShopUI.openForPlayer(p, browseShop, loc, store);
        }
    }

    @EventHandler
    public void onSignCreate(SignChangeEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        if (e.getPlayer() == null) return;
        String full = String.join(" ", e.getLines());
        String lower = full.toLowerCase();
        String marker = ">>shop";
        if (!lower.contains(marker)) return;

        // extract shop name after marker (case-insensitive)
        int pos = lower.indexOf(marker);
        String rest = full.substring(pos + marker.length()).trim();
        String shopName = rest.isEmpty() ? "shop-" + UUID.randomUUID().toString().substring(0,6) : rest;

        Block signBlock = e.getBlock();
        // protect existing shop signs from being modified/broken by non-owner
        try {
            // check adjacent chest blocks for an existing shop
            List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
            for (BlockFace f : faces) {
                Block nb = signBlock.getRelative(f);
                if (nb == null) continue;
                if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                Optional<ChestShop> maybe = store.get(nb.getLocation());
                if (maybe.isPresent()) {
                    Player pl = e.getPlayer();
                    ChestShop cs = maybe.get();
                    if (pl != null && (pl.isOp() || (cs.getOwner() != null && cs.getOwner().equals(pl.getUniqueId().toString())))) {
                        // allow owner/op to proceed
                    } else {
                        if (pl != null) pl.sendMessage("§cこの看板はショップに紐づいているため編集できません");
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        // Try to find the nearest suitable chest (empty + not locked) within a small radius first
        Optional<Block> chestBlock = findNearestAvailableChest(signBlock, 3);
        // Fallback to immediate adjacent chest if none found by radius search
        if (!chestBlock.isPresent()) chestBlock = findAdjacentChest(signBlock);
        if (!chestBlock.isPresent()) {
            e.getPlayer().sendMessage("§c看板の近くにチェストが見つかりません。チェストに紐づける必要があります。");
            return;
        }

        Location loc = chestBlock.get().getLocation();

        // Double-check: chest should not be locked
        if (chestLock != null && chestLock.getLock(loc).isPresent()) {
            e.getPlayer().sendMessage("§cこのチェストは保護されているためショップを作成できません");
            return;
        }

        // Ensure chest is empty
        BlockState st = chestBlock.get().getState();
        if (st instanceof Container) {
            Inventory inv = ((Container) st).getSnapshotInventory();
            if (inv != null && !inv.isEmpty()) {
                e.getPlayer().sendMessage("§cショップを作成するには空のチェストが必要です (近くに空チェストがありません)");
                return;
            }
        }

        Optional<ChestShop> exists = store.get(loc);
        if (exists.isPresent()) {
            e.getPlayer().sendMessage("§cこのチェストには既にショップがあります: " + exists.get().getName());
            return;
        }

        // prevent duplicate shop names server-wide (case-insensitive)
        Map<String, ChestShop> allShops = store.getAll();
        for (ChestShop cs : allShops.values()) {
            if (cs == null) continue;
            String existing = cs.getName();
            if (existing != null && existing.equalsIgnoreCase(shopName)) {
                e.getPlayer().sendMessage("§cショップ名 '" + shopName + "' は既に使われています。別の名前を指定してください。");
                return;
            }
        }

        ChestShop shop = new ChestShop(e.getPlayer().getUniqueId().toString(), e.getPlayer().getName(), shopName);
        store.save(loc, shop);
        // update sign lines to show canonical shop tag (preserve provided name)
        try {
            e.setLine(0, ">>Shop");
            if (shopName != null && !shopName.isEmpty()) e.setLine(1, shopName);
        } catch (Exception ignore) {}
        e.getPlayer().sendMessage("§aショップを作成しました: " + shopName);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        // ignore our own custom shop inventory openings to avoid recursion (buyer/owner/editor)
        if (e.getView() != null && e.getView().getTitle() != null) {
            String t = e.getView().getTitle();
            if (t.startsWith(ChestShopUI.TITLE_PREFIX) || t.startsWith(ChestShopUI.OWNER_TITLE_PREFIX) || t.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) return;
        }
        if (!toggle.getGlobal("chestshop")) return;
            if (e.getInventory() == null) return;
            if (e.getInventory().getType() != InventoryType.CHEST && e.getInventory().getType() != InventoryType.BARREL) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();

        Location holderLoc = null;
        if (e.getInventory().getHolder() instanceof BlockState) {
            holderLoc = ((BlockState) e.getInventory().getHolder()).getLocation();
        } else {
            Block b = p.getTargetBlockExact(6);
            if (b != null) holderLoc = b.getLocation();
        }
        if (holderLoc == null) return;

        Optional<ChestShop> opt = store.get(holderLoc);
        if (!opt.isPresent()) return;
        // cancel the normal chest open so we handle via custom ChestShop UI only
        e.setCancelled(true);
        ChestShop shop = opt.get();

        // open a separate shop UI to show owner/buyer hints
        ChestShopUI.openForPlayer(p, shop, holderLoc, store);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView() == null || e.getView().getTitle() == null) return;
        String title = e.getView().getTitle();
        if (!(title.startsWith(ChestShopUI.TITLE_PREFIX) || title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX) || title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX))) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        // suppress handler if this close was caused by our own scheduled open (avoid recursion)
        if (suppressCloseHandling.remove(p.getUniqueId())) {
            Bukkit.getLogger().info("[ChestShop] onInventoryClose suppressed for player=" + p.getName());
            return;
        }

        // Editor closed -> parse and store listings
        if (title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) {
            ResolveResult rr = resolveByTitle(title);
            if (rr == null) return;
            Location loc = rr.loc;
            ChestShop shop = rr.shop;
            Inventory inv = e.getInventory();
            Map<Integer, ShopListing> old = store.getListings(loc);
            Map<Integer, ShopListing> updated = new LinkedHashMap<>();
            for (int i = 0; i < 26; i++) {
                ItemStack it = inv.getItem(i);
                if (it == null) continue;
                String raw = null;
                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) raw = it.getItemMeta().getDisplayName();
                if (raw == null) raw = it.getType().name();
                String rawDisplay = null;
                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) rawDisplay = it.getItemMeta().getDisplayName();
                if (rawDisplay == null) rawDisplay = it.getType().name();
                ShopListing prev = old.get(i);
                ShopListing sl = parseRawToListing(it, rawDisplay, prev);
                updated.put(i, sl);
            }
            store.saveListings(loc, updated);
            p.sendMessage("§a出品データを保存しました (" + updated.size() + " 件)");
            // return to owner main UI - schedule next tick to avoid re-entrant inventory events
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Bettersurvival");
            if (plugin != null) {
                // mark player as suppressed to avoid immediate re-entrant close handling
                suppressCloseHandling.add(p.getUniqueId());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        InventoryView cur = p.getOpenInventory();
                        String curTitle = cur == null ? null : cur.getTitle();
                        // don't reopen if player already has a different inventory open
                        if (curTitle != null && !curTitle.startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) return;
                        ChestShopUI.openForPlayer(p, shop, loc, store);
                        // remove suppression after a couple ticks so subsequent real closes are handled
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> suppressCloseHandling.remove(p.getUniqueId()), 3L);
                    } catch (Exception ignored) {}
                });
            } else {
                ChestShopUI.openForPlayer(p, shop, loc, store);
            }
            // Clear mapping when editor closes
            ChestShopUI.closeForPlayer(p);
            return;
        }

        // Owner main UI closed -> handle supply slot restock
        if (title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) {
            ResolveResult rr = resolveByTitle(title);
            if (rr == null) return;
            Location loc = rr.loc;
            Inventory inv = e.getInventory();
            ItemStack supply = inv.getItem(10);
                if (supply != null) {
                Map<Integer, ShopListing> listings = store.getListings(loc);
                boolean applied = false;
                for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                    ShopListing sl = en.getValue();
                    if (listingMatchesItem(supply, sl)) {
                        sl.setStock(sl.getStock() + supply.getAmount());
                        store.saveListings(loc, listings);
                        applied = true;
                        break;
                    }
                }
                        if (applied) {
                    p.sendMessage("§a仕入れを補充しました: " + supply.getType().toString() + " x" + supply.getAmount());
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                } else {
                            // return the supplied item back to player's inventory (or drop if full)
                            try {
                                Map<Integer, ItemStack> rema = p.getInventory().addItem(supply);
                                if (!rema.isEmpty()) for (ItemStack r : rema.values()) if (r != null) p.getWorld().dropItemNaturally(p.getLocation(), r);
                            } catch (Exception ignored) {}
                            p.sendMessage("§cこの仕入れアイテムに対応する出品が見つかりませんでした — アイテムを返却しました");
                }
            }
            // Always keep mapping for editor access, clear only when editor closes
            // ChestShopUI.closeForPlayer((Player) e.getPlayer());
            return;
        }

        ChestShopUI.closeForPlayer((Player) e.getPlayer());
    }

    @SuppressWarnings("null")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryView view = e.getView();
        if (view == null || view.getTitle() == null) return;
        String title = view.getTitle();
        if (!(title.startsWith(ChestShopUI.TITLE_PREFIX) || title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX) || title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX))) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        
        // display name available via meta when needed

        // Owner main UI
        if (title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) {
            ResolveResult rr = resolveByTitle(title);
            if (rr == null) return;
            Location loc = rr.loc;
            ChestShop shop = rr.shop;

            // handle earnings collection
            if (e.getRawSlot() == 15) {
                if (shop.getEarnings() > 0) {
                    Material curMat = Material.matchMaterial(shop.getCurrency());
                    if (curMat != null) {
                        ItemStack give = new ItemStack(curMat, shop.getEarnings());
                        Map<Integer, ItemStack> leftover = p.getInventory().addItem(give);
                        if (!leftover.isEmpty()) {
                            for (ItemStack drop : leftover.values()) {
                                p.getWorld().dropItemNaturally(p.getLocation(), drop);
                            }
                        }
                        shop.setEarnings(0);
                        store.save(loc, shop);
                        p.sendMessage("§a収益を回収しました: " + give.getAmount() + " " + shop.getCurrency());
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        // update UI
                        ChestShopUI.openForPlayer(p, shop, loc, store);
                    }
                } else {
                    p.sendMessage("§c回収する収益がありません");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                e.setCancelled(true);
                return;
            }
            // allow supply/currency interaction (raw slot 10 and 12)
            if (e.getRawSlot() == 10) {
                // allow placing and removing items into supply slot, then process restock shortly
                // explicitly allow default inventory behavior (do not cancel this event)
                try { e.setCancelled(false); } catch (Exception ignored) {}
                Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Bettersurvival");
                if (plugin == null) return;
                // schedule one tick later so the inventory reflects the player's placement
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ResolveResult rrInner = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rrInner == null) return;
                    Location locInner = rrInner.loc;
                    Map<Integer, ShopListing> listings = store.getListings(locInner);
                    Inventory invnow = e.getInventory();
                    if (invnow == null) return;
                    ItemStack supplyNow = invnow.getItem(10);
                    if (supplyNow == null) return;
                    boolean applied = false;
                    for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                        ShopListing sl = en.getValue();
                            if (listingMatchesItem(supplyNow, sl)) {
                                sl.setStock(sl.getStock() + supplyNow.getAmount());
                                store.saveListings(locInner, listings);
                            invnow.setItem(10, null);
                            p.sendMessage("§a仕入れを補充しました: " + supplyNow.getType().toString() + " x" + supplyNow.getAmount());
                            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                            applied = true;
                            break;
                        }
                    }
                    if (!applied) {
                        // return the item to the player's inventory immediately (avoid losing it)
                        try {
                            invnow.setItem(10, null);
                            Map<Integer, ItemStack> rema = p.getInventory().addItem(supplyNow);
                            if (!rema.isEmpty()) {
                                // if player's inventory full, drop to world
                                for (ItemStack r : rema.values()) if (r != null) p.getWorld().dropItemNaturally(p.getLocation(), r);
                            }
                        } catch (Exception ignored) {}
                        p.sendMessage("§cこの仕入れアイテムに対応する出品が見つかりませんでした");
                    }
                    // Update owner info in real-time
                    try {
                        if (rrInner.shop != null && rrInner.shop.getOwner() != null) {
                            Player ownerPlayer = Bukkit.getPlayer(java.util.UUID.fromString(rrInner.shop.getOwner()));
                            if (ownerPlayer != null) ChestShopUI.updateOwnerInfo(ownerPlayer, store);
                        }
                    } catch (Exception ignored) {}
                }, 1L);
                return;
            }

            // allow normal clicks in the player's own inventory (bottom) — only let SHIFT-clicks fall through
            // support SHIFT-click from player inventory -> move to slot 10 (supply) or slot 12 (currency) when appropriate
            try {
                InventoryView viewNow = p.getOpenInventory();
                int topSize = viewNow == null || viewNow.getTopInventory() == null ? 0 : viewNow.getTopInventory().getSize();
                if (e.getRawSlot() >= topSize && e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    // regular bottom-inventory clicks should be allowed
                    try { e.setCancelled(false); } catch (Exception ignored) {}
                    return;
                }
                if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getRawSlot() >= topSize) {
                    ResolveResult rrMove = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rrMove == null) return;
                    Location locMove = rrMove.loc;
                    Map<Integer, ShopListing> listings = store.getListings(locMove);
                    ItemStack source = e.getCurrentItem();
                    if (source == null) return;
                    // try supply first
                    Inventory top = viewNow.getTopInventory();
                    if (top == null) return;
                    ItemStack existingSupply = top.getItem(10);
                    boolean matchesListing = false;
                    for (ShopListing sl : listings.values()) if (sl != null && listingMatchesItem(source, sl)) { matchesListing = true; break; }
                    if ((existingSupply == null && matchesListing) || (existingSupply != null && (existingSupply.getType() == source.getType() || existingSupply.isSimilar(source)))) {
                        // move whole stack into supply slot
                        top.setItem(10, source.clone());
                        // clear clicked bottom slot
                        try { ((Player)e.getWhoClicked()).getInventory().setItem(e.getSlot(), null); } catch (Exception ignored) {}
                        // process restock immediately (in next tick)
                        org.bukkit.plugin.Plugin plugin2 = Bukkit.getPluginManager().getPlugin("Bettersurvival");
                        if (plugin2 == null) return;
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin2, () -> {
                            try {
                                ItemStack supNow = top.getItem(10);
                                if (supNow == null) return;
                                boolean applied2 = false;
                                Map<Integer, ShopListing> listingsNow = store.getListings(locMove);
                                for (Map.Entry<Integer, ShopListing> en : listingsNow.entrySet()) {
                                    ShopListing sl = en.getValue();
                                    if (listingMatchesItem(supNow, sl)) {
                                        sl.setStock(sl.getStock() + supNow.getAmount());
                                        store.saveListings(locMove, listingsNow);
                                        top.setItem(10, null);
                                        ((Player)e.getWhoClicked()).sendMessage("§a仕入れを補充しました: " + supNow.getType().toString() + " x" + supNow.getAmount());
                                        ((Player)e.getWhoClicked()).playSound(((Player)e.getWhoClicked()).getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                                        applied2 = true;
                                        break;
                                    }
                                }
                                if (!applied2) {
                                    // return item to player's inventory (drop to world if no space)
                                    try {
                                        top.setItem(10, null);
                                        Map<Integer, ItemStack> ret = ((Player)e.getWhoClicked()).getInventory().addItem(source);
                                        if (!ret.isEmpty()) for (ItemStack r : ret.values()) if (r != null) ((Player)e.getWhoClicked()).getWorld().dropItemNaturally(((Player)e.getWhoClicked()).getLocation(), r);
                                        ((Player)e.getWhoClicked()).sendMessage("§cこの仕入れアイテムに対応する出品が見つかりませんでした");
                                        ((Player)e.getWhoClicked()).playSound(((Player)e.getWhoClicked()).getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                    } catch (Exception ignored) {}
                                }
                                // Update owner info in real-time
                                try {
                                    if (rr.shop != null && rr.shop.getOwner() != null) {
                                        Player ownerPlayer = Bukkit.getPlayer(java.util.UUID.fromString(rr.shop.getOwner()));
                                        if (ownerPlayer != null) ChestShopUI.updateOwnerInfo(ownerPlayer, store);
                                    }
                                } catch (Exception ignored) {}
                            } catch (Exception ignored) {}
                        }, 1L);
                        e.setCancelled(true);
                        return;
                    }
                    // otherwise, if currency slot empty, place into slot 12 to set currency
                    ItemStack cur12 = top.getItem(12);
                    if (cur12 == null) {
                        top.setItem(12, source.clone());
                        try { ((Player)e.getWhoClicked()).getInventory().setItem(e.getSlot(), null); } catch (Exception ignored) {}
                        // save currency
                        String curMat = source.getType().name();
                        boolean ok = store.saveShopCurrency(locMove, curMat);
                        if (ok) {
                            rrMove.shop.setCurrency(curMat);
                            ((Player)e.getWhoClicked()).sendMessage("§a通貨を設定しました: " + curMat);
                            ((Player)e.getWhoClicked()).playSound(((Player)e.getWhoClicked()).getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                        }
                        e.setCancelled(true);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            if (e.getRawSlot() == 12) {
                // currency slot — schedule saving the chosen currency material (or clearing if removed)
                // explicitly allow default inventory behavior (do not cancel this event)
                try { e.setCancelled(false); } catch (Exception ignored) {}
                Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Bettersurvival");
                if (plugin == null) return;
                // schedule one tick later to let inventory update then process instantly
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ResolveResult rrInner = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rrInner == null) return;
                    Location locInner = rrInner.loc;
                    ChestShop shopInner = rrInner.shop;
                    Inventory nowInv = e.getInventory();
                    if (nowInv == null) return;
                    ItemStack cur = nowInv.getItem(12);
                    String curMat = cur == null ? null : cur.getType().name();
                    boolean ok = store.saveShopCurrency(locInner, curMat);
                    if (ok) {
                        shopInner.setCurrency(curMat);
                        p.sendMessage(curMat == null ? "§e通貨設定を解除しました" : ("§a通貨を設定しました: " + curMat));
                        p.playSound(p.getLocation(), curMat == null ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                        // if owner UI is open, reflect change immediately
                        try {
                            InventoryView now = p.getOpenInventory();
                            if (now != null && now.getTitle() != null && now.getTitle().startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) {
                                org.bukkit.inventory.Inventory top = now.getTopInventory();
                                if (top != null) top.setItem(12, cur == null ? null : cur);
                                if (p != null) p.playSound(p.getLocation(), cur == null ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        p.sendMessage("§c通貨設定の保存に失敗しました");
                    }
                }, 1L);
                return;
            }

            // allow opening editor from owner main
            if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String d = clicked.getItemMeta().getDisplayName();
                if (d.contains("編集ページを開く")) {
                    ResolveResult rrEditor = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rrEditor == null) return;
                    Location locEditor = rrEditor.loc;
                    ChestShop shopEditor = rrEditor.shop;
                    if (!p.getUniqueId().toString().equals(shop.getOwner()) && !p.isOp()) { p.sendMessage("§cオーナーのみ編集できます"); return; }
                    // populate editor inventory
                    Inventory editor = Bukkit.createInventory(null, 27, ChestShopUI.EDITOR_TITLE_PREFIX + shopEditor.getName());
                    Map<Integer, ShopListing> listings = store.getListings(locEditor);
                    for (int i = 0; i < 26; i++) {
                        ShopListing sl = listings.get(i);
                        if (sl == null) continue;
                        Material mat = Material.matchMaterial(sl.getMaterial());
                        if (mat == null) mat = Material.PAPER;
                        ItemStack it = null;
                        // reconstruct original item if serialized data available to preserve enchants/NBT
                        if (sl.getItemData() != null) {
                            try {
                                it = ItemStack.deserialize(sl.getItemData());
                                if (it == null) it = new ItemStack(mat, 1);
                            } catch (Exception ignored) { it = new ItemStack(mat, 1); }
                        } else {
                            it = new ItemStack(mat, 1);
                        }
                        // ensure editor shows a single sample item but append editor-only lore
                        it.setAmount(1);
                        ItemMeta im = it.getItemMeta();
                        if (im != null) {
                            // prefer existing display name, otherwise use stored displayName
                            if ((im.hasDisplayName() && im.getDisplayName() != null) || sl.getDisplayName() == null) {
                                // keep existing
                            } else {
                                im.setDisplayName(sl.getDisplayName());
                            }
                            // start with existing lore but remove any editor-only lines to avoid duplication
                            List<String> lore = new ArrayList<>();
                            if (im.hasLore() && im.getLore() != null) {
                                for (String L : im.getLore()) {
                                    if (L == null) continue;
                                    if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                                    lore.add(L);
                                }
                            }
                            // append editor-only lines exactly once
                            lore.add("在庫: " + sl.getStock());
                            lore.add("価格: " + sl.getPrice());
                            if (sl.getDescription() != null && !sl.getDescription().isEmpty()) lore.add("説明: " + sl.getDescription());
                            im.setLore(lore);
                            it.setItemMeta(im);
                        }
                        editor.setItem(i, it);
                    }
                    // ensure editor mapping exists so autosave/detection can find this shop
                    ChestShopUI.registerOpen(p.getUniqueId(), shopEditor, locEditor);
                    Bukkit.getLogger().info("[ChestShop] Opening editor for player=" + p.getName() + " shop=" + shopEditor.getName());
                    // initialize snapshot so periodic tick doesn't write unnecessarily
                    try {
                        StringBuilder sbinit = new StringBuilder();
                        for (int i = 0; i < 26; i++) {
                            ItemStack it2 = editor.getItem(i);
                            if (it2 == null) { sbinit.append("-"); continue; }
                            String disp = null;
                            if (it2.hasItemMeta() && it2.getItemMeta().hasDisplayName()) disp = it2.getItemMeta().getDisplayName();
                            if (disp == null) disp = it2.getType().name();
                            sbinit.append(it2.getType().name()).append('|').append(disp).append('|');
                            if (it2.hasItemMeta() && it2.getItemMeta().hasLore()) sbinit.append(String.join("/", it2.getItemMeta().getLore()));
                            sbinit.append(':').append(it2.getAmount()).append(';');
                        }
                        editorSnapshotHash.put(p.getUniqueId(), sbinit.toString().hashCode());
                        editorLastSavedTick.put(p.getUniqueId(), System.currentTimeMillis());
                    } catch (Exception ignored) {}
                    // manual save removed — rely on autosave
                    p.openInventory(editor);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    return;
                }
                if (d.contains("閉じる")) {
                    ChestShopUI.closeForPlayer(p);
                    p.closeInventory();
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    return;
                }
            }

            e.setCancelled(true);
            return;
        }

        // Editor UI - handle moving an editor item into player inventory (shift-click)
        if (title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) {
            try {
                InventoryView viewNow = p.getOpenInventory();
                int topSize = viewNow == null || viewNow.getTopInventory() == null ? 0 : viewNow.getTopInventory().getSize();
                // handle shift-click from editor top -> player inventory
                if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getRawSlot() < topSize) {
                    ResolveResult rr = resolveByTitle(title);
                    if (rr == null) return;
                    Location loc = rr.loc;
                    Map<Integer, ShopListing> listings = store.getListings(loc);
                    ShopListing sl = listings.get(e.getRawSlot());
                    if (sl == null) return;
                    // build a prototype and count player's existing similar items so we can
                    // only add the missing amount on the next tick (avoids duplication)
                    try { e.setCancelled(true); } catch (Exception ignored) {}
                    ItemStack proto = null;
                    if (sl.getItemData() != null) {
                        try { proto = ItemStack.deserialize(sl.getItemData()); } catch (Exception ignored) { proto = null; }
                    }
                    if (proto == null) {
                        Material m = Material.matchMaterial(sl.getMaterial());
                        proto = new ItemStack(m == null ? Material.PAPER : m);
                    }
                    // sanitize proto meta
                    try {
                        ItemMeta pm = proto.getItemMeta();
                        if (pm != null) {
                            if (pm.hasLore() && pm.getLore() != null) {
                                List<String> nl = new ArrayList<>();
                                for (String L : pm.getLore()) {
                                    if (L == null) continue;
                                    if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                                    String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                    if (cleaned.isEmpty()) continue;
                                    nl.add(cleaned);
                                }
                                pm.setLore(nl.isEmpty() ? null : nl);
                            }
                            if (pm.hasDisplayName() && pm.getDisplayName() != null) {
                                String name = pm.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                                pm.setDisplayName(name.isEmpty() ? null : name);
                            }
                            proto.setItemMeta(pm);
                        }
                    } catch (Exception ignored) {}
                    proto.setAmount(1);
                    final ItemStack protoForCount = proto.clone();
                    final int beforeCount = countSimilarInPlayer(p, protoForCount);
                    Plugin plugin = Bukkit.getPluginManager().getPlugin("Bettersurvival");
                    if (plugin == null) return;
                    final int clickedSlot = e.getRawSlot();
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            ResolveResult rrLater2 = resolveByTitle(title);
                            if (rrLater2 == null) return;
                            Location locLater = rrLater2.loc;
                            Map<Integer, ShopListing> listingsLater = store.getListings(locLater);
                            ShopListing slLater = listingsLater.get(clickedSlot);
                            if (slLater == null) return;
                            ItemStack give = null;
                            if (slLater.getItemData() != null) {
                                try { give = ItemStack.deserialize(slLater.getItemData()); } catch (Exception ignored) { give = null; }
                            }
                            if (give == null) {
                                Material m = Material.matchMaterial(slLater.getMaterial());
                                give = new ItemStack(m == null ? Material.PAPER : m);
                            }
                                ItemMeta gm = give.getItemMeta();
                            if (gm != null) {
                                if (gm.hasLore() && gm.getLore() != null) {
                                    List<String> newl = new ArrayList<>();
                                    for (String L : gm.getLore()) {
                                        if (L == null) continue;
                                        if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                                        String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                        if (cleaned.isEmpty()) continue;
                                        newl.add(cleaned);
                                    }
                                    gm.setLore(newl.isEmpty() ? null : newl);
                                }
                                if (gm.hasDisplayName() && gm.getDisplayName() != null) {
                                    String name = gm.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                                    if (name.isEmpty()) gm.setDisplayName(null); else gm.setDisplayName(name);
                                }
                                give.setItemMeta(gm);
                            }
                            int amountToGive = Math.max(1, 1 + Math.max(0, slLater.getStock()));
                            Player receiver = (Player)e.getWhoClicked();
                            // compute how many similar items the player already received since beforeCount
                            int afterCount = countSimilarInPlayer(receiver, protoForCount);
                            int alreadyReceived = Math.max(0, afterCount - beforeCount);
                            int toGive = Math.max(0, amountToGive - alreadyReceived);
                            if (toGive > 0 && !wasRemovalRecentlyHandled(receiver, locLater, clickedSlot)) {
                                give.setAmount(toGive);
                                giveOrMergeToPlayer(receiver, give);
                                markRemovalHandled(receiver, locLater, clickedSlot);
                            }
                            // remove listing and persist
                            listingsLater.remove(clickedSlot);
                            store.saveListings(locLater, listingsLater);

                            if (viewNow != null && viewNow.getTopInventory() != null) viewNow.getTopInventory().setItem(clickedSlot, null);
                            receiver.sendMessage("§a出品をエディターから取り出しました、在庫を返却しました: " + give.getType().toString() + " x" + amountToGive);
                            receiver.playSound(receiver.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                        } catch (Exception ignored) {}
                    }, 1L);
                    return;
                }
                // --- additionally handle other click/drag pickup flows ---
                // Some clients move items via pickup/place rather than MOVE_TO_OTHER_INVENTORY.
                // Schedule a next-tick check whenever a top-inventory editor slot was clicked
                // and may have been moved to the player's inventory. If the editor slot is
                // found empty next tick, treat it as a removal and return stock and clean lore.
                if (e.getClickedInventory() != null && e.getClickedInventory() == viewNow.getTopInventory() && e.getRawSlot() < topSize) {
                    Plugin plugin = Bukkit.getPluginManager().getPlugin("Bettersurvival");
                    if (plugin != null) {
                        final int clickedSlot = e.getRawSlot();
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            try {
                                ResolveResult rrLater = resolveByTitle(title);
                                if (rrLater == null) return;
                                Location locLater = rrLater.loc;
                                Map<Integer, ShopListing> listingsLater = store.getListings(locLater);
                                Inventory topInv = viewNow.getTopInventory();
                                if (topInv == null) return;
                                ItemStack nowTopItem = topInv.getItem(clickedSlot);
                                // if the editor slot is empty now but the listing still exists, treat as removal
                                if (nowTopItem == null && listingsLater.containsKey(clickedSlot)) {
                                    ShopListing removed = listingsLater.get(clickedSlot);
                                    if (removed == null) return;
                                    // build return item (preserve NBT if possible)
                                    ItemStack giveBack = null;
                                    if (removed.getItemData() != null) {
                                        try { giveBack = ItemStack.deserialize(removed.getItemData()); } catch (Exception ignored) { giveBack = null; }
                                    }
                                    if (giveBack == null) {
                                        Material m = Material.matchMaterial(removed.getMaterial());
                                        giveBack = new ItemStack(m == null ? Material.PAPER : m);
                                    }
                                    // clean editor-only lore and display name
                                    ItemMeta gm = giveBack.getItemMeta();
                                    if (gm != null) {
                                        if (gm.hasLore() && gm.getLore() != null) {
                                            List<String> newl = new ArrayList<>();
                                            for (String L : gm.getLore()) {
                                                if (L == null) continue;
                                                if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                                                String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                                if (cleaned.isEmpty()) continue;
                                                newl.add(cleaned);
                                            }
                                            gm.setLore(newl.isEmpty() ? null : newl);
                                        }
                                        if (gm.hasDisplayName() && gm.getDisplayName() != null) {
                                            String name = gm.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                                            if (name.isEmpty()) gm.setDisplayName(null); else gm.setDisplayName(name);
                                        }
                                        giveBack.setItemMeta(gm);
                                    }
                                    // amount: one sample + stock
                                    int amountToGive = Math.max(1, 1 + Math.max(0, removed.getStock()));
                                    Player clicker = (Player) e.getWhoClicked();
                                    if (!wasRemovalRecentlyHandled(clicker, locLater, clickedSlot)) {
                                        giveBack = sanitizeReturnedItem(giveBack);
                                        ItemStack proto2 = giveBack.clone(); proto2.setAmount(1);
                                        int existing2 = countSimilarInPlayer(clicker, proto2);
                                        int toGive2 = Math.max(0, amountToGive - existing2);
                                        if (toGive2 > 0) {
                                            giveBack.setAmount(toGive2);
                                            giveOrMergeToPlayer(clicker, giveBack);
                                        }
                                        markRemovalHandled(clicker, locLater, clickedSlot);
                                    }

                                    // remove listing and persist
                                    listingsLater.remove(clickedSlot);
                                    store.saveListings(locLater, listingsLater);

                                    // inform and play sound
                                    clicker.sendMessage("§a出品をエディターから取り出しました、在庫を返却しました: " + giveBack.getType().toString() + " x" + amountToGive);
                                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                                    // ensure editor UI shows cleared slot
                                    if (topInv.getItem(clickedSlot) != null) topInv.setItem(clickedSlot, null);
                                }
                            } catch (Exception ignored) {}
                        }, 1L);
                    }
                }
            } catch (Exception ignored) {}
            // otherwise allow simple clicks and drag — auto-save handles state
            return;
        }

        // Buyer UI: handle buying items
        if (title.startsWith(ChestShopUI.TITLE_PREFIX)) {
            ResolveResult rr = resolveByTitle(title);
            if (rr == null) return;
            Location loc = rr.loc;
            ChestShop shop = rr.shop;
            if (shop == null || shop.getCurrency() == null) return;
            Map<Integer, ShopListing> listings = store.getListings(loc);
            ShopListing sl = listings.get(e.getRawSlot());
            if (sl != null && sl.getStock() > 0) {
                // guard: listing must have a valid price
                if (sl.getPrice() <= 0) {
                    p.sendMessage("§cこの出品は販売情報が不十分なため購入できません");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    e.setCancelled(true);
                    return;
                }
                // guard: don't sell damaged items (non-max durability)
                if (sl.getDamage() > 0) {
                    p.sendMessage("§cこのアイテムは耐久が減っているため購入できません");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    e.setCancelled(true);
                    return;
                }
                int price = sl.getPrice();
                Material curMat = Material.matchMaterial(shop.getCurrency());
                if (curMat != null) {
                    int has = countItems(p.getInventory(), curMat);
                    if (has >= price) {
                        // remove currency
                        removeItems(p.getInventory(), curMat, price);
                        // give item (preserve enchantments)
                        Material itemMat = Material.matchMaterial(sl.getMaterial());
                            if (itemMat != null) {
                                ItemStack give = null;
                                if (sl.getItemData() != null) {
                                    try { give = ItemStack.deserialize(sl.getItemData()); } catch (Exception ignored) { give = null; }
                                }
                                if (give == null) give = new ItemStack(itemMat, 1);
                            Map<Integer, ItemStack> leftover = p.getInventory().addItem(give);
                            if (!leftover.isEmpty()) {
                                for (ItemStack drop : leftover.values()) {
                                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                                }
                            }
                        }
                        // decrease stock
                        sl.setStock(sl.getStock() - 1);
                        store.saveListings(loc, listings);
                        // add to earnings
                        shop.setEarnings(shop.getEarnings() + price);
                        store.save(loc, shop);
                        String dispName = sl.getDisplayName();
                        if (dispName == null || dispName.isEmpty()) {
                            Material mm = Material.matchMaterial(sl.getMaterial());
                            dispName = mm == null ? sl.getMaterial() : mm.name();
                        }
                        dispName = dispName.replaceAll("\\{[^}]*\\}", "").trim();
                        p.sendMessage("§a購入しました: " + dispName + " for " + price + " " + shop.getCurrency());
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                        // update owner info if owner online
                        try {
                            Player owner = Bukkit.getPlayer(UUID.fromString(shop.getOwner()));
                            if (owner != null) {
                                ChestShopUI.updateOwnerInfo(owner, store);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        p.sendMessage("§c通貨が不足しています");
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                }
            } else if (sl != null && sl.getStock() <= 0) {
                // Inform buyer when attempting to purchase a sold-out item and play an error sound.
                p.sendMessage("§cそのアイテムは品切れです");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            e.setCancelled(true);
            return;
        }
    }

    @SuppressWarnings("null")
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        InventoryView view = e.getView();
        if (view == null || view.getTitle() == null) return;
        String title = view.getTitle();
        if (!title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) {
            // Owner UI: allow drag-to-top for supply/currency slots (10 and 12)
            if (title.startsWith(ChestShopUI.OWNER_TITLE_PREFIX)) {
                if (!(e.getWhoClicked() instanceof Player)) return;
                Player p = (Player) e.getWhoClicked();
                Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Bettersurvival");
                if (plugin == null) return;
                Bukkit.getLogger().info("[ChestShop] scheduling owner drag-save task for player=" + p.getName());
                // Only allow drag updates that include top slots 10 or 12 and sanitize those placements
                try {
                    InventoryView viewNow = e.getView();
                    int topSize = viewNow == null || viewNow.getTopInventory() == null ? 0 : viewNow.getTopInventory().getSize();
                    Map<Integer, ItemStack> newItems = e.getNewItems();
                    boolean touchedSupplyOrCurrency = false;
                    for (Map.Entry<Integer, ItemStack> ent : newItems.entrySet()) {
                        int raw = ent.getKey();
                        if (raw < topSize) {
                            // top inventory targets
                            if (raw == 10 || raw == 12) {
                                touchedSupplyOrCurrency = true;
                                ItemStack v = ent.getValue();
                                if (v == null) continue;
                                // sanitize: remove editor-only lore
                                // preserve enchantments / meta while removing editor-only lore only
                                ItemStack sanitized = v.clone();
                                sanitized.setAmount(v.getAmount());
                                ItemMeta im = sanitized.getItemMeta();
                                if (im != null) {
                                    if (v.hasItemMeta() && v.getItemMeta().hasDisplayName()) {
                                        // sanitize display name by stripping inline JSON
                                        try { im.setDisplayName(v.getItemMeta().getDisplayName().replaceAll("\\{[^}]*\\}", "").trim()); } catch (Exception ignored) {}
                                    }
                                    // keep enchantments, but remove editor-only lore lines
                                    if (im.hasLore() && im.getLore() != null) {
                                        List<String> newLore = new ArrayList<>();
                                        for (String L : im.getLore()) {
                                            if (L == null) continue;
                                            if (L.startsWith("在庫:") || L.startsWith("価格:") || L.startsWith("説明:")) continue;
                                            String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                            if (cleaned.isEmpty()) continue;
                                            newLore.add(cleaned);
                                        }
                                        im.setLore(newLore.isEmpty() ? null : newLore);
                                    }
                                    sanitized.setItemMeta(im);
                                }
                                ent.setValue(sanitized);
                            }
                        }
                    }
                    if (!touchedSupplyOrCurrency) return;
                    // allow the drag action to proceed (don't block player from placing items)
                    try { e.setCancelled(false); } catch (Exception ignored) {}
                } catch (Exception ignored) {}

                // schedule the same processing as owner slot handlers to run next tick
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ResolveResult rr = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rr == null) return;
                    Location loc = rr.loc;
                    Map<Integer, ShopListing> listings = store.getListings(loc);
                    Inventory invnow = e.getInventory();
                    if (invnow == null) return;
                    // handle supply slot 10
                    try {
                        ItemStack supplyNow = invnow.getItem(10);
                        if (supplyNow != null) {
                            boolean applied = false;
                            for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                                ShopListing sl = en.getValue();
                                if (listingMatchesItem(supplyNow, sl)) {
                                    sl.setStock(sl.getStock() + supplyNow.getAmount());
                                    store.saveListings(loc, listings);
                                    invnow.setItem(10, null);
                                    if (p != null) { p.sendMessage("§a仕入れを補充しました: " + supplyNow.getType().toString() + " x" + supplyNow.getAmount()); p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f); }
                                    applied = true;
                                    break;
                                }
                            }
                            if (!applied && p != null) {
                                try {
                                    invnow.setItem(10, null);
                                    Map<Integer, ItemStack> rema = p.getInventory().addItem(supplyNow);
                                    if (!rema.isEmpty()) for (ItemStack r : rema.values()) if (r != null) p.getWorld().dropItemNaturally(p.getLocation(), r);
                                } catch (Exception ignored2) {}
                                p.sendMessage("§cこの仕入れアイテムに対応する出品が見つかりませんでした");
                                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            }
                        }
                    } catch (Exception ignored) {}

                    // handle currency slot 12
                    try {
                        ItemStack cur = invnow.getItem(12);
                        String curMat = cur == null ? null : cur.getType().name();
                        boolean ok = store.saveShopCurrency(loc, curMat);
                            if (ok && rr.shop != null) {
                            rr.shop.setCurrency(curMat);
                            if (p != null) { p.sendMessage(curMat == null ? "§e通貨設定を解除しました" : ("§a通貨を設定しました: " + curMat)); p.playSound(p.getLocation(), curMat == null ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f); }
                        }
                    } catch (Exception ignored) {}

                    // Update owner info in real-time after supply/currency changes
                    try {
                        if (rr.shop != null && rr.shop.getOwner() != null) {
                            Player ownerPlayer = Bukkit.getPlayer(java.util.UUID.fromString(rr.shop.getOwner()));
                            if (ownerPlayer != null) ChestShopUI.updateOwnerInfo(ownerPlayer, store);
                        }
                    } catch (Exception ignored) {}

                }, 1L);

                return;
            }
            return;
        }
        // エディターのドラッグ処理は無視 (シンプルなインベントリ移動のみ許可)
        if (title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) {
            // ドラッグ&ドロップはシンプルに許可し、自動保存に任せる
            return;
        }
    }

    public ChestShopStore getStore() { return store; }

    // Attempt to merge or overwrite a matching item in player's cursor/inventory before adding new.
    private void giveOrMergeToPlayer(Player p, ItemStack prototype) {
        if (p == null || prototype == null) return;
        // sanitize prototype first to ensure returned items don't carry editor-only lore
        final ItemStack protoSan = sanitizeReturnedItem(prototype.clone());
        try {
            PlayerInventory inv = p.getInventory();
            int amountToGive = prototype.getAmount();
            // helper to compare ignoring amount
            java.util.function.Predicate<ItemStack> similar = (it) -> {
                if (it == null) return false;
                // require same material first
                if (it.getType() != protoSan.getType()) return false;
                ItemStack a = it.clone(); a.setAmount(1);
                ItemStack b = protoSan.clone(); b.setAmount(1);
                if (a.isSimilar(b)) return true;
                // fallback: compare cleaned display names (strip inline JSON blocks)
                try {
                    String an = null, bn = null;
                    if (a.hasItemMeta() && a.getItemMeta().hasDisplayName()) an = a.getItemMeta().getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                    if (b.hasItemMeta() && b.getItemMeta().hasDisplayName()) bn = b.getItemMeta().getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                    if (an != null && bn != null && an.equals(bn)) return true;
                } catch (Exception ignored) {}
                return false;
            };

            // If player has the item on cursor, merge/overwrite there
            try {
                ItemStack cursor = p.getItemOnCursor();
                if (cursor != null && similar.test(cursor)) {
                    ItemStack merged = cursor;
                    ItemMeta pm = prototype.getItemMeta();
                    ItemMeta cm = merged.getItemMeta();
                    if (cm != null) {
                        if (pm != null) {
                            try { cm.setDisplayName(pm.getDisplayName()); } catch (Exception ignored) {}
                            try { cm.setLore(pm.getLore()); } catch (Exception ignored) {}
                        } else {
                            // no prototype meta -> clean existing meta to remove editor-only lines
                            try {
                                if (cm.hasLore() && cm.getLore() != null) {
                                    List<String> newl = new ArrayList<>();
                                    for (String L : cm.getLore()) {
                                        if (L == null) continue;
                                        if (L.contains("在庫:") || L.contains("価格:") || L.contains("説明:")) continue;
                                        String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                        if (cleaned.isEmpty()) continue;
                                        newl.add(cleaned);
                                    }
                                    cm.setLore(newl.isEmpty() ? null : newl);
                                }
                                if (cm.hasDisplayName() && cm.getDisplayName() != null) {
                                    String name = cm.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                                    if (name.isEmpty()) cm.setDisplayName(null); else cm.setDisplayName(name);
                                }
                            } catch (Exception ignored) {}
                        }
                        merged.setItemMeta(cm);
                    }
                    merged.setAmount(merged.getAmount() + amountToGive);
                    p.setItemOnCursor(merged);
                    return;
                }
            } catch (Exception ignored) {}

                // search inventory for similar item and merge/overwrite
            for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                if (it == null) continue;
                if (!similar.test(it)) continue;
                // overwrite meta (displayName/lore) then add amount
                    ItemMeta pm = prototype.getItemMeta();
                    ItemMeta im = it.getItemMeta();
                    if (im != null) {
                        if (pm != null) {
                            try { im.setDisplayName(pm.getDisplayName()); } catch (Exception ignored) {}
                            try { im.setLore(pm.getLore()); } catch (Exception ignored) {}
                        } else {
                            // clean existing meta
                            try {
                                if (im.hasLore() && im.getLore() != null) {
                                    List<String> newl = new ArrayList<>();
                                    for (String L : im.getLore()) {
                                        if (L == null) continue;
                                        if (L.contains("在庫:") || L.contains("価格:") || L.contains("説明:")) continue;
                                        String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                                        if (cleaned.isEmpty()) continue;
                                        newl.add(cleaned);
                                    }
                                    im.setLore(newl.isEmpty() ? null : newl);
                                }
                                if (im.hasDisplayName() && im.getDisplayName() != null) {
                                    String name = im.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                                    if (name.isEmpty()) im.setDisplayName(null); else im.setDisplayName(name);
                                }
                            } catch (Exception ignored) {}
                        }
                        it.setItemMeta(im);
                    }
                it.setAmount(it.getAmount() + amountToGive);
                inv.setItem(i, it);
                return;
            }

            // no matching existing stack found, add and drop leftovers
            Map<Integer, ItemStack> leftover = inv.addItem(prototype);
            if (!leftover.isEmpty()) {
                for (ItemStack r : leftover.values()) if (r != null) p.getWorld().dropItemNaturally(p.getLocation(), r);
            }
        } catch (Exception ignored) {}
    }

    // Protect shop containers from pistons moving them
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        if (e.getBlocks() == null || e.getBlocks().isEmpty()) return;
        for (Block b : e.getBlocks()) {
            if (b == null) continue;
            Location loc = b.getLocation();
            if (store.get(loc).isPresent()) {
                e.setCancelled(true);
                return;
            }
            try {
                if (b.getState() instanceof Sign) {
                    List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
                    for (BlockFace f : faces) {
                        Block nb = b.getRelative(f);
                        if (nb == null) continue;
                        if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                        if (store.get(nb.getLocation()).isPresent()) { e.setCancelled(true); return; }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        Block b = e.getBlock();
        if (b == null) return;
        // retract may move the attached block if sticky; check affected block
        for (Block moved : e.getBlocks()) {
            if (moved == null) continue;
            if (store.get(moved.getLocation()).isPresent()) {
                e.setCancelled(true);
                return;
            }
            try {
                    if (moved.getState() instanceof Sign) {
                    List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
                    for (BlockFace f : faces) {
                        Block nb = moved.getRelative(f);
                        if (nb == null) continue;
                        if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                        if (store.get(nb.getLocation()).isPresent()) { e.setCancelled(true); return; }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // Protect shop containers from explosions by removing them from block lists
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        if (e.blockList() == null || e.blockList().isEmpty()) return;
        e.blockList().removeIf(b -> {
            if (b == null) return false;
            if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST || b.getType() == Material.BARREL) {
                return store.get(b.getLocation()).isPresent();
            }
            try {
                if (b.getState() instanceof Sign) {
                    // if this sign is adjacent to a shop chest, prevent it from being exploded
                    List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
                    for (BlockFace f : faces) {
                        Block nb = b.getRelative(f);
                        if (nb == null) continue;
                        if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                        if (store.get(nb.getLocation()).isPresent()) return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        if (e.blockList() == null || e.blockList().isEmpty()) return;
        e.blockList().removeIf(b -> {
            if (b == null) return false;
            if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST || b.getType() == Material.BARREL) {
                return store.get(b.getLocation()).isPresent();
            }
            try {
                if (b.getState() instanceof Sign) {
                    List<BlockFace> faces = Arrays.asList(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP);
                    for (BlockFace f : faces) {
                        Block nb = b.getRelative(f);
                        if (nb == null) continue;
                        if (!(nb.getType() == Material.CHEST || nb.getType() == Material.TRAPPED_CHEST || nb.getType() == Material.BARREL)) continue;
                        if (store.get(nb.getLocation()).isPresent()) return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        });
    }

    // Disable hopper / inventory automated moves interacting with shop containers
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        try {
            Inventory src = e.getSource();
            Inventory dst = e.getDestination();
            if (src != null && src.getHolder() instanceof BlockState) {
                BlockState st = (BlockState) src.getHolder();
                if (st != null && store.get(st.getLocation()).isPresent()) { e.setCancelled(true); return; }
            }
            if (dst != null && dst.getHolder() instanceof BlockState) {
                BlockState st = (BlockState) dst.getHolder();
                if (st != null && store.get(st.getLocation()).isPresent()) { e.setCancelled(true); return; }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        try {
            Inventory inv = e.getInventory();
            if (inv != null && inv.getHolder() instanceof BlockState) {
                BlockState st = (BlockState) inv.getHolder();
                if (st != null && store.get(st.getLocation()).isPresent()) { e.setCancelled(true); return; }
            }
        } catch (Exception ignored) {}
    }

    // Count items in player's inventory and cursor that match the prototype (ignoring amount)
    private int countSimilarInPlayer(Player p, ItemStack prototype) {
        if (p == null || prototype == null) return 0;
        int total = 0;
        try {
            java.util.function.Predicate<ItemStack> similar = (it) -> {
                if (it == null) return false;
                if (it.getType() != prototype.getType()) return false;
                ItemStack a = it.clone(); a.setAmount(1);
                ItemStack b = prototype.clone(); b.setAmount(1);
                if (a.isSimilar(b)) return true;
                try {
                    String an = null, bn = null;
                    if (a.hasItemMeta() && a.getItemMeta().hasDisplayName()) an = a.getItemMeta().getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                    if (b.hasItemMeta() && b.getItemMeta().hasDisplayName()) bn = b.getItemMeta().getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                    if (an != null && bn != null && an.equals(bn)) return true;
                } catch (Exception ignored) {}
                return false;
            };

            // cursor
            ItemStack cursor = p.getItemOnCursor();
            if (cursor != null && similar.test(cursor)) total += cursor.getAmount();

            // inventory
            PlayerInventory inv = p.getInventory();
            for (ItemStack it : inv.getContents()) {
                if (it == null) continue;
                if (similar.test(it)) total += it.getAmount();
            }
        } catch (Exception ignored) {}
        return total;
    }

    // sanitize returned/returned-prototype items: strip inline JSON blocks from display name and lore,
    // remove any editor-only lore lines (在庫:, 価格:, 説明:) and return a cloned sanitized ItemStack.
    private ItemStack sanitizeReturnedItem(ItemStack src) {
        if (src == null) return null;
        try {
            ItemStack copy = src.clone();
            ItemMeta im = copy.getItemMeta();
            if (im == null) return copy;
            // sanitize display name
            try {
                if (im.hasDisplayName() && im.getDisplayName() != null) {
                    String cleaned = im.getDisplayName().replaceAll("\\{[^}]*\\}", "").trim();
                    im.setDisplayName(cleaned.isEmpty() ? null : cleaned);
                }
            } catch (Exception ignored) {}
            // sanitize lore lines
            try {
                if (im.hasLore() && im.getLore() != null) {
                    List<String> newLore = new ArrayList<>();
                    for (String L : im.getLore()) {
                        if (L == null) continue;
                        // drop any line that looks like editor-only meta
                        if (L.contains("在庫:") || L.contains("価格:") || L.contains("説明:")) continue;
                        String cleaned = L.replaceAll("\\{[^}]*\\}", "").trim();
                        if (cleaned.isEmpty()) continue;
                        newLore.add(cleaned);
                    }
                    im.setLore(newLore.isEmpty() ? null : newLore);
                }
            } catch (Exception ignored) {}
            copy.setItemMeta(im);
            return copy;
        } catch (Exception ignored) { return src; }
    }

    private int countItems(PlayerInventory inv, Material mat) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == mat) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(PlayerInventory inv, Material mat, int amount) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == mat) {
                int remove = Math.min(amount, item.getAmount());
                item.setAmount(item.getAmount() - remove);
                amount -= remove;
                if (item.getAmount() <= 0) {
                    inv.setItem(i, null);
                }
                if (amount <= 0) break;
            }
        }
    }

            // Check if a supplied ItemStack matches the listing exactly (respecting saved NBT / enchants when available)
            private boolean listingMatchesItem(ItemStack supply, ShopListing sl) {
                if (supply == null || sl == null) return false;
                // if listing has serialized item data, use that as prototype and compare via isSimilar (ignores amount)
                if (sl.getItemData() != null) {
                    try {
                        ItemStack proto = ItemStack.deserialize(sl.getItemData());
                        if (proto == null) return false;
                        proto.setAmount(1);
                        ItemStack cmp = supply.clone(); cmp.setAmount(1);
                        // exact match (including name, enchantments, NBT)
                        if (cmp.isSimilar(proto)) return true;
                        // fallback: if types match, allow matching by enchantments subset
                        if (cmp.getType() == proto.getType()) {
                            try {
                                ItemMeta pm = proto.getItemMeta();
                                ItemMeta cm = cmp.getItemMeta();
                                if (pm != null && pm.hasEnchants()) {
                                    if (cm == null || !cm.hasEnchants()) return false;
                                    Map<Enchantment,Integer> req = pm.getEnchants();
                                    Map<Enchantment,Integer> have = cm.getEnchants();
                                    for (Map.Entry<Enchantment,Integer> en : req.entrySet()) {
                                        Integer hv = have.get(en.getKey());
                                        if (hv == null || hv < en.getValue()) return false;
                                    }
                                    return true;
                                }
                                // if proto didn't require enchants, type match is sufficient
                                return true;
                            } catch (Exception ignored) { return false; }
                        }
                        return false;
                    } catch (Exception ignored) {}
                }
                // fallback to type-only compare
                if (sl.getMaterial() != null) return supply.getType().name().equals(sl.getMaterial());
                return false;
            }

    

}
