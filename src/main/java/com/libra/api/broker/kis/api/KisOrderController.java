package com.libra.api.broker.kis.api;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderResponse;
import com.libra.api.broker.kis.service.KisOrderClient;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broker/kis/orders")
@Tag(name = "KIS Orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KisOrderController {

    private final KisOrderClient orderClient;

    public KisOrderController(KisOrderClient orderClient) {
        this.orderClient = orderClient;
    }

    @Operation(summary = "Place or dry-run a KIS domestic cash stock order")
    @PostMapping("/cash")
    public KisOrderResponse cashOrder(@RequestBody @Valid KisOrderRequest request) {
        return orderClient.placeCashOrder(request);
    }
}
