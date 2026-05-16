package com.example.search.application.savedsearch.port.`in`

import com.example.search.domain.savedsearch.SavedSearch

/**
 * 한 사용자의 SavedSearch 등록 상한 ([SavedSearch.MAX_PER_OWNER]) 초과.
 */
class SavedSearchQuotaExceededException(ownerId: String, currentCount: Int) : RuntimeException(
    "SavedSearch quota 초과 owner=$ownerId current=$currentCount max=${SavedSearch.MAX_PER_OWNER}"
) {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
