package com.example.search.application.command;

import java.util.Objects;

/**
 * reindex 결과 — 어느 물리 인덱스로 swap 되었고 몇 건이 옮겨졌는지.
 *
 * <p>운영자가 결과를 확인할 수 있도록 새 물리 이름과 source/target 의 doc count 를 같이 반환한다.
 * 일치하지 않으면 alias swap 을 진행하지 않고 운영자가 다시 봐야 한다.</p>
 */
public record ReindexResult(
        String newPhysicalName,
        long sourceDocCount,
        long targetDocCount,
        boolean swapped
) {

    public ReindexResult {
        Objects.requireNonNull(newPhysicalName, "newPhysicalName");
        if (sourceDocCount < 0 || targetDocCount < 0) {
            throw new IllegalArgumentException("doc count 음수 불가");
        }
    }

    public boolean countsMatch() {
        return sourceDocCount == targetDocCount;
    }
}
