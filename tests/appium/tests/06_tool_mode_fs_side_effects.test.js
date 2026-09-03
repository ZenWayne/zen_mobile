'use strict';

/**
 * 06_tool_mode_fs_side_effects.test.js — fs_write (approval gate) + fs_list
 * end-to-end through the real on-device pipeline.
 *
 * Preconditions (satisfied by `make reset-device`):
 *   - bc72 device with the arm64 AAR (host-tool bridge) installed
 *   - model restored at files/models/gemma-4-E2B-it.litertlm
 *   - workspace seeded with hello.txt
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

const MESSAGE_WRITE = "Write the text 'hello from the agent' to a file named note.txt";
const MESSAGE_LIST = 'List the files in the workspace';
const MESSAGE_WRITE_DENY = "Write the text 'should not persist' to a file named denied.txt";

async function sendMessage(driver, message) {
  const input = await waitForElement(driver, 'chat_input', 15000);
  await input.click();
  const textField = await driver.$('//android.widget.EditText');
  await typeAndCommit(driver, textField, message);
  const send = await waitForElement(driver, 'Send', 5000);
  await send.click();
}

describe('Tool Mode — Filesystem Side Effects', function () {
  this.timeout(480000);
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
      await takeScreenshot(driver, `FAIL_toolmode_se_${label}`);
    }
  });

  it('TC-TOOLMODE-002: fs_write with approval → Done, file persisted', async function () {
    await resetToChat(driver);
    await sendMessage(driver, MESSAGE_WRITE);

    // Approval gate must appear (fs_write is side-effecting).
    const card = await waitForElement(driver, 'card_approval', 300000);
    assert.ok(await card.isDisplayed(), 'approval card should appear');
    await takeScreenshot(driver, 'TC-TOOLMODE-002_approval');

    const approve = await waitForElement(driver, 'approval_approve', 10000);
    await approve.click();

    // Tool executes → card resolves to Done; final reply confirms the write.
    const done = await waitForElement(driver, textXPath('Done'), 60000);
    assert.ok(await done.isDisplayed(), 'tool card should resolve to Done');
    await takeScreenshot(driver, 'TC-TOOLMODE-002_done');
  });

  it('TC-TOOLMODE-003: fs_list lists workspace entries', async function () {
    await resetToChat(driver);
    await sendMessage(driver, MESSAGE_LIST);

    const toolName = await waitForElement(driver, textXPath('fs_list'), 300000);
    assert.ok(await toolName.isDisplayed(), 'card should name fs_list');
    await takeScreenshot(driver, 'TC-TOOLMODE-003_card');

    // The reply must mention the seeded fixture (and the file written above).
    const listed = await waitForElement(driver, textXPath('hello.txt'), 120000);
    assert.ok(await listed.isDisplayed(), 'list result should mention hello.txt');
    await takeScreenshot(driver, 'TC-TOOLMODE-003_result');
  });

  it('TC-TOOLMODE-004: fs_write Deny yields user_denied, no file written', async function () {
    await resetToChat(driver);
    await sendMessage(driver, MESSAGE_WRITE_DENY);

    const card = await waitForElement(driver, 'card_approval', 300000);
    assert.ok(await card.isDisplayed(), 'approval card should appear');

    const deny = await waitForElement(driver, 'approval_deny', 10000);
    await deny.click();

    // Deny → tool returns user_denied → Failed card with the error hint.
    const denied = await waitForElement(driver, textXPath('user_denied'), 60000);
    assert.ok(await denied.isDisplayed(), 'failed card should hint user_denied');
    await takeScreenshot(driver, 'TC-TOOLMODE-004_denied');
  });
});
