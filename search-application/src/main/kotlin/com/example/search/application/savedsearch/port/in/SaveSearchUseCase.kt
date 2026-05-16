package com.example.search.application.savedsearch.port.`in`

import com.example.search.application.savedsearch.command.SaveSearchCommand
import com.example.search.domain.savedsearch.SavedSearch

/**
 * 사용자가 현재 검색 query 를 저장 — 등록 후 5분 단위 스케줄러가 신규 매치를 감지해 알림 발행.
 */
interface SaveSearchUseCase {
    fun save(command: SaveSearchCommand): SavedSearch
}
