package com.libra.api.portfolio;

import com.libra.api.integration.kis.KisPortfolioSyncService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioStateService portfolioStateService;
    private final KisPortfolioSyncService kisPortfolioSyncService;

    public PortfolioController(
            PortfolioStateService portfolioStateService,
            KisPortfolioSyncService kisPortfolioSyncService
    ) {
        this.portfolioStateService = portfolioStateService;
        this.kisPortfolioSyncService = kisPortfolioSyncService;
    }

    @GetMapping("/current")
    public PortfolioSnapshot getCurrentPortfolio() {
        return portfolioStateService.getCurrent()
                .orElseThrow(() -> new IllegalStateException("No portfolio snapshot is stored yet. Sync from KIS first."));
    }

    @PostMapping("/current")
    public PortfolioSnapshot putCurrentPortfolio(@RequestBody @Valid PortfolioSnapshot snapshot) {
        return portfolioStateService.save(snapshot);
    }

    @PostMapping("/current/sync/kis")
    public ResponseEntity<Map<String, Object>> syncCurrentPortfolioFromKis(@RequestBody(required = false) @Valid KisSyncRequest request) {
        KisSyncRequest safeRequest = request == null ? new KisSyncRequest("real", null, null, null) : request;
        PortfolioSnapshot snapshot = kisPortfolioSyncService.syncDomesticPortfolio(safeRequest);
        portfolioStateService.save(snapshot, "KIS_DOMESTIC_BALANCE");
        return ResponseEntity.ok(Map.of(
                "status", "SYNCED",
                "source", "KIS_DOMESTIC_BALANCE",
                "portfolio", snapshot
        ));
    }
}
