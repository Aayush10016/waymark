package io.github.aayush10016.trail.identity.adapter.in.web;

import io.github.aayush10016.trail.identity.application.command.DeleteAccountCommand;
import io.github.aayush10016.trail.identity.application.dto.AccountDeletionResponse;
import io.github.aayush10016.trail.identity.application.service.AccountDeletionService;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenVerifier;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for authenticated account deletion.
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountDeletionController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final AccountDeletionService accountDeletionService;

    /**
     * Creates the controller.
     */
    public AccountDeletionController(
            AccessTokenVerifier accessTokenVerifier, AccountDeletionService accountDeletionService) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier");
        this.accountDeletionService = Objects.requireNonNull(accountDeletionService, "accountDeletionService");
    }

    /**
     * Reauthenticates and deletes the authenticated account.
     */
    @DeleteMapping
    public ResponseEntity<AccountDeletionResponse> deleteAccount(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) DeleteAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        var response = accountDeletionService.delete(authenticate(authorization), request.toCommand());
        return ResponseEntity.ok(response);
    }

    private AuthenticatedPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw IdentityApiException.unauthorized();
        }

        return accessTokenVerifier
                .verify(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(IdentityApiException::unauthorized);
    }

    /**
     * Account deletion request.
     */
    public record DeleteAccountRequest(String password) {

        DeleteAccountCommand toCommand() {
            return new DeleteAccountCommand(password);
        }
    }
}
