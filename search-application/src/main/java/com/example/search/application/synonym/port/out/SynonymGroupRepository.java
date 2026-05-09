package com.example.search.application.synonym.port.out;

import com.example.search.domain.synonym.SynonymGroup;
import com.example.search.domain.synonym.SynonymGroupId;

import java.util.List;
import java.util.Optional;

/**
 * SynonymGroup 영속화 port. ES 와 별개로 RDB 를 진실값으로 둔다 — 인덱스 재생성 / reindex 시
 * RDB 의 모든 그룹을 다시 ES settings 에 밀어 넣을 수 있도록.
 */
public interface SynonymGroupRepository {

    SynonymGroup save(SynonymGroup group);

    Optional<SynonymGroup> findById(SynonymGroupId id);

    /**
     * 전체 목록 — 운영자 화면 + ES settings 적용 시 모두 사용. 동의어 그룹은 보통 수십~수백 단위로
     * 페이지가 필요 없다.
     */
    List<SynonymGroup> findAll();

    void deleteById(SynonymGroupId id);

    long count();
}
