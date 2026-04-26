package com.libra.api.judge;

import com.libra.api.integration.agent.StubAgentGateway;
import com.libra.api.portfolio.PortfolioSnapshot;
import com.libra.api.portfolio.PortfolioStateService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/judge-runs")
public class JudgeRunController {

    private final PortfolioStateService portfolioStateService;
    private final StubAgentGateway stubAgentGateway;

    public JudgeRunController(PortfolioStateService portfolioStateService, StubAgentGateway stubAgentGateway) {
        this.portfolioStateService = portfolioStateService;
        this.stubAgentGateway = stubAgentGateway;
    }

    @PostMapping
    public Map<String, Object> run(@RequestBody @Valid JudgeRunDispatchRequest request) {
        PortfolioSnapshot portfolio = request.portfolio();
        if (portfolio == null) {
            portfolio = portfolioStateService.getCurrent()
                    .orElseThrow(() -> new IllegalStateException("No portfolio snapshot is stored yet. Provide portfolio or sync from KIS first."));
        }
        return stubAgentGateway.run(request, portfolio);
    }
}
