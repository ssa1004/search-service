package com.example.search.application.synonym.command;

import com.example.search.domain.synonym.SynonymDirection;

import java.util.List;
import java.util.Objects;

/**
 * 운영자가 새 동의어 그룹을 등록할 때의 명령.
 */
public record RegisterSynonymGroupCommand(
        List<String> terms,
        SynonymDirection direction,
        String operatorId
) {

    public RegisterSynonymGroupCommand {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(operatorId, "operatorId");
        if (operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId 빈 값 불가");
        }
    }
}
