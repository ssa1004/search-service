package com.example.search.application.port.in;

import com.example.search.application.command.RecordSearchClickCommand;

/**
 * 사용자 검색 클릭 기록 — 다음 검색의 boost rule 학습 시그널. ES 의 해당 product 문서
 * {@code clickCount} 를 1 증가 (partial update).
 *
 * <p>구현체는 click 이벤트도 별도 store (현재는 메모리 / 추후 ClickHouse 등) 에 기록 — boost
 * 비활성화 / 디버깅 / popularity 분석 자료.</p>
 */
public interface RecordSearchClickUseCase {
    void record(RecordSearchClickCommand command);
}
