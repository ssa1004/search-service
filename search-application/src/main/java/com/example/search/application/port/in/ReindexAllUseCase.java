package com.example.search.application.port.in;

import com.example.search.application.command.ReindexAllCommand;
import com.example.search.application.command.ReindexResult;

/**
 * 전체 reindex — alias 패턴 zero-downtime.
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>새 물리 인덱스 생성 (현재와 다른 mapping/세팅 가능).</li>
 *   <li>source DB 의 모든 product 를 bulk indexing → 새 인덱스. 기존 ES 의 click 누적값은 별도
 *       조회 후 보존.</li>
 *   <li>doc count 검증 (source vs target).</li>
 *   <li>alias atomic swap.</li>
 *   <li>{@code dropOld=true} 면 구 인덱스 즉시 삭제 (default false — rollback 시간 확보).</li>
 * </ol>
 */
public interface ReindexAllUseCase {
    ReindexResult reindex(ReindexAllCommand command);
}
