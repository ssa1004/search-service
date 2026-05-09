package com.example.search.adapter.out.elasticsearch;

/**
 * Levenshtein 거리 계산 — fuzzy match 결과의 실제 편집 거리를 RelatedSuggestion 에 채워 정렬에
 * 활용한다.
 *
 * <p>공통 구현 (Apache Commons Text 와 같음). 외부 의존 없이 짧으니 직접 구현.</p>
 */
public final class LevenshteinDistance {

    private LevenshteinDistance() {
    }

    public static int compute(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
