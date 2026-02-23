/**
 * Puppeteer E2E test for PIN Reset flow.
 *
 * Tests:
 *   1. Reset PIN link visible when resetEnabled=true
 *   2. Reset PIN link hidden when resetEnabled=false
 *   3. Full reset PIN flow via email (Mailpit)
 *   4. Admin REST API: POST /realms/{realm}/pin-reset
 *
 * Prerequisites:
 *   - docker compose up -d  (Keycloak + postgres + mailpit)
 *   - PIN extension JAR built and deployed
 *   - pin-test realm with testuser/password123
 *   - Mailpit running on localhost:8025 (SMTP on 1025)
 *
 * Usage:
 *   node test-pin-reset.js              # Run full suite headless
 *   node test-pin-reset.js --headed     # Show browser
 *   node test-pin-reset.js --test 1     # Run specific test
 */

const puppeteer = require('puppeteer');
const crypto    = require('crypto');
const { execSync } = require('child_process');
const http       = require('http');

// ─── Configuration ────────────────────────────────────────────────────────────
const KEYCLOAK_URL = 'http://localhost:8080';
const REALM        = 'pin-test';
const CLIENT_ID    = 'pin-test-client';
const REDIRECT_URI = 'http://localhost:8888/callback';
const USERNAME     = 'testuser';
const PASSWORD     = 'password123';
const PIN_VALID    = 'Abc12345';   // 8 word chars matching [\w]{8}
const PIN_NEW      = 'New12345';   // 8 word chars — new PIN after reset
const MAILPIT_API  = 'http://localhost:8025/api';

const HEADED  = process.argv.includes('--headed');
const ONLY    = (() => { const i = process.argv.indexOf('--test'); return i !== -1 ? parseInt(process.argv[i+1]) : null; })();
const SLOWMO  = HEADED ? 80 : 0;

// ─── Helpers ──────────────────────────────────────────────────────────────────
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

/** Build auth URL requesting LOA 2 (which triggers PIN step) */
function authUrlLoa2() {
    const verifier  = crypto.randomBytes(32).toString('base64url');
    const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
    const claims = JSON.stringify({ id_token: { acr: { essential: true, values: ['2'] }}});
    return `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth` +
           `?client_id=${CLIENT_ID}` +
           `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
           `&response_type=code&scope=openid` +
           `&code_challenge=${challenge}&code_challenge_method=S256` +
           `&claims=${encodeURIComponent(claims)}`;
}

/** Build standard auth URL (LOA 1 only) */
function authUrl() {
    const verifier  = crypto.randomBytes(32).toString('base64url');
    const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
    return `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth` +
           `?client_id=${CLIENT_ID}` +
           `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
           `&response_type=code&scope=openid` +
           `&code_challenge=${challenge}&code_challenge_method=S256`;
}

function kcadm(...args) {
    const cmd = `docker exec keycloak-pin-server /opt/keycloak/bin/kcadm.sh ${args.join(' ')}`;
    try {
        return execSync(cmd, { encoding: 'utf-8', timeout: 15000 }).trim();
    } catch (err) {
        throw new Error(`kcadm failed: ${err.stderr || err.message}`);
    }
}

function kcadmWithBody(subcommand, resource, realm, body) {
    const bodyStr = JSON.stringify(body);
    const cmd = `echo '${bodyStr.replace(/'/g, "'\\''")}' | docker exec -i keycloak-pin-server /opt/keycloak/bin/kcadm.sh ${subcommand} ${resource} -r ${realm} -f -`;
    try {
        return execSync(cmd, { encoding: 'utf-8', timeout: 15000 }).trim();
    } catch (err) {
        throw new Error(`kcadm body failed: ${err.stderr || err.message}`);
    }
}

/** HTTP GET helper */
function httpGet(url) {
    return new Promise((resolve, reject) => {
        http.get(url, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(data)); }
                catch (e) { resolve(data); }
            });
        }).on('error', reject);
    });
}

/** HTTP DELETE helper */
function httpDelete(url) {
    return new Promise((resolve, reject) => {
        const u = new URL(url);
        const opts = { hostname: u.hostname, port: u.port, path: u.pathname + u.search, method: 'DELETE' };
        const req = http.request(opts, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve(data));
        });
        req.on('error', reject);
        req.end();
    });
}

