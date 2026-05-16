package com.example.search.application.savedsearch.port.`in`

import com.example.search.domain.savedsearch.SavedSearch

/**
 * 한 사용자의 SavedSearch 목록 — UI 의 "내 알림" 화면 데이터 소스.
 */
interface ListMySavedSearchesUseCase {
    fun findByOwner(ownerId: String): List<SavedSearch>
}
