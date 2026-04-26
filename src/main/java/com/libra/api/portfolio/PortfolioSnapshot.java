package com.libra.api.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record PortfolioSnapshot(
        @NotNull
        OffsetDateTime generatedAt,
        @NotNull
        List<@Valid PortfolioHolding> holdings,
        Double totalValueKrw,
        @Min(0)
        @Max(1)
        double cashWeight,
        List<String> userPreferences
) {
}