/** HTTP POST with bearer token */
function httpPostWithAuth(url, body, token) {
    return new Promise((resolve, reject) => {
        const u = new URL(url);
        const payload = JSON.stringify(body);
        const opts = {
            hostname: u.hostname, port: u.port, path: u.pathname + u.search,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
                'Authorization': `Bearer ${token}`
            }
        };
        const req = http.request(opts, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve({ status: res.statusCode, body: data }));
        });
        req.on('error', reject);
        req.write(payload);
        req.end();
    });
}

/** Authenticate kcadm CLI */
function kcadmAuth() {
    kcadm('config', 'credentials', '--server', 'http://localhost:8080',
          '--realm', 'master', '--user', 'admin', '--password', 'admin');
}

/** Get testuser ID */
function getUserId() {
    const usersJson = kcadm('get', 'users', '-r', REALM, '-q', `username=${USERNAME}`, '--fields', 'id');
    const users = JSON.parse(usersJson);
    if (users.length === 0) throw new Error('testuser not found');
    return users[0].id;
}

/** Clear required actions on testuser */
function clearRequiredActions() {
    kcadmAuth();
    const userId = getUserId();
    kcadmWithBody('update', `users/${userId}`, REALM, { requiredActions: [] });
}

/** Ensure testuser has a PIN credential. If not, configure one via browser. */
async function ensureUserHasPin(browser) {
    kcadmAuth();
    const userId = getUserId();

    // Check if user has PIN credential
    const credsJson = kcadm('get', `users/${userId}/credentials`, '-r', REALM, '--fields', 'type');
    const creds = JSON.parse(credsJson);
    const hasPin = creds.some(c => c.type === 'pin-code');

    if (hasPin) {
        // Clear required actions
        kcadmWithBody('update', `users/${userId}`, REALM, { requiredActions: [] });
        console.log('    User already has PIN configured');
        return PIN_VALID;
    }

    // Need to configure PIN via browser
    console.log('    Configuring PIN for user via browser...');
    kcadmWithBody('update', `users/${userId}`, REALM, { requiredActions: ['CONFIGURE_PIN'] });

    const ctx = await browser.createBrowserContext();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    try {
        await page.goto(authUrl(), { waitUntil: 'networkidle0', timeout: 20000 });

        // Login
        await page.waitForSelector('#username', { timeout: 15000 });
        await page.type('#username', USERNAME, { delay: 30 });
        await page.type('#password', PASSWORD, { delay: 30 });
        await page.click('#kc-login');
        await sleep(3000);

        // Should be on configure PIN page
        const content = await page.content();
        if (!content.includes('newPin')) {
            throw new Error('Expected Configure PIN page');
        }

        // Configure PIN
        await typePin(page, '#newPin', PIN_VALID);
        await sleep(300);
        await typePin(page, '#confirmPin', PIN_VALID);
        await sleep(300);

        const submitBtn = await page.$('input[type="submit"]');
        if (submitBtn) await submitBtn.click();
        await sleep(3000);

        console.log('    PIN configured successfully');
    } finally {
        await page.close();
        await ctx.close();
    }

    // Clear required actions after configuring
    kcadmWithBody('update', `users/${userId}`, REALM, { requiredActions: [] });

    return PIN_VALID;
}

/** Configure SMTP to point to Mailpit */
function configureSmtp() {
    console.log('  Configuring SMTP to use Mailpit...');
    kcadmAuth();
    kcadmWithBody('update', 'realms/' + REALM, 'master', {
        smtpServer: {
            host: 'mailpit',
            port: '1025',
            from: 'keycloak@pin-test.local',
            fromDisplayName: 'Keycloak PIN Test',
            ssl: 'false',
            starttls: 'false',
            auth: 'false'
        }
    });
    console.log('    SMTP configured');
}

