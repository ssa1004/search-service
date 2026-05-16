package com.example.search.adapter.out.elasticsearch

import com.example.search.application.port.out.SearchIndexProperties

/**
 * 단위 테스트용 / dev 메모리 실행 시 사용되는 정적 properties — bootstrap 의 @ConfigurationProperties
 * 가 빈으로 등록되기 전에 in-memory adapter 가 필요한 경우 fallback.
 *
 * 운영에서는 bootstrap 의 SearchProperties (Configuration Properties) 가 등록되어 이 클래스가
 * 비활성.
 */
class StaticSearchIndexProperties(
    private val alias: String,
    private val reindexBatchSize: Int
) : SearchIndexProperties {

    override fun alias(): String = alias

    override fun reindexBatchSize(): Int = reindexBatchSize

    companion object {
        @JvmStatic
        fun defaults(): StaticSearchIndexProperties = StaticSearchIndexProperties("products", 500)
    }
}
