package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import java.util.*;

/**
 * TPAリクエストのストレージ
 * メモリ内でリクエストを管理（永続化は不要、リスタートで消える）
 */
public class TpaRequestStore {

    // メモリ内でリクエストを管理 (targetUuid -> List<TpaRequest>)
    private final Map<String, List<TpaRequest>> pendingRequests = new HashMap<>();

    public TpaRequestStore(ConfigManager configManager) {
    }

    /**
     * リクエストを追加
     */
    public void addRequest(TpaRequest request) {
        String targetUuid = request.getTargetUuid();
        pendingRequests.computeIfAbsent(targetUuid, k -> new ArrayList<>());
        
        // 同じ送信者からの既存リクエストを削除
        pendingRequests.get(targetUuid).removeIf(r -> r.getSenderUuid().equals(request.getSenderUuid()));
        
        // 新しいリクエストを追加
        pendingRequests.get(targetUuid).add(request);
    }

    /**
     * 対象プレイヤーへのリクエスト一覧を取得
     */
    public List<TpaRequest> getRequestsFor(String targetUuid) {
        cleanupExpired();
        List<TpaRequest> list = pendingRequests.get(targetUuid);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * 特定の送信者からのリクエストを取得
     */
    public Optional<TpaRequest> getRequest(String targetUuid, String senderUuid) {
        cleanupExpired();
        List<TpaRequest> list = pendingRequests.get(targetUuid);
        if (list == null) return Optional.empty();
        
        return list.stream()
                .filter(r -> r.getSenderUuid().equals(senderUuid))
                .findFirst();
    }

    /**
     * 名前で送信者を検索してリクエストを取得
     */
    public Optional<TpaRequest> getRequestBySenderName(String targetUuid, String senderName) {
        cleanupExpired();
        List<TpaRequest> list = pendingRequests.get(targetUuid);
        if (list == null) return Optional.empty();
        
        return list.stream()
                .filter(r -> r.getSenderName().equalsIgnoreCase(senderName))
                .findFirst();
    }

    /**
     * リクエストを削除
     */
    public void removeRequest(String targetUuid, String senderUuid) {
        List<TpaRequest> list = pendingRequests.get(targetUuid);
        if (list != null) {
            list.removeIf(r -> r.getSenderUuid().equals(senderUuid));
            if (list.isEmpty()) {
                pendingRequests.remove(targetUuid);
            }
        }
    }

    /**
     * プレイヤーのすべてのリクエストを削除（送信したもの・受信したもの両方）
     */
    public void removeAllFor(String playerUuid) {
        // 受信したリクエストを削除
        pendingRequests.remove(playerUuid);
        
        // 送信したリクエストを削除
        for (List<TpaRequest> list : pendingRequests.values()) {
            list.removeIf(r -> r.getSenderUuid().equals(playerUuid));
        }
    }

    /**
     * プレイヤーが送信したリクエスト一覧を取得
     */
    public List<TpaRequest> getSentRequestsBy(String senderUuid) {
        cleanupExpired();
        List<TpaRequest> result = new ArrayList<>();
        for (List<TpaRequest> list : pendingRequests.values()) {
            for (TpaRequest r : list) {
                if (r.getSenderUuid().equals(senderUuid)) {
                    result.add(r);
                }
            }
        }
        return result;
    }

    /**
     * 既に送信済みか確認
     */
    public boolean hasPendingRequest(String senderUuid, String targetUuid) {
        cleanupExpired();
        return getRequest(targetUuid, senderUuid).isPresent();
    }

    /**
     * 期限切れリクエストを削除
     */
    public void cleanupExpired() {
        for (Iterator<Map.Entry<String, List<TpaRequest>>> it = pendingRequests.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, List<TpaRequest>> entry = it.next();
            entry.getValue().removeIf(TpaRequest::isExpired);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * すべてのリクエストをクリア
     */
    public void clearAll() {
        pendingRequests.clear();
    }
}
