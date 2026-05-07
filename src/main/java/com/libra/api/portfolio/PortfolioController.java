package com.libra.api.portfolio;

import com.libra.api.auth.AuthenticatedUser;
import com.libra.api.integration.kis.KisCredentialService;
import com.libra.api.integration.kis.KisPortfolioSyncService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioStateService portfolioStateService;
    private final PortfolioDefinitionService portfolioDefinitionService;
    private final KisPortfolioSyncService kisPortfolioSyncService;
    private final KisCredentialService kisCredentialService;

    public PortfolioController(
            PortfolioStateService portfolioStateService,
            PortfolioDefinitionService portfolioDefinitionService,
            KisPortfolioSyncService kisPortfolioSyncService,
            KisCredentialService kisCredentialService
    ) {
        this.portfolioStateService = portfolioStateService;
        this.portfolioDefinitionService = portfolioDefinitionService;
        this.kisPortfolioSyncService = kisPortfolioSyncService;
        this.kisCredentialService = kisCredentialService;
    }

    @GetMapping("/current")
    public PortfolioSnapshot getCurrentPortfolio(@AuthenticationPrincipal AuthenticatedUser principal) {
        return portfolioStateService.getCurrent(principal.id())
                .orElseThrow(() -> new IllegalStateException("No portfolio snapshot is stored yet. Sync from KIS first."));
    }

    @PostMapping("/current")
    public PortfolioSnapshot putCurrentPortfolio(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid PortfolioSnapshot snapshot
    ) {
        return portfolioStateService.save(principal.id(), snapshot);
    }

    @GetMapping("/definition")
    public PortfolioDefinition getPortfolioDefinition(@AuthenticationPrincipal AuthenticatedUser principal) {
        return portfolioDefinitionService.getCurrent(principal.id())
                .orElseThrow(() -> new IllegalStateException("No portfolio definition is stored yet."));
    }

    @PutMapping("/definition")
    public PortfolioDefinition putPortfolioDefinition(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid PortfolioDefinition definition
    ) {
        return portfolioDefinitionService.save(principal.id(), definition);
    }

    @PostMapping("/current/sync/kis")
    public ResponseEntity<Map<String, Object>> syncCurrentPortfolioFromKis(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) @Valid KisSyncRequest request
    ) {
        KisSyncRequest safeRequest = request == null ? new KisSyncRequest("real", null, null, null) : request;
        String requestedEnvironment = request == null ? null : safeRequest.environment();
        PortfolioSnapshot snapshot = kisCredentialService.runtimeCredential(principal.id(), requestedEnvironment)
                .map(credential -> kisPortfolioSyncService.syncDomesticPortfolio(safeRequest, credential))
                .orElseGet(() -> kisPortfolioSyncService.syncDomesticPortfolio(safeRequest));
        portfolioStateService.save(principal.id(), snapshot, "KIS_DOMESTIC_BALANCE");
        return ResponseEntity.ok(Map.of(
                "status", "SYNCED",
                "source", "KIS_DOMESTIC_BALANCE",
                "portfolio", snapshot
        ));
    }
}
