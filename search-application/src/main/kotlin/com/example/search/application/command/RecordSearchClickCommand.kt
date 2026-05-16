package com.example.search.application.command

import com.example.search.domain.product.ProductId

/**
 * 검색 결과 클릭 기록 명령. boost rule 학습을 위한 시그널.
 */
@JvmRecord
data class RecordSearchClickCommand(
    val searchId: String,
    val userId: String?,
    val productId: ProductId,
    val keyword: String,
    val rank: Int
) {
    init {
        if (rank < 1) {
            throw IllegalArgumentException("rank 1 이상: $rank")
        }
    }
}
