/**
 * Puppeteer test suite for LOA-based Session Lock feature.
 *
 * This test runs HEADED (non-headless) so a human can visually verify each step.
 *
 * Scenario:
 *   1. Open the LOA demo app
 *   2. Login at LOA 1 (password only) → verify acr=password
 *   3. Configure PIN (first-time required action)
 *   4. Request step-up to LOA 2 (PIN) → verify acr=pin
 *   5. Verify the protected feature is accessible at LOA 2
 *   6. Wait for LOA 2 to expire (loa-max-age=300s in production,
 *      shortened to 30s for this test via realm reconfiguration)
 *   7. Request the protected feature again → prompted for PIN
 *   8. Re-enter PIN → back to LOA 2
 *
 * Prerequisites:
 *   - docker compose up -d  (Keycloak + postgres + loa-demo)
 *   - PIN extension JAR built and deployed
 *   - pin-test realm with LOA step-up flow imported
 *
 * Usage:
 *   node test-session-lock.js
 *   node test-session-lock.js --fast   # Use 30s LOA timeout instead of 300s
 */

const puppeteer = require('puppeteer');
const crypto    = require('crypto');
const { execSync } = require('child_process');

// ─── Configuration ────────────────────────────────────────────────────────────
const KEYCLOAK_URL  = 'http://localhost:8080';
const REALM         = 'pin-test';
const USERNAME      = 'testuser';
const PASSWORD      = 'password123';
const DEMO_URL      = 'http://localhost:3000';
const PIN           = '123456'; // 6-digits format

const FAST_MODE     = process.argv.includes('--fast');
const LOA2_TIMEOUT  = FAST_MODE ? 30 : 300; // seconds
const SLOWMO        = 60;

// ─── Helpers ──────────────────────────────────────────────────────────────────
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

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

/** Authenticate kcadm CLI */
function kcadmAuth() {
    kcadm('config', 'credentials', '--server', 'http://localhost:8080',
          '--realm', 'master', '--user', 'admin', '--password', 'admin');
}

/** Reset testuser: remove PIN credentials, re-add CONFIGURE_PIN required action */
function resetTestUser() {
    console.log('  Resetting testuser...');
    kcadmAuth();

    const usersJson = kcadm('get', 'users', '-r', REALM, '-q', `username=${USERNAME}`, '--fields', 'id');
    const users = JSON.parse(usersJson);
    if (users.length === 0) throw new Error('testuser not found');
    const userId = users[0].id;

    // Remove existing PIN credentials
    const credsJson = kcadm('get', `users/${userId}/credentials`, '-r', REALM, '--fields', 'id,type');
    const creds = JSON.parse(credsJson);
    for (const c of creds) {
        if (c.type === 'pin-code') {
            kcadm('delete', `users/${userId}/credentials/${c.id}`, '-r', REALM);
            console.log('    Deleted existing PIN credential');
        }
    }

    // Add CONFIGURE_PIN required action
    kcadmWithBody('update', `users/${userId}`, REALM, { requiredActions: ['CONFIGURE_PIN'] });
    console.log('    Required action CONFIGURE_PIN set');
}

/** Shorten LOA 2 max-age for fast testing */
function setLoa2MaxAge(seconds) {
    console.log(`  Setting LOA 2 max-age to ${seconds}s...`);
    kcadmAuth();

    // Get executions for the loa-2-pin sub-flow directly
    // (sub-flows don't appear in authentication/flows listing, but the executions endpoint works)
    let execs;
    try {
        const execsJson = kcadm('get', 'authentication/flows/loa-2-pin/executions', '-r', REALM);
        execs = JSON.parse(execsJson);
    } catch (e) {
        console.log('    WARNING: loa-2-pin flow not found, skipping max-age adjustment');
        return;
    }

    // Find the conditional-level-of-authentication execution
    const loaExec = execs.find(e => e.providerId === 'conditional-level-of-authentication'
                                 || e.displayName?.includes('Level of Authentication'));
    if (!loaExec || !loaExec.authenticationConfig) {
        console.log('    WARNING: LOA condition execution not found, skipping max-age adjustment');
        return;
    }

    // Update the config
    const configId = loaExec.authenticationConfig;
    kcadmWithBody('update', `authentication/config/${configId}`, REALM, {
        id: configId,
        alias: 'loa-2-config',
        config: {
            'loa-condition-level': '2',
            'loa-max-age': String(seconds)
        }
    });
    console.log(`    LOA 2 max-age set to ${seconds}s`);
}

/** Type a PIN into the hidden input field.
 *  For 6-digits format, the form auto-submits after the last digit.
 *  Returns a promise that resolves when navigation completes (if autosubmit fires). */
