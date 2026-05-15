package com.libra.api.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;

public record RunStartRequest(

    @NotBlank
    @Size(max = 8_000)
    String query,

    Map<String, Object> portfolio,

    Map<String, Object> knowledge_base,

    Map<String, Object> knowledge_sources,

    Map<String, Object> portfolio_definition,

    Map<String, Object> trigger_event,

    Map<String, Object> governance_v1,

    @Pattern(regexp = "^(pull|push)$", message = "trigger must be pull or push")
    String trigger,

    @Pattern(regexp = "^(shallow|medium|deep)$", message = "depth must be shallow, medium, or deep")
    String depth,

    @Positive
    Integer deadline_seconds,

    @Size(max = 120)
    String thread_id,

    Boolean approval_required,

    Boolean enable_human_interrupts

) {
    public RunStartRequest {
        trigger = normalizeTrigger(trigger);
        depth = normalizeDepth(depth);
        if (approval_required == null) {
            approval_required = false;
        }
        if (enable_human_interrupts == null) {
            enable_human_interrupts = approval_required;
        }
    }

    private static String normalizeTrigger(String value) {
        if (value == null || value.isBlank()) {
            return "pull";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("user_request".equals(normalized)) {
            return "pull";
        }
        return normalized;
    }

    private static String normalizeDepth(String value) {
        if (value == null || value.isBlank()) {
            return "medium";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
