package com.libra.api.broker.kis.api;

import com.libra.api.auth.domain.User;
import com.libra.api.broker.kis.api.dto.KisOrderAuditResponse;
import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderResponse;
import com.libra.api.broker.kis.domain.KisOrderAudit;
import com.libra.api.broker.kis.service.KisOrderAuditService;
import com.libra.api.broker.kis.service.KisOrderClient;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/broker/kis/orders")
@Tag(name = "KIS Orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KisOrderController {

    private static final Logger log = LoggerFactory.getLogger(KisOrderController.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final KisOrderClient orderClient;
    private final KisOrderAuditService auditService;

    public KisOrderController(KisOrderClient orderClient, KisOrderAuditService auditService) {
        this.orderClient = orderClient;
        this.auditService = auditService;
    }

    @Operation(summary = "Place a KIS domestic cash stock order")
    @PostMapping("/cash")
    public KisOrderResponse cashOrder(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody @Valid KisOrderRequest request
    ) {
        User principal = requireUser(user);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<KisOrderResponse> previousResponse = auditService.completedResponseForIdempotencyKey(
            principal,
            normalizedIdempotencyKey
        );
        if (previousResponse.isPresent()) {
            return previousResponse.get();
        }

        KisOrderAudit audit = auditService.recordRequested(principal, request, normalizedIdempotencyKey);
        try {
            KisOrderResponse response = orderClient.placeCashOrder(request).withAuditId(audit.getId());
            try {
                auditService.markCompleted(audit.getId(), response);
            } catch (RuntimeException e) {
                log.error("Failed to complete KIS order audit id={}", audit.getId(), e);
            }
            return response;
        } catch (ApiException e) {
            tryMarkApiException(audit.getId(), e);
            throw e;
        } catch (RuntimeException e) {
            tryMarkFailed(audit.getId(), e);
            throw e;
        }
    }

    @Operation(summary = "List current user's KIS order audit logs")
    @GetMapping("/audits")
    public List<KisOrderAuditResponse> audits(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return auditService.list(requireUser(user), limit);
    }

    @Operation(summary = "Get a KIS order audit log")
    @GetMapping("/audits/{id}")
    public KisOrderAuditResponse audit(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @PathVariable UUID id
    ) {
        return auditService.get(requireUser(user), id);
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return user;
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH || !normalized.matches("^[A-Za-z0-9._:-]+$")) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "Idempotency-Key는 80자 이하의 영문, 숫자, '.', '_', ':', '-'만 사용할 수 있습니다"
            );
        }
        return normalized;
    }

    private void tryMarkApiException(UUID auditId, ApiException e) {
        try {
            auditService.markApiException(auditId, e);
        } catch (RuntimeException auditError) {
            log.error("Failed to mark KIS order audit exception id={}", auditId, auditError);
        }
    }

    private void tryMarkFailed(UUID auditId, RuntimeException e) {
        try {
            auditService.markFailed(auditId, e);
        } catch (RuntimeException auditError) {
            log.error("Failed to mark KIS order audit failure id={}", auditId, auditError);
        }
    }
}
