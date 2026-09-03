'use strict';

/**
 * 05_tool_mode_fs.test.js — live tool-mode filesystem round-trip.
 *
 * Sends "read workspace file hello.txt" through the real on-device pipeline
 * (constrained tool-call decoding → JNI host-tool bridge → FsReadTool) and
 * asserts the tool card renders with fs_read resolving to Done, and the final
 * reply mentions the file content.
 *
 * Preconditions:
 *   - bc72 device with the arm64 AAR installed (agentflow host-tool bridge)
 *   - model at /sdcard/Android/data/com.zenwayne.zenagent/files/models/
 *   - workspace file hello.txt (created by the test itself via adb is NOT
 *     allowed — E2E owns the device state; the file must pre-exist or the
 *     test fails with a clear message)
 *
 * NOTE: text entry uses setValue() (bypasses the IME — never adb input text).
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const { waitForElement, textXPath, takeScreenshot, resetToChat, typeAndCommit } = require('../helpers/gestures');

const MESSAGE = 'read workspace file hello.txt';

describe('Tool Mode — Filesystem', function () {
  this.timeout(480000);
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
      await takeScreenshot(driver, `FAIL_toolmode_${label}`);
    }
  });

  it('TC-TOOLMODE-001: fs_read tool card resolves to Done with file content', async function () {
    const input = await waitForElement(driver, 'chat_input', 15000);
    await input.click();

    // Compose's BasicTextField surfaces to UiAutomator2 as an EditText node;
    // setValue on it injects text without touching the IME (never driver.keys
    // — a Chinese IME transliterates raw key events). typeAndCommit waits for
    // the async commit so the send doesn't fire with an empty field.
    const textField = await driver.$('//android.widget.EditText');
    await typeAndCommit(driver, textField, MESSAGE);
    await takeScreenshot(driver, 'TC-TOOLMODE-001_typed');

    const send = await waitForElement(driver, 'Send', 5000);
    await send.click();

    // The tool card appears when the native loop dispatches fs_read. First
    // on-device run also pays model load + xnnpack cache generation, so the
    // timeout is generous. NOTE: the seeded conversation already contains a
    // search_flights card — wait for the fs_read text specifically.
    const toolName = await waitForElement(driver, textXPath('fs_read'), 300000);
    assert.ok(await toolName.isDisplayed(), 'card should name fs_read');
    await takeScreenshot(driver, 'TC-TOOLMODE-001_card_running');

    // ToolReturn resolves the card to Done (FsReadTool returns in ms).
    const done = await waitForElement(driver, textXPath('Done'), 30000);
    assert.ok(await done.isDisplayed(), 'tool card should resolve to Done');
    await takeScreenshot(driver, 'TC-TOOLMODE-001_card_done');

    // Final answer echoes the workspace file's content.
    const reply = await waitForElement(
      driver,
      textXPath('ZenAgent workspace smoke test file'),
      120000,
    );
    assert.ok(await reply.isDisplayed(), 'final reply should mention file content');
    await takeScreenshot(driver, 'TC-TOOLMODE-001_final');
  });
});
