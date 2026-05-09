package com.example.search.domain.synonym;

/**
 * 동의어 방향.
 *
 * <p>ES synonym graph token filter 의 두 가지 표현:</p>
 * <ul>
 *   <li>{@link #BIDIRECTIONAL} — {@code "조던1, 에어조던1, Air Jordan 1"} — 어느 쪽으로 검색하든
 *       모두 매칭.</li>
 *   <li>{@link #ONE_WAY} — {@code "조던1 => 에어 조던 1"} — "조던1" 검색 시 "에어 조던 1" 로 확장
 *       하지만 역방향은 X. (오타 → 정식 표기 매핑 등에 적합)</li>
 * </ul>
 */
public enum SynonymDirection {
    BIDIRECTIONAL,
    ONE_WAY
}
