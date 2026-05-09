package com.example.search.adapter.out;

import com.example.search.adapter.out.elasticsearch.LevenshteinDistance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevenshteinDistanceTest {

    @Test
    void identical_은_0() {
        assertThat(LevenshteinDistance.compute("nike", "nike")).isZero();
    }

    @Test
    void 빈_문자열은_상대_길이() {
        assertThat(LevenshteinDistance.compute("", "abc")).isEqualTo(3);
        assertThat(LevenshteinDistance.compute("abc", "")).isEqualTo(3);
    }

    @Test
    void 한_글자_치환_은_1() {
        assertThat(LevenshteinDistance.compute("nike", "nikr")).isEqualTo(1);
    }

    @Test
    void 한_글자_삽입_은_1() {
        assertThat(LevenshteinDistance.compute("nik", "nike")).isEqualTo(1);
    }

    @Test
    void null_은_빈_문자열로_취급() {
        assertThat(LevenshteinDistance.compute(null, "abc")).isEqualTo(3);
        assertThat(LevenshteinDistance.compute(null, null)).isZero();
    }
}
