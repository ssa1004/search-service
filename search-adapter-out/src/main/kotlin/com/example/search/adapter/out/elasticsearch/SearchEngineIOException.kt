package com.example.search.adapter.out.elasticsearch

/**
 * ES 호출 시 IOException 을 도메인 친화적인 RuntimeException 으로 감싼 예외. Resilience4j CB 가
 * 이 타입을 보고 회로를 연다.
 */
class SearchEngineIOException(message: String, cause: Throwable) : RuntimeException(message, cause)
