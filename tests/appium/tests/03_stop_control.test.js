'use strict';

/**
 * 03_stop_control.test.js — Stop button interrupts a running inference.
 *
 * Sends a message, waits for btn_stop, taps it, asserts:
 *   - header_status → "Stopped"
 *   - stopped banner (banner_stopped) with Restart + Resume
 *
 * Requires the real device + model (same as 02_inference_streaming).
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const { waitForElement, takeScreenshot, resetToChat, typeAndCommit } = require('../helpers/gestures');

describe('Stop Control', function () {
  this.timeout(180000);
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
      await takeScreenshot(driver, `FAIL_stop_${label}`);
    }
  });

  it('TC-STOP-001: stop interrupts the run and shows stopped banner', async function () {
    const input = await waitForElement(driver, 'chat_input', 15000);
    await input.click();
    const textField = await driver.$('//android.widget.EditText');
    await typeAndCommit(driver, textField, 'Tell me a long story about the history of tea.');

    const send = await waitForElement(driver, 'Send', 10000);
    await send.click();

    // Running state must appear first.
    const stop = await waitForElement(driver, 'Stop', 30000);
    assert.ok(await stop.isDisplayed(), 'stop button should be visible while running');

    await stop.click();

    // Header flips to Stopped; stopped banner appears.
    const status = await waitForElement(driver, 'header_status', 30000);
    const statusText = await status.getText();
    assert.strictEqual(statusText, 'Stopped', 'status should be Stopped after stop');

    const banner = await waitForElement(driver, 'banner_stopped', 15000);
    assert.ok(await banner.isDisplayed(), 'stopped banner should render');
    const restart = await waitForElement(driver, 'action_restart', 5000);
    const resume = await waitForElement(driver, 'action_resume', 5000);
    assert.ok(await restart.isDisplayed() && await resume.isDisplayed(), 'restart + resume visible');

    await takeScreenshot(driver, 'TC-STOP-001_stopped');
  });
});
