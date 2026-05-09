package com.example.search.adapter.out.elasticsearch;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * classpath 의 ES 인덱스 mapping JSON 파일을 읽는 helper. bootstrap 모듈이 실제 파일을 둔다
 * (resources/elasticsearch/products-mapping.json).
 *
 * <p>매번 IO 가 일어나지 않도록 한 번 읽고 캐싱.</p>
 */
@Component
public class IndexMappingResource {

    private static final String MAPPING_PATH = "classpath:elasticsearch/products-mapping.json";

    private final ResourceLoader resourceLoader;
    private volatile String cached;

    public IndexMappingResource(ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader);
    }

    public String read() {
        String c = cached;
        if (c != null) return c;
        try {
            Resource resource = resourceLoader.getResource(MAPPING_PATH);
            if (!resource.exists()) {
                throw new IllegalStateException("mapping JSON 파일 없음: " + MAPPING_PATH);
            }
            try (var in = resource.getInputStream()) {
                cached = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return cached;
            }
        } catch (IOException e) {
            throw new IllegalStateException("mapping JSON 읽기 실패: " + MAPPING_PATH, e);
        }
    }
}