/** Enable or disable pin.resetEnabled on the authenticator config */
function setResetEnabled(enabled) {
    console.log(`  Setting pin.resetEnabled=${enabled}...`);
    kcadmAuth();

    // Get executions — try known flow names
    const flowNames = ['browser-with-pin-loa', 'Browser%20with%20PIN%20forms'];
    let execs = null;
    for (const flow of flowNames) {
        try {
            const execsJson = kcadm('get', `authentication/flows/${flow}/executions`, '-r', REALM);
            execs = JSON.parse(execsJson);
            break;
        } catch (e) { /* try next */ }
    }
    if (!execs) throw new Error('Could not find PIN authentication flow');

    const pinExec = execs.find(e => e.providerId === 'pin-authenticator');
    if (!pinExec) throw new Error('pin-authenticator execution not found');

    const configId = pinExec.authenticationConfig;
    if (configId) {
        // Update existing config
        const configJson = kcadm('get', `authentication/config/${configId}`, '-r', REALM);
        const config = JSON.parse(configJson);
        config.config['pin.resetEnabled'] = String(enabled);
        kcadmWithBody('update', `authentication/config/${configId}`, REALM, config);
    } else {
        // Create new config
        kcadmWithBody('create', `authentication/executions/${pinExec.id}/config`, REALM, {
            alias: 'PIN Authentication Config',
            config: {
                'pin.requiredAction': 'false',
                'maxAttempts': '3',
                'pin.resetEnabled': String(enabled)
            }
        });
    }
    console.log(`    pin.resetEnabled=${enabled}`);
}

/** Clear Mailpit inbox */
async function clearMailpit() {
    try {
        await httpDelete(`${MAILPIT_API}/v1/messages`);
    } catch (e) {
        console.log('    Warning: Could not clear Mailpit:', e.message);
    }
}

/** Wait for a message in Mailpit and return the first one */
async function waitForEmail(maxWaitMs = 15000) {
    const start = Date.now();
    while (Date.now() - start < maxWaitMs) {
        try {
            const data = await httpGet(`${MAILPIT_API}/v1/messages`);
            if (data.messages && data.messages.length > 0) {
                const msgId = data.messages[0].ID;
                const msg = await httpGet(`${MAILPIT_API}/v1/message/${msgId}`);
                return msg;
            }
        } catch (e) { /* retry */ }
        await sleep(1000);
    }
    throw new Error('No email received within timeout');
}

/** Navigate to auth URL with retry, handling connection refused (callback redirect) */
async function navigateToAuth(page, url) {
    const target = url || authUrl();
    for (let attempt = 0; attempt < 3; attempt++) {
        try {
            await page.goto(target, { waitUntil: 'networkidle0', timeout: 20000 });
            return;
        } catch (err) {
            if (err.message.includes('ERR_CONNECTION_REFUSED') && page.url().includes('callback')) {
                // Redirected to callback but no server — that's OK
                return;
            }
            if (attempt < 2) {
                console.log(`    Retry navigation (attempt ${attempt + 2})...`);
                await sleep(5000);
            } else {
                throw err;
            }
        }
    }
}

/** Fill login form */
async function doLogin(page) {
    await page.waitForSelector('#username', { timeout: 15000 });
    await page.type('#username', USERNAME, { delay: 30 });
    await page.type('#password', PASSWORD, { delay: 30 });
    await page.click('#kc-login');
    await sleep(3000);
}

/** Type a PIN into a hidden input */
async function typePin(page, inputSelector, pin) {
    const input = await page.$(inputSelector);
    if (!input) {
        const altSelector = `input[name="${inputSelector.replace('#', '')}"]`;
        const altInput = await page.$(altSelector);
        if (!altInput) throw new Error(`Input ${inputSelector} not found`);
        await page.evaluate((sel) => {
            const el = document.querySelector(sel);
            if (el) { el.style.position = 'relative'; el.style.left = '0'; el.style.opacity = '1'; el.style.height = 'auto'; el.style.width = '100%'; }
        }, altSelector);
        await altInput.click();
        await altInput.type(pin, { delay: 50 });
        return;
    }
    await page.evaluate((sel) => {
        const el = document.querySelector(sel);
        if (el) { el.style.position = 'relative'; el.style.left = '0'; el.style.opacity = '1'; el.style.height = 'auto'; el.style.width = '100%'; }
    }, inputSelector);
    await input.click();
    await input.type(pin, { delay: 50 });
}

/** Navigate to auth with LOA 2 claims, login, and reach PIN page.
 *  Returns the page on the PIN authenticator form. */
