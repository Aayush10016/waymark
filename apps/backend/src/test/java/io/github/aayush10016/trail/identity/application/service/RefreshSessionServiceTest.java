package io.github.aayush10016.trail.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.application.command.RotateRefreshSessionCommand;
import io.github.aayush10016.trail.identity.application.command.RevokeCurrentSessionCommand;
import io.github.aayush10016.trail.identity.application.dto.CurrentSessionRevocationResult;
import io.github.aayush10016.trail.identity.application.dto.RefreshSessionResult;
import io.github.aayush10016.trail.identity.application.dto.RefreshSessionStatus;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.domain.model.IssuedAccessToken;
import io.github.aayush10016.trail.identity.domain.model.RefreshTokenHash;
import io.github.aayush10016.trail.identity.domain.model.StoredRefreshSession;
import io.github.aayush10016.trail.identity.domain.port.AccessTokenIssuer;
import io.github.aayush10016.trail.identity.domain.port.RefreshSessionRepository;
import io.github.aayush10016.trail.identity.domain.port.RefreshTokenGenerator;
import io.github.aayush10016.trail.identity.domain.port.RefreshTokenHashingPolicy;
import io.github.aayush10016.trail.identity.security.Sha256RefreshTokenHashingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T15:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final UUID USER_ID = UUID.fromString("72fb55bd-9358-49f5-acdd-c9d5677d97fd");
    private static final UUID INITIAL_SESSION_ID = UUID.fromString("5ea17b59-60c0-4445-bd99-c3a108dd7abd");
    private static final UUID FAMILY_ID = UUID.fromString("6d1ec3a9-2fe3-43e9-8a71-eed45d332f10");
    private static final UUID ROTATED_SESSION_ID = UUID.fromString("64c6069d-8c71-4f7b-8899-aa8fed78a012");
    private static final AuthenticatedPrincipal PRINCIPAL = new AuthenticatedPrincipal(USER_ID);
    private static final IssuedAccessToken ACCESS_TOKEN =
            IssuedAccessToken.bearer("signed.jwt.token", NOW.plus(Duration.ofMinutes(10)));

    private final RefreshTokenHashingPolicy hashingPolicy = new Sha256RefreshTokenHashingPolicy();
    private final SequencedRefreshTokenGenerator tokenGenerator =
            new SequencedRefreshTokenGenerator(
                    "initial-refresh-token",
                    "rotated-refresh-token",
                    "replay-generated-token",
                    "compromised-generated-token",
                    "unknown-generated-token");
    private final InMemoryRefreshSessionRepository repository = new InMemoryRefreshSessionRepository();
    private final CapturingAccessTokenIssuer accessTokenIssuer = new CapturingAccessTokenIssuer();
    private final SequencedUuidSupplier sessionIds =
            new SequencedUuidSupplier(
                    INITIAL_SESSION_ID,
                    ROTATED_SESSION_ID,
                    UUID.fromString("614f57ca-f368-436a-832d-72c6c6354c07"),
                    UUID.fromString("76b1df35-2ab5-4b67-bf14-26cf099a0c5a"),
                    UUID.fromString("18f08818-1a8a-4a59-9f56-7eca498f850e"));
    private final RefreshSessionService service =
            new RefreshSessionService(
                    repository,
                    tokenGenerator,
                    hashingPolicy,
                    accessTokenIssuer,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    sessionIds::get,
                    () -> FAMILY_ID,
                    REFRESH_TTL);

    @Test
    void createsInitialRefreshSessionWithHashOnlyStorage() {
        var issuedToken = service.createSession(PRINCIPAL);

        assertThat(issuedToken.token()).isEqualTo("initial-refresh-token");
        assertThat(issuedToken.expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(issuedToken.toString()).doesNotContain("initial-refresh-token");
        assertThat(repository.sessions()).hasSize(1);

        var storedSession = repository.sessions().get(0);
        assertThat(storedSession.id()).isEqualTo(INITIAL_SESSION_ID);
        assertThat(storedSession.sessionFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(storedSession.principal()).isEqualTo(PRINCIPAL);
        assertThat(storedSession.rotationCounter()).isZero();
        assertThat(storedSession.createdAt()).isEqualTo(NOW);
        assertThat(storedSession.expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(storedSession.tokenHash()).isEqualTo(hashingPolicy.hash("initial-refresh-token"));
        assertThat(new String(storedSession.tokenHash().bytes())).doesNotContain("initial-refresh-token");
    }

    @Test
    void rotatesActiveRefreshTokenAndIssuesNewAccessToken() {
        service.createSession(PRINCIPAL);

        var result = service.rotate(new RotateRefreshSessionCommand("initial-refresh-token"));

        assertThat(result.status()).isEqualTo(RefreshSessionStatus.ROTATED);
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken().token()).isEqualTo("rotated-refresh-token");
        assertThat(result.refreshToken().expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(accessTokenIssuer.principal()).isEqualTo(PRINCIPAL);
        assertThat(repository.sessions()).hasSize(2);
        assertThat(repository.activeTokenHashes()).containsExactly(hashingPolicy.hash("rotated-refresh-token"));
    }

    @Test
    void replayingUsedRefreshTokenMarksFamilyCompromisedAndRejectsFutureRotations() {
        service.createSession(PRINCIPAL);
        assertThat(service.rotate(new RotateRefreshSessionCommand("initial-refresh-token")).status())
                .isEqualTo(RefreshSessionStatus.ROTATED);

        var replay = service.rotate(new RotateRefreshSessionCommand("initial-refresh-token"));
        var rotatedTokenAfterCompromise = service.rotate(new RotateRefreshSessionCommand("rotated-refresh-token"));

        assertThat(replay).isEqualTo(RefreshSessionResult.rejected());
        assertThat(rotatedTokenAfterCompromise).isEqualTo(RefreshSessionResult.rejected());
        assertThat(repository.compromisedFamilies()).containsExactly(FAMILY_ID);
    }

    @Test
    void unknownAndExpiredRefreshTokensReturnGenericRejection() {
        service.createSession(PRINCIPAL);
        repository.expire(hashingPolicy.hash("initial-refresh-token"));

        var expired = service.rotate(new RotateRefreshSessionCommand("initial-refresh-token"));
        var unknown = service.rotate(new RotateRefreshSessionCommand("unknown-refresh-token"));

        assertThat(expired).isEqualTo(RefreshSessionResult.rejected());
        assertThat(unknown).isEqualTo(RefreshSessionResult.rejected());
        assertThat(repository.compromisedFamilies()).isEmpty();
    }

    @Test
    void concurrentReuseRotatesOnceAndRejectsReplay() throws Exception {
        var concurrentRepository = new InMemoryRefreshSessionRepository();
        var concurrentTokenGenerator =
                new SequencedRefreshTokenGenerator("initial-refresh-token", "concurrent-one", "concurrent-two");
        var concurrentSessionIds =
                new SequencedUuidSupplier(
                        INITIAL_SESSION_ID,
                        UUID.fromString("3f5477d8-84e3-4cd1-8095-47b628cc6c94"),
                        UUID.fromString("e0a037ad-d33b-4658-82b2-7e0889e5c880"));
        var concurrentService =
                new RefreshSessionService(
                        concurrentRepository,
                        concurrentTokenGenerator,
                        hashingPolicy,
                        accessTokenIssuer,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        concurrentSessionIds::get,
                        () -> FAMILY_ID,
                        REFRESH_TTL);
        concurrentService.createSession(PRINCIPAL);

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> rotateAfterLatch(concurrentService, start));
            var second = executor.submit(() -> rotateAfterLatch(concurrentService, start));

            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .extracting(RefreshSessionResult::status)
                    .containsExactlyInAnyOrder(RefreshSessionStatus.ROTATED, RefreshSessionStatus.REJECTED);
            assertThat(concurrentRepository.compromisedFamilies()).containsExactly(FAMILY_ID);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revokesCurrentRefreshFamilyIdempotently() {
        service.createSession(PRINCIPAL);
        assertThat(service.rotate(new RotateRefreshSessionCommand("initial-refresh-token")).status())
                .isEqualTo(RefreshSessionStatus.ROTATED);

        var firstLogout = service.revokeCurrentSession(new RevokeCurrentSessionCommand("rotated-refresh-token"));
        var secondLogout = service.revokeCurrentSession(new RevokeCurrentSessionCommand("rotated-refresh-token"));
        var rotationAfterLogout = service.rotate(new RotateRefreshSessionCommand("rotated-refresh-token"));

        assertThat(firstLogout).isEqualTo(CurrentSessionRevocationResult.completed());
        assertThat(secondLogout).isEqualTo(CurrentSessionRevocationResult.completed());
        assertThat(rotationAfterLogout).isEqualTo(RefreshSessionResult.rejected());
        assertThat(repository.revokedFamilies()).containsExactly(FAMILY_ID);
    }

    @Test
    void revokingExpiredRevokedOrUnknownRefreshTokenReturnsGenericCompletion() {
        service.createSession(PRINCIPAL);
        repository.expire(hashingPolicy.hash("initial-refresh-token"));

        var expired = service.revokeCurrentSession(new RevokeCurrentSessionCommand("initial-refresh-token"));
        var alreadyRevoked = service.revokeCurrentSession(new RevokeCurrentSessionCommand("initial-refresh-token"));
        var unknown = service.revokeCurrentSession(new RevokeCurrentSessionCommand("unknown-refresh-token"));

        assertThat(expired).isEqualTo(CurrentSessionRevocationResult.completed());
        assertThat(alreadyRevoked).isEqualTo(CurrentSessionRevocationResult.completed());
        assertThat(unknown).isEqualTo(CurrentSessionRevocationResult.completed());
        assertThat(service.rotate(new RotateRefreshSessionCommand("initial-refresh-token")))
                .isEqualTo(RefreshSessionResult.rejected());
    }

    private static RefreshSessionResult rotateAfterLatch(
            RefreshSessionService concurrentService, CountDownLatch start) throws InterruptedException {
        start.await();
        return concurrentService.rotate(new RotateRefreshSessionCommand("initial-refresh-token"));
    }

    private static final class SequencedRefreshTokenGenerator implements RefreshTokenGenerator {

        private final List<String> tokens;
        private int index;

        private SequencedRefreshTokenGenerator(String... tokens) {
            this.tokens = List.of(tokens);
        }

        @Override
        public synchronized String generateToken() {
            return tokens.get(index++);
        }
    }

    private static final class SequencedUuidSupplier {

        private final List<UUID> ids;
        private int index;

        private SequencedUuidSupplier(UUID... ids) {
            this.ids = List.of(ids);
        }

        private synchronized UUID get() {
            return ids.get(index++);
        }
    }

    private static final class CapturingAccessTokenIssuer implements AccessTokenIssuer {

        private AuthenticatedPrincipal principal;

        @Override
        public IssuedAccessToken issue(AuthenticatedPrincipal principal) {
            this.principal = principal;
            return ACCESS_TOKEN;
        }

        AuthenticatedPrincipal principal() {
            return principal;
        }
    }

    private static final class InMemoryRefreshSessionRepository implements RefreshSessionRepository {

        private final Map<RefreshTokenHash, SessionState> sessionsByTokenHash = new HashMap<>();
        private final Set<UUID> compromisedFamilies = new HashSet<>();
        private final Set<RefreshTokenHash> expiredTokenHashes = new HashSet<>();

        @Override
        public synchronized void create(StoredRefreshSession session) {
            sessionsByTokenHash.put(session.tokenHash(), SessionState.active(session));
        }

        @Override
        public synchronized RotationResult rotate(
                RefreshTokenHash presentedTokenHash,
                RefreshSessionReplacement replacement,
                Instant rotatedAt) {
            var current = sessionsByTokenHash.get(presentedTokenHash);
            if (current == null) {
                return RotationResult.notActive();
            }

            if (compromisedFamilies.contains(current.session().sessionFamilyId())) {
                return RotationResult.notActive();
            }

            if (current.revoked()
                    || current.consumed()
                    || expiredTokenHashes.contains(presentedTokenHash)
                    || !current.session().expiresAt().isAfter(rotatedAt)) {
                if (current.consumed()) {
                    compromisedFamilies.add(current.session().sessionFamilyId());
                }
                return current.consumed() ? RotationResult.replayDetected() : RotationResult.notActive();
            }

            sessionsByTokenHash.put(presentedTokenHash, current.markConsumed());
            var replacementSession =
                    new StoredRefreshSession(
                            replacement.sessionId(),
                            current.session().sessionFamilyId(),
                            current.session().principal(),
                            replacement.tokenHash(),
                            current.session().rotationCounter() + 1,
                            rotatedAt,
                            replacement.expiresAt());
            sessionsByTokenHash.put(replacement.tokenHash(), SessionState.active(replacementSession));
            return RotationResult.rotated(current.session().principal());
        }

        @Override
        public synchronized void revokeCurrentFamily(RefreshTokenHash presentedTokenHash, Instant revokedAt) {
            var current = sessionsByTokenHash.get(presentedTokenHash);
            if (current == null) {
                return;
            }

            var familyId = current.session().sessionFamilyId();
            sessionsByTokenHash.replaceAll((tokenHash, state) -> {
                if (state.session().sessionFamilyId().equals(familyId) && !state.revoked()) {
                    return state.markRevoked();
                }
                return state;
            });
        }

        @Override
        public synchronized void revokeAllFamiliesForUser(AuthenticatedPrincipal principal, Instant revokedAt) {
            sessionsByTokenHash.replaceAll((tokenHash, state) -> {
                if (state.session().principal().equals(principal) && !state.revoked()) {
                    return state.markRevoked();
                }
                return state;
            });
        }

        List<StoredRefreshSession> sessions() {
            return sessionsByTokenHash.values().stream().map(SessionState::session).toList();
        }

        List<RefreshTokenHash> activeTokenHashes() {
            return sessionsByTokenHash.entrySet().stream()
                    .filter(entry -> !entry.getValue().consumed())
                    .filter(entry -> !entry.getValue().revoked())
                    .map(Map.Entry::getKey)
                    .toList();
        }

        Set<UUID> compromisedFamilies() {
            return Set.copyOf(compromisedFamilies);
        }

        Set<UUID> revokedFamilies() {
            return sessionsByTokenHash.values().stream()
                    .filter(SessionState::revoked)
                    .map(state -> state.session().sessionFamilyId())
                    .collect(java.util.stream.Collectors.toSet());
        }

        void expire(RefreshTokenHash tokenHash) {
            expiredTokenHashes.add(tokenHash);
        }
    }

    private record SessionState(StoredRefreshSession session, boolean consumed, boolean revoked) {

        static SessionState active(StoredRefreshSession session) {
            return new SessionState(session, false, false);
        }

        SessionState markConsumed() {
            return new SessionState(session, true, true);
        }

        SessionState markRevoked() {
            return new SessionState(session, consumed, true);
        }
    }
}
