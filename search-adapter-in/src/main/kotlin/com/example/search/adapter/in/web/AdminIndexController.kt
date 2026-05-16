package com.example.search.adapter.`in`.web

import com.example.search.adapter.`in`.web.dto.SearchDtos
import com.example.search.application.command.ReindexAllCommand
import com.example.search.application.port.`in`.ReindexAllUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 운영 admin endpoint — 전체 reindex / alias swap. 운영 환경에서는 별도 보안 게이트 (네트워크 또는
 * 인증 미들웨어) 뒤에 둔다.
 */
@RestController
@RequestMapping("/api/v1/admin/index")
class AdminIndexController(
    private val reindexAllUseCase: ReindexAllUseCase
) {

    @PostMapping("/reindex")
    fun reindex(@Valid @RequestBody req: SearchDtos.ReindexRequest): SearchDtos.ReindexResponse {
        val result = reindexAllUseCase.reindex(ReindexAllCommand(req.suffix, req.dropOld))
        return SearchDtos.ReindexResponse(
            result.newPhysicalName, result.sourceDocCount,
            result.targetDocCount, result.swapped
        )
    }
}
