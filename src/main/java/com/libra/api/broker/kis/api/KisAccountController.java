package com.libra.api.broker.kis.api;

import com.libra.api.broker.kis.api.dto.KisBalanceResponse;
import com.libra.api.broker.kis.api.dto.KisBuyableCashResponse;
import com.libra.api.broker.kis.service.KisAccountClient;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/broker/kis/account")
@Tag(name = "KIS Account")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KisAccountController {

    private final KisAccountClient accountClient;

    public KisAccountController(KisAccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @Operation(summary = "Get KIS domestic stock account balance and holdings")
    @GetMapping("/balance")
    public KisBalanceResponse balance() {
        return accountClient.balance();
    }

    @Operation(summary = "Get KIS buyable cash and quantity for a domestic stock")
    @GetMapping("/buyable")
    public KisBuyableCashResponse buyable(
        @Parameter(example = "005930")
        @RequestParam
        @Pattern(regexp = "^[A-Z0-9]{5,12}$", message = "symbol must be 5-12 uppercase alphanumeric characters")
        String symbol,
        @RequestParam(defaultValue = "0") BigDecimal price,
        @RequestParam(defaultValue = "01") String orderDivision
    ) {
        return accountClient.buyableCash(symbol, price, orderDivision);
    }
}
