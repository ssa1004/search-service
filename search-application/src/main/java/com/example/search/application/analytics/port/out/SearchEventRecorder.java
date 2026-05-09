package com.example.search.application.analytics.port.out;

import com.example.search.domain.analytics.SearchEvent;

/**
 * 한 건의 검색 결과 관측을 비동기 저장. 호출자 (SearchProductService) 는 fire-and-forget 호출.
 *
 * <p>인터페이스 규약:</p>
 * <ul>
 *   <li>실패 시에도 호출자에게 예외를 전파하지 않는다 — 분석 저장 실패가 검색 응답을 깨뜨리면
 *       안 된다. 구현체는 자체 로깅 / metric 만.</li>
 *   <li>호출은 가벼워야 한다 — 무거운 작업은 큐 / async 로 위임.</li>
 * </ul>
 */
public interface SearchEventRecorder {

    void record(SearchEvent event);
}
