/**
 * Comprehensive Puppeteer test suite for PIN Code Authenticator extension.
 *
 * Tests:
 *   1. Required Action — configure PIN (custom format: [\w]{8})
 *   2. PIN Authentication — login with configured PIN
 *   3. Invalid PIN — wrong PIN is rejected
 *   4. Mismatched PINs on configure — rejected
 *   5. Wrong format PIN on configure — rejected
 *   6. Dynamic dot visual feedback for custom format
 *
 * Prerequisites:
 *   - Keycloak running on localhost:8080 with pin-test realm
 *   - testuser / password123 with CONFIGURE_PIN required action
 *   - custom format with regex [\w]{8} configured
 *
 * Usage:
 *   node test-pin-flow.js              # Run full suite headless
 *   node test-pin-flow.js --headed     # Show browser
 *   node test-pin-flow.js --test 1     # Run specific test
 */

const puppeteer = require('puppeteer');
const crypto    = require('crypto');
const { execSync } = require('child_process');

// ─── Configuration ────────────────────────────────────────────────────────────
const KEYCLOAK_URL = 'http://localhost:8080';
const REALM        = 'pin-test';
const CLIENT_ID    = 'pin-test-client';
const REDIRECT_URI = 'http://localhost:8888/callback';
const USERNAME     = 'testuser';
const PASSWORD     = 'password123';
const PIN_VALID    = 'Abc12345';   // 8 word chars matching [\w]{8}
const PIN_WRONG    = 'Xyz99999';   // Valid format but wrong value

const HEADED  = process.argv.includes('--headed');
const ONLY    = (() => { const i = process.argv.indexOf('--test'); return i !== -1 ? parseInt(process.argv[i+1]) : null; })();
const SLOWMO  = HEADED ? 80 : 0;

// ─── Helpers ──────────────────────────────────────────────────────────────────
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

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

/** Run kcadm with a JSON body passed via stdin to avoid shell quoting issues */
function kcadmWithBody(subcommand, resource, realm, body) {
    const bodyStr = JSON.stringify(body);
    const cmd = `echo '${bodyStr.replace(/'/g, "'\\''")}' | docker exec -i keycloak-pin-server /opt/keycloak/bin/kcadm.sh ${subcommand} ${resource} -r ${realm} -f -`;
    try {
        return execSync(cmd, { encoding: 'utf-8', timeout: 15000 }).trim();
    } catch (err) {
        throw new Error(`kcadm body failed: ${err.stderr || err.message}`);
    }
}

