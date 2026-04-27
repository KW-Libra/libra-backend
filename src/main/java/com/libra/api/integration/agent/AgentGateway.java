package com.libra.api.integration.agent;

import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.util.Map;

public interface AgentGateway {

    Map<String, Object> run(JudgeRunDispatchRequest request, PortfolioSnapshot portfolio);

    Map<String, Object> evaluate(Map<String, Object> payload);
}
