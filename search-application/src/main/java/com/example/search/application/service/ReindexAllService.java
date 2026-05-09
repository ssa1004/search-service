package com.example.search.application.service;

import com.example.search.application.command.ReindexAllCommand;
import com.example.search.application.command.ReindexResult;
import com.example.search.application.port.in.ReindexAllUseCase;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.ProductSourceRepository;
import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.domain.index.IndexAlias;
import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 전체 reindex — alias-based zero-downtime 패턴 (ADR-0005).
 *
 * <p>운영 시나리오:</p>
 * <ul>
 *   <li>mapping / analyzer 변경이 필요해 새 인덱스를 만들어야 할 때.</li>
 *   <li>CDC 가 깨졌거나 일부 문서가 누락됐을 때.</li>
 * </ul>
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>현재 alias 가 가리키는 물리 인덱스 확인 (없으면 reindex 가 사실상 첫 indexing).</li>
 *   <li>새 물리 인덱스 생성 (alias-{suffix}).</li>
 *   <li>source DB 의 product 를 batch 단위로 읽어 bulk indexing — clickCount 는 source 측 click 로그
 *       합산으로 보존.</li>
 *   <li>doc count 검증 — source 와 새 인덱스의 개수가 일치하지 않으면 swap 하지 않고 운영자에게
 *       반환 (수동 검토 후 다시).</li>
 *   <li>alias atomic swap.</li>
 *   <li>{@code dropOld=true} 면 구 인덱스 삭제. default false (rollback 시간 확보).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReindexAllService implements ReindexAllUseCase {

    /**
     * popularity 시그널 lookback — IndexProductService 와 같은 1년.
     */
    private static final java.time.Duration POPULARITY_LOOKBACK = java.time.Duration.ofDays(365);

    private final ProductSourceRepository products;
    private final SearchClickRepository clicks;
    private final IndexWriterPort indexWriter;
    private final SearchIndexProperties properties;
    private final Clock clock;

    @Override
    public ReindexResult reindex(ReindexAllCommand command) {
        String alias = properties.alias();
        String oldPhysical = indexWriter.currentPhysicalName();
        String newPhysical = IndexAlias.physicalNameFor(alias, command.suffix());
        log.info("reindex 시작 alias={} old={} new={}", alias, oldPhysical, newPhysical);

        indexWriter.createIndex(newPhysical);

        long sourceTotal = products.countAll();
        int batchSize = properties.reindexBatchSize();
        Instant since = clock.instant().minus(POPULARITY_LOOKBACK);

        long indexed = 0;
        int offset = 0;
        while (offset < sourceTotal) {
            List<Product> batch = products.findAll(offset, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            List<IndexDocument> docs = new ArrayList<>(batch.size());
            for (Product p : batch) {
                long clickCount = clicks.sumClicksFor(p.id(), since);
                docs.add(IndexDocument.from(p, clickCount));
            }
            indexWriter.bulkIndex(docs);
            indexed += docs.size();
            offset += batch.size();
            log.debug("reindex bulk indexed={} total={}", indexed, sourceTotal);
        }

        long targetCount = indexWriter.countDocuments(newPhysical);
        boolean countsMatch = sourceTotal == targetCount;

        if (!countsMatch) {
            log.warn("reindex doc count 불일치 — alias swap 보류. source={} target={}",
                    sourceTotal, targetCount);
            return new ReindexResult(newPhysical, sourceTotal, targetCount, false);
        }

        indexWriter.swapAlias(oldPhysical, newPhysical);
        log.info("reindex 완료 + alias swap done. old={} new={} docs={}",
                oldPhysical, newPhysical, targetCount);

        if (command.dropOld() && oldPhysical != null) {
            indexWriter.deleteIndex(oldPhysical);
            log.info("구 인덱스 즉시 삭제 (dropOld=true): {}", oldPhysical);
        }

        return new ReindexResult(newPhysical, sourceTotal, targetCount, true);
    }
}
