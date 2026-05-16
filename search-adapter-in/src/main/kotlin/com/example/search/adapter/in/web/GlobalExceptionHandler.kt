package com.example.search.adapter.`in`.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * REST 공통 예외 매핑.
 *
 * - [IllegalArgumentException] → 400 (도메인 검증 실패 메시지 전달)
 * - [MethodArgumentNotValidException] (Bean Validation) → 400
 * - [HttpMessageNotReadableException] (잘못된 JSON) → 400 — Jackson 내부 메시지는 노출
 *   하지 않음 (스택 / 위치 정보 유출 방지).
 * - [MissingServletRequestParameterException] / [MethodArgumentTypeMismatchException]
 *   (필수 query param 누락 / 타입 불일치) → 400 — 파라미터 이름만 노출.
 * - 그 외 RuntimeException → 500 — 메시지 노출 안 함 (운영 정보 / stacktrace 유출 방지).
 *
 * 모든 5xx 응답에는 본문에 내부 메시지를 담지 않는다 — OWASP API3 (sensitive info leakage) /
 * API8 (security misconfig) 의 일반 권고.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "BAD_REQUEST", "message" to e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any?>> {
        val message = e.bindingResult.fieldErrors
            .firstOrNull()
            ?.let { "${it.field} ${it.defaultMessage}" }
            ?: "validation failed"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "VALIDATION_FAILED", "message" to message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<Map<String, Any?>> {
        // 요청 body 가 malformed JSON 또는 타입 변환 실패. 원본 메시지는 Jackson 내부 위치 / 필드
        // 정보를 포함할 수 있어 응답으로 노출하지 않는다.
        log.debug("요청 body 읽기 실패: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "error" to "MALFORMED_REQUEST",
                    "message" to "요청 본문이 올바른 JSON 이 아니거나 필드 타입이 맞지 않음"
                )
            )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "error" to "MISSING_PARAMETER",
                    "message" to "필수 파라미터 누락: ${e.parameterName}"
                )
            )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, Any?>> =
        // 예: /admin/analytics/latency?from=not-a-date — Instant 파싱 실패.
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "error" to "INVALID_PARAMETER",
                    "message" to "파라미터 타입 불일치: ${e.name}"
                )
            )

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: RuntimeException): ResponseEntity<Map<String, Any?>> {
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "INTERNAL_ERROR"))
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
