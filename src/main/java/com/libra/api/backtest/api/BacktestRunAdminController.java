package com.libra.api.backtest.api;

import com.libra.api.backtest.api.dto.BacktestRunStartRequest;
import com.libra.api.backtest.api.dto.BacktestRunStatusResponse;
import com.libra.api.backtest.service.BacktestRunService;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/backtests/runs")
@Tag(name = "Backtest Admin")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BacktestRunAdminController {

    private final BacktestRunService runs;

    public BacktestRunAdminController(BacktestRunService runs) {
        this.runs = runs;
    }

    @Operation(summary = "Start a Claude committee backtest replay")
    @PostMapping
    public BacktestRunStatusResponse start(@RequestBody @Valid BacktestRunStartRequest request) {
        return runs.start(request);
    }

    @Operation(summary = "Get backtest replay run status")
    @GetMapping("/{runId}")
    public BacktestRunStatusResponse status(@PathVariable String runId) {
        return runs.status(runId);
    }
}
