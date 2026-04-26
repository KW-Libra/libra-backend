package com.libra.api.portfolio;

import jakarta.validation.constraints.Pattern;
import java.util.List;

public record KisSyncRequest(
        @Pattern(regexp = "real|demo", message = "environment must be real or demo")
        String environment,
        String accountNo,
        String productCode,
        List<String> userPreferences
) {
}
