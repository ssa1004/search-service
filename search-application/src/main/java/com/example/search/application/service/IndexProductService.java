package com.example.search.application.service;

import com.example.search.application.command.IndexProductCommand;
import com.example.search.application.port.in.IndexProductUseCase;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.domain.index.IndexDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 단건 상품 indexing.
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>기존 click 누적값을 source 측 로그에서 읽어와 보존 — 인덱싱 후에도 popularity 시그널이
 *       사라지지 않도록.</li>
 *   <li>{@link IndexDocument#from(com.example.search.domain.product.Product, long)} 으로 변환.</li>
 *   <li>ES external version (= product.version) 으로 upsert.</li>
 * </ol>
 *
 * <p>ES 호출 자체의 fault tolerance 는 {@link IndexWriterPort} 의 운영 구현체에 적용된 Resilience4j
 * 가 책임진다. 이 service 는 도메인 invariant 만 본다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexProductService implements IndexProductUseCase {

    /**
     * popularity 시그널을 얼마나 거슬러 올라가서 합산할지. 1년 — 그 이상은 의미 약함.
     */
    private static final java.time.Duration POPULARITY_LOOKBACK = java.time.Duration.ofDays(365);

    private final IndexWriterPort indexWriter;
    private final SearchClickRepository clicks;
    private final Clock clock;

    @Override
    public void index(IndexProductCommand command) {
        Instant since = clock.instant().minus(POPULARITY_LOOKBACK);
        long clickCount = clicks.sumClicksFor(command.product().id(), since);

        IndexDocument doc = IndexDocument.from(command.product(), clickCount);
        indexWriter.index(doc);
        log.debug("indexed product id={} version={} clicks={}",
                doc.id(), doc.version(), doc.clickCount());
    }
}
