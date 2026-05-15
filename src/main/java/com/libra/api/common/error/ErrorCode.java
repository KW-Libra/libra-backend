package com.libra.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 에러 카탈로그. 클라이언트는 {@code code} 문자열로 분기.
 * Vue 쪽 i18n 도 이 enum 이름을 키로 사용.
 */
public enum ErrorCode {
    // Auth
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    AUTH_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),

    // Common
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 검증에 실패했습니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 오류가 발생했습니다"),

    // Downstream (libra-agent)
    AGENT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "에이전트 서비스에 접근할 수 없습니다"),
    AGENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "에이전트 응답이 지연됩니다"),

    // Broker / market data
    KIS_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "한국투자증권 API 설정이 필요합니다"),
    KIS_TRADING_DISABLED(HttpStatus.FORBIDDEN, "한국투자증권 주문 기능이 비활성화되어 있습니다"),
    KIS_ORDER_REJECTED(HttpStatus.BAD_REQUEST, "한국투자증권 주문 요청이 안전장치에 의해 거부되었습니다"),
    KIS_ORDER_DUPLICATE(HttpStatus.CONFLICT, "이미 처리 중이거나 처리된 주문 요청입니다"),
    KIS_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "한국투자증권 API 응답을 처리할 수 없습니다");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
