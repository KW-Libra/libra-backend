package com.libra.api.broker.kis.api.dto;

import com.libra.api.broker.kis.config.KisProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record KisCredentialUpsertRequest(
    @NotNull KisProperties.Environment environment,
    boolean tradingEnabled,
    @NotBlank String appKey,
    @NotBlank String appSecret,
    @NotBlank
    @Pattern(regexp = "^[0-9]{8,12}$", message = "accountNumber must be 8-12 digits")
    String accountNumber,
    @Pattern(regexp = "^[0-9]{2}$", message = "accountProductCode must be 2 digits")
    String accountProductCode,
    String htsId
) {

    public KisCredentialUpsertRequest {
        if (appKey != null) {
            appKey = appKey.trim();
        }
        if (appSecret != null) {
            appSecret = appSecret.trim();
        }
        if (accountNumber != null) {
            accountNumber = accountNumber.trim();
        }
        if (accountProductCode == null || accountProductCode.isBlank()) {
            accountProductCode = "01";
        } else {
            accountProductCode = accountProductCode.trim();
        }
        if (htsId != null) {
            htsId = htsId.trim();
        }
    }
}
