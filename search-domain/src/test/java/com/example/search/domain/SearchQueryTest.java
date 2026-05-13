package com.example.search.domain;

import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchQueryTest {

    @Test
    void byKeyword_helper() {
        SearchQuery q = SearchQuery.byKeyword("nike", Page.first(20));
        assertThat(q.hasKeyword()).isTrue();
        assertThat(q.hasFilters()).isFalse();
        assertThat(q.wantsFacets()).isFalse();
    }

    @Test
    void 빈_keyword_는_허용_단_hasKeyword_false() {
        SearchQuery q = SearchQuery.byKeyword("", Page.first(20));
        assertThat(q.hasKeyword()).isFalse();
    }

    @Test
    void filters_에_terms_가_들어가면_hasFilters_true() {
        SearchQuery q = new SearchQuery("nike",
                List.of(new FilterCriterion.Terms("brand", List.of("nike", "adidas"))),
                List.of("brand"), null, Page.first(10));
        assertThat(q.hasFilters()).isTrue();
        assertThat(q.wantsFacets()).isTrue();
    }

    @Test
    void Range_필터_from_to_둘다_null_금지() {
        assertThatThrownBy(() -> new FilterCriterion.Range("price", null, false, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Page_size_상한_초과_금지() {
        assertThatThrownBy(() -> new Page(0, Page.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Page_from_offset_계산() {
        Page p = new Page(2, 20);
        assertThat(p.from()).isEqualTo(40);
    }

    @Test
    void Page_deep_pagination_상한_초과_금지() {
        // (number + 1) * size = (100 + 1) * 100 = 10100 > MAX_WINDOW (10000)
        assertThatThrownBy(() -> new Page(100, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deep pagination");
    }

    @Test
    void Page_deep_pagination_경계_허용() {
        // (number + 1) * size = (99 + 1) * 100 = 10000 == MAX_WINDOW. 허용.
        Page p = new Page(99, 100);
        assertThat(p.from()).isEqualTo(9900);
    }
}
