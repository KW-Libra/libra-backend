package com.libra.api.judge;

import com.libra.api.portfolio.PortfolioSnapshot;
import com.libra.api.portfolio.PortfolioDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record JudgeRunDispatchRequest(
        @NotBlank
        String query,
        @Valid
        PortfolioSnapshot portfolio,
        Map<String, String> knowledgeSources,
        @Pattern(regexp = "shallow|medium|deep", message = "depth must be shallow, medium, or deep")
        String depth,
        @Pattern(regexp = "pull|push", message = "trigger must be pull or push")
        String trigger,
        Map<String, Object> triggerEvent,
        @Min(1)
        Integer deadlineSeconds,
        String threadId,
        Boolean enableHumanInterrupts,
        Boolean allowIngestRefresh,
        Map<String, Object> ingestRefresh,
        @Valid
        PortfolioDefinition portfolioDefinition
) {
}
