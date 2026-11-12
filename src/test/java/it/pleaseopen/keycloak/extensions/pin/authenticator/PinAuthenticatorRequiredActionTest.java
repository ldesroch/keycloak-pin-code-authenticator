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
package it.pleaseopen.keycloak.extensions.pin.authenticator;

import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialConstants;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the "Force PIN Configuration" ({@code pin.requiredAction}) option
 * handled by {@link PinAuthenticator#setRequiredActions}.
 */
@DisplayName("PinAuthenticator Force PIN Configuration Tests")
class PinAuthenticatorRequiredActionTest {

    private static final String PIN_EXECUTION_ID = "pin-execution";
    private static final String PIN_CONFIG_ID = "pin-config";

    private final PinAuthenticator authenticator = new PinAuthenticator();

    private KeycloakSession session;
    private RealmModel realm;
    private UserModel user;
    private PinCredentialProvider pinProvider;
    private Map<String, AuthenticationSessionModel.ExecutionStatus> executionStatus;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
        pinProvider = mock(PinCredentialProvider.class);
        executionStatus = new HashMap<>();

        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        when(authSession.getExecutionStatus()).thenReturn(executionStatus);
        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authSession);
        when(keycloakContext.getRealm()).thenReturn(realm);
        when(session.getContext()).thenReturn(keycloakContext);
        when(session.getProvider(CredentialProvider.class, PinCredentialProviderFactory.PROVIDER_ID))
                .thenReturn(pinProvider);

        // User has no PIN yet
        when(pinProvider.isConfiguredFor(any(), any(), eq(PinCredentialConstants.CREDENTIAL_TYPE))).thenReturn(false);
    }

    @Test
    @DisplayName("Force PIN Configuration enabled on the execution should add the CONFIGURE_PIN required action")
    void executionConfigEnabledAddsRequiredAction() {
        setUpPinExecution("true");

        authenticator.setRequiredActions(session, realm, user);

        verify(user).addRequiredAction("CONFIGURE_PIN");
    }

    @Test
    @DisplayName("Force PIN Configuration disabled on the execution should not add the required action")
    void executionConfigDisabledDoesNotAddRequiredAction() {
        setUpPinExecution("false");

        authenticator.setRequiredActions(session, realm, user);

        verify(user, never()).addRequiredAction(anyString());
    }

    @Test
    @DisplayName("Without any configuration the required action should not be added")
    void noConfigDoesNotAddRequiredAction() {
        authenticator.setRequiredActions(session, realm, user);

        verify(user, never()).addRequiredAction(anyString());
    }

    @Test
    @DisplayName("Legacy pin.requiredAction.force realm attribute should still be honored")
    void legacyRealmAttributeStillWorks() {
        when(realm.getAttribute("pin.requiredAction.force")).thenReturn("true");

        authenticator.setRequiredActions(session, realm, user);

        verify(user).addRequiredAction("CONFIGURE_PIN");
    }

    @Test
    @DisplayName("Users who already have a PIN should not get the required action")
    void configuredUserIsNotForced() {
        setUpPinExecution("true");
        when(pinProvider.isConfiguredFor(any(), any(), eq(PinCredentialConstants.CREDENTIAL_TYPE))).thenReturn(true);

        authenticator.setRequiredActions(session, realm, user);

        verify(user, never()).addRequiredAction(anyString());
    }

    @Test
    @DisplayName("Config of another authenticator in SETUP_REQUIRED state should be ignored")
    void otherAuthenticatorConfigIsIgnored() {
        AuthenticationExecutionModel otherExecution = new AuthenticationExecutionModel();
        otherExecution.setId("otp-execution");
        otherExecution.setAuthenticator("auth-otp-form");
        otherExecution.setAuthenticatorConfig("otp-config");
        when(realm.getAuthenticationExecutionById("otp-execution")).thenReturn(otherExecution);
        when(realm.getAuthenticatorConfigById("otp-config"))
                .thenReturn(config("pin.requiredAction", "true"));
        executionStatus.put("otp-execution", AuthenticationSessionModel.ExecutionStatus.SETUP_REQUIRED);

        authenticator.setRequiredActions(session, realm, user);

        verify(user, never()).addRequiredAction(anyString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setUpPinExecution(String forcePinConfiguration) {
        AuthenticationExecutionModel execution = new AuthenticationExecutionModel();
        execution.setId(PIN_EXECUTION_ID);
        execution.setAuthenticator(PinAuthenticatorFactory.PROVIDER_ID);
        execution.setAuthenticatorConfig(PIN_CONFIG_ID);
        when(realm.getAuthenticationExecutionById(PIN_EXECUTION_ID)).thenReturn(execution);
        when(realm.getAuthenticatorConfigById(PIN_CONFIG_ID))
                .thenReturn(config("pin.requiredAction", forcePinConfiguration));
        // Keycloak marks the execution as SETUP_REQUIRED right before calling setRequiredActions
        executionStatus.put(PIN_EXECUTION_ID, AuthenticationSessionModel.ExecutionStatus.SETUP_REQUIRED);
    }

    private static AuthenticatorConfigModel config(String key, String value) {
        AuthenticatorConfigModel config = new AuthenticatorConfigModel();
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        config.setConfig(map);
        return config;
    }
}
