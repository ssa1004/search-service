package com.example.search.application.command;

import java.util.Objects;

/**
 * 전체 reindex 명령. alias-based zero-downtime 패턴 (ADR-0005).
 *
 * <p>{@code suffix} 가 새 물리 인덱스 이름의 꼬리에 붙는다 (예: "v202605"). 호출자가 timestamp 등으로
 * 유일성을 보장.</p>
 *
 * <p>{@code dropOld} 가 true 면 alias swap 직후 구 인덱스를 즉시 삭제 — 운영에서는 보통 false 로 두고
 * delay 후 별도 운영 명령으로 정리한다 (rollback 가능 시간 확보).</p>
 */
public record ReindexAllCommand(String suffix, boolean dropOld) {

    public ReindexAllCommand {
        Objects.requireNonNull(suffix, "suffix");
        if (suffix.isBlank()) {
            throw new IllegalArgumentException("suffix 빈 값 불가");
        }
    }
}
