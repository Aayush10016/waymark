package io.github.aayush10016.trail.identity.domain.model;

/**
 * Request context used by identity abuse controls.
 *
 * @param clientAddress canonical client network address or trusted proxy-derived source key
 */
public record IdentityRequestContext(String clientAddress) {

    private static final IdentityRequestContext UNSPECIFIED = new IdentityRequestContext("unspecified");

    /**
     * Creates a validated identity request context.
     */
    public IdentityRequestContext {
        if (clientAddress == null || clientAddress.isBlank()) {
            throw new IllegalArgumentException("clientAddress must not be blank");
        }
        clientAddress = clientAddress.trim();
    }

    /**
     * Returns a context for call sites that do not yet have an HTTP source.
     *
     * @return unspecified request context
     */
    public static IdentityRequestContext unspecified() {
        return UNSPECIFIED;
    }
}
