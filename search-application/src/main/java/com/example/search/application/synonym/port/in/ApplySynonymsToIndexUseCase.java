package com.example.search.application.synonym.port.in;

/**
 * 현재 RDB 의 모든 동의어 그룹을 ES 인덱스 settings 에 reload — 운영자가 명시 호출.
 *
 * <p>등록 / 삭제 직후가 아니라 운영자 판단 시점에 호출하는 이유는 ADR-0017 의 "묶어서 적용" 결정
 * 참고. ES 는 settings 변경 시 close → update → open 단계가 필요해 매 변경마다 호출하면 검색이
 * 중단되는 시간이 길어진다.</p>
 *
 * <p>반환은 적용된 그룹 수 — 운영자 화면이 "n개 적용됨" 으로 표시.</p>
 */
public interface ApplySynonymsToIndexUseCase {

    int apply();
}
