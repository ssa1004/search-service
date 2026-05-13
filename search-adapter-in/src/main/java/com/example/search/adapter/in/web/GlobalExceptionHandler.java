package com.example.search.adapter.in.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * REST 공통 예외 매핑.
 *
 * <ul>
 *   <li>{@code IllegalArgumentException} → 400 (도메인 검증 실패 메시지 전달)</li>
 *   <li>{@code MethodArgumentNotValidException} (Bean Validation) → 400</li>
 *   <li>{@code HttpMessageNotReadableException} (잘못된 JSON) → 400 — Jackson 내부 메시지는 노출
 *       하지 않음 (스택 / 위치 정보 유출 방지).</li>
 *   <li>{@code MissingServletRequestParameterException} / {@code MethodArgumentTypeMismatchException}
 *       (필수 query param 누락 / 타입 불일치) → 400 — 파라미터 이름만 노출.</li>
 *   <li>그 외 RuntimeException → 500 — 메시지 노출 안 함 (운영 정보 / stacktrace 유출 방지).</li>
 * </ul>
 *
 * <p>모든 5xx 응답에는 본문에 내부 메시지를 담지 않는다 — OWASP API3 (sensitive info leakage) /
 * API8 (security misconfig) 의 일반 권고.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION_FAILED", "message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        // 요청 body 가 malformed JSON 또는 타입 변환 실패. 원본 메시지는 Jackson 내부 위치 / 필드
        // 정보를 포함할 수 있어 응답으로 노출하지 않는다.
        log.debug("요청 body 읽기 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "MALFORMED_REQUEST",
                        "message", "요청 본문이 올바른 JSON 이 아니거나 필드 타입이 맞지 않음"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "MISSING_PARAMETER",
                        "message", "필수 파라미터 누락: " + e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        // 예: /admin/analytics/latency?from=not-a-date — Instant 파싱 실패.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_PARAMETER",
                        "message", "파라미터 타입 불일치: " + e.getName()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR"));
    }
}
