package io.github.aayush10016.trail.intelligence.domain.service;

import io.github.aayush10016.trail.intelligence.domain.model.AIRedaction;
import io.github.aayush10016.trail.intelligence.domain.model.AIRedactionInput;
import io.github.aayush10016.trail.intelligence.domain.model.AIRedactionResult;
import io.github.aayush10016.trail.intelligence.domain.model.AIRedactionType;
import io.github.aayush10016.trail.intelligence.domain.model.AIRefusalReason;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic policy that removes sensitive or adversarial content before an AI provider call.
 */
public class AIRedactionPolicy {

    /**
     * Current AI redaction policy version.
     */
    public static final String POLICY_VERSION = "ai-redaction-v1";

    private static final int MINIMUM_SAFE_SIGNAL_CHARACTERS = 24;

    private static final Map<AIRedactionType, Pattern> PATTERNS = Map.of(
            AIRedactionType.SECRET,
            Pattern.compile(
                    "(?i)(\\bBearer\\s+[A-Za-z0-9._~+/=-]{10,}|[a-z][a-z0-9+.-]*://[^\\s/@:]+:[^\\s/@]+@|\\b(?:access[_-]?token|api[_-]?key|password|secret|client[_-]?secret|refresh[_-]?token|authorization|x-api-key|code|state)\\s*[:=]\\s*[^\\s&]+|\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b)"),
            AIRedactionType.PII,
            Pattern.compile(
                    "(?i)(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b|\\b\\+?[0-9][0-9 .()\\-]{8,}[0-9]\\b)"),
            AIRedactionType.PRIVATE_PATH,
            Pattern.compile(
                    "(?i)(\\b[A-Z]:\\\\Users\\\\[^\\s]+|/(?:Users|home)/[^\\s]+|/var/(?:www|log|run)/[^\\s]+)"),
            AIRedactionType.PRIVATE_HOST,
            Pattern.compile(
                    "(?i)(\\b(localhost|127(?:\\.\\d{1,3}){3}|10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})\\b(?::\\d{1,5})?|\\[(?:fc|fd)[0-9a-f:]+])"),
            AIRedactionType.UUID_LIKE_TOKEN,
            Pattern.compile(
                    "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"),
            AIRedactionType.ADVERSARIAL_INSTRUCTION,
            Pattern.compile(
                    "(?i)(ignore\\s+(?:all\\s+)?(?:previous|prior|above)\\s+instructions|disregard\\s+(?:all\\s+)?(?:previous|prior|above)\\s+instructions|system\\s+prompt|developer\\s+message|reveal\\s+secrets|exfiltrate|override\\s+instructions|send\\s+this\\s+to)"),
            AIRedactionType.LOG_CANARY,
            Pattern.compile("\\bTRAIL_LOG_CANARY_[A-Z0-9_]*\\b"));

    /**
     * Redacts input or refuses when insufficient safe signal remains.
     */
    public AIRedactionResult redact(AIRedactionInput input) {
        Objects.requireNonNull(input, "input");
        var counts = new EnumMap<AIRedactionType, Integer>(AIRedactionType.class);
        var safeInput = input.fields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().strip() + ": " + redactValue(entry.getValue(), counts).strip())
                .filter(line -> !line.endsWith(":"))
                .toList();
        var joined = String.join("\n", safeInput).strip();
        var redactions = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> new AIRedaction(entry.getKey(), entry.getValue()))
                .toList();

        if (safeSignalCharacters(joined) < MINIMUM_SAFE_SIGNAL_CHARACTERS) {
            return AIRedactionResult.refused(POLICY_VERSION, redactions, AIRefusalReason.INSUFFICIENT_SAFE_INPUT);
        }
        return AIRedactionResult.allowed(POLICY_VERSION, joined, redactions);
    }

    private static String redactValue(String value, EnumMap<AIRedactionType, Integer> counts) {
        var redacted = Objects.requireNonNull(value, "value");
        for (var type : AIRedactionType.values()) {
            redacted = redactPattern(redacted, type, counts);
        }
        return redacted.replaceAll("\\s+", " ").strip();
    }

    private static String redactPattern(String value, AIRedactionType type, EnumMap<AIRedactionType, Integer> counts) {
        var matcher = PATTERNS.get(type).matcher(value);
        var matches = new ArrayList<String>();
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        if (matches.isEmpty()) {
            return value;
        }
        counts.merge(type, matches.size(), Integer::sum);
        return matcher.replaceAll("[REDACTED_" + type.name() + "]");
    }

    private static int safeSignalCharacters(String safeInput) {
        var withoutPlaceholders = safeInput.replaceAll("\\[REDACTED_[A-Z_]+]", "");
        var count = 0;
        for (var index = 0; index < withoutPlaceholders.length(); index += 1) {
            if (Character.isLetterOrDigit(withoutPlaceholders.charAt(index))) {
                count += 1;
            }
        }
        return count;
    }
}
