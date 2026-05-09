package com.example.search.application.service;

import com.example.search.application.command.RecordSearchClickCommand;
import com.example.search.application.port.in.RecordSearchClickUseCase;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.domain.event.SearchClick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 검색 클릭 기록 — boost rule 학습 시그널.
 *
 * <p>두 단계:</p>
 * <ol>
 *   <li>{@link SearchClickRepository#save} — 상세 로그 저장 (트랜잭션 안).</li>
 *   <li>{@link IndexWriterPort#incrementClickCount} — ES 문서의 clickCount += 1 (partial update,
 *       트랜잭션 밖 — ES 는 트랜잭션이 없음).</li>
 * </ol>
 *
 * <p>ES 호출 실패가 트랜잭션을 롤백시키지 않도록 두 단계가 의도적으로 분리된다 — DB 는 commit
 * 되어 있고, ES 갱신은 retry 로 따라잡는다 (eventually consistent). ES 에 click count 가 좀 늦게
 * 반영되는 것이 클릭 기록 자체가 사라지는 것보다 낫다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecordSearchClickService implements RecordSearchClickUseCase {

    private final SearchClickRepository clicks;
    private final IndexWriterPort indexWriter;
    private final Clock clock;

    @Override
    @Transactional
    public void record(RecordSearchClickCommand command) {
        SearchClick click = new SearchClick(
                command.searchId(),
                command.userId(),
                command.productId(),
                command.keyword(),
                command.rank(),
                clock.instant());

        clicks.save(click);
        log.debug("click recorded user={} product={} keyword='{}' rank={}",
                click.userId(), click.productId(), click.keyword(), click.rank());

        // ES partial update — 실패해도 DB 는 commit. retry / DLQ 는 IndexWriter 구현체에서.
        indexWriter.incrementClickCount(command.productId());
    }
}