async function loginAndReachPinPage(context) {
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    await navigateToAuth(page, authUrlLoa2());
    await sleep(1000);

    let content = await page.content();

    // If we see the login form, fill it in
    if (content.includes('id="username"') || content.includes('name="username"')) {
        await doLogin(page);
        content = await page.content();
    }

    // If we see configure PIN (shouldn't happen if ensureUserHasPin ran), handle it
    if (content.includes('newPin') && content.includes('confirmPin')) {
        await typePin(page, '#newPin', PIN_VALID);
        await sleep(300);
        await typePin(page, '#confirmPin', PIN_VALID);
        await sleep(300);
        const submitBtn = await page.$('input[type="submit"]');
        if (submitBtn) await submitBtn.click();
        await sleep(3000);

        // Navigate again for a fresh session
        await navigateToAuth(page, authUrlLoa2());
        await sleep(1000);
        content = await page.content();
        if (content.includes('id="username"') || content.includes('name="username"')) {
            await doLogin(page);
            content = await page.content();
        }
    }

    // We should now be on the PIN authenticator page
    const isPinPage = content.includes('pin-dot') || content.includes('pin-authenticator') ||
                      content.includes('pin-dots-dynamic') || content.includes('id="pin"');
    if (!isPinPage) {
        const url = page.url();
        if (url.includes('callback') || url.includes('code=')) {
            throw new Error('Already authenticated at LOA 2 — session active, cannot test PIN page');
        }
        const bodyText = await page.evaluate(() => document.body?.innerText?.substring(0, 300) || '');
        throw new Error('Expected PIN authenticator page. URL=' + url + ' Content: ' + bodyText.substring(0, 200));
    }

    return page;
}

/** Setup admin service account in pin-test realm with 'admin' realm role */
function setupRealmAdmin() {
    console.log('  Setting up realm admin service account...');
    kcadmAuth();

    // Create 'admin' realm role if it doesn't exist
    try {
        kcadm('get', 'roles/admin', '-r', REALM);
    } catch (e) {
        kcadmWithBody('create', 'roles', REALM, { name: 'admin', description: 'Realm admin role' });
        console.log('    Created admin realm role');
    }

    // Create pin-admin-client (confidential with service account)
    try {
        const clientsJson = kcadm('get', 'clients', '-r', REALM, '-q', 'clientId=pin-admin-client', '--fields', 'id');
        const clients = JSON.parse(clientsJson);
        if (clients.length === 0) {
            kcadmWithBody('create', 'clients', REALM, {
                clientId: 'pin-admin-client',
                enabled: true,
                publicClient: false,
                secret: 'pin-admin-secret',
                serviceAccountsEnabled: true,
                directAccessGrantsEnabled: false,
                standardFlowEnabled: false
            });
            console.log('    Created pin-admin-client');
        }

        // Get client UUID
        const clientsJson2 = kcadm('get', 'clients', '-r', REALM, '-q', 'clientId=pin-admin-client', '--fields', 'id');
        const clientUUID = JSON.parse(clientsJson2)[0].id;

        // Get service account user
        const serviceUserJson = kcadm('get', `clients/${clientUUID}/service-account-user`, '-r', REALM, '--fields', 'id');
        const serviceUserId = JSON.parse(serviceUserJson).id;

        // Assign admin role to service account
        kcadm('add-roles', '-r', REALM, '--uid', serviceUserId, '--rolename', 'admin');
        console.log('    Assigned admin role to service account');
    } catch (e) {
        console.log('    setupRealmAdmin: ' + e.message);
    }
}

