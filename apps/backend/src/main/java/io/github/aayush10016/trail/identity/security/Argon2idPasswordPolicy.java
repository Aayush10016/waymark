package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.model.PasswordHashRecord;
import io.github.aayush10016.trail.identity.domain.port.PasswordHashingPolicy;
import io.github.aayush10016.trail.identity.domain.port.PasswordVerificationPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Defines Trail's current Argon2id password hashing policy and rehash decision
 * rules.
 */
public final class Argon2idPasswordPolicy implements PasswordHashingPolicy, PasswordVerificationPolicy {

    /**
     * Algorithm value stored with password hash metadata.
     */
    public static final String ALGORITHM = "ARGON2ID";

    /**
     * Current password hash policy version.
     */
    public static final int VERSION = 1;

    /**
     * Maximum accepted raw password length after UTF-8 encoding.
     */
    public static final int MAXIMUM_PASSWORD_BYTES = 1024;

    /**
     * Salt length in bytes.
     */
    public static final int SALT_LENGTH_BYTES = 16;

    /**
     * Derived hash length in bytes.
     */
    public static final int HASH_LENGTH_BYTES = 32;

    /**
     * Argon2 parallelism parameter.
     */
    public static final int PARALLELISM = 1;

    /**
     * Argon2 memory cost in KiB.
     */
    public static final int MEMORY_KIB = 19_456;

    /**
     * Argon2 iteration count.
     */
    public static final int ITERATIONS = 2;

    private static final String PARAMETER_STRING =
            "m=%d,t=%d,p=%d,salt=%d,hash=%d"
                    .formatted(MEMORY_KIB, ITERATIONS, PARALLELISM, SALT_LENGTH_BYTES, HASH_LENGTH_BYTES);

    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a policy using Trail's current Argon2id parameters.
     */
    public Argon2idPasswordPolicy() {
        passwordEncoder =
                new Argon2PasswordEncoder(
                        SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
    }

    /**
     * Returns the Spring Security encoder configured with the current policy.
     *
     * @return password encoder for hashing and verification
     */
    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    /**
     * Returns immutable metadata that must be stored next to every password
     * hash created by this policy.
     *
     * @return current password hash metadata
     */
    public PasswordHashMetadata currentMetadata() {
        return new PasswordHashMetadata(ALGORITHM, VERSION, PARAMETER_STRING);
    }

    /**
     * Returns whether a raw password is within the configured UTF-8 byte cap.
     *
     * @param rawPassword raw password candidate
     * @return true when the password can be hashed by Trail
     */
    @Override
    public boolean isWithinMaximumInputSize(CharSequence rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        return rawPassword.toString().getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_PASSWORD_BYTES;
    }

    @Override
    public PasswordHashRecord hash(CharSequence rawPassword) {
        var metadata = currentMetadata();
        return new PasswordHashRecord(
                passwordEncoder.encode(Objects.requireNonNull(rawPassword, "rawPassword")),
                metadata.algorithm(),
                metadata.parameters(),
                metadata.version());
    }

    @Override
    public boolean matches(CharSequence rawPassword, PasswordHashRecord passwordHash) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        if (!isWithinMaximumInputSize(rawPassword)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash.encodedHash());
    }

    /**
     * Returns whether stored password metadata must be rehashed after a
     * successful credential verification.
     *
     * @param storedMetadata metadata loaded with the existing password hash
     * @return true when the password should be rehashed with the current policy
     */
    public boolean requiresRehash(PasswordHashMetadata storedMetadata) {
        return !currentMetadata().equals(storedMetadata);
    }

    /**
     * Returns whether an encoded password or its stored metadata requires a
     * rehash after successful credential verification.
     *
     * @param encodedPassword encoded password hash
     * @param storedMetadata metadata loaded with the existing password hash
     * @return true when the password should be rehashed with the current policy
     */
    public boolean requiresRehash(String encodedPassword, PasswordHashMetadata storedMetadata) {
        return requiresRehash(storedMetadata)
                || passwordEncoder.upgradeEncoding(Objects.requireNonNull(encodedPassword, "encodedPassword"));
    }

    @Override
    public boolean requiresRehash(PasswordHashRecord passwordHash) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return requiresRehash(
                passwordHash.encodedHash(),
                new PasswordHashMetadata(
                        passwordHash.algorithm(), passwordHash.version(), passwordHash.parameters()));
    }
}
