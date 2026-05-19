package com.libra.api.backtest.api;

import com.libra.api.backtest.service.BacktestValidationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/backtests")
@Tag(name = "Backtests")
public class BacktestValidationController {

    private final BacktestValidationReportService reports;

    public BacktestValidationController(BacktestValidationReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "Get public RSS 3-year backtest validation summary")
    @GetMapping("/public-rss-3y/validation")
    public JsonNode publicRss3yValidation() {
        return reports.publicRss3yValidation();
    }
}
