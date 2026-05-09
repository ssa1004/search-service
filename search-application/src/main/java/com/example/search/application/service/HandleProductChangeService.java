package com.example.search.application.service;

import com.example.search.application.command.IndexProductCommand;
import com.example.search.application.port.in.HandleProductChangeUseCase;
import com.example.search.application.port.in.IndexProductUseCase;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.domain.event.ProductChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CDC 컨슈머가 호출하는 진입점. INSERT/UPDATE 는 {@link IndexProductUseCase} 위임, DELETE 는 ES 문서
 * 삭제.
 *
 * <p>이 service 는 idempotent — 같은 이벤트가 두 번 와도 결과가 같다 (ES external version 이 거부 +
 * delete 도 멱등). Kafka at-least-once 와 잘 맞는다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandleProductChangeService implements HandleProductChangeUseCase {

    private final IndexProductUseCase indexer;
    private final IndexWriterPort indexWriter;

    @Override
    public void handle(ProductChangeEvent event) {
        log.debug("CDC event op={} id={} version={}",
                event.op(), event.productId(), event.version());

        switch (event.op()) {
            case INSERT, UPDATE -> indexer.index(new IndexProductCommand(event.product()));
            case DELETE -> indexWriter.delete(event.productId());
        }
    }
}
