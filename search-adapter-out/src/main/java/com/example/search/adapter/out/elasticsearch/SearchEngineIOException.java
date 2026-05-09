package com.example.search.adapter.out.elasticsearch;

/**
 * ES 호출 시 IOException 을 도메인 친화적인 RuntimeException 으로 감싼 예외. Resilience4j CB 가
 * 이 타입을 보고 회로를 연다.
 */
public class SearchEngineIOException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SearchEngineIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
