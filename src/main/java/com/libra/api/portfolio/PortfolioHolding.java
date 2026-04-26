package com.libra.api.portfolio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PortfolioHolding(
        @NotBlank
        String ticker,
        @NotBlank
        String companyName,
        @Min(0)
        @Max(1)
        double weight,
        List<String> aliases,
        Double shares,
        Double lastPrice
) {
}
