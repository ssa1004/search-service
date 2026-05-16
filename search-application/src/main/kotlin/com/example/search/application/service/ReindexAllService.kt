package com.example.search.application.service

import com.example.search.application.command.ReindexAllCommand
import com.example.search.application.command.ReindexResult
import com.example.search.application.port.`in`.ReindexAllUseCase
import com.example.search.application.port.out.IndexWriterPort
import com.example.search.application.port.out.ProductSourceRepository
import com.example.search.application.port.out.SearchClickRepository
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.domain.index.IndexAlias
import com.example.search.domain.index.IndexDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * 전체 reindex — alias-based zero-downtime 패턴 (ADR-0005).
 *
 * 운영 시나리오:
 * - mapping / analyzer 변경이 필요해 새 인덱스를 만들어야 할 때.
 * - CDC 가 깨졌거나 일부 문서가 누락됐을 때.
 *
 * 흐름:
 * 1. 현재 alias 가 가리키는 물리 인덱스 확인 (없으면 reindex 가 사실상 첫 indexing).
 * 2. 새 물리 인덱스 생성 (alias-{suffix}).
 * 3. source DB 의 product 를 batch 단위로 읽어 bulk indexing — clickCount 는 source 측 click 로그
 *    합산으로 보존.
 * 4. doc count 검증 — source 와 새 인덱스의 개수가 일치하지 않으면 swap 하지 않고 운영자에게
 *    반환 (수동 검토 후 다시).
 * 5. alias atomic swap.
 * 6. dropOld=true 면 구 인덱스 삭제. default false (rollback 시간 확보).
 */
@Service
class ReindexAllService(
    private val products: ProductSourceRepository,
    private val clicks: SearchClickRepository,
    private val indexWriter: IndexWriterPort,
    private val properties: SearchIndexProperties,
    private val clock: Clock
) : ReindexAllUseCase {

    override fun reindex(command: ReindexAllCommand): ReindexResult {
        val alias = properties.alias()
        val oldPhysical = indexWriter.currentPhysicalName()
        val newPhysical = IndexAlias.physicalNameFor(alias, command.suffix)
        log.info("reindex 시작 alias={} old={} new={}", alias, oldPhysical, newPhysical)

        indexWriter.createIndex(newPhysical)

        val sourceTotal = products.countAll()
        val batchSize = properties.reindexBatchSize()
        val since = clock.instant().minus(POPULARITY_LOOKBACK)

        var indexed = 0L
        var offset = 0
        while (offset < sourceTotal) {
            val batch = products.findAll(offset, batchSize)
            if (batch.isEmpty()) {
                break
            }
            val docs = ArrayList<IndexDocument>(batch.size)
            for (p in batch) {
                val clickCount = clicks.sumClicksFor(p.id, since)
                docs.add(IndexDocument.from(p, clickCount))
            }
            indexWriter.bulkIndex(docs)
            indexed += docs.size.toLong()
            offset += batch.size
            log.debug("reindex bulk indexed={} total={}", indexed, sourceTotal)
        }

        val targetCount = indexWriter.countDocuments(newPhysical)
        val countsMatch = sourceTotal == targetCount

        if (!countsMatch) {
            log.warn(
                "reindex doc count 불일치 — alias swap 보류. source={} target={}",
                sourceTotal, targetCount
            )
            return ReindexResult(newPhysical, sourceTotal, targetCount, false)
        }

        indexWriter.swapAlias(oldPhysical, newPhysical)
        log.info(
            "reindex 완료 + alias swap done. old={} new={} docs={}",
            oldPhysical, newPhysical, targetCount
        )

        if (command.dropOld && oldPhysical != null) {
            indexWriter.deleteIndex(oldPhysical)
            log.info("구 인덱스 즉시 삭제 (dropOld=true): {}", oldPhysical)
        }

        return ReindexResult(newPhysical, sourceTotal, targetCount, true)
    }

    companion object {
        /**
         * popularity 시그널 lookback — IndexProductService 와 같은 1년.
         */
        private val POPULARITY_LOOKBACK: Duration = Duration.ofDays(365)

        private val log = LoggerFactory.getLogger(ReindexAllService::class.java)
    }
}
