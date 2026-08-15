package io.github.aayush10016.trail.identity.domain.model;

import java.util.Arrays;

/**
 * SHA-256 hash of an opaque refresh credential.
 */
public final class RefreshTokenHash {

    private static final int SHA_256_BYTE_LENGTH = 32;

    private final byte[] bytes;

    /**
     * Creates a refresh token hash from SHA-256 bytes.
     *
     * @param bytes hash bytes
     */
    public RefreshTokenHash(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("hash bytes must not be null");
        }
        if (bytes.length != SHA_256_BYTE_LENGTH) {
            throw new IllegalArgumentException("hash must be 32 bytes");
        }
        this.bytes = bytes.clone();
    }

    /**
     * Returns a defensive copy of hash bytes.
     *
     * @return hash bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof RefreshTokenHash other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
