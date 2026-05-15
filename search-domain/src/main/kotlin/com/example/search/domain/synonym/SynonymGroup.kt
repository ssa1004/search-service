package com.example.search.domain.synonym

import java.time.Instant
import java.util.Locale
import java.util.regex.Pattern

/**
 * 운영자가 등록 / 관리하는 동의어 그룹.
 *
 * 예:
 * - `BIDIRECTIONAL` — terms = ["조던1", "에어조던1", "Air Jordan 1"]
 *   → 어느 표기로 검색해도 셋 다 매칭.
 * - `ONE_WAY` — terms = ["조던1", "에어 조던 1"]
 *   → 첫 토큰("조던1") 검색 시 두 번째 이상으로 확장. 역방향은 X.
 *
 * 변경 사항은 ES `_settings` reload 또는 새 인덱스 생성 시 반영 — 본 도메인은 "어떤 그룹이
 * 등록되어 있는가" 만 책임지고, ES 적용은 outbound port 가 처리.
 *
 * 한 그룹의 terms 가 너무 많으면 (50+) ES 의 synonym graph 분석 비용이 비싸진다 — 운영 가독성
 * 측면에서도 그룹을 쪼개는 게 낫다. 도메인은 hard cap 만 두고 운영자 판단에 맡긴다.
 *
 * record 의 compact constructor 가 `terms` 를 방어 복사 (List.copyOf) 하므로 data class 가
 * 아닌 일반 class — 컴포넌트 변형이 필요하고, equals/hashCode 는 정규화된 필드 기준으로
 * 직접 정의한다.
 */
class SynonymGroup(
    id: SynonymGroupId,
    terms: List<String>,
    direction: SynonymDirection,
    updatedAt: Instant,
    updatedBy: String
) {

    @get:JvmName("id")
    val id: SynonymGroupId

    @get:JvmName("terms")
    val terms: List<String>

    @get:JvmName("direction")
    val direction: SynonymDirection

    @get:JvmName("updatedAt")
    val updatedAt: Instant

    @get:JvmName("updatedBy")
    val updatedBy: String

    init {
        require(updatedBy.isNotBlank()) { "updatedBy 빈 값 불가" }

        // 외부에서 mutation 못 하도록 방어 복사 + immutable wrap.
        val copied = java.util.List.copyOf(terms)

        require(copied.size >= 2) { "synonym 그룹은 최소 2개 term 필요: ${copied.size}" }
        require(copied.size <= MAX_TERMS) {
            "synonym 그룹 term 수가 $MAX_TERMS 초과: ${copied.size}"
        }
        require(!(direction == SynonymDirection.ONE_WAY && copied.size < 2)) {
            "ONE_WAY 는 LHS + RHS 최소 1개씩 필요"
        }

        for (term in copied) {
            validateTerm(term)
        }
        rejectDuplicates(copied)

        this.id = id
        this.terms = copied
        this.direction = direction
        this.updatedAt = updatedAt
        this.updatedBy = updatedBy
    }

    /**
     * ES synonym graph token filter 가 받는 라인 한 줄 표현.
     *
     * - BIDIRECTIONAL — `"term1, term2, term3"`
     * - ONE_WAY — `"term1 => term2, term3"` (LHS = 첫 1개, RHS = 나머지)
     *
     * 줄바꿈은 [FORBIDDEN] 으로 등록 단계에서 거부하므로 본 출력에 줄바꿈이 섞일 가능성은
     * 없다.
     */
    fun toElasticsearchRule(): String = when (direction) {
        SynonymDirection.BIDIRECTIONAL -> terms.joinToString(", ")
        SynonymDirection.ONE_WAY ->
            terms[0] + " => " + terms.subList(1, terms.size).joinToString(", ")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynonymGroup) return false
        return id == other.id &&
            terms == other.terms &&
            direction == other.direction &&
            updatedAt == other.updatedAt &&
            updatedBy == other.updatedBy
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + terms.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + updatedBy.hashCode()
        return result
    }

    override fun toString(): String =
        "SynonymGroup[id=$id, terms=$terms, direction=$direction, " +
            "updatedAt=$updatedAt, updatedBy=$updatedBy]"

    companion object {
        /** 한 그룹에 묶을 수 있는 최대 term 수 — ES 분석 비용 / 운영 가독성 가이드. */
        const val MAX_TERMS: Int = 50

        /** term 길이 — 너무 긴 표현은 색인 토큰화 자체에 무리. */
        const val MAX_TERM_LENGTH: Int = 100

        /**
         * ES synonym 표현은 쉼표 (BIDIRECTIONAL) 와 화살표 (ONE_WAY) 가 구분자, 그리고 ES 는 rule
         * 자체를 라인 단위로 파싱한다. 추가로 어댑터 측 JPA 직렬화가 줄바꿈을 term 구분자로 쓰므로
         * `\n` / `\r` 가 term 안에 들어오면 round-trip 시 한 term 이 두 term 으로 split 되어
         * 데이터가 조용히 망가진다. 등록 단계에서 모두 거부.
         */
        private val FORBIDDEN: Pattern = Pattern.compile("[,\\\\\\n\\r]|=>")

        /**
         * 한 그룹에 같은 term 이 두 번 이상 들어오면 거부. `ko_standard` analyzer 가 lowercase
         * 필터를 거치므로 대소문자만 다른 표기 (Air Jordan 1 / air jordan 1) 도 동일 토큰이 되어 중복.
         * 운영자 입력 실수 (붙여넣기 / typo) 단계에서 잡는다.
         */
        private fun rejectDuplicates(terms: List<String>) {
            val seen = HashSet<String>()
            for (term in terms) {
                val key = term.lowercase(Locale.ROOT)
                require(seen.add(key)) {
                    "synonym 그룹에 중복 term (대소문자 무시): $term"
                }
            }
        }

        private fun validateTerm(term: String) {
            require(term.isNotBlank()) { "term 빈 값 불가" }
            require(term.length <= MAX_TERM_LENGTH) {
                "term 길이 $MAX_TERM_LENGTH 이하: $term"
            }
            require(!FORBIDDEN.matcher(term).find()) {
                "term 안에 ES synonym 구분자 (',', '\\', '=>', 줄바꿈) 사용 불가: $term"
            }
        }

        @JvmStatic
        fun create(
            terms: List<String>,
            direction: SynonymDirection,
            now: Instant,
            updatedBy: String
        ): SynonymGroup =
            SynonymGroup(SynonymGroupId.newId(), terms, direction, now, updatedBy)
    }
}