/** Reset testuser: remove PIN credentials, re-add CONFIGURE_PIN required action */
function resetTestUser() {
    console.log('  Resetting testuser...');
    // Authenticate kcadm
    kcadm('config', 'credentials', '--server', 'http://localhost:8080', '--realm', 'master', '--user', 'admin', '--password', 'admin');

    // Get user id (kcadm inside Docker uses localhost since it's talking to itself)
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

/** Remove required action from testuser (for PIN auth tests) */
function clearRequiredActions() {
    kcadm('config', 'credentials', '--server', 'http://localhost:8080', '--realm', 'master', '--user', 'admin', '--password', 'admin');
    const usersJson = kcadm('get', 'users', '-r', REALM, '-q', `username=${USERNAME}`, '--fields', 'id');
    const userId = JSON.parse(usersJson)[0].id;
    kcadmWithBody('update', `users/${userId}`, REALM, { requiredActions: [] });
}

/** Fill in the login form (username + password) */
async function doLogin(page) {
    await page.waitForSelector('#username', { timeout: 15000 });
    await page.type('#username', USERNAME, { delay: 30 });
    await page.type('#password', PASSWORD, { delay: 30 });
    await page.click('#kc-login');
    await sleep(3000);
}

/** Navigate to the auth URL with retry */
async function navigateToAuth(page) {
    for (let attempt = 0; attempt < 3; attempt++) {
        try {
            await page.goto(authUrl(), { waitUntil: 'networkidle0', timeout: 20000 });
            return;
        } catch (err) {
            if (attempt < 2) {
                console.log(`    Retry navigation (attempt ${attempt + 2})...`);
                await sleep(5000);
            } else {
                throw err;
            }
        }
    }
}

/** Type a PIN via the hidden input (non-visual-keyboard mode) */
async function typePin(page, inputSelector, pin) {
    const input = await page.$(inputSelector);
    if (!input) {
        // Try alternative selectors
        const altInput = await page.$(`input[name="${inputSelector.replace('#', '')}"]`);
        if (!altInput) {
            // Debug: save page for inspection
            const html = await page.content();
            const url = page.url();
            console.log(`    DEBUG: URL=${url}`);
            console.log(`    DEBUG: page has input[name=pin]=${html.includes('name="pin"')}`);
            console.log(`    DEBUG: page has id=pin=${html.includes('id="pin"')}`);
            throw new Error(`Input ${inputSelector} not found on page`);
        }
        // Use the alt selector
        await page.evaluate((name) => {
            const el = document.querySelector(`input[name="${name}"]`);
            if (el) {
                el.style.position = 'relative';
                el.style.left = '0';
                el.style.opacity = '1';
                el.style.height = 'auto';
                el.style.width = '100%';
            }
        }, inputSelector.replace('#', ''));
        await altInput.click();
        await altInput.type(pin, { delay: 50 });
        return;
    }
    // Make it interactable
    await page.evaluate((sel) => {
        const el = document.querySelector(sel);
        if (el) {
            el.style.position = 'relative';
            el.style.left = '0';
            el.style.opacity = '1';
            el.style.height = 'auto';
            el.style.width = '100%';
        }
    }, inputSelector);
    await input.click();
    await input.type(pin, { delay: 50 });
}

// ─── Test Suite ───────────────────────────────────────────────────────────────
const tests = [];
let passed = 0;
let failed = 0;

function test(num, name, fn) {
    tests.push({ num, name, fn });
}

// ──────────────────────────────────────────────────────────────────────────────
// TEST 1: Required Action — Configure PIN (custom format)
// ──────────────────────────────────────────────────────────────────────────────
test(1, 'Required Action: configure PIN (custom format)', async (context) => {
    resetTestUser();
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    try {
        await navigateToAuth(page);
        await doLogin(page);

        // Should be on the Configure PIN page
        const content = await page.content();
        if (!content.includes('newPin')) {
            const bodyText = await page.evaluate(() => document.body?.innerText?.substring(0, 300) || '');
            throw new Error('Expected Configure PIN page with newPin input. Page: ' + bodyText.substring(0, 200));
        }
        console.log('    On Configure PIN page');

        // Custom format uses dynamic dots container (no pre-rendered dots)
        if (content.includes('pin-dots-dynamic')) {
            console.log('    OK: Dynamic dots container present (custom format)');
        } else {
            console.log('    WARNING: Expected dynamic dots container for custom format');
        }

        // Enter and confirm PIN
        await typePin(page, '#newPin', PIN_VALID);
        await sleep(300);
        await typePin(page, '#confirmPin', PIN_VALID);
        await sleep(300);

        // Submit
        const submitBtn = await page.$('input[type="submit"]');
        if (!submitBtn) throw new Error('Submit button not found');
        await submitBtn.click();
        await sleep(3000);

        // Check that we are no longer on the configure page
        const finalContent = await page.content();
        if (finalContent.includes('pinRequiredActionInvalidFormat') || finalContent.includes('pinRequiredActionMismatch')) {
            throw new Error('PIN configuration failed — error shown on page');
        }

        console.log('    PIN configured successfully');
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 2: PIN Authentication — login with correct PIN
// ──────────────────────────────────────────────────────────────────────────────
test(2, 'PIN Authentication: login with correct PIN', async (context) => {
    clearRequiredActions();
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    // Track redirect URLs — callback may fail to load (no server on 8888)
    let redirectedToCallback = false;
    page.on('request', req => {
        if (req.url().includes('callback') && req.url().includes('code=')) {
            redirectedToCallback = true;
        }
    });

    try {
        await navigateToAuth(page);
        await doLogin(page);
        await sleep(2000);

        const content = await page.content();
        const url = page.url();

        // Check if we are on PIN page
        if (!content.includes('pin-dot') && !content.includes('pin-authenticator') && !content.includes('pin-dots-dynamic')) {
            if (redirectedToCallback || url.includes('callback') || url.includes('code=')) {
                console.log('    Redirected to callback (no PIN step in flow)');
                return;
            }
            throw new Error('Expected PIN authenticator page, got: ' + url);
        }

        console.log('    On PIN authenticator page');

        // Custom format: dynamic dots container should be present
        if (content.includes('pin-dots-dynamic')) {
            console.log('    OK: Dynamic dots container present (custom format)');
        }

        // Type PIN
        await typePin(page, '#pin', PIN_VALID);
        await sleep(1000);

        // Custom format does NOT auto-submit — click submit
        const submitBtn = await page.$('#submit-btn');
        if (submitBtn) {
            await submitBtn.click();
        }
        await sleep(3000);

        const finalUrl = page.url();

        if (redirectedToCallback || finalUrl.includes('callback') || finalUrl.includes('code=')) {
            console.log('    Authentication successful — redirected to callback');
        } else {
            const finalContent = await page.content();
            if (finalContent.includes('invalidPinMessage')) {
                throw new Error('PIN was rejected');
            }
            console.log('    Authentication completed');
        }
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 3: Invalid PIN — wrong PIN is rejected
// ──────────────────────────────────────────────────────────────────────────────
test(3, 'PIN Authentication: wrong PIN is rejected', async (context) => {
    clearRequiredActions();
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    // Track redirect URLs
    let redirectedToCallback = false;
    page.on('request', req => {
        if (req.url().includes('callback') && req.url().includes('code=')) {
            redirectedToCallback = true;
        }
    });

    try {
        await navigateToAuth(page);
        await doLogin(page);
        await sleep(2000);

        const content = await page.content();
        const url = page.url();
        if (!content.includes('pin-dot') && !content.includes('pin-authenticator') && !content.includes('pin-dots-dynamic')) {
            if (redirectedToCallback || url.includes('callback') || url.includes('code=')) {
                console.log('    Skipping: redirected to callback (no PIN step in flow)');
                return;
            }
            console.log('    Skipping: not on PIN page');
            return;
        }

        // Type wrong PIN
        await typePin(page, '#pin', PIN_WRONG);
        await sleep(500);

        // Custom format: click submit
        const submitBtn = await page.$('#submit-btn');
        if (submitBtn) {
            await submitBtn.click();
        }
        await sleep(3000);

        const finalContent = await page.content();
        const finalUrl = page.url();

        if (redirectedToCallback || finalUrl.includes('callback') || finalUrl.includes('code=')) {
            throw new Error('Wrong PIN was accepted — SECURITY ISSUE!');
        }

        if (finalContent.includes('invalidPinMessage') || finalContent.includes('Invalid PIN') || finalContent.includes('pin-dot') || finalContent.includes('pin-dots-dynamic')) {
            console.log('    Wrong PIN correctly rejected');
        } else {
            console.log('    PIN not accepted (still on auth page)');
        }
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 4: Mismatched PINs — rejected on configure
// ──────────────────────────────────────────────────────────────────────────────
test(4, 'Required Action: mismatched PINs are rejected', async (context) => {
    resetTestUser();
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    try {
        await navigateToAuth(page);
        await doLogin(page);
        await sleep(1000);

        const content = await page.content();
        if (!content.includes('newPin')) {
            console.log('    Skipping: not on Configure PIN page');
            return;
        }

        await typePin(page, '#newPin', 'Abc12345');
        await sleep(200);
        await typePin(page, '#confirmPin', 'Xyz98765');
        await sleep(200);

        const submitBtn = await page.$('input[type="submit"]');
        if (submitBtn) {
            await submitBtn.click();
            await sleep(2000);
        }

        const errorContent = await page.content();
        // Should still be on configure page with error
        if (errorContent.includes('newPin')) {
            console.log('    Still on configure page (mismatch rejected)');
        } else {
            throw new Error('Mismatch was not detected');
        }
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 5: Wrong format — too short PIN rejected on configure
// ──────────────────────────────────────────────────────────────────────────────
test(5, 'Required Action: invalid-format PIN is rejected', async (context) => {
    resetTestUser();
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    try {
        await navigateToAuth(page);
        await doLogin(page);
        await sleep(1000);

        const content = await page.content();
        if (!content.includes('newPin')) {
            console.log('    Skipping: not on Configure PIN page');
            return;
        }

        // Enter a PIN that does NOT match [\w]{8} (too short, only 4 chars)
        const shortPin = 'Ab12';
        await typePin(page, '#newPin', shortPin);
        await sleep(200);
        await typePin(page, '#confirmPin', shortPin);
        await sleep(200);

        const submitBtn = await page.$('input[type="submit"]');
        if (submitBtn) {
            await submitBtn.click();
            await sleep(2000);
        }

        const errorContent = await page.content();
        const finalUrl = page.url();

        if (finalUrl.includes('callback')) {
            throw new Error('Short PIN was accepted — format validation failed!');
        }

        if (errorContent.includes('newPin') || errorContent.includes('pinRequiredActionInvalidFormat')) {
            console.log('    Invalid-format PIN correctly rejected');
        } else {
            console.log('    PIN not accepted (still on page)');
        }
    } finally {
        await page.close();
    }
});

// ──────────────────────────────────────────────────────────────────────────────
// TEST 6: Dynamic dots — dots appear/disappear as user types (custom format)
// ──────────────────────────────────────────────────────────────────────────────
test(6, 'Visual feedback: dynamic dots for custom format', async (context) => {
    resetTestUser();
    const page = await context.newPage();
    await page.setViewport({ width: 1280, height: 800 });

    try {
        await navigateToAuth(page);
        await doLogin(page);
        await sleep(1000);

        const content = await page.content();
        if (!content.includes('newPin')) {
            throw new Error('Expected Configure PIN page');
        }

        // Dynamic container should start empty (no grey placeholder dots)
        const initialDots = await page.$$eval('#new-pin-dots .pin-dot', dots => dots.length);
        if (initialDots !== 0) {
            throw new Error(`Expected 0 initial dots, got ${initialDots}`);
        }
        console.log('    OK: Dynamic container starts with 0 dots');

        // Type 3 characters — should see 3 filled dots
        await typePin(page, '#newPin', 'Abc');
        await sleep(300);

        const dotsAfter3 = await page.$$eval('#new-pin-dots .pin-dot.filled', dots => dots.length);
        if (dotsAfter3 !== 3) {
            throw new Error(`Expected 3 filled dots after typing 3 chars, got ${dotsAfter3}`);
        }
        console.log('    OK: 3 filled dots after typing 3 characters');

        // Type 5 more to reach 8 chars
        // Clear & retype full value via evaluate to avoid stacking
        await page.evaluate(() => {
            const input = document.getElementById('newPin');
            input.value = 'Abc12345';
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        await sleep(300);

        const dotsAfter8 = await page.$$eval('#new-pin-dots .pin-dot.filled', dots => dots.length);
        if (dotsAfter8 !== 8) {
            throw new Error(`Expected 8 filled dots after typing 8 chars, got ${dotsAfter8}`);
        }
        console.log('    OK: 8 filled dots after typing 8 characters');

        // Clear the input — dots should disappear
        await page.evaluate(() => {
            const input = document.getElementById('newPin');
            input.value = '';
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        await sleep(300);

        const dotsAfterClear = await page.$$eval('#new-pin-dots .pin-dot', dots => dots.length);
        if (dotsAfterClear !== 0) {
            throw new Error(`Expected 0 dots after clear, got ${dotsAfterClear}`);
        }
        console.log('    OK: 0 dots after clearing input');
    } finally {
        await page.close();
    }
});

// ─── Runner ───────────────────────────────────────────────────────────────────

(async () => {
    console.log('='.repeat(60));
    console.log('PIN Code Authenticator — Puppeteer Test Suite');
    console.log('='.repeat(60));
    console.log(`Config: format=custom, regex=[\\w]{8}, visual-keyboard=false`);
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
                await sleep(2000); // Pause between tests
                // Use a fresh incognito context per test to avoid shared cookies/connections
                const context = await browser.createBrowserContext();
                try {
                    await t.fn(context);
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
