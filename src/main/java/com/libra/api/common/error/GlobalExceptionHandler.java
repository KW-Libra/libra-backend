package com.libra.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 ProblemDetail 로 통일된 에러 응답.
 * 추가 필드: {@code code} (ErrorCode 이름) + {@code traceId} (MDC).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handle(ApiException ex, HttpServletRequest req) {
        ErrorCode c = ex.getCode();
        log.warn("ApiException code={} traceId={} path={} message={}",
                 c, MDC.get("traceId"), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(c.status())
            .body(problem(c.status().value(), c.name(), ex.getMessage(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Validation failed traceId={} path={} detail={}",
                 MDC.get("traceId"), req.getRequestURI(), detail);
        return ResponseEntity.badRequest()
            .body(problem(400, ErrorCode.VALIDATION_FAILED.name(), detail, req));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest req) {
        String detail = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));
        log.warn("Constraint violation traceId={} path={} detail={}",
                 MDC.get("traceId"), req.getRequestURI(), detail);
        return ResponseEntity.badRequest()
            .body(problem(400, ErrorCode.VALIDATION_FAILED.name(), detail, req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception traceId={} path={}",
                  MDC.get("traceId"), req.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
            .body(problem(500, ErrorCode.INTERNAL_ERROR.name(),
                          ErrorCode.INTERNAL_ERROR.defaultMessage(), req));
    }

    private static ProblemDetail problem(int status, String code, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        pd.setType(URI.create("about:blank"));
        pd.setTitle(code);
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code);
        pd.setProperty("traceId", MDC.get("traceId"));
        return pd;
    }
}
