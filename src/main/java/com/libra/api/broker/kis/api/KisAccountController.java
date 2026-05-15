package com.libra.api.broker.kis.api;

import com.libra.api.broker.kis.api.dto.KisBalanceResponse;
import com.libra.api.broker.kis.api.dto.KisBuyableCashResponse;
import com.libra.api.broker.kis.service.KisCredentialService;
import com.libra.api.broker.kis.service.KisAccountClient;
import com.libra.api.auth.domain.User;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.config.OpenApiConfig;
import com.libra.api.portfolio.domain.PortfolioSnapshot;
import com.libra.api.portfolio.service.PortfolioSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final KisCredentialService credentials;
    private final PortfolioSnapshotService snapshots;

    public KisAccountController(
        KisAccountClient accountClient,
        KisCredentialService credentials,
        PortfolioSnapshotService snapshots
    ) {
        this.accountClient = accountClient;
        this.credentials = credentials;
        this.snapshots = snapshots;
    }

    @Operation(summary = "Get KIS domestic stock account balance and holdings")
    @GetMapping("/balance")
    public KisBalanceResponse balance(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "true") boolean saveSnapshot
    ) {
        User principal = requireUser(user);
        KisBalanceResponse balance = accountClient.balance(credentials.resolve(principal));
        if (!saveSnapshot) {
            return balance;
        }
        PortfolioSnapshot snapshot = snapshots.saveKisBalance(principal, balance);
        return balance.withSnapshotId(snapshot.getId());
    }

    @Operation(summary = "Get KIS buyable cash and quantity for a domestic stock")
    @GetMapping("/buyable")
    public KisBuyableCashResponse buyable(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @Parameter(example = "005930")
        @RequestParam
        @Pattern(regexp = "^[A-Z0-9]{5,12}$", message = "symbol must be 5-12 uppercase alphanumeric characters")
        String symbol,
        @RequestParam(defaultValue = "0") BigDecimal price,
        @RequestParam(defaultValue = "01") String orderDivision
    ) {
        return accountClient.buyableCash(symbol, price, orderDivision, credentials.resolve(requireUser(user)));
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return user;
    }
}
