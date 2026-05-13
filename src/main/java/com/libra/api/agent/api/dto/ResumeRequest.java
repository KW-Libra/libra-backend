package com.libra.api.agent.api.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record ResumeRequest(

    boolean approved,

    @Size(max = 40)
    String decision,

    Integer option_index,

    Map<String, Double> override_plan,

    @Size(max = 2_000)
    String note

) {}
