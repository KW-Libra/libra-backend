package com.libra.api.judge;

import com.libra.api.auth.AuthenticatedUser;
import com.libra.api.decision.DecisionRunRecord;
import com.libra.api.decision.DecisionRunService;
import com.libra.api.decision.PriorReflection;
import com.libra.api.integration.agent.AgentGateway;
import com.libra.api.portfolio.PortfolioDefinition;
import com.libra.api.portfolio.PortfolioDefinitionService;
import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import com.libra.api.portfolio.PortfolioStateService;
import com.libra.api.portfolio.TargetWeight;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/judge-runs")
public class JudgeRunController {

    private final PortfolioStateService portfolioStateService;
    private final PortfolioDefinitionService portfolioDefinitionService;
    private final AgentGateway agentGateway;
    private final DecisionRunService decisionRunService;

    public JudgeRunController(
            PortfolioStateService portfolioStateService,
            PortfolioDefinitionService portfolioDefinitionService,
            AgentGateway agentGateway,
            DecisionRunService decisionRunService
    ) {
        this.portfolioStateService = portfolioStateService;
        this.portfolioDefinitionService = portfolioDefinitionService;
        this.agentGateway = agentGateway;
        this.decisionRunService = decisionRunService;
    }

    private static final int PRIOR_REFLECTION_LIMIT = 5;

    @PostMapping
    public ResponseEntity<Map<String, Object>> run(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid JudgeRunDispatchRequest request
    ) {
        PortfolioDefinition portfolioDefinition = request.portfolioDefinition();
        if (portfolioDefinition == null) {
            portfolioDefinition = portfolioDefinitionService.getCurrent(principal.id()).orElse(null);
        }
        PortfolioDefinition resolvedPortfolioDefinition = portfolioDefinition;
        PortfolioSnapshot portfolio = request.portfolio();
        if (portfolio == null) {
            portfolio = portfolioStateService.getCurrent(principal.id())
                    .orElseGet(() -> syntheticCashPortfolio(resolvedPortfolioDefinition));
        }
        portfolio = ensureAgentPortfolioHasHoldings(portfolio, resolvedPortfolioDefinition);
        JudgeRunDispatchRequest resolvedRequest = withPortfolioDefinition(request, resolvedPortfolioDefinition);
        List<PriorReflection> priorReflections = decisionRunService.recentReflectionsForUser(
                principal.id(),
                PRIOR_REFLECTION_LIMIT
        );
        Map<String, Object> result = agentGateway.run(resolvedRequest, portfolio, priorReflections);
        DecisionRunRecord record = decisionRunService.record(principal.id(), resolvedRequest, portfolio, result);
        return ResponseEntity.ok()
                .header("X-Libra-Decision-Run-Id", record.id())
                .header("X-Libra-Thread-Id", record.threadId())
                .body(result);
    }

    private JudgeRunDispatchRequest withPortfolioDefinition(
            JudgeRunDispatchRequest request,
            PortfolioDefinition portfolioDefinition
    ) {
        return new JudgeRunDispatchRequest(
                request.query(),
                request.portfolio(),
                request.knowledgeSources(),
                request.depth(),
                request.trigger(),
                request.triggerEvent(),
                request.deadlineSeconds(),
                request.threadId(),
                request.enableHumanInterrupts(),
                request.allowIngestRefresh(),
                request.ingestRefresh(),
                portfolioDefinition
        );
    }

    private PortfolioSnapshot ensureAgentPortfolioHasHoldings(
            PortfolioSnapshot portfolio,
            PortfolioDefinition definition
    ) {
        if (portfolio.holdings() != null && !portfolio.holdings().isEmpty()) {
            return portfolio;
        }
        if (definition == null || definition.targetWeights() == null || definition.targetWeights().isEmpty()) {
            throw new IllegalStateException("No portfolio snapshot is stored yet. Provide portfolio or sync from KIS first.");
        }
        return new PortfolioSnapshot(
                portfolio.generatedAt(),
                zeroWeightHoldings(definition),
                portfolio.totalValueKrw(),
                1.0d,
                portfolio.userPreferences() == null ? List.of() : portfolio.userPreferences()
        );
    }

    private PortfolioSnapshot syntheticCashPortfolio(PortfolioDefinition definition) {
        if (definition == null || definition.targetWeights() == null || definition.targetWeights().isEmpty()) {
            throw new IllegalStateException("No portfolio snapshot is stored yet. Provide portfolio or sync from KIS first.");
        }
        return new PortfolioSnapshot(
                OffsetDateTime.now(),
                zeroWeightHoldings(definition),
                0.0d,
                1.0d,
                List.of("현재 KIS 잔고가 비어 있어 목표 종목을 0% 보유 상태로 판단합니다.")
        );
    }

    private List<PortfolioHolding> zeroWeightHoldings(PortfolioDefinition definition) {
        return definition.targetWeights().stream()
                .map(this::zeroWeightHolding)
                .toList();
    }

    private PortfolioHolding zeroWeightHolding(TargetWeight target) {
        return new PortfolioHolding(
                target.ticker(),
                target.companyName(),
                0.0d,
                List.of(target.ticker() + ".KS", "KRX:" + target.ticker(), target.companyName()),
                0.0d,
                null,
                null,
                0.0d,
                null
        );
    }
}
