package com.example.search.adapter.`in`.web

import com.example.search.adapter.`in`.web.dto.SynonymDtos
import com.example.search.application.synonym.command.RegisterSynonymGroupCommand
import com.example.search.application.synonym.port.`in`.ApplySynonymsToIndexUseCase
import com.example.search.application.synonym.port.`in`.DeleteSynonymGroupUseCase
import com.example.search.application.synonym.port.`in`.ListSynonymGroupsUseCase
import com.example.search.application.synonym.port.`in`.RegisterSynonymGroupUseCase
import com.example.search.domain.synonym.SynonymDirection
import com.example.search.domain.synonym.SynonymGroupId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 운영자 동의어 사전 admin endpoint (ADR-0017).
 *
 * 모든 endpoint 는 운영 보안 게이트 (네트워크 분리 또는 인증 미들웨어) 뒤에 둔다 — 운영자 식별은
 * `X-Operator-Id` 헤더로 받아 audit trail 의 `updatedBy` 에 사용한다.
 */
@RestController
@RequestMapping("/api/v1/admin/synonyms")
class AdminSynonymController(
    private val registerUseCase: RegisterSynonymGroupUseCase,
    private val listUseCase: ListSynonymGroupsUseCase,
    private val deleteUseCase: DeleteSynonymGroupUseCase,
    private val applyUseCase: ApplySynonymsToIndexUseCase
) {

    @PostMapping
    fun register(
        @RequestHeader("X-Operator-Id") operatorId: String,
        @Valid @RequestBody request: SynonymDtos.RegisterRequest
    ): ResponseEntity<SynonymDtos.SynonymGroupDto> {
        val direction = parseDirection(request.direction)
        val saved = registerUseCase.register(
            RegisterSynonymGroupCommand(request.terms, direction, operatorId)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SynonymDtos.SynonymGroupDto.from(saved))
    }

    @GetMapping
    fun list(): SynonymDtos.ListResponse {
        val dtos = listUseCase.listAll().map { SynonymDtos.SynonymGroupDto.from(it) }
        return SynonymDtos.ListResponse(dtos)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable("id") id: String): ResponseEntity<Void> {
        deleteUseCase.delete(SynonymGroupId.of(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/apply")
    fun apply(): SynonymDtos.ApplyResponse {
        val applied = applyUseCase.apply()
        return SynonymDtos.ApplyResponse(applied)
    }

    private fun parseDirection(raw: String): SynonymDirection = try {
        SynonymDirection.valueOf(raw)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("direction 은 BIDIRECTIONAL / ONE_WAY 만 허용: $raw")
    }
}
