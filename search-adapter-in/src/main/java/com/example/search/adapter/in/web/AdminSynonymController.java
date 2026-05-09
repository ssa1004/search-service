package com.example.search.adapter.in.web;

import com.example.search.adapter.in.web.dto.SynonymDtos;
import com.example.search.application.synonym.command.RegisterSynonymGroupCommand;
import com.example.search.application.synonym.port.in.ApplySynonymsToIndexUseCase;
import com.example.search.application.synonym.port.in.DeleteSynonymGroupUseCase;
import com.example.search.application.synonym.port.in.ListSynonymGroupsUseCase;
import com.example.search.application.synonym.port.in.RegisterSynonymGroupUseCase;
import com.example.search.domain.synonym.SynonymDirection;
import com.example.search.domain.synonym.SynonymGroup;
import com.example.search.domain.synonym.SynonymGroupId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 운영자 동의어 사전 admin endpoint (ADR-0017).
 *
 * <p>모든 endpoint 는 운영 보안 게이트 (네트워크 분리 또는 인증 미들웨어) 뒤에 둔다 — 운영자 식별은
 * {@code X-Operator-Id} 헤더로 받아 audit trail 의 {@code updatedBy} 에 사용한다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/synonyms")
@RequiredArgsConstructor
public class AdminSynonymController {

    private final RegisterSynonymGroupUseCase registerUseCase;
    private final ListSynonymGroupsUseCase listUseCase;
    private final DeleteSynonymGroupUseCase deleteUseCase;
    private final ApplySynonymsToIndexUseCase applyUseCase;

    @PostMapping
    public ResponseEntity<SynonymDtos.SynonymGroupDto> register(
            @RequestHeader("X-Operator-Id") String operatorId,
            @Valid @RequestBody SynonymDtos.RegisterRequest request) {
        SynonymDirection direction = parseDirection(request.direction());
        SynonymGroup saved = registerUseCase.register(
                new RegisterSynonymGroupCommand(request.terms(), direction, operatorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(SynonymDtos.SynonymGroupDto.from(saved));
    }

    @GetMapping
    public SynonymDtos.ListResponse list() {
        List<SynonymDtos.SynonymGroupDto> dtos = listUseCase.listAll().stream()
                .map(SynonymDtos.SynonymGroupDto::from)
                .toList();
        return new SynonymDtos.ListResponse(dtos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        deleteUseCase.delete(SynonymGroupId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/apply")
    public SynonymDtos.ApplyResponse apply() {
        int applied = applyUseCase.apply();
        return new SynonymDtos.ApplyResponse(applied);
    }

    private SynonymDirection parseDirection(String raw) {
        try {
            return SynonymDirection.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "direction 은 BIDIRECTIONAL / ONE_WAY 만 허용: " + raw);
        }
    }
}
