package com.libra.api.decision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DecisionExecutionRequest(
        @NotEmpty
        List<@Valid DecisionExecutionOrderItem> orders,
        Boolean dryRun
) {
    public boolean isDryRun() {
        return Boolean.TRUE.equals(dryRun);
    }
}
