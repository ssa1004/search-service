package com.example.search.adapter.`in`.web.dto

import com.example.search.domain.synonym.SynonymGroup
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * 동의어 사전 admin REST DTO. 도메인 객체와 분리해 도메인 변경이 외부 API 를 흔들지 않게 한다.
 */
object SynonymDtos {

    @JvmRecord
    data class RegisterRequest(
        @field:NotNull @field:Size(min = 2, max = SynonymGroup.MAX_TERMS)
        val terms: List<@NotEmpty String>,
        @field:NotEmpty val direction: String
    )

    @JvmRecord
    data class SynonymGroupDto(
        val id: String,
        val terms: List<String>,
        val direction: String,
        val updatedAt: Instant,
        val updatedBy: String
    ) {
        companion object {
            @JvmStatic
            fun from(g: SynonymGroup): SynonymGroupDto = SynonymGroupDto(
                g.id.value, g.terms, g.direction.name,
                g.updatedAt, g.updatedBy
            )
        }
    }

    @JvmRecord
    data class ListResponse(val groups: List<SynonymGroupDto>)

    @JvmRecord
    data class ApplyResponse(val applied: Int)
}
