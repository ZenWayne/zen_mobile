'use strict';

/**
 * 02_inference_streaming.test.js — REAL on-device Gemma inference.
 *
 * Requires: bc72 (QV7808CA8G), debug APK with the agentflow .so, model pushed
 * to getExternalFilesDir("models"). Sends a message, watches the streaming
 * bubble (+ Generating note), and asserts the run completes to Online with a
 * non-empty agent reply.
 *
 * Does NOT run on the x86_64 emulator — the arm64 .so cannot load there
 * (the run fails to Failed with a NativeUnavailable banner instead).
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const { waitForElement, descXPath, textXPath, takeScreenshot, resetToChat, dismissKeyboard } = require('../helpers/gestures');

describe('On-Device Inference Streaming', function () {
  this.timeout(360000);
  let driver;

  before(async function () {
    driver = await remote(getRemoteOptions(resolveDeviceCaps()));
    await resetToChat(driver);
  });

  after(async function () {
    if (driver) await driver.deleteSession();
  });

  afterEach(async function () {
    if (this.currentTest && this.currentTest.state === 'failed') {
      const label = this.currentTest.title.replace(/\s+/g, '_').toLowerCase();
      await takeScreenshot(driver, `FAIL_inference_${label}`);
    }
  });

  it('TC-INF-001: sending starts a run and streams a reply', async function () {
    const input = await waitForElement(driver, 'chat_input', 15000);
    await input.click();

    const textField = await driver.$('//android.widget.EditText');
    await textField.setValue('What is 2+2? Answer in one word.');

    // Hide keyboard, then tap send (btn_send label on the Icon contentDescription).
    // Keyboard stays up: the Compose input bar sits above it and the Send
    // button is visible. hideKeyboard 500s on this device's IME and the
    // back-key fallback would navigate the app away from the chat.

    const send = await waitForElement(driver, 'Send', 10000);
    await send.click();

    // Wait for the generating state: Stop button + Generating note.
    // Cold start needs the model load + KV cache alloc (can take minutes on
// a fresh install - see comment below). Give Stop a long window.
const stop = await waitForElement(driver, 'Stop', 180000);
    assert.ok(await stop.isDisplayed(), 'stop button should appear while running');
    await takeScreenshot(driver, 'TC-INF-001_running');

    // Completion is NOT fast: the first on-device run loads the Gemma model
    // + allocates KV cache, which can take minutes. Poll the header status
    // until it leaves Running, up to the suite ceiling.
    const statusEl = await waitForElement(driver, 'header_status', 30000);
    let lastText = '';
    const deadline = Date.now() + 150000;
    while (Date.now() < deadline) {
      lastText = await statusEl.getText();
      if (lastText !== 'Generating…') break;
      await new Promise((r) => setTimeout(r, 3000));
    }

    if (lastText === 'Run failed') {
      await takeScreenshot(driver, 'TC-INF-001_failed');
      assert.fail('inference failed — check model presence / .so arch');
    }
    assert.strictEqual(lastText, 'Online', 'run should complete to Online');

    // The agent reply must be non-empty.
    const bubbles = await driver.$$('//*[contains(@content-desc,"bubble_agent")]');
    assert.ok(bubbles.length > 0, 'agent bubble should exist');
    await takeScreenshot(driver, 'TC-INF-001_completed');
  });
});
