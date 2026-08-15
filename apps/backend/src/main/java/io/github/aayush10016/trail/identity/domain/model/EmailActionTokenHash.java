package io.github.aayush10016.trail.identity.domain.model;

import java.util.Arrays;
import java.util.Base64;

/**
 * SHA-256 hash of an email action token.
 */
public final class EmailActionTokenHash {

    private static final int SHA_256_BYTE_LENGTH = 32;

    private final byte[] bytes;

    /**
     * Creates a token hash from SHA-256 bytes.
     *
     * @param bytes hash bytes
     */
    public EmailActionTokenHash(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("hash bytes must not be null");
        }
        if (bytes.length != SHA_256_BYTE_LENGTH) {
            throw new IllegalArgumentException("hash must be 32 bytes");
        }
        this.bytes = bytes.clone();
    }

    /**
     * Returns a defensive copy of the hash bytes.
     *
     * @return hash bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * Returns a non-secret fingerprint suitable for abuse-control keys.
     *
     * @return URL-safe hash fingerprint
     */
    public String fingerprint() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof EmailActionTokenHash other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
