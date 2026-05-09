package com.example.search.adapter.out.synonym;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SynonymGroupSpringDataRepository extends JpaRepository<SynonymGroupJpaEntity, String> {

    /** 운영 화면 / ES reload 모두 updated_at desc 정렬 — 최근 수정이 위로. */
    List<SynonymGroupJpaEntity> findAllByOrderByUpdatedAtDesc();
}
