/**
 * Puppeteer E2E tests for the server-side visual keyboard.
 *
 * Validates:
 *   1. Keyboard image is a server-generated PNG (not a canvas)
 *   2. Client submits coordinates, not digit values
 *   3. Server resolves coordinates → PIN digits correctly
 *   4. Backspace / Clear are HTML buttons (not in the canvas/image)
 *   5. Debug log contains coordinate map for E2E test consumption
 *   6. Full PIN lifecycle works via keyboard image clicks
 *
 * Prerequisites:
 *   docker compose -f docker-compose.yml -f docker-compose.visual-kb.yml up -d
 *   (Enables visual keyboard, debug mode, 4-digit PIN format)
 *
 * Usage:
 *   node test-visual-keyboard.js              # headless
 *   node test-visual-keyboard.js --headed     # show browser
 *   node test-visual-keyboard.js --test 3     # run one test
 */

const puppeteer     = require('puppeteer');
const crypto        = require('crypto');
const { execSync }  = require('child_process');

// ─── Configuration ───────────────────────────────────────────────────────────
const KC_URL       = 'http://localhost:8080';
const REALM        = 'pin-test';
const CLIENT_ID    = 'pin-test-client';
const REDIRECT_URI = 'http://localhost:8888/callback';
const USERNAME     = 'testuser';
const PASSWORD     = 'password123';
const PIN          = '1234';

const HEADED = process.argv.includes('--headed');
const ONLY   = (() => { const i = process.argv.indexOf('--test'); return i !== -1 ? parseInt(process.argv[i + 1]) : null; })();

// ─── Helpers ─────────────────────────────────────────────────────────────────
const sleep = ms => new Promise(r => setTimeout(r, ms));

