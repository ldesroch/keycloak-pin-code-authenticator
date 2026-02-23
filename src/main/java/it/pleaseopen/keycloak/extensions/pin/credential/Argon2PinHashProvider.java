/*
 * Copyright 2026 Pin Code Authenticator Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.pleaseopen.keycloak.extensions.pin.credential;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jboss.logging.Logger;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Argon2id PIN hash provider — mirrors Keycloak's own Argon2 password hash provider.
 *
 * <p>Default parameters (same as Keycloak 26 defaults):
 * <ul>
 *   <li>type = id (Argon2id)</li>
 *   <li>version = 1.3</li>
 *   <li>memory = 7168 KB</li>
 *   <li>iterations = 5</li>
 *   <li>parallelism = 1</li>
 *   <li>hashLength = 32</li>
 *   <li>saltLength = 16</li>
 * </ul>
 */
public class Argon2PinHashProvider implements PinHashProvider {

    private static final Logger logger = Logger.getLogger(Argon2PinHashProvider.class);

    public static final String ALGORITHM = "argon2";

    // Parameter keys stored in additionalParameters
    public static final String TYPE_KEY        = "type";
    public static final String VERSION_KEY     = "version";
    public static final String HASH_LENGTH_KEY = "hashLength";
    public static final String MEMORY_KEY      = "memory";
    public static final String PARALLELISM_KEY = "parallelism";

    // Defaults — same as Keycloak
    public static final String DEFAULT_TYPE       = "id";
    public static final String DEFAULT_VERSION    = "1.3";
    public static final int    DEFAULT_HASH_LENGTH = 32;
    public static final int    DEFAULT_MEMORY      = 7168;
    public static final int    DEFAULT_ITERATIONS  = 5;
    public static final int    DEFAULT_PARALLELISM = 1;
    public static final int    SALT_LENGTH         = 16;

    private final String type;
    private final String version;
    private final int hashLength;
    private final int memory;
    private final int defaultIterations;
    private final int parallelism;

    /** Default constructor using Keycloak defaults. */
    public Argon2PinHashProvider() {
        this(DEFAULT_TYPE, DEFAULT_VERSION, DEFAULT_HASH_LENGTH,
             DEFAULT_MEMORY, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    public Argon2PinHashProvider(String type, String version, int hashLength,
                                 int memory, int iterations, int parallelism) {
        this.type = type;
        this.version = version;
        this.hashLength = hashLength;
        this.memory = memory;
        this.defaultIterations = iterations;
        this.parallelism = parallelism;
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM;
    }

    @Override
    public EncodedPin encode(String rawPin, int iterations) {
        if (iterations <= 0 || iterations > 100) {
            iterations = defaultIterations;
        }
        byte[] salt = generateSalt();
        String hash = argon2Hash(rawPin, salt, version, type, hashLength, parallelism, memory, iterations);
        String saltB64 = Base64.getEncoder().encodeToString(salt);

        Map<String, String> params = new HashMap<>();
        params.put(TYPE_KEY, type);
        params.put(VERSION_KEY, version);
        params.put(HASH_LENGTH_KEY, String.valueOf(hashLength));
        params.put(MEMORY_KEY, String.valueOf(memory));
        params.put(PARALLELISM_KEY, String.valueOf(parallelism));

        return new EncodedPin(hash, saltB64, params);
    }

    @Override
    public boolean verify(String rawPin, String storedHash, String storedSalt,
                          int iterations, Map<String, String> additionalParams) {
        if (storedHash == null || storedSalt == null) return false;

        // Read parameters from stored credential (allows verifying old-param credentials)
        String vType = getParam(additionalParams, TYPE_KEY, type);
        String vVersion = getParam(additionalParams, VERSION_KEY, version);
        int vHashLen = getIntParam(additionalParams, HASH_LENGTH_KEY, hashLength);
        int vMemory = getIntParam(additionalParams, MEMORY_KEY, memory);
        int vParallelism = getIntParam(additionalParams, PARALLELISM_KEY, parallelism);

        byte[] salt = Base64.getDecoder().decode(storedSalt);
        String computed = argon2Hash(rawPin, salt, vVersion, vType, vHashLen, vParallelism, vMemory, iterations);
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                computed.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public boolean policyMatch(String algorithm, int iterations, Map<String, String> additionalParams) {
        if (!ALGORITHM.equals(algorithm)) return false;
        if (iterations != defaultIterations) return false;
        if (!type.equals(getParam(additionalParams, TYPE_KEY, type))) return false;
        if (!version.equals(getParam(additionalParams, VERSION_KEY, version))) return false;
        if (hashLength != getIntParam(additionalParams, HASH_LENGTH_KEY, hashLength)) return false;
        if (memory != getIntParam(additionalParams, MEMORY_KEY, memory)) return false;
        return parallelism == getIntParam(additionalParams, PARALLELISM_KEY, parallelism);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private String argon2Hash(String rawPin, byte[] salt, String ver, String typ,
                              int hLen, int par, int mem, int iters) {
        int typeValue = getTypeValue(typ);
        int versionValue = getVersionValue(ver);

        Argon2Parameters params = new Argon2Parameters.Builder(typeValue)
                .withVersion(versionValue)
                .withSalt(salt)
                .withParallelism(par)
                .withMemoryAsKB(mem)
                .withIterations(iters)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] result = new byte[hLen];
        generator.generateBytes(rawPin.toCharArray(), result);
        return Base64.getEncoder().encodeToString(result);
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static int getTypeValue(String type) {
        switch (type) {
            case "id": return Argon2Parameters.ARGON2_id;
            case "d":  return Argon2Parameters.ARGON2_d;
            case "i":  return Argon2Parameters.ARGON2_i;
            default:   return Argon2Parameters.ARGON2_id;
        }
    }

    private static int getVersionValue(String version) {
        switch (version) {
            case "1.3": return Argon2Parameters.ARGON2_VERSION_13;
            case "1.0": return Argon2Parameters.ARGON2_VERSION_10;
            default:    return Argon2Parameters.ARGON2_VERSION_13;
        }
    }

    private static String getParam(Map<String, String> params, String key, String defaultVal) {
        if (params == null) return defaultVal;
        String v = params.get(key);
        return v != null ? v : defaultVal;
    }

    private static int getIntParam(Map<String, String> params, String key, int defaultVal) {
        if (params == null) return defaultVal;
        String v = params.get(key);
        if (v == null) return defaultVal;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    @Override
    public void close() {
        // No resources to release
    }
}
