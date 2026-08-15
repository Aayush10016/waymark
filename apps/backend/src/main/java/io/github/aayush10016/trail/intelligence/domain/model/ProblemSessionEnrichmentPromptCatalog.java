package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;

/**
 * Approved v1.1 Problem Session enrichment prompt catalog.
 */
public final class ProblemSessionEnrichmentPromptCatalog {

    /**
     * Prompt key for Problem Session enrichment.
     */
    public static final String PROMPT_KEY = "problem-session.enrichment";

    /**
     * Current prompt version.
     */
    public static final int VERSION = 1;

    /**
     * Golden SHA-256 hash for the v1 prompt body and output schema.
     */
    public static final String HASH = "7cd15398144e53a89063a7ab5807c641c1580fd3f4adcd5600140b003904896b";

    private static final String BODY = String.join(
            "\n",
            "You extract structured engineering memory metadata from sanitized Problem Session input.",
            "Return only fields supported by the schema.",
            "Do not infer details not present in the input.");

    private static final String OUTPUT_SCHEMA_JSON =
            "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"summary\",\"technologies\",\"problemType\",\"errorMessages\"],\"properties\":{\"summary\":{\"type\":\"string\"},\"technologies\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"problemType\":{\"type\":\"string\"},\"errorMessages\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}";

    private ProblemSessionEnrichmentPromptCatalog() {}

    /**
     * Returns approved prompt definitions.
     */
    public static List<PromptDefinition> definitions() {
        return List.of(new PromptDefinition(new PromptIdentity(PROMPT_KEY, VERSION, HASH), BODY, OUTPUT_SCHEMA_JSON));
    }
}
