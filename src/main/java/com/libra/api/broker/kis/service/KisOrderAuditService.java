package com.libra.api.broker.kis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.auth.domain.User;
import com.libra.api.broker.kis.api.dto.KisOrderAuditResponse;
import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderResponse;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.broker.kis.domain.KisOrderAudit;
import com.libra.api.broker.kis.domain.KisOrderAuditRepository;
import com.libra.api.common.correlation.CorrelationIdFilter;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KisOrderAuditService {

    private static final int MAX_LIMIT = 100;

    private final KisProperties properties;
    private final KisOrderAuditRepository audits;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public KisOrderAuditService(
        KisProperties properties,
        KisOrderAuditRepository audits
    ) {
        this.properties = properties;
        this.audits = audits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KisOrderAudit recordRequested(User user, KisOrderRequest request) {
        KisOrderAudit audit = KisOrderAudit.requested(
            user.getId(),
            properties.environment().name().toLowerCase(),
            properties.tradingEnabled(),
            request,
            toJson(request),
            MDC.get(CorrelationIdFilter.MDC_KEY)
        );
        return audits.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KisOrderAudit markCompleted(UUID id, KisOrderResponse response) {
        KisOrderAudit audit = getAudit(id);
        audit.markCompleted(response, toJson(response));
        return audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markApiException(UUID id, ApiException ex) {
        KisOrderAudit audit = getAudit(id);
        String code = ex.getCode().name();
        String responseJson = errorJson(code, ex.getMessage());
        if (ex.getCode() == ErrorCode.KIS_UNAVAILABLE || ex.getCode() == ErrorCode.INTERNAL_ERROR) {
            audit.markFailed(code, ex.getMessage(), responseJson);
            return;
        }
        audit.markRejected(code, ex.getMessage(), responseJson);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, Exception ex) {
        KisOrderAudit audit = getAudit(id);
        audit.markFailed(
            ErrorCode.INTERNAL_ERROR.name(),
            ex.getMessage(),
            errorJson(ErrorCode.INTERNAL_ERROR.name(), ex.getMessage())
        );
    }

    @Transactional(readOnly = true)
    public List<KisOrderAuditResponse> list(User user, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return audits.findByUserId(user.getId(), page)
            .stream()
            .map(KisOrderAuditResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public KisOrderAuditResponse get(User user, UUID id) {
        return audits.findByIdAndUserId(id, user.getId())
            .map(KisOrderAuditResponse::from)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private KisOrderAudit getAudit(UUID id) {
        return audits.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String errorJson(String code, String message) {
        return toJson(Map.of(
            "code", code,
            "message", message == null ? "" : message
        ));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize KIS order audit payload", e);
        }
    }
}
