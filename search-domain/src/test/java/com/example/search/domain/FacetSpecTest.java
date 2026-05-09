package com.example.search.domain;

import com.example.search.domain.facet.FacetResult;
import com.example.search.domain.facet.FacetSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacetSpecTest {

    @Test
    void Terms_size_상한_초과_거부() {
        assertThatThrownBy(() -> new FacetSpec.Terms("brand", "brand",
                FacetSpec.Terms.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Range_빈_bucket_거부() {
        assertThatThrownBy(() -> new FacetSpec.Range("price", "priceWon", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Range_Bucket_from_to_둘다_null_거부() {
        assertThatThrownBy(() -> new FacetSpec.Range.Bucket("k", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void FacetResult_count_음수_거부() {
        assertThatThrownBy(() -> new FacetResult.Bucket("nike", -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void FacetResult_정상_조립() {
        FacetResult r = new FacetResult("brand", List.of(
                new FacetResult.Bucket("nike", 120L),
                new FacetResult.Bucket("adidas", 80L)
        ));
        assertThat(r.buckets()).hasSize(2);
        assertThat(r.buckets().get(0).count()).isEqualTo(120L);
    }
}