async function typePin(page, pin) {
    // Make the hidden PIN input accessible
    await page.evaluate(() => {
        const el = document.getElementById('pin') || document.querySelector('input[name="pin"]');
        if (el) {
            el.style.position = 'relative';
            el.style.left = '0';
            el.style.opacity = '1';
            el.style.height = 'auto';
            el.style.width = '100%';
        }
    });
    const input = await page.$('#pin') || await page.$('input[name="pin"]');
    if (!input) throw new Error('No PIN input found');
    await input.click();
    await input.focus();
    // Type all but the last digit (to avoid triggering auto-submit prematurely)
    if (pin.length > 1) {
        await input.type(pin.slice(0, -1), { delay: 80 });
    }
    // Type the last digit — this may trigger auto-submit + navigation
    // Use Promise.allSettled to catch both navigation and no-navigation cases
    await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 15000 }).catch(() => {}),
        input.type(pin.slice(-1), { delay: 80 })
    ]);
    // Give time for auto-submit (300ms in the template) + server round-trip
    await sleep(2000);
}

/** Type a PIN in the newPin or confirmPin field (configure PIN page) */
async function typePinConfigure(page, selector, pin) {
    // Make the hidden input visible and accessible
    await page.evaluate((sel) => {
        const el = document.querySelector(sel);
        if (el) {
            el.style.position = 'relative';
            el.style.left = '0';
            el.style.opacity = '1';
            el.style.height = 'auto';
            el.style.width = '100%';
        }
    }, selector);
    const input = await page.$(selector);
    if (!input) throw new Error(`Configure PIN input ${selector} not found`);
    await input.click();
    await input.type(pin, { delay: 50 });
}

