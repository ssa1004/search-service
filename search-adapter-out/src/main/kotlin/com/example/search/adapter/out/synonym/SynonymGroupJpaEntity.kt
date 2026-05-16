package com.example.search.adapter.out.synonym

import com.example.search.domain.synonym.SynonymDirection
import com.example.search.domain.synonym.SynonymGroup
import com.example.search.domain.synonym.SynonymGroupId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 동의어 그룹 JPA 매핑.
 *
 * `terms` 는 H2 / Postgres 양쪽 호환을 위해 단일 TEXT 컬럼에 `"\n"` (줄바꿈) 으로
 * 직렬화. 도메인 [SynonymGroup] 의 term validation 이 줄바꿈 / 쉼표 / 백슬래시 / 화살표를
 * 모두 막아주므로 한 term 이 split 되어 두 term 으로 풀리는 round-trip 사고가 막혀 있다. 별도 자식
 * 테이블을 두는 대안은 reload 시 N+1 query 가 부담스럽고, 그룹 수 자체가 수백 단위라 단일 컬럼이면
 * 충분.
 */
@Entity
@Table(name = "synonym_groups")
class SynonymGroupJpaEntity protected constructor() {

    @Id
    @Column(name = "id", length = 64)
    var id: String = ""
        private set

    @Column(name = "terms", nullable = false, columnDefinition = "TEXT")
    var termsSerialized: String = ""
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    var direction: SynonymDirection = SynonymDirection.BIDIRECTIONAL
        private set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
        private set

    @Column(name = "updated_by", nullable = false, length = 64)
    var updatedBy: String = ""
        private set

    fun toDomain(): SynonymGroup {
        // Java 의 split(delimiter, -1) 동작 — trailing empty 도 보존. Kotlin 의 split 은 인자
        // 없이 호출하면 limit=0 (== Java 와 동일하게 trailing 보존). 도메인 validation 이 빈 term
        // 을 거부하므로 round-trip 시 손실이 없도록 명시.
        val terms = termsSerialized.split(TERM_DELIMITER)
        return SynonymGroup(SynonymGroupId.of(id), terms, direction, updatedAt, updatedBy)
    }

    companion object {
        private const val TERM_DELIMITER: String = "\n"

        @JvmStatic
        fun from(g: SynonymGroup): SynonymGroupJpaEntity {
            val e = SynonymGroupJpaEntity()
            e.id = g.id.value
            e.termsSerialized = g.terms.joinToString(TERM_DELIMITER)
            e.direction = g.direction
            e.updatedAt = g.updatedAt
            e.updatedBy = g.updatedBy
            return e
        }
    }
}
