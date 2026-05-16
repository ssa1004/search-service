package com.example.search.application.synonym.port.out

import com.example.search.domain.synonym.SynonymGroup

/**
 * ES 인덱스에 synonym 규칙을 적용하는 port.
 *
 * 운영 ES 어댑터는 두 단계 작업:
 * 1. 인덱스 close (live update 가 settings 변경 시 거부됨)
 * 2. `_settings` 로 synonym filter 갱신
 * 3. 인덱스 open
 *
 * close → open 사이 ~수 초 동안 검색이 차단되므로 운영 시간 외 호출 권장. 무중단이 필수면
 * 새 물리 인덱스를 만들고 alias swap 하는 ADR-0005 흐름을 활용.
 *
 * memory / 단위 테스트 모드에서는 no-op 어댑터가 등록 — 도메인 로직만 검증.
 */
interface SynonymIndexUpdaterPort {

    /**
     * 전달된 그룹 전체로 ES synonym graph filter 를 덮어쓴다 (replace, not append). 적용된 그룹
     * 수를 반환 — 호출자가 성공 응답에 포함.
     */
    fun reload(groups: List<SynonymGroup>): Int
}
