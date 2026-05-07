package com.libra.api.decision;

import com.libra.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decision-runs")
public class DecisionRunController {

    private final DecisionRunService decisionRunService;

    public DecisionRunController(DecisionRunService decisionRunService) {
        this.decisionRunService = decisionRunService;
    }

    @GetMapping
    public List<DecisionRunSummary> recent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return decisionRunService.recent(principal.id());
    }

    @GetMapping("/{id}")
    public DecisionRunDetail get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String id
    ) {
        return decisionRunService.getDetail(principal.id(), id);
    }

    @GetMapping("/{id}/executions")
    public List<DecisionExecutionResult> executions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String id
    ) {
        return decisionRunService.executions(principal.id(), id);
    }

    @GetMapping("/{id}/executions/kis-demo/proposal")
    public List<DecisionExecutionProposalItem> proposeKisDemoOrders(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String id
    ) {
        return decisionRunService.proposeKisDemoOrders(principal.id(), id);
    }

    @PostMapping("/{id}/executions/kis-demo")
    public List<DecisionExecutionResult> executeKisDemoOrders(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String id,
            @RequestBody @Valid DecisionExecutionRequest request
    ) {
        return decisionRunService.executeKisDemoOrders(principal.id(), id, request);
    }

    @GetMapping("/{id}/evaluations")
    public List<DecisionEvaluationResult> evaluations(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String id
    ) {
        return decisionRunService.evaluations(principal.id(), id);
    }

    @PostMapping("/{id}/evaluations")
    public DecisionEvaluationResult evaluate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String id,
            @RequestBody @Valid DecisionEvaluationRequest request
    ) {
        return decisionRunService.evaluate(principal.id(), id, request);
    }
}
