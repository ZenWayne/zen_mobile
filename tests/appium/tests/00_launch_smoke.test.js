'use strict';

/**
 * 00_launch_smoke.test.js — App launches; header + messages + input bar render.
 *
 * Selector convention (from ui/TestTags.kt):
 *   - Header:      Back / header_agent_name / header_status / Agent tree
 *   - Messages:    messages_list, bubble_user, bubble_agent, message_timestamp
 *   - Input bar:   chat_input, btn_add, btn_send / btn_stop
 *
 * Requires the device already unlocked and the app installed (debug APK).
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const { waitForElement, descXPath, textXPath, takeScreenshot } = require('../helpers/gestures');

describe('Launch & Smoke', function () {
  this.timeout(90000);
  let driver;

  before(async function () {
    driver = await remote(getRemoteOptions(resolveDeviceCaps()));
    await driver.activateApp('com.zenwayne.zenagent');
  });

  after(async function () {
    if (driver) await driver.deleteSession();
  });

  afterEach(async function () {
    if (this.currentTest && this.currentTest.state === 'failed') {
      const label = this.currentTest.title.replace(/\s+/g, '_').toLowerCase();
      await takeScreenshot(driver, `FAIL_smoke_${label}`);
    }
  });

  it('TC-SMOKE-001: app launches to a conversation', async function () {
    // Back button = chat screen is front (not drawer/settings)
    const back = await waitForElement(driver, 'Back', 20000);
    assert.ok(await back.isDisplayed(), 'chat screen should be visible');

    const agentName = await waitForElement(driver, 'header_agent_name', 10000);
    assert.ok(await agentName.isDisplayed(), 'header agent name should be visible');
  });

  it('TC-SMOKE-002: header status text is present (any state)', async function () {
    const status = await waitForElement(driver, 'header_status', 10000);
    const text = await status.getText();
    // Accept any of the FR-1.3 labels — the exact value depends on run state.
    const known = [
      'Generating…', 'Awaiting approval…', 'Sub-agent returned a proposal',
      'Online', 'Run failed', 'Stopped', 'Completed with warnings',
    ];
    assert.isTrue(known.includes(text), `unexpected status text: "${text}"`);
  });

  it('TC-SMOKE-003: input bar shows placeholder and send button', async function () {
    const input = await waitForElement(driver, 'chat_input', 10000);
    // Placeholder is drawn by the text field — assert the field exists.
    assert.ok(await input.isDisplayed(), 'chat input should be visible');

    const send = await waitForElement(driver, 'Send', 10000);
    assert.ok(await send.isDisplayed(), 'send button should be visible');

    await takeScreenshot(driver, 'TC-SMOKE-003_input_bar');
  });

  it('TC-SMOKE-004: history messages render (c1 seed)', async function () {
    // c1 seed: "Plan a 5-day Tokyo trip and book the flights and hotel ✈️"
    const userMsg = await waitForElement(driver, textXPath('Tokyo trip'), 10000);
    assert.ok(await userMsg.isDisplayed(), 'seeded user message should render');
  });
});
