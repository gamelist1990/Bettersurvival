package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.settings;

import org.bukkit.Material;

/**
 * ツール設定メニューに並ぶ 1 項目の定義。
 *
 * エンチャントの「効果を出すか出さないか」といった ON/OFF を、
 * プレイヤーごとに切り替えられるようにするための宣言。
 *
 * @param id              内部ID (エンチャント側の判定キーと一致させる)
 * @param displayName     メニューに表示する名前
 * @param icon            メニューのアイコン
 * @param description     説明 (\n 区切り可)
 * @param defaultEnabled  既定で ON かどうか
 */
public record ToolSetting(String id, String displayName, Material icon, String description, boolean defaultEnabled) {
}
