package com.example.search.application.port.out

import com.example.search.domain.event.SearchClick
import com.example.search.domain.product.ProductId
import java.time.Instant

/**
 * 검색 클릭 이벤트 저장소 port. ES 갱신 (clickCount + 1) 외에 별도 상세 로그를 남겨 popularity 분석
 * 자료로 활용한다.
 */
interface SearchClickRepository {

    fun save(click: SearchClick)

    /**
     * 특정 product 의 누적 click 수 — reindex 시 ES 의 clickCount 를 직접 읽지 않고 source 측 로그를
     * 다시 계산해 정합성 회복용.
     */
    fun sumClicksFor(productId: ProductId, since: Instant): Long
}
