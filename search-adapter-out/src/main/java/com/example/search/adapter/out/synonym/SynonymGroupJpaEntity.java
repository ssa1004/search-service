package com.example.search.adapter.out.synonym;

import com.example.search.domain.synonym.SynonymDirection;
import com.example.search.domain.synonym.SynonymGroup;
import com.example.search.domain.synonym.SynonymGroupId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 동의어 그룹 JPA 매핑.
 *
 * <p>{@code terms} 는 H2 / Postgres 양쪽 호환을 위해 단일 TEXT 컬럼에 {@code "\n"} (줄바꿈) 으로
 * 직렬화. 도메인 {@code SynonymGroup} 의 term validation 이 줄바꿈 / 쉼표 / 백슬래시 / 화살표를
 * 모두 막아주므로 한 term 이 split 되어 두 term 으로 풀리는 round-trip 사고가 막혀 있다. 별도 자식
 * 테이블을 두는 대안은 reload 시 N+1 query 가 부담스럽고, 그룹 수 자체가 수백 단위라 단일 컬럼이면
 * 충분.</p>
 */
@Entity
@Table(name = "synonym_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SynonymGroupJpaEntity {

    private static final String TERM_DELIMITER = "\n";

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "terms", nullable = false, columnDefinition = "TEXT")
    private String termsSerialized;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private SynonymDirection direction;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    public static SynonymGroupJpaEntity from(SynonymGroup g) {
        SynonymGroupJpaEntity e = new SynonymGroupJpaEntity();
        e.id = g.id().value();
        e.termsSerialized = String.join(TERM_DELIMITER, g.terms());
        e.direction = g.direction();
        e.updatedAt = g.updatedAt();
        e.updatedBy = g.updatedBy();
        return e;
    }

    public SynonymGroup toDomain() {
        List<String> terms = Arrays.asList(termsSerialized.split(TERM_DELIMITER, -1));
        return new SynonymGroup(SynonymGroupId.of(id), terms, direction, updatedAt, updatedBy);
    }
}
