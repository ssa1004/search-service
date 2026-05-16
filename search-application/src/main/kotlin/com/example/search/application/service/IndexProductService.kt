package com.example.search.application.service

import com.example.search.application.command.IndexProductCommand
import com.example.search.application.port.`in`.IndexProductUseCase
import com.example.search.application.port.out.IndexWriterPort
import com.example.search.application.port.out.SearchClickRepository
import com.example.search.domain.index.IndexDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * 단건 상품 indexing.
 *
 * 흐름:
 * 1. 기존 click 누적값을 source 측 로그에서 읽어와 보존 — 인덱싱 후에도 popularity 시그널이
 *    사라지지 않도록.
 * 2. [IndexDocument.from] 으로 변환.
 * 3. ES external version (= product.version) 으로 upsert.
 *
 * ES 호출 자체의 fault tolerance 는 [IndexWriterPort] 의 운영 구현체에 적용된 Resilience4j
 * 가 책임진다. 이 service 는 도메인 invariant 만 본다.
 */
@Service
class IndexProductService(
    private val indexWriter: IndexWriterPort,
    private val clicks: SearchClickRepository,
    private val clock: Clock
) : IndexProductUseCase {

    override fun index(command: IndexProductCommand) {
        val since = clock.instant().minus(POPULARITY_LOOKBACK)
        val clickCount = clicks.sumClicksFor(command.product.id, since)

        val doc = IndexDocument.from(command.product, clickCount)
        indexWriter.index(doc)
        log.debug(
            "indexed product id={} version={} clicks={}",
            doc.id, doc.version, doc.clickCount
        )
    }

    companion object {
        /**
         * popularity 시그널을 얼마나 거슬러 올라가서 합산할지. 1년 — 그 이상은 의미 약함.
         */
        private val POPULARITY_LOOKBACK: Duration = Duration.ofDays(365)

        private val log = LoggerFactory.getLogger(IndexProductService::class.java)
    }
}
