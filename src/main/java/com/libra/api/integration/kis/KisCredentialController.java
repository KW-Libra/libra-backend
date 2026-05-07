package com.libra.api.integration.kis;

import com.libra.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kis/credential")
public class KisCredentialController {

    private final KisCredentialService service;

    public KisCredentialController(KisCredentialService service) {
        this.service = service;
    }

    @GetMapping
    public KisCredentialStatus getCredential(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.getStatus(principal.id());
    }

    @PutMapping
    public KisCredentialStatus saveCredential(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid KisCredentialRequest request
    ) {
        return service.save(principal.id(), request);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteCredential(@AuthenticationPrincipal AuthenticatedUser principal) {
        service.delete(principal.id());
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }
}
