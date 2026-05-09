package com.example.search.adapter.in.web.dto;

import com.example.search.domain.synonym.SynonymGroup;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 동의어 사전 admin REST DTO. 도메인 객체와 분리해 도메인 변경이 외부 API 를 흔들지 않게 한다.
 */
public final class SynonymDtos {

    private SynonymDtos() {
    }

    public record RegisterRequest(
            @NotNull @Size(min = 2, max = SynonymGroup.MAX_TERMS) List<@NotEmpty String> terms,
            @NotEmpty String direction
    ) {
    }

    public record SynonymGroupDto(
            String id,
            List<String> terms,
            String direction,
            Instant updatedAt,
            String updatedBy
    ) {
        public static SynonymGroupDto from(SynonymGroup g) {
            return new SynonymGroupDto(
                    g.id().value(), g.terms(), g.direction().name(),
                    g.updatedAt(), g.updatedBy());
        }
    }

    public record ListResponse(List<SynonymGroupDto> groups) {
    }

    public record ApplyResponse(int applied) {
    }
}
