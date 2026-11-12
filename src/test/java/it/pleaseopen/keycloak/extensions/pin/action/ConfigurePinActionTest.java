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
package it.pleaseopen.keycloak.extensions.pin.action;

import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialConstants;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfigurePinAction}.
 */
@DisplayName("ConfigurePinAction Tests")
class ConfigurePinActionTest {

    @Test
    @DisplayName("Configuring a new PIN should reset the failed attempt counter")
    void newPinResetsFailedAttempts() {
        RequiredActionContext context = mock(RequiredActionContext.class);
        KeycloakSession session = mock(KeycloakSession.class);
        PinCredentialProvider pinProvider = mock(PinCredentialProvider.class);
        HttpRequest httpRequest = mock(HttpRequest.class);
        UserModel user = mock(UserModel.class);

        MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
        formData.putSingle("newPin", "1234");
        formData.putSingle("confirmPin", "1234");

        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(mock(RealmModel.class));
        when(context.getUser()).thenReturn(user);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);
        when(session.getProvider(CredentialProvider.class, PinCredentialProviderFactory.PROVIDER_ID))
                .thenReturn(pinProvider);
        when(pinProvider.isValidPinFormat(eq("1234"), anyString(), any())).thenReturn(true);
        when(pinProvider.createCredential(any(), any(), eq("1234"), anyString(), any()))
                .thenReturn(new CredentialModel());

        new ConfigurePinAction().processAction(context);

        verify(user).removeAttribute(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS);
        verify(context).success();
    }
}
