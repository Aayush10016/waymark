package io.github.aayush10016.trail.intelligence.application.service;

import io.github.aayush10016.trail.intelligence.domain.model.AIRedactionInput;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemEmbedding;
import io.github.aayush10016.trail.intelligence.domain.port.EmbeddingProvider;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemEmbeddingRepository;
import io.github.aayush10016.trail.intelligence.domain.port.SolvedProblemEmbeddingContextRepository;
import io.github.aayush10016.trail.intelligence.domain.service.AIRedactionPolicy;
import io.github.aayush10016.trail.intelligence.domain.service.CanonicalEmbeddingDocumentBuilder;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates one canonical solved-session embedding generation attempt.
 */
public class SolvedProblemEmbeddingService {

    private static final String SESSION_NOT_FOUND = "SOLVED_PROBLEM_SESSION_NOT_FOUND";
    private static final String GENERATION_NOT_FOUND = "EMBEDDING_GENERATION_NOT_FOUND";
    private static final String INPUT_REFUSED = "EMBEDDING_INPUT_REFUSED";
    private static final String PROVIDER_RESPONSE_INVALID = "EMBEDDING_PROVIDER_RESPONSE_INVALID";

    private final SolvedProblemEmbeddingContextRepository contextRepository;
    private final ProblemEmbeddingRepository embeddingRepository;
    private final EmbeddingProvider embeddingProvider;
    private final AIRedactionPolicy redactionPolicy;
    private final CanonicalEmbeddingDocumentBuilder documentBuilder;
    private final SolvedProblemEmbeddingPolicy policy;
    private final Clock clock;

    /**
     * Creates the solved-session embedding application service.
     */
    public SolvedProblemEmbeddingService(
            SolvedProblemEmbeddingContextRepository contextRepository,
            ProblemEmbeddingRepository embeddingRepository,
            EmbeddingProvider embeddingProvider,
            AIRedactionPolicy redactionPolicy,
            CanonicalEmbeddingDocumentBuilder documentBuilder,
            SolvedProblemEmbeddingPolicy policy,
            Clock clock) {
        this.contextRepository = Objects.requireNonNull(contextRepository, "contextRepository");
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository, "embeddingRepository");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        this.redactionPolicy = Objects.requireNonNull(redactionPolicy, "redactionPolicy");
        this.documentBuilder = Objects.requireNonNull(documentBuilder, "documentBuilder");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Generates or reuses one active embedding for a user-owned solved Problem Session.
     */
    public SolvedProblemEmbeddingJobResult embed(UUID userId, UUID problemSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        var generation = embeddingRepository.findActiveGeneration(policy.generationKey());
        if (generation.isEmpty()) {
            return SolvedProblemEmbeddingJobResult.terminalFailure(GENERATION_NOT_FOUND);
        }

        var context = contextRepository.findSolvedContext(userId, problemSessionId);
        if (context.isEmpty()) {
            return SolvedProblemEmbeddingJobResult.terminalFailure(SESSION_NOT_FOUND);
        }

        var document = documentBuilder.build(context.get());
        var redaction = redactionPolicy.redact(new AIRedactionInput(Map.of("doc", document.text())));
        if (!redaction.allowed() || !hasEnoughSafeEmbeddingSignal(document.text())) {
            return SolvedProblemEmbeddingJobResult.terminalFailure(INPUT_REFUSED);
        }

        var active = embeddingRepository.findActive(
                userId,
                problemSessionId,
                generation.get().generationKey(),
                generation.get().generationVersion());
        if (active.filter(existing -> existing.inputFingerprint().equals(document.fingerprint())).isPresent()) {
            return SolvedProblemEmbeddingJobResult.succeeded();
        }

        var embeddingResult = embeddingProvider.embed(new EmbeddingRequest(
                generation.get().model(),
                redaction.safeInput(),
                generation.get().dimensions(),
                document.fingerprint(),
                redaction.policyVersion(),
                policy.timeout(),
                Map.of(
                        "documentVersion", document.version(),
                        "generationKey", generation.get().generationKey(),
                        "generationVersion", Integer.toString(generation.get().generationVersion()))));

        if (!embeddingResult.succeeded()) {
            var failure = embeddingResult.failure();
            return failure.retryable()
                    ? SolvedProblemEmbeddingJobResult.retryableFailure(failure.code().name())
                    : SolvedProblemEmbeddingJobResult.terminalFailure(failure.code().name());
        }

        var response = embeddingResult.response();
        if (!response.inputFingerprint().equals(document.fingerprint())
                || response.dimensions() != generation.get().dimensions()
                || response.vector().size() != generation.get().dimensions()) {
            return SolvedProblemEmbeddingJobResult.terminalFailure(PROVIDER_RESPONSE_INVALID);
        }

        embeddingRepository.replaceActive(new ProblemEmbedding(
                UUID.randomUUID(),
                userId,
                problemSessionId,
                generation.get().generationKey(),
                generation.get().generationVersion(),
                response.provider(),
                response.model(),
                response.dimensions(),
                response.inputFingerprint(),
                redaction.policyVersion(),
                response.vector(),
                true,
                null,
                clock.instant()));
        return SolvedProblemEmbeddingJobResult.succeeded();
    }

    private static boolean hasEnoughSafeEmbeddingSignal(String canonicalDocumentText) {
        var contentOnly = canonicalDocumentText
                .replace(CanonicalEmbeddingDocumentBuilder.DOCUMENT_VERSION, "")
                .replaceAll("(?m)^(documentVersion|title|description|localNote|finalSolution|generatedSummary|technologies|frameworks|errorPhrases|problemType|resources):\\s*", "")
                .replaceAll("(?m)^-\\s*", "");
        var count = 0;
        for (var index = 0; index < contentOnly.length(); index += 1) {
            if (Character.isLetterOrDigit(contentOnly.charAt(index))) {
                count += 1;
            }
        }
        return count >= 24;
    }
}
