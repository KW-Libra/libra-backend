package com.libra.api.integration.kis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KisCredentialRequest(
        @Pattern(regexp = "real|demo", message = "environment must be real or demo")
        String environment,
        @NotBlank
        String appKey,
        @NotBlank
        String appSecret,
        @NotBlank
        String accountNo,
        String productCode,
        String userAgent
) {
}
