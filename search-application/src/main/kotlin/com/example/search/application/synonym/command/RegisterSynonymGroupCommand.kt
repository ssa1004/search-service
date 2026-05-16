package com.example.search.application.synonym.command

import com.example.search.domain.synonym.SynonymDirection

/**
 * 운영자가 새 동의어 그룹을 등록할 때의 명령.
 */
@JvmRecord
data class RegisterSynonymGroupCommand(
    val terms: List<String>,
    val direction: SynonymDirection,
    val operatorId: String
) {
    init {
        if (operatorId.isBlank()) {
            throw IllegalArgumentException("operatorId 빈 값 불가")
        }
    }
}
