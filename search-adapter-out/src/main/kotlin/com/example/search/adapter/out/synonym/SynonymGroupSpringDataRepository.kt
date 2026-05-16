package com.example.search.adapter.out.synonym

import org.springframework.data.jpa.repository.JpaRepository

interface SynonymGroupSpringDataRepository : JpaRepository<SynonymGroupJpaEntity, String> {

    /** 운영 화면 / ES reload 모두 updated_at desc 정렬 — 최근 수정이 위로. */
    fun findAllByOrderByUpdatedAtDesc(): List<SynonymGroupJpaEntity>
}
