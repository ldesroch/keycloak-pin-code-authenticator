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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.credential.CredentialModel;

import java.io.IOException;
import java.util.Map;

/**
 * Model representing PIN credential data stored in Keycloak.
 *
 * <p>Storage layout (mirrors Keycloak's PasswordCredentialModel):
 * <ul>
 *   <li><b>secretData</b> — JSON: {@code {"value":"<base64-hash>","salt":"<base64-salt>"}}</li>
 *   <li><b>credentialData</b> — JSON: {@code {"algorithm":"argon2","hashIterations":5,
 *       "pinFormat":"6-digits","customRegex":null,"additionalParameters":{...}}}</li>
 * </ul>
 */
public class PinCredentialModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecretData secretData;
    private final CredentialData credentialData;

    public PinCredentialModel(SecretData secretData, CredentialData credentialData) {
        this.secretData = secretData;
        this.credentialData = credentialData;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getHashedPin()       { return secretData.value; }
    public String getSalt()            { return secretData.salt; }
    public String getAlgorithm()       { return credentialData.algorithm; }
    public int    getHashIterations()  { return credentialData.hashIterations; }
    public String getPinFormat()       { return credentialData.pinFormat; }
    public String getCustomRegex()     { return credentialData.customRegex; }
    public Map<String, String> getAdditionalParameters() {
        return credentialData.additionalParameters;
    }
    public String getAdditionalParam(String key) {
        return credentialData.additionalParameters == null ? null
                : credentialData.additionalParameters.get(key);
    }

    // Compat aliases
    public String getPinHash() { return getHashedPin(); }
    public String getFormat()  { return getPinFormat(); }

    // ── Factory ──────────────────────────────────────────────────────────────

    public static PinCredentialModel create(String hashValue, String salt,
                                            String algorithm, int iterations,
                                            String pinFormat, String customRegex,
                                            Map<String, String> additionalParams) {
        SecretData sd = new SecretData(hashValue, salt);
        CredentialData cd = new CredentialData(algorithm, iterations, pinFormat, customRegex, additionalParams);
        return new PinCredentialModel(sd, cd);
    }

    /** Converts to a Keycloak {@link CredentialModel} for persistence. */
    public CredentialModel toCredentialModel() {
        try {
            CredentialModel model = new CredentialModel();
            model.setType(PinCredentialConstants.CREDENTIAL_TYPE);
            model.setCreatedDate(System.currentTimeMillis());
            model.setSecretData(MAPPER.writeValueAsString(secretData));
            model.setCredentialData(MAPPER.writeValueAsString(credentialData));
            return model;
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize PIN credential", e);
        }
    }

    /** Creates from a stored Keycloak {@link CredentialModel}. */
    public static PinCredentialModel createFromCredentialModel(CredentialModel model) {
        if (model == null || !PinCredentialConstants.CREDENTIAL_TYPE.equals(model.getType())) {
            return null;
        }
        try {
            SecretData sd = MAPPER.readValue(model.getSecretData(), SecretData.class);
            CredentialData cd = MAPPER.readValue(model.getCredentialData(), CredentialData.class);
            return new PinCredentialModel(sd, cd);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Inner DTOs ───────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SecretData {
        @JsonProperty("value") public String value;
        @JsonProperty("salt")  public String salt;

        @JsonCreator
        public SecretData(@JsonProperty("value") String value,
                          @JsonProperty("salt") String salt) {
            this.value = value;
            this.salt = salt;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CredentialData {
        @JsonProperty("algorithm")            public String algorithm;
        @JsonProperty("hashIterations")       public int hashIterations;
        @JsonProperty("pinFormat")            public String pinFormat;
        @JsonProperty("customRegex")          public String customRegex;
        @JsonProperty("additionalParameters") public Map<String, String> additionalParameters;

        @JsonCreator
        public CredentialData(@JsonProperty("algorithm") String algorithm,
                              @JsonProperty("hashIterations") int hashIterations,
                              @JsonProperty("pinFormat") String pinFormat,
                              @JsonProperty("customRegex") String customRegex,
                              @JsonProperty("additionalParameters") Map<String, String> additionalParameters) {
            this.algorithm = algorithm;
            this.hashIterations = hashIterations;
            this.pinFormat = pinFormat;
            this.customRegex = customRegex;
            this.additionalParameters = additionalParameters;
        }
    }

}
