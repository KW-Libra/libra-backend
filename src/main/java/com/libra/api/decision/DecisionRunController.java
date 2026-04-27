package com.libra.api.decision;

import jakarta.validation.Valid;
import java.util.List;
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
    public List<DecisionRunSummary> recent() {
        return decisionRunService.recent();
    }

    @GetMapping("/{id}")
    public DecisionRunDetail get(@PathVariable String id) {
        return decisionRunService.getDetail(id);
    }

    @GetMapping("/{id}/evaluations")
    public List<DecisionEvaluationResult> evaluations(@PathVariable String id) {
        return decisionRunService.evaluations(id);
    }

    @PostMapping("/{id}/evaluations")
    public DecisionEvaluationResult evaluate(
            @PathVariable String id,
            @RequestBody @Valid DecisionEvaluationRequest request
    ) {
        return decisionRunService.evaluate(id, request);
    }
}