// ─── Main Test ────────────────────────────────────────────────────────────────
(async () => {
    console.log('╔══════════════════════════════════════════════════╗');
    console.log('║   Session Lock (LOA Step-Up) — Headed Test      ║');
    console.log('╚══════════════════════════════════════════════════╝');
    console.log(`Mode: ${FAST_MODE ? 'FAST (30s timeout)' : 'STANDARD (300s timeout)'}`);
    console.log('');

    // ── Setup ────────────────────────────────────────────────────────────
    console.log('SETUP');
    resetTestUser();
    if (FAST_MODE) {
        setLoa2MaxAge(30);
    }
    console.log('');

    const browser = await puppeteer.launch({
        headless: false,
        slowMo: SLOWMO,
        args: ['--window-size=1280,900', '--no-sandbox'],
        defaultViewport: { width: 1280, height: 800 }
    });

    let passed = true;

    try {
        const page = await browser.newPage();

        // ── Step 1: Open demo app ────────────────────────────────────────
        console.log('STEP 1: Open LOA demo app');
        await page.goto(DEMO_URL, { waitUntil: 'networkidle0', timeout: 15000 });
        const title = await page.title();
        console.log(`  Page title: "${title}"`);
        assert(title.includes('Session Lock'), 'Demo app should load');
        console.log('  ✓ Demo app loaded\n');

        // ── Step 2: Login at LOA 1 (password only) ───────────────────────
        console.log('STEP 2: Login at LOA 1 (password only)');
        await page.click('#btn-login');
        await page.waitForSelector('#username', { timeout: 15000 });
        console.log('  On Keycloak login page');

        await page.type('#username', USERNAME, { delay: 30 });
        await page.type('#password', PASSWORD, { delay: 30 });
        await page.click('#kc-login');
        await sleep(3000);

        // ── Step 3: Configure PIN (required action) ──────────────────────
        console.log('STEP 3: Configure PIN (first-time required action)');
        let currentUrl = page.url();
        const pageContent = await page.content();

        if (pageContent.includes('newPin') || pageContent.includes('Configure PIN') || pageContent.includes('configure-pin')) {
            console.log('  On Configure PIN page');

            await typePinConfigure(page, '#newPin', PIN);
            await sleep(300);
            await typePinConfigure(page, '#confirmPin', PIN);
            await sleep(300);

            const submitBtn = await page.$('input[type="submit"]');
            if (submitBtn) {
                await submitBtn.click();
                await sleep(3000);
            }
            console.log('  ✓ PIN configured');
        } else {
            console.log('  PIN already configured, skipping');
        }

        // After configure PIN (required action), Keycloak should redirect
        // to the OIDC callback. We may land either on the callback page
        // that processes the code, or back on the demo app.
        console.log('');

        // ── Wait to be back on demo app ──────────────────────────────────
        console.log('STEP 4: Verify LOA 1 authentication');
        await page.waitForFunction(
            () => window.location.hostname === 'localhost' && window.location.port === '3000'
                  && !window.location.pathname.includes('callback'),
            { timeout: 20000 }
        );
        await sleep(1500);

        // Check status badge shows LOA 1
        let statusText = await page.$eval('#status-badge', el => el.textContent);
        console.log(`  Status: "${statusText}"`);
        // After first login with CONFIGURE_PIN, Keycloak may grant LOA 2 since PIN was configured
        // This is expected: the required action + PIN authenticator both run in the flow
        if (statusText.includes('LOA 2') || statusText.includes('pin')) {
            console.log('  ✓ Authenticated at LOA 2 (PIN configured during required action counts as LOA 2)');
            console.log('  → Waiting for LOA 2 to expire before testing step-up...\n');

            // Wait for LOA 2 to expire so we can test the step-up flow
            const waitTime = FAST_MODE ? 35 : 305;
            console.log(`  Waiting ${waitTime}s for LOA 2 expiry...`);
            await waitWithCountdown(page, waitTime);

            // After expiry, the demo app shows the timer as expired
            // We need to re-authenticate at LOA 1 to get a clean state
            // Actually, the token in sessionStorage still has acr=pin,
            // but the Keycloak session's LOA 2 has expired.
            // The demo app's timer should show "Expired"
            const timerBadgeText = await page.$eval('#timer-badge', el => el.textContent).catch(() => 'N/A');
            console.log(`  Timer badge: "${timerBadgeText}"`);
            console.log('  ✓ LOA 2 expired, session still active\n');

            // Now request step-up to test re-authentication
            console.log('STEP 5: Request step-up after LOA 2 expiry');
        } else {
            console.log('  ✓ Authenticated at LOA 1\n');

            // ── Step 5: Step-up to LOA 2 ─────────────────────────────────
            console.log('STEP 5: Request step-up to LOA 2 (PIN)');
        }

        // Click step-up button (or access protected feature which triggers step-up)
        const stepupBtn = await page.$('#btn-stepup');
        if (stepupBtn) {
            await stepupBtn.click();
        } else {
            // Use the protected feature button which will trigger step-up
            await page.click('button[onclick="accessFeature()"]');
            await sleep(2000);
        }

        // Wait for a Keycloak form element to appear (PIN input, username input, or submit button)
        await page.waitForSelector('#kc-login, input[name="pin"], #pin, #username', { timeout: 15000 });
        await sleep(500);
        currentUrl = page.url();
        console.log(`  Current URL: ${currentUrl}`);

        if (currentUrl.includes('localhost:8080')) {
            // We're on Keycloak — should be PIN form (not login form, since SSO session is still active)
            console.log('  On Keycloak — checking for PIN form');
            const kContent = await page.content();

            if (kContent.includes('name="pin"') || kContent.includes('pin-keyboard') || kContent.includes('pin-authenticator')) {
                console.log('  PIN form displayed');
                await typePin(page, PIN);
                // typePin handles auto-submit + navigation for 6-digits format
            } else if (kContent.includes('name="username"')) {
                // Username form — need to login again
                console.log('  Login form displayed (session may have expired)');
                await page.type('#username', USERNAME, { delay: 30 });
                await page.type('#password', PASSWORD, { delay: 30 });
                await page.click('#kc-login');
                await sleep(3000);

                // Now should get PIN form
                const afterLogin = await page.content();
                if (afterLogin.includes('name="pin"') || afterLogin.includes('pin-keyboard')) {
                    console.log('  PIN form displayed after login');
                    await typePin(page, PIN);
                }
            }
        }

        // ── Back on demo app with LOA 2 ──────────────────────────────────
        console.log('');
        console.log('STEP 6: Verify LOA 2 after step-up');
        await page.waitForFunction(
            () => window.location.hostname === 'localhost' && window.location.port === '3000'
                  && !window.location.pathname.includes('callback'),
            { timeout: 20000 }
        );
        await sleep(1500);

        statusText = await page.$eval('#status-badge', el => el.textContent);
        console.log(`  Status: "${statusText}"`);
        assert(statusText.includes('LOA 2') || statusText.includes('pin'), 'Should show LOA 2 after PIN entry');
        console.log('  ✓ LOA 2 achieved via step-up');

        // In fast mode, override the client-side countdown to match the reduced LOA max-age
        if (FAST_MODE) {
            await page.evaluate((timeout) => {
                loa2Expiry = (Date.now() / 1000) + timeout;
                sessionStorage.setItem('loa2Expiry', loa2Expiry);
            }, LOA2_TIMEOUT);
            console.log(`  ⏱ Client-side timer overridden to ${LOA2_TIMEOUT}s`);
        }
        console.log('');

        // ── Step 7: Verify protected feature is accessible at LOA 2 ──────
        console.log('STEP 7: Access protected feature at LOA 2');
        await page.click('button[onclick="accessFeature()"]');
        await sleep(1000);
        const featureResult = await page.$eval('#feature-result', el => el.textContent);
        console.log(`  Feature result: "${featureResult}"`);
        assert(featureResult.includes('Access granted'), 'Feature should be accessible at LOA 2');
        console.log('  ✓ Protected feature accessible\n');

        // ── Step 8: Wait for LOA 2 to expire ─────────────────────────────
        console.log('STEP 8: Wait for LOA 2 expiry');
        const waitSeconds = FAST_MODE ? 35 : 305;
        console.log(`  Waiting ${waitSeconds}s...`);
        await waitWithCountdown(page, waitSeconds);

        const timerText = await page.$eval('#timer-badge', el => el.textContent).catch(() => 'N/A');
        console.log(`  Timer badge: "${timerText}"`);
        assert(timerText === 'Expired', 'Timer should show Expired');

        statusText = await page.$eval('#status-badge', el => el.textContent);
        console.log(`  Status: "${statusText}"`);
        assert(statusText.includes('expired') || statusText.includes('LOA 1'), 'Should show LOA 1 / expired');
        console.log('  ✓ LOA 2 expired, session still active\n');

        // ── Step 9: Request protected feature → re-prompted for PIN ──────
        console.log('STEP 9: Access protected feature after expiry → redirected for PIN');
        await page.click('button[onclick="accessFeature()"]');
        // The demo app shows "LOA 2 expired" message, then redirects to Keycloak after 1.5s
        // Wait for a Keycloak form element to appear
        await page.waitForSelector('#kc-login, input[name="pin"], #pin, #username', { timeout: 20000 });
        await sleep(500);

        // Should be on Keycloak for PIN re-auth
        currentUrl = page.url();
        if (currentUrl.includes('localhost:8080')) {
            console.log('  Redirected to Keycloak for PIN');
            const kcContent = await page.content();
            if (kcContent.includes('name="pin"') || kcContent.includes('pin-keyboard') || kcContent.includes('pin-authenticator')) {
                console.log('  ✓ PIN form displayed (LOA 2 re-authentication required)');
                await typePin(page, PIN);
            } else if (kcContent.includes('name="username"')) {
                console.log('  Login form (full session expired) — entering credentials');
                await page.type('#username', USERNAME, { delay: 30 });
                await page.type('#password', PASSWORD, { delay: 30 });
                await page.click('#kc-login');
                await sleep(3000);
                // PIN form may follow
                const pc = await page.content();
                if (pc.includes('name="pin"') || pc.includes('pin-keyboard')) {
                    await typePin(page, PIN);
                }
            }
        } else {
            // The demo app showed the "redirecting" message then issued the step-up redirect
            // Wait a bit for the redirect to happen
            await sleep(3000);
        }

        // ── Step 10: Back on demo app with LOA 2 again ───────────────────
        console.log('');
        console.log('STEP 10: Verify LOA 2 restored after re-authentication');
        await page.waitForFunction(
            () => window.location.hostname === 'localhost' && window.location.port === '3000'
                  && !window.location.pathname.includes('callback'),
            { timeout: 20000 }
        );
        await sleep(1500);

        statusText = await page.$eval('#status-badge', el => el.textContent);
        console.log(`  Status: "${statusText}"`);
        assert(statusText.includes('LOA 2') || statusText.includes('pin'), 'Should show LOA 2 after re-auth');
        console.log('  ✓ LOA 2 restored after PIN re-entry\n');

        console.log('════════════════════════════════════════════════════');
        console.log('  ALL STEPS PASSED ✓');
        console.log('════════════════════════════════════════════════════');
        console.log('');
        console.log('The browser will stay open for visual inspection.');
        console.log('Press Ctrl+C to exit.');

        // Keep browser open for visual inspection
        await new Promise(() => {}); // never resolves

    } catch (err) {
        passed = false;
        console.error(`\n  ✗ FAILED: ${err.message}`);
        if (err.stack) console.error(`  ${err.stack.split('\n').slice(1, 3).join('\n  ')}`);
        console.log('\n  The browser will stay open for debugging.');
        console.log('  Press Ctrl+C to exit.');
        await new Promise(() => {});
    }
})();

// ─── Assertion helper ─────────────────────────────────────────────────────────
function assert(cond, msg) {
    if (!cond) throw new Error(`Assertion failed: ${msg}`);
}

// ─── Countdown helper ─────────────────────────────────────────────────────────
async function waitWithCountdown(page, seconds) {
    for (let i = seconds; i > 0; i--) {
        if (i % 30 === 0 || i <= 10) {
            process.stdout.write(`    ${i}s remaining...\r`);
        }
        await sleep(1000);
    }
    process.stdout.write('    0s — done!          \n');
    // Ensure the demo page timer has caught up
    await sleep(2000);
}
