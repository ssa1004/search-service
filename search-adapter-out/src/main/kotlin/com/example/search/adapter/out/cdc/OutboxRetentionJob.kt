package com.example.search.adapter.out.cdc

import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxRepository
import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * outbox 테이블 retention 정리 — published_at 가 NOT NULL 이고 7일 이상 지난 row 를 batch 삭제
 * (ADR-0014).
 *
 * 매일 03:00 KST 에 실행. 멀티 인스턴스 환경에서 ShedLock 으로 한 인스턴스만 실행 보장.
 *
 * batch 1000 row 단위로 반복 — 한 transaction 이 너무 커지지 않게 (long-running transaction
 * → leak detection 경고 / WAL 부담).
 */
@Component
class OutboxRetentionJob(
    private val outbox: ProductChangeOutboxRepository,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock
) {

    /**
     * 매일 03:00 KST. ShedLock lockAtMostFor=10m → 인스턴스 crash 후에도 10분 후 다른 인스턴스가
     * 재실행. lockAtLeastFor=1m → polling job 의 짧은 race 회피.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "outbox-retention", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    fun purgeOldRows() {
        val cutoff = clock.instant().minus(RETENTION)
        var totalDeleted = 0L
        var deleted: Int
        do {
            deleted = deleteBatch(cutoff)
            totalDeleted += deleted
        } while (deleted == BATCH_SIZE)

        meterRegistry.counter("outbox.retention.deleted").increment(totalDeleted.toDouble())
        log.info("outbox retention 완료 cutoff={} deleted={}", cutoff, totalDeleted)
    }

    @Transactional
    internal fun deleteBatch(cutoff: Instant): Int =
        outbox.deletePublishedBefore(cutoff, BATCH_SIZE)

    companion object {
        private val RETENTION: Duration = Duration.ofDays(7)
        private const val BATCH_SIZE: Int = 1000

        private val log = LoggerFactory.getLogger(OutboxRetentionJob::class.java)
    }
}
