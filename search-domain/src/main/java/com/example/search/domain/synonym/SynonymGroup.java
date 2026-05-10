package com.example.search.domain.synonym;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 운영자가 등록 / 관리하는 동의어 그룹.
 *
 * <p>예:</p>
 * <ul>
 *   <li>{@code BIDIRECTIONAL} — terms = ["조던1", "에어조던1", "Air Jordan 1"]
 *       → 어느 표기로 검색해도 셋 다 매칭.</li>
 *   <li>{@code ONE_WAY} — terms = ["조던1", "에어 조던 1"]
 *       → 첫 토큰("조던1") 검색 시 두 번째 이상으로 확장. 역방향은 X.</li>
 * </ul>
 *
 * <p>변경 사항은 ES {@code _settings} reload 또는 새 인덱스 생성 시 반영 — 본 도메인은 "어떤 그룹이
 * 등록되어 있는가" 만 책임지고, ES 적용은 outbound port 가 처리.</p>
 *
 * <p>한 그룹의 terms 가 너무 많으면 (50+) ES 의 synonym graph 분석 비용이 비싸진다 — 운영 가독성
 * 측면에서도 그룹을 쪼개는 게 낫다. 도메인은 hard cap 만 두고 운영자 판단에 맡긴다.</p>
 */
public record SynonymGroup(
        SynonymGroupId id,
        List<String> terms,
        SynonymDirection direction,
        Instant updatedAt,
        String updatedBy
) {

    /** 한 그룹에 묶을 수 있는 최대 term 수 — ES 분석 비용 / 운영 가독성 가이드. */
    public static final int MAX_TERMS = 50;

    /** term 길이 — 너무 긴 표현은 색인 토큰화 자체에 무리. */
    public static final int MAX_TERM_LENGTH = 100;

    /**
     * ES synonym 표현은 쉼표 (BIDIRECTIONAL) 와 화살표 (ONE_WAY) 가 구분자. term 안에 그 문자가
     * 들어가면 표현 자체가 깨진다 — 등록 단계에서 막아야 함.
     */
    private static final Pattern FORBIDDEN = Pattern.compile("[,\\\\]|=>");

    public SynonymGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");

        if (updatedBy.isBlank()) {
            throw new IllegalArgumentException("updatedBy 빈 값 불가");
        }

        // 외부에서 mutation 못 하도록 방어 복사 + immutable wrap.
        terms = List.copyOf(terms);

        if (terms.size() < 2) {
            throw new IllegalArgumentException("synonym 그룹은 최소 2개 term 필요: " + terms.size());
        }
        if (terms.size() > MAX_TERMS) {
            throw new IllegalArgumentException(
                    "synonym 그룹 term 수가 " + MAX_TERMS + " 초과: " + terms.size());
        }
        if (direction == SynonymDirection.ONE_WAY && terms.size() < 2) {
            throw new IllegalArgumentException("ONE_WAY 는 LHS + RHS 최소 1개씩 필요");
        }

        for (String term : terms) {
            validateTerm(term);
        }
        rejectDuplicates(terms);
    }

    /**
     * 한 그룹에 같은 term 이 두 번 이상 들어오면 거부. {@code ko_standard} analyzer 가 lowercase
     * 필터를 거치므로 대소문자만 다른 표기 (Air Jordan 1 / air jordan 1) 도 동일 토큰이 되어 중복.
     * 운영자 입력 실수 (붙여넣기 / typo) 단계에서 잡는다.
     */
    private static void rejectDuplicates(List<String> terms) {
        Set<String> seen = new HashSet<>();
        for (String term : terms) {
            String key = term.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "synonym 그룹에 중복 term (대소문자 무시): " + term);
            }
        }
    }

    private static void validateTerm(String term) {
        Objects.requireNonNull(term, "term");
        if (term.isBlank()) {
            throw new IllegalArgumentException("term 빈 값 불가");
        }
        if (term.length() > MAX_TERM_LENGTH) {
            throw new IllegalArgumentException(
                    "term 길이 " + MAX_TERM_LENGTH + " 이하: " + term);
        }
        if (FORBIDDEN.matcher(term).find()) {
            throw new IllegalArgumentException(
                    "term 안에 ES synonym 구분자 (',', '\\', '=>') 사용 불가: " + term);
        }
    }

    public static SynonymGroup create(List<String> terms, SynonymDirection direction,
                                       Instant now, String updatedBy) {
        return new SynonymGroup(SynonymGroupId.newId(), terms, direction, now, updatedBy);
    }

    /**
     * ES synonym graph token filter 가 받는 라인 한 줄 표현.
     *
     * <ul>
     *   <li>BIDIRECTIONAL — {@code "term1, term2, term3"}</li>
     *   <li>ONE_WAY — {@code "term1 => term2, term3"} (LHS = 첫 1개, RHS = 나머지)</li>
     * </ul>
     *
     * <p>ES 가 line 단위 파싱하므로 line break 가 들어가서는 안 됨 — 도메인이 line break 를 term 에
     * 못 넣게 막진 않지만, 색인 시점에 그런 term 은 토큰화 자체로 의미가 없으므로 사실상 운영자
     * 실수다. ES 측에서 무시되거나 split 된다.</p>
     */
    public String toElasticsearchRule() {
        return switch (direction) {
            case BIDIRECTIONAL -> String.join(", ", terms);
            case ONE_WAY -> terms.get(0) + " => " + String.join(", ", terms.subList(1, terms.size()));
        };
    }
}
