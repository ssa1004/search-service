package com.example.search.application.synonym.port.in;

import com.example.search.application.synonym.command.RegisterSynonymGroupCommand;
import com.example.search.domain.synonym.SynonymGroup;

/**
 * 운영자가 새 동의어 그룹을 등록 — RDB 만 갱신 (ES 는 별도 reload 로 적용).
 *
 * <p>등록 즉시 ES 에 반영하지 않는 이유: 운영자가 여러 그룹을 한꺼번에 추가 / 삭제 후 한 번만
 * reload 하는 게 ES 인덱스 close → open 비용 측면에서 유리. {@link ApplySynonymsToIndexUseCase}
 * 가 별도로 호출된다.</p>
 */
public interface RegisterSynonymGroupUseCase {

    SynonymGroup register(RegisterSynonymGroupCommand command);
}
