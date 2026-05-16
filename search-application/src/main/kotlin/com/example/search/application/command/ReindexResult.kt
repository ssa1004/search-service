package com.example.search.application.command

/**
 * reindex 결과 — 어느 물리 인덱스로 swap 되었고 몇 건이 옮겨졌는지.
 *
 * 운영자가 결과를 확인할 수 있도록 새 물리 이름과 source/target 의 doc count 를 같이 반환한다.
 * 일치하지 않으면 alias swap 을 진행하지 않고 운영자가 다시 봐야 한다.
 */
@JvmRecord
data class ReindexResult(
    val newPhysicalName: String,
    val sourceDocCount: Long,
    val targetDocCount: Long,
    val swapped: Boolean
) {
    init {
        if (sourceDocCount < 0 || targetDocCount < 0) {
            throw IllegalArgumentException("doc count 음수 불가")
        }
    }

    fun countsMatch(): Boolean = sourceDocCount == targetDocCount
}
