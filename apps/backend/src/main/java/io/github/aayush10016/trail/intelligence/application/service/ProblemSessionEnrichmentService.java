package io.github.aayush10016.trail.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationRecord;
import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationStatus;
import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationType;
import io.github.aayush10016.trail.intelligence.domain.model.AIRedactionInput;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentContext;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentProjection;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentPromptCatalog;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIRequest;
import io.github.aayush10016.trail.intelligence.domain.port.AIDerivationRepository;
import io.github.aayush10016.trail.intelligence.domain.port.AIProvider;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemSessionEnrichmentContextRepository;
import io.github.aayush10016.trail.intelligence.domain.service.AIRedactionPolicy;
import io.github.aayush10016.trail.intelligence.domain.service.PromptRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates structured AI enrichment for one solved Problem Session.
 */
public class ProblemSessionEnrichmentService {

    private static final String SESSION_NOT_FOUND = "PROBLEM_SESSION_NOT_FOUND";
    private static final String AI_INPUT_REFUSED = "AI_INPUT_REFUSED";
    private static final String INVALID_AI_OUTPUT = "INVALID_AI_OUTPUT";
    private static final String LOCAL_PROVIDER = "local-policy";

    private final ProblemSessionEnrichmentContextRepository contextRepository;
    private final AIDerivationRepository derivationRepository;
    private final AIProvider aiProvider;
    private final PromptRegistry promptRegistry;
    private final AIRedactionPolicy redactionPolicy;
    private final ProblemSessionEnrichmentPolicy policy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates the enrichment application service.
     */
    public ProblemSessionEnrichmentService(
            ProblemSessionEnrichmentContextRepository contextRepository,
            AIDerivationRepository derivationRepository,
            AIProvider aiProvider,
            PromptRegistry promptRegistry,
            AIRedactionPolicy redactionPolicy,
            ProblemSessionEnrichmentPolicy policy,
            ObjectMapper objectMapper,
            Clock clock) {
        this.contextRepository = Objects.requireNonNull(contextRepository, "contextRepository");
        this.derivationRepository = Objects.requireNonNull(derivationRepository, "derivationRepository");
        this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider");
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
        this.redactionPolicy = Objects.requireNonNull(redactionPolicy, "redactionPolicy");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Runs one enrichment attempt for a user-owned Problem Session.
     */
    public ProblemSessionEnrichmentJobResult enrich(UUID userId, UUID problemSessionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        var context = contextRepository.findSolvedContext(userId, problemSessionId);
        if (context.isEmpty()) {
            return ProblemSessionEnrichmentJobResult.terminalFailure(SESSION_NOT_FOUND);
        }

        var prompt = promptRegistry.latest(ProblemSessionEnrichmentPromptCatalog.PROMPT_KEY);
        var outputSchemaHash = sha256(prompt.outputSchemaJson());
        var redactionInput = new AIRedactionInput(context.get().redactionFields());
        var inputFingerprint = fingerprint(redactionInput.fields());
        var redaction = redactionPolicy.redact(redactionInput);

        if (!redaction.allowed()) {
            derivationRepository.save(newDerivation(
                    userId,
                    problemSessionId,
                    LOCAL_PROVIDER,
                    policy.model(),
                    prompt.identity(),
                    outputSchemaHash,
                    redaction.policyVersion(),
                    inputFingerprint,
                    AIDerivationStatus.REFUSED,
                    AI_INPUT_REFUSED,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    AIUsageMetadata.zero(),
                    AICostMetadata.zeroUsd(),
                    true));
            return ProblemSessionEnrichmentJobResult.terminalFailure(AI_INPUT_REFUSED);
        }

        var aiResult = aiProvider.generateStructured(new StructuredAIRequest(
                policy.model(),
                prompt.body(),
                redaction.safeInput(),
                prompt.outputSchemaJson(),
                policy.timeout(),
                Map.of(
                        "promptKey", prompt.identity().key(),
                        "promptVersion", Integer.toString(prompt.identity().version()),
                        "policyVersion", redaction.policyVersion())));

        if (!aiResult.succeeded()) {
            var failure = aiResult.failure();
            var status = failure.retryable() ? AIDerivationStatus.FAILED_RETRYABLE : AIDerivationStatus.FAILED_TERMINAL;
            derivationRepository.save(newDerivation(
                    userId,
                    problemSessionId,
                    "unknown",
                    policy.model(),
                    prompt.identity(),
                    outputSchemaHash,
                    redaction.policyVersion(),
                    inputFingerprint,
                    status,
                    failure.code().name(),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    AIUsageMetadata.zero(),
                    AICostMetadata.zeroUsd(),
                    status != AIDerivationStatus.FAILED_RETRYABLE));
            return failure.retryable()
                    ? ProblemSessionEnrichmentJobResult.retryableFailure(failure.code().name())
                    : ProblemSessionEnrichmentJobResult.terminalFailure(failure.code().name());
        }

        var response = aiResult.response();
        var output = parseAndValidate(response.outputJson(), redaction.safeInput());
        if (output == null) {
            derivationRepository.save(newDerivation(
                    userId,
                    problemSessionId,
                    response.provider(),
                    response.model(),
                    prompt.identity(),
                    outputSchemaHash,
                    redaction.policyVersion(),
                    inputFingerprint,
                    AIDerivationStatus.INVALID_OUTPUT,
                    INVALID_AI_OUTPUT,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    response.usage(),
                    response.cost(),
                    true));
            return ProblemSessionEnrichmentJobResult.terminalFailure(INVALID_AI_OUTPUT);
        }

        var projection = new ProblemSessionEnrichmentProjection(
                userId,
                problemSessionId,
                output.summary(),
                output.technologies(),
                List.of(),
                output.errorMessages(),
                output.problemType(),
                searchDocument(context.get(), output));
        var derivation = newDerivation(
                userId,
                problemSessionId,
                response.provider(),
                response.model(),
                prompt.identity(),
                outputSchemaHash,
                redaction.policyVersion(),
                inputFingerprint,
                AIDerivationStatus.SUCCEEDED,
                null,
                output.summary(),
                output.technologies(),
                List.of(),
                output.errorMessages(),
                output.problemType(),
                response.usage(),
                response.cost(),
                true);
        derivationRepository.saveSuccessfulEnrichment(derivation, projection);
        return ProblemSessionEnrichmentJobResult.succeeded();
    }

    private ProblemSessionEnrichmentOutput parseAndValidate(String outputJson, String safeInput) {
        try {
            var root = objectMapper.readTree(outputJson);
            if (!root.isObject()) {
                return null;
            }
            var summary = text(root, "summary", 4000);
            var technologies = textArray(root, "technologies", 32, 64);
            var problemType = text(root, "problemType", 64);
            var errorMessages = textArray(root, "errorMessages", 32, 240);
            if (summary == null
                    || problemType == null
                    || technologies == null
                    || errorMessages == null
                    || !isGrounded(technologies, safeInput)
                    || !isGrounded(errorMessages, safeInput)) {
                return null;
            }
            return new ProblemSessionEnrichmentOutput(summary, technologies, problemType, errorMessages);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private AIDerivationRecord newDerivation(
            UUID userId,
            UUID problemSessionId,
            String provider,
            String model,
            io.github.aayush10016.trail.intelligence.domain.model.PromptIdentity promptIdentity,
            String outputSchemaHash,
            String policyVersion,
            String inputFingerprint,
            AIDerivationStatus status,
            String safeFailureCode,
            String generatedSummary,
            List<String> technologies,
            List<String> frameworks,
            List<String> errorPhrases,
            String problemType,
            AIUsageMetadata usage,
            AICostMetadata cost,
            boolean completed) {
        var now = clock.instant();
        return new AIDerivationRecord(
                UUID.randomUUID(),
                userId,
                problemSessionId,
                AIDerivationType.PROBLEM_SESSION_ENRICHMENT,
                provider,
                model,
                promptIdentity,
                outputSchemaHash,
                policyVersion,
                inputFingerprint,
                status,
                safeFailureCode,
                generatedSummary,
                technologies,
                frameworks,
                errorPhrases,
                problemType,
                usage,
                cost,
                completed ? now : null,
                now);
    }

    private String searchDocument(ProblemSessionEnrichmentContext context, ProblemSessionEnrichmentOutput output) {
        var values = new java.util.ArrayList<String>();
        add(values, context.title());
        add(values, context.description());
        add(values, context.localNote());
        add(values, context.finalSolution());
        add(values, output.summary());
        output.technologies().forEach(value -> add(values, value));
        output.errorMessages().forEach(value -> add(values, value));
        add(values, output.problemType());
        return String.join(" ", values).strip();
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.strip());
        }
    }

    private static boolean isGrounded(List<String> values, String safeInput) {
        var lowerSafeInput = safeInput.toLowerCase(java.util.Locale.ROOT);
        return values.stream().allMatch(value -> lowerSafeInput.contains(value.toLowerCase(java.util.Locale.ROOT)));
    }

    private static String text(JsonNode root, String fieldName, int maxLength) {
        var node = root.get(fieldName);
        if (node == null || !node.isTextual()) {
            return null;
        }
        var value = node.asText().strip();
        if (value.isBlank() || value.length() > maxLength) {
            return null;
        }
        return value;
    }

    private static List<String> textArray(JsonNode root, String fieldName, int maxItems, int maxLength) {
        var node = root.get(fieldName);
        if (node == null || !node.isArray() || node.size() > maxItems) {
            return null;
        }
        var values = new java.util.ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual()) {
                return null;
            }
            var value = item.asText().strip();
            if (value.isBlank() || value.length() > maxLength) {
                return null;
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static String fingerprint(Map<String, String> fields) {
        var ordered = new LinkedHashMap<String, String>();
        fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        var canonical = ordered.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ProblemSessionEnrichmentOutput(
            String summary, List<String> technologies, String problemType, List<String> errorMessages) {}
}
