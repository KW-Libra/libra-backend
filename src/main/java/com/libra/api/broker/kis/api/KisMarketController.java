package com.libra.api.broker.kis.api;

import com.libra.api.broker.kis.api.dto.KisInstrumentResponse;
import com.libra.api.broker.kis.api.dto.KisQuoteResponse;
import com.libra.api.broker.kis.api.dto.KisStatusResponse;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.broker.kis.service.KisMarketClient;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/market/kis")
@Tag(name = "KIS Market")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KisMarketController {

    private final KisProperties properties;
    private final KisMarketClient marketClient;

    public KisMarketController(KisProperties properties, KisMarketClient marketClient) {
        this.properties = properties;
        this.marketClient = marketClient;
    }

    @Operation(summary = "Check KIS market-data integration status")
    @GetMapping("/status")
    public KisStatusResponse status() {
        return KisStatusResponse.from(properties);
    }

    @Operation(summary = "Get a domestic stock quote from KIS")
    @GetMapping("/quotes/{symbol}")
    public KisQuoteResponse quote(
        @Parameter(example = "005930")
        @PathVariable
        @Pattern(regexp = "^[A-Z0-9]{5,12}$", message = "symbol must be 5-12 uppercase alphanumeric characters")
        String symbol,
        @RequestParam(defaultValue = "J") String marketCode
    ) {
        return marketClient.quote(symbol, marketCode);
    }

    @Operation(summary = "Resolve a domestic stock instrument by code through KIS quote metadata")
    @GetMapping("/symbols/{symbol}")
    public KisInstrumentResponse instrument(
        @Parameter(example = "005930")
        @PathVariable
        @Pattern(regexp = "^[A-Z0-9]{5,12}$", message = "symbol must be 5-12 uppercase alphanumeric characters")
        String symbol,
        @RequestParam(defaultValue = "J") String marketCode
    ) {
        return KisInstrumentResponse.fromQuote(marketClient.quote(symbol, marketCode));
    }
}
