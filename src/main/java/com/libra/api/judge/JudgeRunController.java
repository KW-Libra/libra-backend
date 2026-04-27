package com.libra.api.judge;

import com.libra.api.decision.DecisionRunRecord;
import com.libra.api.decision.DecisionRunService;
import com.libra.api.integration.agent.AgentGateway;
import com.libra.api.portfolio.PortfolioSnapshot;
import com.libra.api.portfolio.PortfolioStateService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/judge-runs")
public class JudgeRunController {

    private final PortfolioStateService portfolioStateService;
    private final AgentGateway agentGateway;
    private final DecisionRunService decisionRunService;

    public JudgeRunController(
            PortfolioStateService portfolioStateService,
            AgentGateway agentGateway,
            DecisionRunService decisionRunService
    ) {
        this.portfolioStateService = portfolioStateService;
        this.agentGateway = agentGateway;
        this.decisionRunService = decisionRunService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> run(@RequestBody @Valid JudgeRunDispatchRequest request) {
        PortfolioSnapshot portfolio = request.portfolio();
        if (portfolio == null) {
            portfolio = portfolioStateService.getCurrent()
                    .orElseThrow(() -> new IllegalStateException("No portfolio snapshot is stored yet. Provide portfolio or sync from KIS first."));
        }
        Map<String, Object> result = agentGateway.run(request, portfolio);
        DecisionRunRecord record = decisionRunService.record(request, portfolio, result);
        return ResponseEntity.ok()
                .header("X-Libra-Decision-Run-Id", record.id())
                .header("X-Libra-Thread-Id", record.threadId())
                .body(result);
    }
}