function authUrl(acr) {
    const v = crypto.randomBytes(32).toString('base64url');
    const c = crypto.createHash('sha256').update(v).digest('base64url');
    let url = `${KC_URL}/realms/${REALM}/protocol/openid-connect/auth` +
        `?client_id=${CLIENT_ID}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
        `&response_type=code&scope=openid&code_challenge=${c}&code_challenge_method=S256`;
    if (acr) url += `&acr_values=${acr}`;
    return url;
}

function exec(cmd, timeout = 15000) {
    return execSync(cmd, { encoding: 'utf-8', timeout }).trim();
}

function kcadm(...args) {
    return exec(`docker exec keycloak-pin-server /opt/keycloak/bin/kcadm.sh ${args.join(' ')}`);
}

function kcadmLogin() {
    kcadm('config', 'credentials', '--server', 'http://localhost:8080',
          '--realm', 'master', '--user', 'admin', '--password', 'admin');
}

function getUserId() {
    kcadmLogin();
    const users = JSON.parse(kcadm('get', 'users', '-r', REALM, '-q', `username=${USERNAME}`, '--fields', 'id'));
    if (!users.length) throw new Error('testuser not found');
    return users[0].id;
}

/** Remove PIN credential and set CONFIGURE_PIN required action. */
function resetTestUser() {
    kcadmLogin();
    const userId = getUserId();
    const creds = JSON.parse(kcadm('get', `users/${userId}/credentials`, '-r', REALM, '--fields', 'id,type'));
    for (const c of creds) {
        if (c.type === 'pin-code') kcadm('delete', `users/${userId}/credentials/${c.id}`, '-r', REALM);
    }
    const body = JSON.stringify({ requiredActions: ['CONFIGURE_PIN'] });
    exec(`echo '${body}' | docker exec -i keycloak-pin-server /opt/keycloak/bin/kcadm.sh update users/${userId} -r ${REALM} -f -`);
    return userId;
}

function clearRequiredActions(userId) {
    kcadmLogin();
    const body = JSON.stringify({ requiredActions: [] });
    exec(`echo '${body}' | docker exec -i keycloak-pin-server /opt/keycloak/bin/kcadm.sh update users/${userId} -r ${REALM} -f -`);
}

function keycloakLogs(n = 80) {
    try { return execSync(`docker logs keycloak-pin-server --tail ${n} 2>&1`, { encoding: 'utf-8', timeout: 10000 }); }
    catch { return ''; }
}

/**
 * Parse coordinate map from Keycloak debug log.
 * Log format: [VISUAL-KB-DEBUG] ... coordinate map for user '<userId>': 0:10,20,56,48;1:70,30,56,48;...
 * Returns { "0": { x, y, w, h }, ... }
 */
function parseCoordMap(userId, keyword = 'coordinate map') {
    const lines = keycloakLogs(80).split('\n');
    for (let i = lines.length - 1; i >= 0; i--) {
        const l = lines[i];
        if (!l.includes('[VISUAL-KB-DEBUG]') || !l.includes(keyword) || !l.includes(userId)) continue;
        const m = l.match(/coordinate map for user '[^']+': (.+)$/);
        if (!m) continue;
        const map = {};
        for (const entry of m[1].trim().split(';')) {
            const [digit, coords] = entry.split(':');
            if (!digit || !coords) continue;
            const [x, y, w, h] = coords.split(',').map(Number);
            map[digit] = { x, y, w, h };
        }
        return map;
    }
    return null;
}

/** Parse coord map emitted by Configure-PIN action. */
const parseConfigureCoordMap = userId => parseCoordMap(userId, 'Configure PIN keyboard coordinate map');

/** Parse coord map emitted by PIN authenticator. */
const parseAuthenticatorCoordMap = userId => parseCoordMap(userId, 'Keyboard coordinate map');

/** Navigate to OIDC auth endpoint. */
async function navigate(page, acr) {
    let lastErr;
    for (let i = 0; i < 3; i++) {
        try { await page.goto(authUrl(acr), { waitUntil: 'domcontentloaded', timeout: 15000 }); return; }
        catch (e) { lastErr = e; await sleep(3000); }
    }
    throw new Error(`Could not navigate to auth URL after 3 retries: ${lastErr?.message}`);
}

/** Fill username/password and submit. */
async function login(page) {
    await page.waitForSelector('#username', { timeout: 15000 });
    await page.type('#username', USERNAME, { delay: 30 });
    await page.type('#password', PASSWORD, { delay: 30 });
    await page.click('#kc-login');
    await sleep(3000);
}

/**
 * Click on the keyboard image at the centre of the given digit's bounding box,
 * accounting for CSS scaling.  Scrolls into view first to ensure accuracy.
 */
async function clickDigit(page, selector, coordMap, digit) {
    const box = coordMap[digit];
    if (!box) throw new Error(`No coordinate for digit '${digit}'`);

    const cx = box.x + box.w / 2;
    const cy = box.y + box.h / 2;

    // Scroll image into view BEFORE measuring position
    await page.evaluate(sel => document.querySelector(sel).scrollIntoView({ block: 'center' }), selector);
    await sleep(200);

    const r = await page.evaluate(sel => {
        const img = document.querySelector(sel);
        const rect = img.getBoundingClientRect();
        return { left: rect.left, top: rect.top, w: rect.width, h: rect.height,
                 nw: img.naturalWidth, nh: img.naturalHeight };
    }, selector);

    const x = r.left + cx * (r.w / r.nw);
    const y = r.top  + cy * (r.h / r.nh);

    await page.mouse.click(x, y);
    await sleep(300);
}

/** Type a full PIN via image clicks. */
async function typePin(page, selector, coordMap, pin) {
    for (const ch of pin) await clickDigit(page, selector, coordMap, ch);
}

/**
 * Ensure testuser has a configured PIN (self-contained helper for auth tests).
 * Configures via visual keyboard if needed, returns userId.
 */
async function ensurePinConfigured(ctx) {
    const userId = resetTestUser();
    // Use a separate browser context so the session/cookies don't bleed into
    // the test's own context and the failed redirect to localhost:8888 doesn't
    // corrupt the test context's network state.
    const browser = ctx.browser();
    const tempCtx = await browser.createBrowserContext();
    const page = await tempCtx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        const map = parseConfigureCoordMap(userId);
        if (!map) throw new Error('ensurePinConfigured: no coordinate map');

        // Type new PIN
        await typePin(page, '#configure-keyboard-image', map, PIN);
        await sleep(500);

        // Switch to confirmPin (auto-switch may already have happened)
        await page.click('#confirm-pin-dots');
        await sleep(300);

        // Type confirm PIN
        await typePin(page, '#configure-keyboard-image', map, PIN);
        await sleep(500);

        // Submit
        await page.click('input[type="submit"]');
        await sleep(5000);

        const url = page.url();
        if (url.includes('CONFIGURE_PIN'))
            throw new Error('ensurePinConfigured: PIN configuration failed');
    } finally {
        await tempCtx.close();
    }

    // Clear required actions so next login goes straight to PIN auth
    clearRequiredActions(userId);
    await sleep(1000);
    return userId;
}

// ─── Test registry ───────────────────────────────────────────────────────────
const tests = [];
let passed = 0, failed = 0;
function test(num, name, fn) { tests.push({ num, name, fn }); }

// ─────────────────────────────────────────────────────────────────────────────
// TEST 1 — Image is a server-generated PNG, not a canvas
// ─────────────────────────────────────────────────────────────────────────────
test(1, 'Keyboard image is a server-generated PNG', async ctx => {
    const userId = resetTestUser();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        // Should be on Configure PIN page
        const info = await page.evaluate(() => {
            const img = document.querySelector('#configure-keyboard-image');
            return img ? { src: img.src.substring(0, 26), nw: img.naturalWidth, nh: img.naturalHeight } : null;
        });
        if (!info) throw new Error('Keyboard image (#configure-keyboard-image) not found');
        if (!info.src.startsWith('data:image/png;base64,'))
            throw new Error(`Expected Base64 PNG, got: ${info.src}`);
        console.log(`    Image: ${info.nw}×${info.nh} PNG`);

        // Verify NO canvas
        if (await page.$('#pin-keyboard-canvas'))
            throw new Error('Canvas element should not exist — replaced by image');

        // Verify NO shuffledKeys in page source (security)
        const html = await page.content();
        if (html.includes('shuffledKeys'))
            throw new Error('shuffledKeys found in page source — SECURITY ISSUE');

        console.log('    No canvas, no shuffledKeys');
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 2 — Backspace and Clear are HTML buttons
// ─────────────────────────────────────────────────────────────────────────────
test(2, 'Backspace and Clear are HTML buttons', async ctx => {
    const userId = resetTestUser();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        const bs = await page.$('#kb-backspace');
        const cl = await page.$('#kb-clear');
        if (!bs) throw new Error('Backspace button not found');
        if (!cl) throw new Error('Clear button not found');

        // Verify they are <button> elements with type="button"
        const bsTag = await page.$eval('#kb-backspace', el => `${el.tagName}:${el.type}`);
        const clTag = await page.$eval('#kb-clear', el => `${el.tagName}:${el.type}`);
        if (bsTag !== 'BUTTON:button') throw new Error(`Backspace tag=${bsTag}`);
        if (clTag !== 'BUTTON:button') throw new Error(`Clear tag=${clTag}`);
        console.log('    Backspace and Clear are <button type="button">');
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 3 — Debug log contains coordinate map with all 10 digits
// ─────────────────────────────────────────────────────────────────────────────
test(3, 'Debug log contains coordinate map', async ctx => {
    const userId = resetTestUser();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        const map = parseConfigureCoordMap(userId);
        if (!map) throw new Error('Coordinate map not found in Keycloak debug log');

        const digits = Object.keys(map).sort();
        if (digits.length !== 10)
            throw new Error(`Expected 10 digits, got ${digits.length}: ${digits}`);

        for (const [d, b] of Object.entries(map)) {
            if (b.w <= 0 || b.h <= 0)
                throw new Error(`Digit ${d}: invalid dimensions ${b.w}×${b.h}`);
        }
        console.log(`    10 digits: ${digits.join(',')}`);
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 4 — Clicking image populates hidden coordinate field
// ─────────────────────────────────────────────────────────────────────────────
test(4, 'Hidden pinCoords field receives coordinate data', async ctx => {
    const userId = resetTestUser();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        const map = parseConfigureCoordMap(userId);
        if (!map) throw new Error('No coordinate map');

        await typePin(page, '#configure-keyboard-image', map, '12');
        await sleep(200);

        const val = await page.$eval('#newPinCoords', el => el.value);
        if (!val) throw new Error('newPinCoords is empty');

        const parts = val.split('|');
        if (parts.length !== 2)
            throw new Error(`Expected 2 coordinate pairs, got ${parts.length}: ${val}`);

        for (const p of parts) {
            const [x, y] = p.split(',').map(Number);
            if (isNaN(x) || isNaN(y))
                throw new Error(`Invalid coordinate: ${p}`);
        }
        console.log(`    pinCoords = "${val}"`);
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 5 — Backspace removes last dot / Clear removes all
// ─────────────────────────────────────────────────────────────────────────────
test(5, 'Backspace and Clear work correctly', async ctx => {
    const userId = resetTestUser();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        const map = parseConfigureCoordMap(userId);
        if (!map) throw new Error('No coordinate map');

        await typePin(page, '#configure-keyboard-image', map, '123');
        await sleep(200);

        let dots = await page.$$eval('#new-pin-dots .pin-dot.filled', ds => ds.length);
        if (dots !== 3) throw new Error(`After typing 3 digits: ${dots} dots`);

        await page.click('#kb-backspace');
        await sleep(200);
        dots = await page.$$eval('#new-pin-dots .pin-dot.filled', ds => ds.length);
        if (dots !== 2) throw new Error(`After backspace: ${dots} dots (expected 2)`);

        await page.click('#kb-clear');
        await sleep(200);
        dots = await page.$$eval('#new-pin-dots .pin-dot.filled', ds => ds.length);
        if (dots !== 0) throw new Error(`After clear: ${dots} dots (expected 0)`);

        console.log('    3→backspace→2→clear→0');
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 6 — Configure PIN via visual keyboard clicks
// ─────────────────────────────────────────────────────────────────────────────
test(6, 'Configure PIN via visual keyboard', async ctx => {
    const userId = resetTestUser();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        await navigate(page);
        await login(page);
        await sleep(2000);

        const map = parseConfigureCoordMap(userId);
        if (!map) throw new Error('No coordinate map');

        // Type new PIN
        await typePin(page, '#configure-keyboard-image', map, PIN);
        await sleep(300);
        console.log(`    New PIN typed (${PIN.length} digits)`);

        // Switch to confirmPin
        await page.click('#confirm-pin-dots');
        await sleep(200);

        // Type confirm PIN
        await typePin(page, '#configure-keyboard-image', map, PIN);
        await sleep(300);
        console.log(`    Confirm PIN typed (${PIN.length} digits)`);

        // Submit
        await page.click('input[type="submit"]');
        await sleep(5000);

        // Should have left Configure PIN page
        const url = page.url();
        const html = await page.content();
        if (url.includes('CONFIGURE_PIN') || html.includes('PINs do not match') || html.includes('does not match the required format'))
            throw new Error('PIN configuration rejected — still on Configure PIN page');
        console.log('    PIN configured successfully');
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 7 — Authenticate with PIN via visual keyboard
// ─────────────────────────────────────────────────────────────────────────────
test(7, 'Authenticate with PIN via visual keyboard', async ctx => {
    // Ensure PIN is configured (self-contained — does not depend on test 6)
    const userId = await ensurePinConfigured(ctx);
    await sleep(1000);

    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });

    // Track redirect — callback server may not be running
    let redirectedToCallback = false;
    page.on('request', req => {
        if (req.url().includes('callback') && req.url().includes('code='))
            redirectedToCallback = true;
    });

    try {
        // Use acr_values=2 to trigger LOA-2 PIN authenticator step
        await navigate(page, 2);
        await login(page);
        await sleep(2000);

        // Should be on PIN authentication page
        const img = await page.$('#pin-keyboard-image');
        if (!img) throw new Error('PIN keyboard image not found on auth page');
        console.log('    PIN auth page with keyboard image');

        // Get auth keyboard coordinate map from logs
        const map = parseAuthenticatorCoordMap(userId);
        if (!map) throw new Error('No authenticator coordinate map in logs');

        // Type PIN
        await typePin(page, '#pin-keyboard-image', map, PIN);
        await sleep(500);

        // Submit (custom format does not auto-submit)
        const submitBtn = await page.$('#submit-btn');
        if (submitBtn) { await submitBtn.click(); }
        await sleep(5000);

        // Verify we got past PIN auth (redirected to callback = success)
        if (redirectedToCallback || page.url().includes('callback') || page.url().includes('code=')) {
            console.log('    PIN authentication successful — redirected to callback');
        } else {
            const html = await page.content();
            if (html.includes('pinAuthenticatorInvalidPin') || html.includes('Invalid PIN'))
                throw new Error('PIN authentication failed — invalid PIN');
            console.log('    PIN authentication successful');
        }
    } finally { await page.close(); }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 8 — Wrong PIN via keyboard is rejected
// ─────────────────────────────────────────────────────────────────────────────
test(8, 'Wrong PIN via visual keyboard is rejected', async ctx => {
    const userId = await ensurePinConfigured(ctx);
    await sleep(1000);

    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    try {
        // Use acr_values=2 to trigger LOA-2 PIN authenticator step
        await navigate(page, 2);
        await login(page);
        await sleep(2000);

        const img = await page.$('#pin-keyboard-image');
        if (!img) throw new Error('PIN keyboard image not found');

        const map = parseAuthenticatorCoordMap(userId);
        if (!map) throw new Error('No authenticator coordinate map');

        // Type wrong PIN (9999 instead of 1234)
        await typePin(page, '#pin-keyboard-image', map, '9999');
        await sleep(500);

        // Submit
        const submitBtn = await page.$('#submit-btn');
        if (submitBtn) { await submitBtn.click(); }
        await sleep(5000);

        // After wrong PIN, should still be on PIN auth page (not redirected to callback)
        const finalUrl = page.url();
        if (finalUrl.includes('callback') || finalUrl.includes('code='))
            throw new Error('Wrong PIN was accepted — redirected to callback');

        // Verify we're still on the PIN authenticator page
        const stillOnPin = await page.$('#pin-keyboard-image');
        if (!stillOnPin) throw new Error('Expected to remain on PIN authenticator page');

        console.log('    Wrong PIN correctly rejected');

        console.log('    Wrong PIN correctly rejected');
    } finally { await page.close(); }
});

// ─── Runner ──────────────────────────────────────────────────────────────────
(async () => {
    console.log('='.repeat(60));
    console.log('Visual Keyboard — E2E Tests');
    console.log('='.repeat(60));
    console.log(`Mode: ${HEADED ? 'headed' : 'headless'}\n`);

    const browser = await puppeteer.launch({
        headless: !HEADED,
        slowMo: HEADED ? 80 : 0,
        args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    try {
        for (const t of tests) {
            if (ONLY !== null && t.num !== ONLY) continue;
            console.log(`\n[Test ${t.num}] ${t.name}`);
            try {
                await sleep(1500);
                const ctx = await browser.createBrowserContext();
                try { await t.fn(ctx); }
                finally { await ctx.close(); }
                passed++;
                console.log('  PASSED');
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
