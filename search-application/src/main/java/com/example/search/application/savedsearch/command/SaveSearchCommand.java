package com.example.search.application.savedsearch.command;

import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.savedsearch.NotifyChannel;

import java.util.Objects;

/**
 * 사용자가 현재 query 를 저장 — REST 입력의 도메인 명령 표현.
 */
public record SaveSearchCommand(
        String ownerId,
        String label,
        SearchQuery query,
        NotifyChannel notifyChannel
) {

    public SaveSearchCommand {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(notifyChannel, "notifyChannel");
    }
}
