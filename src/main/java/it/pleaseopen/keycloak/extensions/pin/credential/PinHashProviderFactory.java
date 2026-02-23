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

import org.keycloak.provider.ProviderFactory;

/**
 * Factory for {@link PinHashProvider} implementations.
 *
 * <p>Follows the same pattern as Keycloak's {@code PasswordHashProviderFactory}.
 * Each factory is identified by its {@link #getId()} which corresponds to the
 * algorithm name stored in credential data (e.g. "argon2").
 * The factory with the highest {@link #order()} value becomes the default
 * provider returned by {@code session.getProvider(PinHashProvider.class)}.</p>
 */
public interface PinHashProviderFactory extends ProviderFactory<PinHashProvider> {
}
