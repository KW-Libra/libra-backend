package com.libra.api.portfolio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TargetWeight(
        @NotBlank
        String ticker,
        @NotBlank
        String companyName,
        @Min(0)
        @Max(1)
        double weight,
        String market
) {
}
