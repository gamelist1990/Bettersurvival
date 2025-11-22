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
                    for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
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
                            org.bukkit.inventory.Inventory invEditor = view.getTopInventory();
                            if (invEditor == null) continue;
                            // compute snapshot hash to detect changes and avoid saving every tick
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < 26; i++) {
                                org.bukkit.inventory.ItemStack it = invEditor.getItem(i);
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
                                org.bukkit.inventory.ItemStack it = invEditor.getItem(i);
                                if (it == null) continue;
                                String rawDisplay = null;
                                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) rawDisplay = it.getItemMeta().getDisplayName();
                                if (rawDisplay == null) rawDisplay = it.getType().name();
                                ShopListing prev = old.get(i);
                                int prevStock = prev != null ? prev.getStock() : 0;
                                ShopListing sl = parseRawToListing(it, rawDisplay, prevStock);
                                if (prev != null) sl.setStock(prev.getStock());
                                updated.put(i, sl);
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
                                                                org.bukkit.World w = Bukkit.getWorld(parts[0]);
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
                                        org.bukkit.inventory.Inventory invOwner = view.getTopInventory();
                                        if (invOwner == null) continue;
                                        // only watch currency slot (12) for changes
                                        org.bukkit.inventory.ItemStack cur = invOwner.getItem(12);
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
            org.bukkit.World w = Bukkit.getWorld(parts[0]);
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

    private ShopListing parseRawToListing(org.bukkit.inventory.ItemStack it, String raw, int prevStock) {
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

        String display = raw;
        ShopListing sl = new ShopListing(it.getType().name(), display, price, desc, prevStock);
        return sl;
    }

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (!toggle.getGlobal("chestshop")) return;
        org.bukkit.block.Block b = e.getBlock();
        if (b == null) return;
        if (!(b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST || b.getType() == Material.BARREL)) return;
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
        // owner or op: remove shop entries for this chest set
        for (Location l : ChestLockModule.getChestRelatedLocations(b)) {
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
                    org.bukkit.block.BlockState st = b.getState();
                    if (st instanceof org.bukkit.block.Container) {
                        org.bukkit.inventory.Inventory inv = ((org.bukkit.block.Container) st).getSnapshotInventory();
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
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
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
        org.bukkit.block.BlockState st = chestBlock.get().getState();
        if (st instanceof org.bukkit.block.Container) {
            org.bukkit.inventory.Inventory inv = ((org.bukkit.block.Container) st).getSnapshotInventory();
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
            org.bukkit.inventory.Inventory inv = e.getInventory();
            Map<Integer, ShopListing> old = store.getListings(loc);
            Map<Integer, ShopListing> updated = new LinkedHashMap<>();
            for (int i = 0; i < 26; i++) {
                org.bukkit.inventory.ItemStack it = inv.getItem(i);
                if (it == null) continue;
                String raw = null;
                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) raw = it.getItemMeta().getDisplayName();
                if (raw == null) raw = it.getType().name();
                String rawDisplay = null;
                if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) rawDisplay = it.getItemMeta().getDisplayName();
                if (rawDisplay == null) rawDisplay = it.getType().name();
                ShopListing prev = old.get(i);
                int prevStock = prev != null ? prev.getStock() : 0;
                ShopListing sl = parseRawToListing(it, rawDisplay, prevStock);
                updated.put(i, sl);
            }
            store.saveListings(loc, updated);
            p.sendMessage("§a出品データを保存しました (" + updated.size() + " 件)");
            // return to owner main UI - schedule next tick to avoid re-entrant inventory events
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("Bettersurvival");
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
            org.bukkit.inventory.Inventory inv = e.getInventory();
            org.bukkit.inventory.ItemStack supply = inv.getItem(10);
                if (supply != null) {
                Map<Integer, ShopListing> listings = store.getListings(loc);
                boolean applied = false;
                for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                    ShopListing sl = en.getValue();
                    if (sl.getMaterial() != null && sl.getMaterial().equals(supply.getType().name())) {
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
                    p.sendMessage("§cこの仕入れアイテムに対応する出品が見つかりませんでした");
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
                org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Bettersurvival");
                if (plugin == null) return;
                // schedule one tick later so the inventory reflects the player's placement
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ResolveResult rrInner = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rrInner == null) return;
                    Location locInner = rrInner.loc;
                    Map<Integer, ShopListing> listings = store.getListings(locInner);
                    org.bukkit.inventory.Inventory invnow = e.getInventory();
                    if (invnow == null) return;
                    org.bukkit.inventory.ItemStack supplyNow = invnow.getItem(10);
                    if (supplyNow == null) return;
                    boolean applied = false;
                    for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                        ShopListing sl = en.getValue();
                            if (sl.getMaterial() != null && sl.getMaterial().equals(supplyNow.getType().name())) {
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
                    org.bukkit.inventory.Inventory top = viewNow.getTopInventory();
                    if (top == null) return;
                    org.bukkit.inventory.ItemStack existingSupply = top.getItem(10);
                    String srcMat = source.getType().name();
                    boolean matchesListing = false;
                    for (ShopListing sl : listings.values()) if (sl != null && srcMat.equals(sl.getMaterial())) { matchesListing = true; break; }
                    if ((existingSupply == null && matchesListing) || (existingSupply != null && existingSupply.getType() == source.getType())) {
                        // move whole stack into supply slot
                        top.setItem(10, source.clone());
                        // clear clicked bottom slot
                        try { ((Player)e.getWhoClicked()).getInventory().setItem(e.getSlot(), null); } catch (Exception ignored) {}
                        // process restock immediately (in next tick)
                        org.bukkit.plugin.Plugin plugin2 = Bukkit.getPluginManager().getPlugin("Bettersurvival");
                        if (plugin2 == null) return;
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin2, () -> {
                            try {
                                org.bukkit.inventory.ItemStack supNow = top.getItem(10);
                                if (supNow == null) return;
                                boolean applied2 = false;
                                Map<Integer, ShopListing> listingsNow = store.getListings(locMove);
                                for (Map.Entry<Integer, ShopListing> en : listingsNow.entrySet()) {
                                    ShopListing sl = en.getValue();
                                    if (sl.getMaterial() != null && sl.getMaterial().equals(supNow.getType().name())) {
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
                    org.bukkit.inventory.ItemStack cur12 = top.getItem(12);
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
                org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Bettersurvival");
                if (plugin == null) return;
                // schedule one tick later to let inventory update then process instantly
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ResolveResult rrInner = resolveByTitle(p.getOpenInventory() == null ? null : p.getOpenInventory().getTitle());
                    if (rrInner == null) return;
                    Location locInner = rrInner.loc;
                    ChestShop shopInner = rrInner.shop;
                    org.bukkit.inventory.Inventory nowInv = e.getInventory();
                    if (nowInv == null) return;
                    org.bukkit.inventory.ItemStack cur = nowInv.getItem(12);
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
                    org.bukkit.inventory.Inventory editor = org.bukkit.Bukkit.createInventory(null, 27, ChestShopUI.EDITOR_TITLE_PREFIX + shopEditor.getName());
                    Map<Integer, ShopListing> listings = store.getListings(locEditor);
                    for (int i = 0; i < 26; i++) {
                        ShopListing sl = listings.get(i);
                        if (sl == null) continue;
                        Material mat = Material.matchMaterial(sl.getMaterial());
                        if (mat == null) mat = Material.PAPER;
                        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(mat, 1);
                        org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
                        if (im != null) {
                            im.setDisplayName(sl.getDisplayName());
                            List<String> lore = new ArrayList<>();
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
                            org.bukkit.inventory.ItemStack it2 = editor.getItem(i);
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

        // Editor UI - シンプルなドラッグ&ドロップと基本クリックを許可
        if (title.startsWith(ChestShopUI.EDITOR_TITLE_PREFIX)) {
            // エディターはシンプルなクリックとドラッグ&ドロップのみ許可
            // 自動保存に任せて、複雑な操作は避ける
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
                int price = sl.getPrice();
                Material curMat = Material.matchMaterial(shop.getCurrency());
                if (curMat != null) {
                    int has = countItems(p.getInventory(), curMat);
                    if (has >= price) {
                        // remove currency
                        removeItems(p.getInventory(), curMat, price);
                        // give item
                        Material itemMat = Material.matchMaterial(sl.getMaterial());
                        if (itemMat != null) {
                            ItemStack give = new ItemStack(itemMat, 1);
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
                org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Bettersurvival");
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
                                ItemStack sanitized = new ItemStack(v.getType(), v.getAmount());
                                org.bukkit.inventory.meta.ItemMeta im = sanitized.getItemMeta();
                                if (im != null) {
                                    if (v.hasItemMeta() && v.getItemMeta().hasDisplayName()) im.setDisplayName(v.getItemMeta().getDisplayName());
                                    im.setLore(null);
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
                    org.bukkit.inventory.Inventory invnow = e.getInventory();
                    if (invnow == null) return;
                    // handle supply slot 10
                    try {
                        org.bukkit.inventory.ItemStack supplyNow = invnow.getItem(10);
                        if (supplyNow != null) {
                            boolean applied = false;
                            for (Map.Entry<Integer, ShopListing> en : listings.entrySet()) {
                                ShopListing sl = en.getValue();
                                if (sl.getMaterial() != null && sl.getMaterial().equals(supplyNow.getType().name())) {
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
                        org.bukkit.inventory.ItemStack cur = invnow.getItem(12);
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

    private int countItems(org.bukkit.inventory.PlayerInventory inv, Material mat) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == mat) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(org.bukkit.inventory.PlayerInventory inv, Material mat, int amount) {
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

}
