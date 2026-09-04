'use strict';

/**
 * 07_tool_mode_python.test.js — python_run (Chaquopy, P2) end-to-end through
 * the real on-device pipeline.
 *
 * Preconditions (satisfied by `make reset-device`):
 *   - bc72 device with the arm64 AAR (host-tool bridge) installed
 *   - model restored at files/models/gemma-4-E2B-it.litertlm
 *
 * Note on timings: the first approved run pays for `Python.start()` (the
 * interpreter is started lazily on the worker), so the first assertion after
 * Approve gets a generous timeout.
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const {
  waitForElement,
  textXPath,
  takeScreenshot,
  resetToChat,
  typeAndCommit,
} = require('../helpers/gestures');

const MESSAGE_COMPUTE =
  'Use python to calculate 6 * 7 and print the result';
const MESSAGE_DENY =
  'Use python to print the string blocked';

async function sendMessage(driver, message) {
  const input = await waitForElement(driver, 'chat_input', 15000);
  await input.click();
  const textField = await driver.$('//android.widget.EditText');
  await typeAndCommit(driver, textField, message);
  const send = await waitForElement(driver, 'Send', 5000);
  await send.click();
}

describe('Tool Mode — Python', function () {
  this.timeout(600000);
  let driver;

  before(async function () {
    driver = await remote(getRemoteOptions(resolveDeviceCaps()));
  });

  after(async function () {
    if (driver) await driver.deleteSession();
  });

  afterEach(async function () {
    if (this.currentTest && this.currentTest.state === 'failed') {
      const label = this.currentTest.title.replace(/\s+/g, '_').toLowerCase();
      await takeScreenshot(driver, `FAIL_toolmode_py_${label}`);
    }
  });

  it('TC-TOOLMODE-005: python_run is gated, then returns captured stdout', async function () {
    await resetToChat(driver);
    await sendMessage(driver, MESSAGE_COMPUTE);

    // Running code is side-effecting → the approval gate must open first.
    const card = await waitForElement(driver, 'card_approval', 300000);
    assert.ok(await card.isDisplayed(), 'approval card should appear');
    const named = await driver.$(textXPath('python_run'));
    assert.ok(await named.isExisting(), 'the gated tool should be python_run');
    await takeScreenshot(driver, 'TC-TOOLMODE-005_approval');

    const approve = await waitForElement(driver, 'approval_approve', 10000);
    await approve.click();

    // First run also starts the interpreter — allow for that before Done.
    const done = await waitForElement(driver, textXPath('Done'), 180000);
    assert.ok(await done.isDisplayed(), 'tool card should resolve to Done');

    // stdout must round-trip: 6 * 7 computed by Python, not by the model.
    const answer = await waitForElement(driver, textXPath('42'), 120000);
    assert.ok(await answer.isDisplayed(), 'captured stdout should contain 42');
    await takeScreenshot(driver, 'TC-TOOLMODE-005_done');
  });

  it('TC-TOOLMODE-006: Deny keeps code away from the interpreter', async function () {
    await resetToChat(driver);
    await sendMessage(driver, MESSAGE_DENY);

    const card = await waitForElement(driver, 'card_approval', 300000);
    assert.ok(await card.isDisplayed(), 'approval card should appear');

    const deny = await waitForElement(driver, 'approval_deny', 10000);
    await deny.click();

    const denied = await waitForElement(driver, textXPath('user_denied'), 60000);
    assert.ok(await denied.isDisplayed(), 'failed card should hint user_denied');
    await takeScreenshot(driver, 'TC-TOOLMODE-006_denied');
  });
});
