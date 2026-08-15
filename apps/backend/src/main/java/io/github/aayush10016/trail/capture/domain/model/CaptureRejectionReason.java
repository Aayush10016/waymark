package io.github.aayush10016.trail.capture.domain.model;

/**
 * Stable backend URL/query sanitation rejection reasons.
 */
public enum CaptureRejectionReason {
    /**
     * URL includes username or password authority data.
     */
    CREDENTIALS_PRESENT("credentials_present"),

    /**
     * URL points at localhost, loopback, private, or link-local hosts.
     */
    PRIVATE_OR_LOCAL_HOST("private_or_local_host"),

    /**
     * URL scheme is not eligible for Trail capture.
     */
    UNSUPPORTED_SCHEME("unsupported_scheme");

    private final String wireValue;

    CaptureRejectionReason(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the language-neutral fixture value for this reason.
     */
    public String wireValue() {
        return wireValue;
    }
}
