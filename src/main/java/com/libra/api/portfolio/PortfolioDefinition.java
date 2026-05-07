package com.libra.api.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;

public record PortfolioDefinition(
        @NotBlank
        String name,
        String description,
        @NotEmpty
        List<@Valid TargetWeight> targetWeights,
        String riskProfile,
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("0.5")
        Double driftThreshold,
        String rebalancingFrequency,
        Boolean thresholdOverridden,
        OffsetDateTime createdAt
) {
}
