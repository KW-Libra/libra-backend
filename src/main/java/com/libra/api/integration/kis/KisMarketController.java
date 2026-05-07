package com.libra.api.integration.kis;

import com.libra.api.auth.AuthenticatedUser;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/market/kis")
public class KisMarketController {

    private final KisMarketPriceService marketPriceService;
    private final KisStockMasterService stockMasterService;
    private final KisCredentialService credentialService;

    public KisMarketController(
            KisMarketPriceService marketPriceService,
            KisStockMasterService stockMasterService,
            KisCredentialService credentialService
    ) {
        this.marketPriceService = marketPriceService;
        this.stockMasterService = stockMasterService;
        this.credentialService = credentialService;
    }

    @GetMapping("/quotes")
    public List<KisQuote> quotes(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "demo") String environment,
            @RequestParam String tickers
    ) {
        List<String> tickerList = Arrays.stream(tickers.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        KisProperties.Credential credential = credentialService.runtimeCredential(principal.id(), environment)
                .orElse(null);
        return marketPriceService.fetchDomesticQuotes(environment, tickerList, credential);
    }

    @GetMapping("/stocks")
    public List<KisStockListing> stocks(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "KOSPI,KOSDAQ") String markets,
            @RequestParam(defaultValue = "30") int limit
    ) {
        Set<String> marketSet = Arrays.stream(markets.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return stockMasterService.search(query, marketSet, limit);
    }
}
