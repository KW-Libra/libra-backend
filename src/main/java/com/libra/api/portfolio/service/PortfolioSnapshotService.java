package com.libra.api.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.auth.domain.User;
import com.libra.api.broker.kis.api.dto.KisBalanceResponse;
import com.libra.api.common.correlation.CorrelationIdFilter;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.portfolio.api.dto.PortfolioSnapshotDetailResponse;
import com.libra.api.portfolio.api.dto.PortfolioSnapshotResponse;
import com.libra.api.portfolio.domain.PortfolioSnapshot;
import com.libra.api.portfolio.domain.PortfolioSnapshotRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioSnapshotService {

    private static final int MAX_LIMIT = 100;

    private final PortfolioSnapshotRepository snapshots;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public PortfolioSnapshotService(PortfolioSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public PortfolioSnapshot saveKisBalance(User user, KisBalanceResponse balance) {
        PortfolioSnapshot snapshot = PortfolioSnapshot.fromKisBalance(
            user.getId(),
            balance,
            toJson(balance),
            traceId()
        );
        return snapshots.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshotResponse> list(User user, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return snapshots.findByUserId(user.getId(), page)
            .stream()
            .map(PortfolioSnapshotResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioSnapshotDetailResponse latest(User user) {
        return snapshots.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
            .map(PortfolioSnapshotDetailResponse::from)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PortfolioSnapshotDetailResponse get(User user, UUID id) {
        return snapshots.findByIdAndUserId(id, user.getId())
            .map(PortfolioSnapshotDetailResponse::from)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize portfolio snapshot", e);
        }
    }

    private static String traceId() {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return traceId == null || traceId.isBlank() ? null : traceId;
    }
}
