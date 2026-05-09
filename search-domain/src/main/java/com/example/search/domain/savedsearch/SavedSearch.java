package com.example.search.domain.savedsearch;

import com.example.search.domain.query.SearchQuery;

import java.time.Instant;
import java.util.Objects;

/**
 * 사용자가 저장한 검색 조건 + 알림 설정.
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>사용자가 현재 검색 query 를 "저장" 요청 → {@code SavedSearch} 생성.</li>
 *   <li>스케줄러가 5분마다 모든 active SavedSearch 를 평가 — {@code lastEvaluatedAt} 이후 신규 매치.</li>
 *   <li>매치 발견 시 {@link com.example.search.application.savedsearch.port.out.SavedSearchAlertPublisher} 로 알림 발행.</li>
 * </ol>
 *
 * <p>{@code lastEvaluatedAt} 은 평가가 끝난 시점 — 매치 여부와 무관하게 갱신. 매치된 시점만 별도로
 * 추적하려면 별도 record (SavedSearchMatch) 가 책임진다.</p>
 *
 * <p>active 상태가 false 이면 스케줄러가 건너뜀 — 사용자가 일시 중지 / 재개 가능. 영구 삭제는 별도
 * delete use case.</p>
 */
public record SavedSearch(
        SavedSearchId id,
        String ownerId,
        String label,
        SearchQuery query,
        NotifyChannel notifyChannel,
        boolean active,
        Instant createdAt,
        Instant lastEvaluatedAt
) {

    /** 한 사용자가 등록 가능한 SavedSearch 상한 — 무한 등록 방지. */
    public static final int MAX_PER_OWNER = 50;

    /** 라벨 길이 상한 — DB column 200 에 맞춤. */
    public static final int MAX_LABEL_LENGTH = 200;

    public SavedSearch {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(notifyChannel, "notifyChannel");
        Objects.requireNonNull(createdAt, "createdAt");
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 빈 값 불가");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label 빈 값 불가");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "label 길이 " + MAX_LABEL_LENGTH + " 이하: " + label.length());
        }
    }

    /** 신규 등록 — id 자동 발급, active=true, lastEvaluatedAt=null. */
    public static SavedSearch create(String ownerId, String label, SearchQuery query,
                                     NotifyChannel channel, Instant now) {
        return new SavedSearch(SavedSearchId.newId(), ownerId, label, query, channel,
                true, now, null);
    }

    /** 평가 완료 시 lastEvaluatedAt 만 갱신한 새 인스턴스. record 의 immutability 유지. */
    public SavedSearch markEvaluated(Instant at) {
        Objects.requireNonNull(at, "at");
        return new SavedSearch(id, ownerId, label, query, notifyChannel,
                active, createdAt, at);
    }

    public SavedSearch deactivate() {
        return new SavedSearch(id, ownerId, label, query, notifyChannel,
                false, createdAt, lastEvaluatedAt);
    }

    /** 스케줄러가 첫 평가 직전 lastEvaluatedAt 을 createdAt 으로 fallback — 신규 등록 시 과거 매치 폭주 방지. */
    public Instant evaluationCursor() {
        return lastEvaluatedAt != null ? lastEvaluatedAt : createdAt;
    }
}
