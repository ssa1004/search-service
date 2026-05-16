package com.example.search.application.synonym.port.`in`

import com.example.search.domain.synonym.SynonymGroupId

/**
 * 운영자가 동의어 그룹 삭제 — RDB 에서만 제거. ES 반영은 reload 호출 시점.
 */
interface DeleteSynonymGroupUseCase {

    fun delete(id: SynonymGroupId)
}
