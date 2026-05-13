package com.libra.api.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record RunStartRequest(

    @NotBlank
    @Size(max = 8_000)
    String query,

    Map<String, Object> portfolio,

    @Size(max = 40)
    String trigger,

    @Size(max = 120)
    String thread_id,

    boolean approval_required

) {
    public RunStartRequest {
        if (trigger == null || trigger.isBlank()) {
            trigger = "user_request";
        }
    }
}
