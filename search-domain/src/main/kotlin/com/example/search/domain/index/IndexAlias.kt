package com.example.search.domain.index

/**
 * alias-based zero-downtime reindex 를 위한 alias / 물리 인덱스 이름 페어. ADR-0005 참조.
 *
 * 패턴:
 * ```
 *   alias  = "products"            ← 검색은 항상 alias 로
 *   v1     = "products-v202605"    ← 첫 인덱스
 *   v2     = "products-v202608"    ← reindex 시 새로 만든 인덱스
 * ```
 *
 * 운영 흐름:
 * 1. 현재 alias 가 v1 을 가리킴.
 * 2. 새 mapping/세팅으로 v2 생성.
 * 3. v1 → v2 reindex (ES native API).
 * 4. alias atomic swap (v1 제거 + v2 추가 한 번에). 검색은 무중단 전환.
 * 5. v1 삭제 (delay 둬서 rollback 가능).
 */
@JvmRecord
data class IndexAlias(
    val alias: String,
    val physicalName: String
) {
    init {
        require(!(alias.isBlank() || physicalName.isBlank())) {
            "alias / physicalName 빈 값 불가"
        }
        // alias 가 물리 이름과 같으면 ES 가 거부 — 도메인 단계에서 미리 차단.
        require(alias != physicalName) { "alias 와 physical 이름이 같을 수 없음: $alias" }
    }

    companion object {
        /**
         * 새 reindex 인덱스의 물리 이름 — alias + suffix.
         * suffix 는 보통 timestamp (초 단위) — 같은 분 안에 두 번 reindex 해도 충돌 안 남.
         */
        @JvmStatic
        fun physicalNameFor(alias: String, suffix: String): String = "$alias-$suffix"
    }
}
