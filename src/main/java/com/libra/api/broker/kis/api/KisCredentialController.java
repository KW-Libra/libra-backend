package com.libra.api.broker.kis.api;

import com.libra.api.auth.domain.User;
import com.libra.api.broker.kis.api.dto.KisCredentialStatusResponse;
import com.libra.api.broker.kis.api.dto.KisCredentialUpsertRequest;
import com.libra.api.broker.kis.service.KisCredentialService;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broker/kis/credentials")
@Tag(name = "KIS Credentials")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KisCredentialController {

    private final KisCredentialService credentials;

    public KisCredentialController(KisCredentialService credentials) {
        this.credentials = credentials;
    }

    @Operation(summary = "Get current user's KIS credential registration status")
    @GetMapping
    public KisCredentialStatusResponse status(
        @Parameter(hidden = true) @AuthenticationPrincipal User user
    ) {
        return credentials.status(requireUser(user));
    }

    @Operation(summary = "Register or replace current user's encrypted KIS credentials")
    @PutMapping
    public KisCredentialStatusResponse upsert(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @RequestBody @Valid KisCredentialUpsertRequest request
    ) {
        return credentials.upsert(requireUser(user), request);
    }

    @Operation(summary = "Delete current user's KIS credentials")
    @DeleteMapping
    public void delete(
        @Parameter(hidden = true) @AuthenticationPrincipal User user
    ) {
        credentials.delete(requireUser(user));
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return user;
    }
}
