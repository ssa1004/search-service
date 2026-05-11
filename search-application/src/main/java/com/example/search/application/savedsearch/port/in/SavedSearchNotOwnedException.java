package com.example.search.application.savedsearch.port.in;

import com.example.search.domain.savedsearch.SavedSearchId;

/**
 * 다른 사용자의 SavedSearch 에 대해 변경/삭제를 시도했을 때.
 *
 * <p>web 레이어에서 401/403 으로 매핑. 도메인 invariant 가 아닌 권한 위반이므로 별도 unchecked
 * exception.</p>
 */
public class SavedSearchNotOwnedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SavedSearchNotOwnedException(String ownerId, SavedSearchId id) {
        super("SavedSearch %s 는 owner %s 의 소유가 아님".formatted(id.value(), ownerId));
    }
}
