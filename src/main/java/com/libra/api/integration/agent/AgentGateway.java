package com.libra.api.integration.agent;

import com.libra.api.decision.PriorReflection;
import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.util.List;
import java.util.Map;

public interface AgentGateway {

    Map<String, Object> run(
            JudgeRunDispatchRequest request,
            PortfolioSnapshot portfolio,
            List<PriorReflection> priorReflections
    );

    default Map<String, Object> run(JudgeRunDispatchRequest request, PortfolioSnapshot portfolio) {
        return run(request, portfolio, List.of());
    }

    Map<String, Object> evaluate(Map<String, Object> payload);
}
