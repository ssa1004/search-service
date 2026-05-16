package com.example.search.application.savedsearch.command

import com.example.search.domain.query.SearchQuery
import com.example.search.domain.savedsearch.NotifyChannel

/**
 * 사용자가 현재 query 를 저장 — REST 입력의 도메인 명령 표현.
 */
@JvmRecord
data class SaveSearchCommand(
    val ownerId: String,
    val label: String,
    val query: SearchQuery,
    val notifyChannel: NotifyChannel
)
