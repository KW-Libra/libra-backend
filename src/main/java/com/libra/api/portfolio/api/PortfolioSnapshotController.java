package com.libra.api.portfolio.api;

import com.libra.api.auth.domain.User;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.config.OpenApiConfig;
import com.libra.api.portfolio.api.dto.PortfolioSnapshotDetailResponse;
import com.libra.api.portfolio.api.dto.PortfolioSnapshotResponse;
import com.libra.api.portfolio.service.PortfolioSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/snapshots")
@Tag(name = "Portfolio")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PortfolioSnapshotController {

    private final PortfolioSnapshotService snapshots;

    public PortfolioSnapshotController(PortfolioSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @Operation(summary = "List current user's portfolio snapshots")
    @GetMapping
    public List<PortfolioSnapshotResponse> list(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return snapshots.list(requireUser(user), limit);
    }

    @Operation(summary = "Get current user's latest portfolio snapshot")
    @GetMapping("/latest")
    public PortfolioSnapshotDetailResponse latest(
        @Parameter(hidden = true) @AuthenticationPrincipal User user
    ) {
        return snapshots.latest(requireUser(user));
    }

    @Operation(summary = "Get current user's portfolio snapshot")
    @GetMapping("/{id}")
    public PortfolioSnapshotDetailResponse get(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @PathVariable UUID id
    ) {
        return snapshots.get(requireUser(user), id);
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return user;
    }
}
