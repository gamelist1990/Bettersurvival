package org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.io.File;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * オフラインプレイヤーのデータを読み書きするユーティリティ
 * 
 * Paper/Spigotのplayerdataフォルダからプレイヤーのdatファイルを直接読み書きする
 * NBT形式でインベントリ・装備・エンダーチェストを操作
 */
public class InvseeOfflineData {

    /**
     * オフラインプレイヤーのplayerdata ファイルを取得
     */
    private static File getPlayerDataFile(OfflinePlayer player) {
        // サーバーのワールドフォルダからplayerdataを探す
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        File playerDataFolder = new File(worldFolder, "playerdata");
        return new File(playerDataFolder, player.getUniqueId().toString() + ".dat");
    }

    /**
     * ListTagから指定インデックスのCompoundTagを取得
     */
    private static CompoundTag getCompoundFromList(ListTag list, int index) {
        if (index < 0 || index >= list.size()) return null;
        Tag tag = list.get(index);
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        }
        return null;
    }

    /**
     * NBTからプレイヤーのインベントリ内容を取得
     */
    public static ItemStack[] getInventoryContents(OfflinePlayer player) {
        ItemStack[] contents = new ItemStack[36];
        
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) {
                return contents;
            }

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag inventoryTag = nbt.getListOrEmpty("Inventory");

            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(inventoryTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                
                // スロット0-35がメインインベントリ
                if (slot >= 0 && slot < 36) {
                    ItemStack item = nbtToItemStack(itemTag);
                    if (item != null) {
                        contents[slot] = item;
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーのインベントリ読み込みに失敗: " + e.getMessage());
        }

        return contents;
    }

    /**
     * NBTからプレイヤーの装備内容を取得
     */
    public static ItemStack[] getArmorContents(OfflinePlayer player) {
        ItemStack[] armor = new ItemStack[4];
        
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) {
                return armor;
            }

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag inventoryTag = nbt.getListOrEmpty("Inventory");

            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(inventoryTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                
                // スロット100-103が装備スロット（ブーツ、レギンス、チェストプレート、ヘルメット）
                if (slot >= 100 && slot <= 103) {
                    ItemStack item = nbtToItemStack(itemTag);
                    if (item != null) {
                        armor[slot - 100] = item;
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーの装備読み込みに失敗: " + e.getMessage());
        }

        return armor;
    }

    /**
     * NBTからプレイヤーのオフハンドアイテムを取得
     */
    public static ItemStack getOffhandItem(OfflinePlayer player) {
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) {
                return null;
            }

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag inventoryTag = nbt.getListOrEmpty("Inventory");

            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(inventoryTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                
                // スロット-106がオフハンド
                if (slot == 150) { // -106 as unsigned byte = 150
                    return nbtToItemStack(itemTag);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーのオフハンド読み込みに失敗: " + e.getMessage());
        }

        return null;
    }

    /**
     * NBTからプレイヤーのエンダーチェスト内容を取得
     */
    public static ItemStack[] getEnderchestContents(OfflinePlayer player) {
        ItemStack[] contents = new ItemStack[27];
        
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) {
                return contents;
            }

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag enderItemsTag = nbt.getListOrEmpty("EnderItems");

            for (int i = 0; i < enderItemsTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(enderItemsTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                
                if (slot >= 0 && slot < 27) {
                    ItemStack item = nbtToItemStack(itemTag);
                    if (item != null) {
                        contents[slot] = item;
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーのエンダーチェスト読み込みに失敗: " + e.getMessage());
        }

        return contents;
    }

    /**
     * プレイヤーのインベントリ内容を保存
     */
    public static void setInventoryContents(OfflinePlayer player, ItemStack[] contents) {
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) {
                Bukkit.getLogger().warning("[InvSee] プレイヤーデータファイルが存在しません");
                return;
            }

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag inventoryTag = nbt.getListOrEmpty("Inventory");
            ListTag newInventoryTag = new ListTag();

            // 既存の非インベントリスロットを保持
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(inventoryTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                if (slot >= 36) { // 装備やオフハンドは保持
                    newInventoryTag.add(itemTag);
                }
            }

            // 新しいインベントリ内容を追加
            for (int i = 0; i < Math.min(contents.length, 36); i++) {
                if (contents[i] != null && contents[i].getType() != org.bukkit.Material.AIR) {
                    CompoundTag itemTag = itemStackToNbt(contents[i]);
                    if (itemTag != null) {
                        itemTag.putByte("Slot", (byte) i);
                        newInventoryTag.add(itemTag);
                    }
                }
            }

            nbt.put("Inventory", newInventoryTag);
            NbtIo.writeCompressed(nbt, dataFile.toPath());

        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーのインベントリ保存に失敗: " + e.getMessage());
        }
    }

    /**
     * プレイヤーの装備内容を保存
     */
    public static void setArmorContents(OfflinePlayer player, ItemStack[] armor) {
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) return;

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag inventoryTag = nbt.getListOrEmpty("Inventory");
            ListTag newInventoryTag = new ListTag();

            // 既存の非装備スロットを保持
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(inventoryTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                if (slot < 100 || slot > 103) {
                    newInventoryTag.add(itemTag);
                }
            }

            // 新しい装備内容を追加
            for (int i = 0; i < Math.min(armor.length, 4); i++) {
                if (armor[i] != null && armor[i].getType() != org.bukkit.Material.AIR) {
                    CompoundTag itemTag = itemStackToNbt(armor[i]);
                    if (itemTag != null) {
                        itemTag.putByte("Slot", (byte) (100 + i));
                        newInventoryTag.add(itemTag);
                    }
                }
            }

            nbt.put("Inventory", newInventoryTag);
            NbtIo.writeCompressed(nbt, dataFile.toPath());

        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーの装備保存に失敗: " + e.getMessage());
        }
    }

    /**
     * プレイヤーのオフハンドを保存
     */
    public static void setOffhandItem(OfflinePlayer player, ItemStack item) {
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) return;

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag inventoryTag = nbt.getListOrEmpty("Inventory");
            ListTag newInventoryTag = new ListTag();

            // 既存のオフハンド以外を保持
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = getCompoundFromList(inventoryTag, i);
                if (itemTag == null) continue;
                
                int slot = itemTag.getByteOr("Slot", (byte) 0) & 0xFF;
                if (slot != 150) { // -106 as unsigned byte
                    newInventoryTag.add(itemTag);
                }
            }

            // オフハンドを追加
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                CompoundTag itemTag = itemStackToNbt(item);
                if (itemTag != null) {
                    itemTag.putByte("Slot", (byte) -106);
                    newInventoryTag.add(itemTag);
                }
            }

            nbt.put("Inventory", newInventoryTag);
            NbtIo.writeCompressed(nbt, dataFile.toPath());

        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーのオフハンド保存に失敗: " + e.getMessage());
        }
    }

    /**
     * プレイヤーのエンダーチェスト内容を保存
     */
    public static void setEnderchestContents(OfflinePlayer player, ItemStack[] contents) {
        try {
            File dataFile = getPlayerDataFile(player);
            if (!dataFile.exists()) return;

            CompoundTag nbt = NbtIo.readCompressed(dataFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag newEnderItemsTag = new ListTag();

            for (int i = 0; i < Math.min(contents.length, 27); i++) {
                if (contents[i] != null && contents[i].getType() != org.bukkit.Material.AIR) {
                    CompoundTag itemTag = itemStackToNbt(contents[i]);
                    if (itemTag != null) {
                        itemTag.putByte("Slot", (byte) i);
                        newEnderItemsTag.add(itemTag);
                    }
                }
            }

            nbt.put("EnderItems", newEnderItemsTag);
            NbtIo.writeCompressed(nbt, dataFile.toPath());

        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] オフラインプレイヤーのエンダーチェスト保存に失敗: " + e.getMessage());
        }
    }

    // ========== NBT変換ヘルパー ==========

    /**
     * NBTタグからItemStackに変換
     * Paper 1.21.x対応 - CraftItemStackの内部メソッドを使用
     */
    private static ItemStack nbtToItemStack(CompoundTag tag) {
        try {
            // Paper/CraftBukkitのリフレクションを使わずにNMSのItemStackを作成
            net.minecraft.core.RegistryAccess registryAccess = net.minecraft.server.MinecraftServer.getServer().registryAccess();
            
            // 1.21.xではItemStack.of(registryAccess, tag)を使用
            java.lang.reflect.Method parseMethod = null;
            
            // 複数のメソッド名を試行
            for (String methodName : new String[]{"parseOptional", "of", "parse"}) {
                try {
                    parseMethod = net.minecraft.world.item.ItemStack.class.getDeclaredMethod(
                        methodName, 
                        net.minecraft.core.HolderLookup.Provider.class, 
                        net.minecraft.nbt.CompoundTag.class
                    );
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            
            if (parseMethod == null) {
                // フォールバック: 直接のコンストラクタアプローチ
                return fallbackNbtToItemStack(tag);
            }
            
            parseMethod.setAccessible(true);
            Object result = parseMethod.invoke(null, registryAccess, tag);
            
            net.minecraft.world.item.ItemStack nmsStack;
            if (result instanceof java.util.Optional) {
                nmsStack = ((java.util.Optional<net.minecraft.world.item.ItemStack>) result)
                    .orElse(net.minecraft.world.item.ItemStack.EMPTY);
            } else {
                nmsStack = (net.minecraft.world.item.ItemStack) result;
            }
            
            if (nmsStack == null || nmsStack.isEmpty()) {
                return null;
            }
            
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nmsStack);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] NBTからItemStack変換失敗: " + e.getMessage());
            return fallbackNbtToItemStack(tag);
        }
    }

    /**
     * フォールバック: 基本的なアイテム情報のみを復元
     */
    private static ItemStack fallbackNbtToItemStack(CompoundTag tag) {
        try {
            // ID文字列を取得
            String id = tag.getStringOr("id", "minecraft:air");
            int count = tag.getByteOr("Count", (byte) 1);
            
            // マテリアルを取得
            String materialName = id.replace("minecraft:", "").toUpperCase();
            org.bukkit.Material material;
            try {
                material = org.bukkit.Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                return null;
            }
            
            if (material == org.bukkit.Material.AIR) {
                return null;
            }
            
            return new ItemStack(material, count);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ItemStackをNBTタグに変換
     * Paper 1.21.x対応
     */
    private static CompoundTag itemStackToNbt(ItemStack item) {
        try {
            net.minecraft.world.item.ItemStack nmsStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
            net.minecraft.core.RegistryAccess registryAccess = net.minecraft.server.MinecraftServer.getServer().registryAccess();
            
            // 1.21.xではsave(RegistryAccess)またはsaveOptional(RegistryAccess)を使用
            java.lang.reflect.Method saveMethod = null;
            
            for (String methodName : new String[]{"save", "saveOptional", "serializeAsTag"}) {
                try {
                    saveMethod = net.minecraft.world.item.ItemStack.class.getDeclaredMethod(
                        methodName,
                        net.minecraft.core.HolderLookup.Provider.class
                    );
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            
            if (saveMethod == null) {
                // 2つ目の引数を受け付けるsaveを試行
                for (String methodName : new String[]{"save", "saveOptional"}) {
                    try {
                        saveMethod = net.minecraft.world.item.ItemStack.class.getDeclaredMethod(
                            methodName,
                            net.minecraft.core.HolderLookup.Provider.class,
                            net.minecraft.nbt.CompoundTag.class
                        );
                        CompoundTag resultTag = new CompoundTag();
                        saveMethod.setAccessible(true);
                        Object result = saveMethod.invoke(nmsStack, registryAccess, resultTag);
                        if (result instanceof CompoundTag) {
                            return (CompoundTag) result;
                        }
                        return resultTag;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            
            if (saveMethod != null) {
                saveMethod.setAccessible(true);
                Object result = saveMethod.invoke(nmsStack, registryAccess);
                if (result instanceof net.minecraft.nbt.Tag) {
                    if (result instanceof CompoundTag) {
                        return (CompoundTag) result;
                    }
                }
            }
            
            // フォールバック
            return fallbackItemStackToNbt(item);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InvSee] ItemStackからNBT変換失敗: " + e.getMessage());
            return fallbackItemStackToNbt(item);
        }
    }

    /**
     * フォールバック: 基本的なアイテム情報のみを保存
     */
    private static CompoundTag fallbackItemStackToNbt(ItemStack item) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:" + item.getType().name().toLowerCase());
            tag.putByte("Count", (byte) item.getAmount());
            return tag;
        } catch (Exception e) {
            return null;
        }
    }
}
