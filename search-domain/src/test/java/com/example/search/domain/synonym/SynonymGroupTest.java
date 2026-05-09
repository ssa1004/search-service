package com.example.search.domain.synonym;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SynonymGroupTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void create_BIDIRECTIONAL_으로_쉼표_구분_라인_생성() {
        SynonymGroup g = SynonymGroup.create(
                List.of("조던1", "에어조던1", "Air Jordan 1"),
                SynonymDirection.BIDIRECTIONAL, NOW, "operator-1");

        assertThat(g.toElasticsearchRule()).isEqualTo("조던1, 에어조던1, Air Jordan 1");
        assertThat(g.id()).isNotNull();
        assertThat(g.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void create_ONE_WAY_으로_화살표_라인_생성() {
        SynonymGroup g = SynonymGroup.create(
                List.of("AJ1", "에어 조던 1", "Air Jordan 1"),
                SynonymDirection.ONE_WAY, NOW, "operator-1");

        assertThat(g.toElasticsearchRule()).isEqualTo("AJ1 => 에어 조던 1, Air Jordan 1");
    }

    @Test
    void terms_2개_미만이면_예외() {
        assertThatThrownBy(() -> SynonymGroup.create(
                List.of("only-one"), SynonymDirection.BIDIRECTIONAL, NOW, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 2개");
    }

    @Test
    void terms_상한_초과시_예외() {
        List<String> tooMany = java.util.stream.IntStream.range(0, SynonymGroup.MAX_TERMS + 1)
                .mapToObj(i -> "t" + i).toList();
        assertThatThrownBy(() -> SynonymGroup.create(
                tooMany, SynonymDirection.BIDIRECTIONAL, NOW, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("term 수");
    }

    @Test
    void term_안에_쉼표_있으면_예외() {
        assertThatThrownBy(() -> SynonymGroup.create(
                List.of("a, b", "c"), SynonymDirection.BIDIRECTIONAL, NOW, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("구분자");
    }

    @Test
    void term_안에_화살표_있으면_예외() {
        assertThatThrownBy(() -> SynonymGroup.create(
                List.of("a => b", "c"), SynonymDirection.BIDIRECTIONAL, NOW, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("구분자");
    }

    @Test
    void term_길이_상한_초과시_예외() {
        String tooLong = "a".repeat(SynonymGroup.MAX_TERM_LENGTH + 1);
        assertThatThrownBy(() -> SynonymGroup.create(
                List.of(tooLong, "b"), SynonymDirection.BIDIRECTIONAL, NOW, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("term 길이");
    }

    @Test
    void updatedBy_빈_문자열_예외() {
        assertThatThrownBy(() -> SynonymGroup.create(
                List.of("a", "b"), SynonymDirection.BIDIRECTIONAL, NOW, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedBy");
    }

    @Test
    void terms_는_immutable_방어복사() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        SynonymGroup g = SynonymGroup.create(mutable, SynonymDirection.BIDIRECTIONAL, NOW, "op");

        // 원본 변형이 도메인에 영향 없어야 함.
        mutable.add("c");
        assertThat(g.terms()).containsExactly("a", "b");

        // 노출된 list 자체도 immutable.
        assertThatThrownBy(() -> g.terms().add("z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
