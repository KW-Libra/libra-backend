package com.libra.api.backtest.api;

import com.libra.api.backtest.api.dto.BacktestValidationResponse;
import com.libra.api.backtest.service.BacktestValidationReportService;
import com.libra.api.backtest.service.BacktestValidationService;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/backtests")
@Tag(name = "Backtests")
public class BacktestValidationController {

    private final BacktestValidationReportService reports;
    private final BacktestValidationService validationService;

    public BacktestValidationController(
        BacktestValidationReportService reports,
        BacktestValidationService validationService
    ) {
        this.reports = reports;
        this.validationService = validationService;
    }

    @Operation(summary = "Get public RSS 3-year backtest validation summary")
    @GetMapping("/public-rss-3y/validation")
    public JsonNode publicRss3yValidation() {
        return reports.publicRss3yValidation();
    }

    @Operation(summary = "Get validated backtest result artifacts")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/{experimentId}/validation")
    public BacktestValidationResponse validation(@PathVariable String experimentId) {
        return validationService.getValidation(experimentId);
    }
}
