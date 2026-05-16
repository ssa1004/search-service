package com.example.search.application.savedsearch.port.`in`

import com.example.search.domain.savedsearch.SavedSearchId

/**
 * SavedSearch 영구 삭제. owner 가 본인 것만 지울 수 있도록 ownerId 도 함께 받아 권한 검사.
 */
interface DeleteSavedSearchUseCase {

    /**
     * @throws SavedSearchNotOwnedException ownerId 가 SavedSearch 의 owner 와 다를 때
     */
    fun delete(ownerId: String, id: SavedSearchId)
}
