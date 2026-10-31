package org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * ネームタグエンティティ情報を保持するクラス
 */
public class NametagEntity {
    private final List<Integer> entityIds;
    private List<Component> lines;

    /**
     * コンストラクタ
     * @param entityIds エンティティIDのリスト（下から上の順序）
     * @param lines 表示する行のリスト（下から上の順序）
     */
    public NametagEntity(List<Integer> entityIds, List<Component> lines) {
        this.entityIds = entityIds;
        this.lines = lines;
    }

    /**
     * エンティティIDのリストを取得
     * @return エンティティIDのリスト
     */
    public List<Integer> getEntityIds() {
        return entityIds;
    }

    /**
     * 表示する行のリストを取得
     * @return 行のリスト
     */
    public List<Component> getLines() {
        return lines;
    }

    /**
     * 表示する行のリストを設定
     * @param lines 行のリスト
     */
    public void setLines(List<Component> lines) {
        this.lines = lines;
    }
}