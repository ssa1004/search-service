package com.example.search.domain.event

import com.example.search.domain.product.ProductId
import java.time.Instant

/**
 * 사용자가 검색 결과에서 어떤 상품을 클릭했는지의 로그. 다음 검색의 boost rule 학습 시그널이다.
 *
 * 흐름:
 * 1. 사용자가 검색 → 결과 노출.
 * 2. 결과 중 한 건 클릭 → REST POST /searches/{searchId}/clicks {productId}
 * 3. [SearchClick] 이벤트 저장.
 * 4. indexer 가 해당 product 의 ES 문서 `clickCount` += 1 (partial update).
 * 5. 다음 검색에서 function_score 의 popularity 가중치에 반영.
 *
 * [rank] 는 결과 페이지에서 클릭된 위치 (1-based). 1순위 클릭과 50순위 클릭은 시그널 강도가
 * 다름 — 후속 학습 단계에서 weight 분리 가능.
 */
@JvmRecord
data class SearchClick(
    val searchId: String,
    // userId 는 Java record 가 null 을 허용 — 익명 클릭 대응.
    val userId: String?,
    val productId: ProductId,
    val keyword: String,
    val rank: Int,
    val occurredAt: Instant
) {
    init {
        require(rank >= 1) { "rank 1 이상이어야 함: $rank" }
    }
}
