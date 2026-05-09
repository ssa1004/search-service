package com.example.search.application.synonym.port.in;

import com.example.search.domain.synonym.SynonymGroup;

import java.util.List;

/**
 * 등록된 동의어 그룹 전체 조회 — 운영자 화면 + reload 직전 sanity check 용.
 */
public interface ListSynonymGroupsUseCase {

    List<SynonymGroup> listAll();
}
