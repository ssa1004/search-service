package com.example.search.application.savedsearch.port.in;

import com.example.search.domain.savedsearch.SavedSearch;

/**
 * 한 사용자의 SavedSearch 등록 상한 ({@link SavedSearch#MAX_PER_OWNER}) 초과.
 */
public class SavedSearchQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SavedSearchQuotaExceededException(String ownerId, int currentCount) {
        super("SavedSearch quota 초과 owner=%s current=%d max=%d"
                .formatted(ownerId, currentCount, SavedSearch.MAX_PER_OWNER));
    }
}
