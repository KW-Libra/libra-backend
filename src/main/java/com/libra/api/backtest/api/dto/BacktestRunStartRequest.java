package com.libra.api.backtest.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record BacktestRunStartRequest(
    @Pattern(regexp = "^[A-Za-z0-9_.-]{0,140}$", message = "runId는 영문, 숫자, '.', '_', '-'만 사용할 수 있습니다")
    String runId,
    String model,
    String governancePreset,
    String promptVariant,
    String executionPolicyMode,
    String executionParticipationRate,
    String executionMaxAbsDeltaPct,
    Boolean executionResolveTickerConflicts,
    Boolean issueStateEnabled,
    @Min(1)
    Integer issueStateCooldownObservations,
    LocalDate startDate,
    LocalDate endDate,
    String decisionFrequency,
    @Min(1)
    Integer decisionInterval,
    @Min(1)
    Integer limit,
    Boolean force
) {
}
