package com.example.search.adapter.in;

import com.example.search.adapter.in.web.SearchRequestMapper;
import com.example.search.adapter.in.web.dto.SearchDtos;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.query.FilterCriterion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestMapperTest {

    @Test
    void term_filter_매핑() {
        SearchDtos.SearchRequest req = new SearchDtos.SearchRequest(
                "nike",
                List.of(new SearchDtos.FilterDto("brand", "term", "nike",
                        null, null, null, null, null)),
                List.of(),
                null,
                0, 20);
        SearchProductCommand cmd = SearchRequestMapper.toCommand(req);
        assertThat(cmd.query().filters()).hasSize(1);
        assertThat(cmd.query().filters().get(0)).isInstanceOf(FilterCriterion.Term.class);
    }

    @Test
    void range_filter_inclusive_default() {
        SearchDtos.SearchRequest req = new SearchDtos.SearchRequest(
                "",
                List.of(new SearchDtos.FilterDto("priceWon", "range", null,
                        null, 100_000L, null, 200_000L, null)),
                List.of(),
                null,
                0, 20);
        SearchProductCommand cmd = SearchRequestMapper.toCommand(req);
        FilterCriterion.Range r = (FilterCriterion.Range) cmd.query().filters().get(0);
        assertThat(r.fromInclusive()).isTrue();   // null → default true
        assertThat(r.toInclusive()).isFalse();    // null → default false
    }

    @Test
    void terms_facet_매핑() {
        SearchDtos.SearchRequest req = new SearchDtos.SearchRequest(
                "nike",
                List.of(),
                List.of(new SearchDtos.FacetSpecDto("by-brand", "brand", "terms", 5, null)),
                null,
                0, 20);
        SearchProductCommand cmd = SearchRequestMapper.toCommand(req);
        assertThat(cmd.facetSpecs()).hasSize(1);
        assertThat(cmd.facetSpecs().get(0)).isInstanceOf(FacetSpec.Terms.class);
        FacetSpec.Terms t = (FacetSpec.Terms) cmd.facetSpecs().get(0);
        assertThat(t.size()).isEqualTo(5);
    }

    @Test
    void 알_수_없는_filter_op_은_거부() {
        SearchDtos.SearchRequest req = new SearchDtos.SearchRequest(
                "",
                List.of(new SearchDtos.FilterDto("brand", "weird", "x",
                        null, null, null, null, null)),
                List.of(),
                null,
                0, 20);
        assertThatThrownBy(() -> SearchRequestMapper.toCommand(req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
