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

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for {@link Argon2PinHashProvider}.
 *
 * <p>Follows the same pattern as Keycloak's {@code Argon2PasswordHashProviderFactory}.
 * Configuration is read from Keycloak's {@code Config.Scope} during {@link #init(Config.Scope)}
 * and passed to every provider instance created by {@link #create(KeycloakSession)}.</p>
 *
 * <p>Default parameters match Keycloak 26's password hashing defaults:
 * <ul>
 *   <li>type = id (Argon2id)</li>
 *   <li>version = 1.3</li>
 *   <li>memory = 7168 KB</li>
 *   <li>iterations = 5</li>
 *   <li>parallelism = 1</li>
 *   <li>hashLength = 32</li>
 * </ul>
 */
public class Argon2PinHashProviderFactory implements PinHashProviderFactory {

    public static final String ID = "argon2";

    // Configuration keys
    public static final String TYPE_KEY        = "type";
    public static final String VERSION_KEY     = "version";
    public static final String HASH_LENGTH_KEY = "hashLength";
    public static final String MEMORY_KEY      = "memory";
    public static final String ITERATIONS_KEY  = "iterations";
    public static final String PARALLELISM_KEY = "parallelism";

    // Defaults — same as Keycloak 26
    public static final String DEFAULT_TYPE        = "id";
    public static final String DEFAULT_VERSION     = "1.3";
    public static final int    DEFAULT_HASH_LENGTH = 32;
    public static final int    DEFAULT_MEMORY      = 7168;
    public static final int    DEFAULT_ITERATIONS  = 5;
    public static final int    DEFAULT_PARALLELISM = 1;

    private String type;
    private String version;
    private int hashLength;
    private int memory;
    private int iterations;
    private int parallelism;

    @Override
    public PinHashProvider create(KeycloakSession session) {
        return new Argon2PinHashProvider(type, version, hashLength, memory, iterations, parallelism);
    }

    @Override
    public void init(Config.Scope config) {
        type        = config.get(TYPE_KEY, DEFAULT_TYPE);
        version     = config.get(VERSION_KEY, DEFAULT_VERSION);
        hashLength  = config.getInt(HASH_LENGTH_KEY, DEFAULT_HASH_LENGTH);
        memory      = config.getInt(MEMORY_KEY, DEFAULT_MEMORY);
        iterations  = config.getInt(ITERATIONS_KEY, DEFAULT_ITERATIONS);
        parallelism = config.getInt(PARALLELISM_KEY, DEFAULT_PARALLELISM);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // No cleanup needed
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name(TYPE_KEY)
                    .type("string")
                    .helpText("Argon2 type (id, i, or d)")
                    .defaultValue(DEFAULT_TYPE)
                    .add()
                .property()
                    .name(VERSION_KEY)
                    .type("string")
                    .helpText("Argon2 version (1.3 or 1.0)")
                    .defaultValue(DEFAULT_VERSION)
                    .add()
                .property()
                    .name(HASH_LENGTH_KEY)
                    .type("int")
                    .helpText("Hash output length in bytes")
                    .defaultValue(DEFAULT_HASH_LENGTH)
                    .add()
                .property()
                    .name(MEMORY_KEY)
                    .type("int")
                    .helpText("Memory size in KB")
                    .defaultValue(DEFAULT_MEMORY)
                    .add()
                .property()
                    .name(ITERATIONS_KEY)
                    .type("int")
                    .helpText("Number of iterations")
                    .defaultValue(DEFAULT_ITERATIONS)
                    .add()
                .property()
                    .name(PARALLELISM_KEY)
                    .type("int")
                    .helpText("Degree of parallelism")
                    .defaultValue(DEFAULT_PARALLELISM)
                    .add()
                .build();
    }
}