/** Get a pin-test realm admin token via client credentials */
async function getRealmAdminToken() {
    setupRealmAdmin();
    return new Promise((resolve, reject) => {
        const payload = 'grant_type=client_credentials&client_id=pin-admin-client&client_secret=pin-admin-secret';
        const u = new URL(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`);
        const opts = {
            hostname: u.hostname, port: u.port, path: u.pathname,
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': Buffer.byteLength(payload) }
        };
        const req = http.request(opts, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    if (json.access_token) resolve(json.access_token);
                    else reject(new Error(json.error_description || json.error || 'Token request failed'));
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(payload);
        req.end();
    });
}

// ─── Test Suite ───────────────────────────────────────────────────────────────
const tests = [];
let passed = 0;
let failed = 0;

function test(num, name, fn) {
    tests.push({ num, name, fn });
}

// ──────────────────────────────────────────────────────────────────────────────
// TEST 1: Reset PIN link appears when resetEnabled=true
// ──────────────────────────────────────────────────────────────────────────────
test(1, 'Reset PIN link visible when resetEnabled=true', async (context, browser) => {
    // Setup: user needs PIN, resetEnabled=true
    await ensureUserHasPin(browser);
    clearRequiredActions();
    setResetEnabled(true);

    const page = await loginAndReachPinPage(context);

    try {
        const hasResetLink = await page.evaluate(() => !!document.querySelector('#reset-pin-link'));
        if (!hasResetLink) {
            const bodyText = await page.evaluate(() => document.body?.innerText?.substring(0, 500) || '');
            throw new Error('Reset PIN link not found on page. Content: ' + bodyText.substring(0, 200));
        }
        console.log('    OK: Reset PIN link is visible');
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 2: Reset PIN link hidden when resetEnabled=false
// ──────────────────────────────────────────────────────────────────────────────
test(2, 'Reset PIN link hidden when resetEnabled=false', async (context, browser) => {
    await ensureUserHasPin(browser);
    clearRequiredActions();
    setResetEnabled(false);

    const page = await loginAndReachPinPage(context);

    try {
        const hasResetLink = await page.evaluate(() => !!document.querySelector('#reset-pin-link'));
        if (hasResetLink) {
            throw new Error('Reset PIN link should NOT be visible when resetEnabled=false');
        }
        console.log('    OK: Reset PIN link is correctly hidden');
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 3: Full reset PIN email flow
// ──────────────────────────────────────────────────────────────────────────────
test(3, 'Full reset PIN flow via email', async (context, browser) => {
    // Setup
    configureSmtp();
    await ensureUserHasPin(browser);
    clearRequiredActions();
    setResetEnabled(true);
    await clearMailpit();

    const page = await loginAndReachPinPage(context);

    try {
        // Step 1: Verify we're on PIN page and reset link is present
        let content = await page.content();
        const resetLink = await page.$('#reset-pin-link');
        if (!resetLink) throw new Error('Reset PIN link not found');
        console.log('    Step 1: On PIN page with reset link');

        // Step 2: Click 'Reset PIN credential?' link
        await resetLink.click();
        await sleep(4000);

        // Step 3: Check page after click — should show info about email sent
        content = await page.content();
        const bodyText = await page.evaluate(() => document.body?.innerText || '');
        console.log('    Step 2: Page after clicking reset link: ' + bodyText.substring(0, 150));

        // Step 4: Check Mailpit for the email
        console.log('    Step 3: Waiting for email in Mailpit...');
        const email = await waitForEmail(15000);
        if (!email) throw new Error('No email received');
        console.log(`    Step 3: Email received — Subject: ${email.Subject || 'N/A'}`);

        // Step 5: Extract the reset link from the email body
        const emailText = email.Text || email.HTML || '';
        const linkMatch = emailText.match(/https?:\/\/[^\s"<>]+action-token[^\s"<>]*/);
        if (!linkMatch) {
            console.log('    Email body: ' + emailText.substring(0, 500));
            throw new Error('No action-token link found in email');
        }
        const resetUrl = linkMatch[0];
        console.log('    Step 4: Found reset link in email');

        // Step 6: Navigate to the reset link in a new page
        const resetPage = await context.newPage();
        await resetPage.setViewport({ width: 1280, height: 800 });

        try {
            await resetPage.goto(resetUrl, { waitUntil: 'networkidle0', timeout: 20000 });
            await sleep(3000);

            content = await resetPage.content();

            // Should be on configure PIN page
            if (content.includes('newPin') || content.includes('configure-pin') || content.includes('Configure PIN')) {
                console.log('    Step 5: On Configure PIN page after reset link');

                // Configure new PIN
                await typePin(resetPage, '#newPin', PIN_NEW);
                await sleep(300);
                await typePin(resetPage, '#confirmPin', PIN_NEW);
                await sleep(300);

                const submitBtn = await resetPage.$('input[type="submit"]');
                if (submitBtn) {
                    await submitBtn.click();
                    await sleep(3000);
                }
                console.log('    Step 6: New PIN configured');
            } else {
                const resetBodyText = await resetPage.evaluate(() => document.body?.innerText?.substring(0, 300) || '');
                console.log('    Step 5: After reset link: ' + resetBodyText.substring(0, 200));
            }
        } finally {
            await resetPage.close();
        }

        // Step 7: Verify login with new PIN
        clearRequiredActions();
        const verifyPage = await context.newPage();
        await verifyPage.setViewport({ width: 1280, height: 800 });

        let redirectedToCallback = false;
        verifyPage.on('request', req => {
            if (req.url().includes('callback') && req.url().includes('code=')) {
                redirectedToCallback = true;
            }
        });

        try {
            await navigateToAuth(verifyPage, authUrlLoa2());
            await sleep(1000);
            content = await verifyPage.content();

            if (content.includes('id="username"') || content.includes('name="username"')) {
                await doLogin(verifyPage);
                content = await verifyPage.content();
            }

            if (content.includes('pin-dot') || content.includes('pin-authenticator') || content.includes('pin-dots-dynamic')) {
                // Type new PIN
                await typePin(verifyPage, '#pin', PIN_NEW);
                await sleep(500);
                const submitBtn = await verifyPage.$('#submit-btn');
                if (submitBtn) await submitBtn.click();
                await sleep(3000);

                const finalUrl = verifyPage.url();
                if (redirectedToCallback || finalUrl.includes('callback') || finalUrl.includes('code=')) {
                    console.log('    Step 7: Login with new PIN successful!');
                } else {
                    content = await verifyPage.content();
                    if (content.includes('invalidPinMessage')) {
                        throw new Error('New PIN was rejected!');
                    }
                    console.log('    Step 7: Authentication completed');
                }
            } else if (redirectedToCallback || verifyPage.url().includes('callback')) {
                console.log('    Step 7: Redirected to callback (session active)');
            } else {
                console.log('    Step 7: Final state: ' + (await verifyPage.evaluate(() => document.body?.innerText?.substring(0, 100) || '')));
            }
        } finally {
            await verifyPage.close();
        }
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 4: Admin REST API — PIN reset
// ──────────────────────────────────────────────────────────────────────────────
test(4, 'Admin REST API: POST /realms/{realm}/pin-reset', async (context) => {
    configureSmtp();
    await clearMailpit();

    try {
        // Get realm admin token (from pin-test realm via service account)
        const token = await getRealmAdminToken();
        console.log('    Realm admin token acquired');

        // Call the pin-reset endpoint
        const result = await httpPostWithAuth(
            `${KEYCLOAK_URL}/realms/${REALM}/pin-reset`,
            { username: USERNAME },
            token
        );

        console.log(`    Response: ${result.status}`);

        if (result.status === 204) {
            console.log('    OK: PIN reset triggered via admin API');

            // Check Mailpit for the email
            const email = await waitForEmail(10000);
            if (email) {
                console.log(`    OK: Email received — Subject: ${email.Subject || 'N/A'}`);

                // Verify email contains action-token link
                const emailText = email.Text || email.HTML || '';
                if (emailText.includes('action-token') || emailText.includes('reset')) {
                    console.log('    OK: Email contains reset link');
                } else {
                    console.log('    WARNING: Email does not contain expected link');
                }
            }
        } else if (result.status === 401 || result.status === 403) {
            throw new Error(`Auth failed: ${result.status} — ${result.body}`);
        } else {
            console.log(`    Response body: ${result.body}`);
            throw new Error(`Unexpected response: ${result.status}`);
        }
    } catch (err) {
        throw err;
    }
});

// ─── Runner ───────────────────────────────────────────────────────────────────

(async () => {
    console.log('='.repeat(60));
    console.log('PIN Reset Feature — Puppeteer E2E Test Suite');
    console.log('='.repeat(60));
    console.log(`Config: Mailpit on ${MAILPIT_API}`);
    console.log(`Mode: ${HEADED ? 'headed' : 'headless'}\n`);

    const browser = await puppeteer.launch({
        headless: !HEADED,
        slowMo: SLOWMO,
        args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    try {
        for (const t of tests) {
            if (ONLY !== null && t.num !== ONLY) continue;

            console.log(`\n[Test ${t.num}] ${t.name}`);
            try {
                await sleep(2000);
                const context = await browser.createBrowserContext();
                try {
                    await t.fn(context, browser);
                } finally {
                    await context.close();
                }
                passed++;
                console.log(`  PASSED`);
            } catch (err) {
                failed++;
                console.log(`  FAILED: ${err.message}`);
            }
        }
    } finally {
        await browser.close();
    }

    console.log('\n' + '='.repeat(60));
    console.log(`Results: ${passed} passed, ${failed} failed, ${tests.length} total`);
    console.log('='.repeat(60));

    process.exit(failed > 0 ? 1 : 0);
})();
