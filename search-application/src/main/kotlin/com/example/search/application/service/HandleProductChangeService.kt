package com.example.search.application.service

import com.example.search.application.command.IndexProductCommand
import com.example.search.application.port.`in`.HandleProductChangeUseCase
import com.example.search.application.port.`in`.IndexProductUseCase
import com.example.search.application.port.out.IndexWriterPort
import com.example.search.domain.event.ProductChangeEvent
import com.example.search.domain.event.ProductChangeEvent.Op
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * CDC 컨슈머가 호출하는 진입점. INSERT/UPDATE 는 [IndexProductUseCase] 위임, DELETE 는 ES 문서
 * 삭제.
 *
 * 이 service 는 idempotent — 같은 이벤트가 두 번 와도 결과가 같다 (ES external version 이 거부 +
 * delete 도 멱등). Kafka at-least-once 와 잘 맞는다.
 */
@Service
class HandleProductChangeService(
    private val indexer: IndexProductUseCase,
    private val indexWriter: IndexWriterPort
) : HandleProductChangeUseCase {

    override fun handle(event: ProductChangeEvent) {
        log.debug(
            "CDC event op={} id={} version={}",
            event.op, event.productId, event.version
        )

        when (event.op) {
            Op.INSERT, Op.UPDATE -> indexer.index(IndexProductCommand(event.product!!))
            Op.DELETE -> indexWriter.delete(event.productId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(HandleProductChangeService::class.java)
    }
}
