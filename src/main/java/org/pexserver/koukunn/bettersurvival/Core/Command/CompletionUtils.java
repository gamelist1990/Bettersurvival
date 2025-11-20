package org.pexserver.koukunn.bettersurvival.Core.Command;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tab補完のユーティリティクラス
 * 類似度判定やフィルタリング機能を提供します
 */
public class CompletionUtils {

    /**
     * リストから類似度の高い順にフィルタリングしたリストを返す
     * @param input ユーザー入力
     * @param candidates 候補リスト
     * @return 類似度でソートされた補完候補
     */
    public static List<String> filterBySimilarity(String input, List<String> candidates) {
        if (input == null || input.isEmpty()) {
            return candidates;
        }

        String inputLower = input.toLowerCase();
        
        return candidates.stream()
                .filter(c -> c.toLowerCase().startsWith(inputLower))
                .sorted((a, b) -> {
                    double scoreA = calculateSimilarity(inputLower, a.toLowerCase());
                    double scoreB = calculateSimilarity(inputLower, b.toLowerCase());
                    return Double.compare(scoreB, scoreA);  // 高い順
                })
                .collect(Collectors.toList());
    }

    /**
     * レーベンシュタイン距離を使用した類似度を計算
     * @param input ユーザー入力
     * @param candidate 候補文字列
     * @return 0.0～1.0の類似度スコア（1.0に近いほど似ている）
     */
    public static double calculateSimilarity(String input, String candidate) {
        int distance = levenshteinDistance(input, candidate);
        int maxLength = Math.max(input.length(), candidate.length());
        
        if (maxLength == 0) {
            return 1.0;
        }
        
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * レーベンシュタイン距離を計算
     * @param a 文字列1
     * @param b 文字列2
     * @return 編集距離
     */
    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]),
                        dp[i - 1][j - 1]
                    );
                }
            }
        }

        return dp[a.length()][b.length()];
    }

    /**
     * ページ式の補完を計算
     * @param input ユーザー入力
     * @param candidates 候補リスト
     * @param pageSize 1ページあたりの行数
     * @return ページと総ページ数を含むマップ
     */
    public static Map<String, Object> getPagedCompletions(String input, List<String> candidates, int pageSize) {
        List<String> filtered = filterBySimilarity(input, candidates);
        
        int totalPages = (int) Math.ceil((double) filtered.size() / pageSize);
        int currentPage = 1;
        
        List<String> pagedResults = filtered.stream()
                .limit(pageSize)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("items", pagedResults);
        result.put("currentPage", currentPage);
        result.put("totalPages", totalPages);
        result.put("totalItems", filtered.size());
        
        return result;
    }
}
