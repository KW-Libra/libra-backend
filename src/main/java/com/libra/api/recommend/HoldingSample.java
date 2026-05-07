package com.libra.api.recommend;

public record HoldingSample(
        String ticker,
        String companyName,
        double targetWeight,
        String note
) {
}
