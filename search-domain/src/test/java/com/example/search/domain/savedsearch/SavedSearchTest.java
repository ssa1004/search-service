package com.example.search.domain.savedsearch;

import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedSearchTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");
    private static final SearchQuery QUERY = SearchQuery.byKeyword("nike", Page.first(20));
    private static final NotifyChannel CHANNEL = NotifyChannel.kafka("search.alert.fired");

    @Test
    void create_시_active_true이고_lastEvaluatedAt_은_null() {
        SavedSearch saved = SavedSearch.create("user-1", "나이키 신상", QUERY, CHANNEL, NOW);

        assertThat(saved.active()).isTrue();
        assertThat(saved.lastEvaluatedAt()).isNull();
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.id()).isNotNull();
    }

    @Test
    void evaluationCursor_는_lastEvaluatedAt_또는_createdAt() {
        SavedSearch fresh = SavedSearch.create("user-1", "label", QUERY, CHANNEL, NOW);
        assertThat(fresh.evaluationCursor()).isEqualTo(NOW);

        Instant later = NOW.plusSeconds(600);
        SavedSearch evaluated = fresh.markEvaluated(later);
        assertThat(evaluated.evaluationCursor()).isEqualTo(later);
        assertThat(evaluated.lastEvaluatedAt()).isEqualTo(later);
    }

    @Test
    void deactivate_은_active_false_나머지_보존() {
        SavedSearch saved = SavedSearch.create("user-1", "label", QUERY, CHANNEL, NOW);
        SavedSearch off = saved.deactivate();

        assertThat(off.active()).isFalse();
        assertThat(off.id()).isEqualTo(saved.id());
        assertThat(off.createdAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void label_상한_초과시_예외() {
        String longLabel = "a".repeat(SavedSearch.MAX_LABEL_LENGTH + 1);
        assertThatThrownBy(() -> SavedSearch.create("user-1", longLabel, QUERY, CHANNEL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label 길이");
    }

    @Test
    void ownerId_빈_문자열_예외() {
        assertThatThrownBy(() -> SavedSearch.create("", "label", QUERY, CHANNEL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerId");
    }
}
