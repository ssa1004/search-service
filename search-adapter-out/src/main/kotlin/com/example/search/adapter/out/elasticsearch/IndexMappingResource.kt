package com.example.search.adapter.out.elasticsearch

import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * classpath 의 ES 인덱스 mapping JSON 파일을 읽는 helper. bootstrap 모듈이 실제 파일을 둔다
 * (resources/elasticsearch/products-mapping.json).
 *
 * 매번 IO 가 일어나지 않도록 한 번 읽고 캐싱.
 */
@Component
class IndexMappingResource(
    private val resourceLoader: ResourceLoader
) {

    @Volatile
    private var cached: String? = null

    fun read(): String {
        cached?.let { return it }
        try {
            val resource = resourceLoader.getResource(MAPPING_PATH)
            if (!resource.exists()) {
                throw IllegalStateException("mapping JSON 파일 없음: $MAPPING_PATH")
            }
            resource.inputStream.use { input ->
                val read = String(input.readAllBytes(), StandardCharsets.UTF_8)
                cached = read
                return read
            }
        } catch (e: IOException) {
            throw IllegalStateException("mapping JSON 읽기 실패: $MAPPING_PATH", e)
        }
    }

    companion object {
        private const val MAPPING_PATH: String = "classpath:elasticsearch/products-mapping.json"
    }
}
