'use strict';

/**
 * 08_shared_storage.test.js — the `/shared` root (SAF, P3).
 *
 * Scope note: granting the tree means driving Android's own document picker,
 * which is a different package with a layout that varies by OEM and version —
 * exactly the kind of system-UI automation that makes suites flaky. So this
 * suite covers what is deterministic and ours: the Settings affordance, and
 * the unauthorized path through the real tool pipeline. Authorizing a tree and
 * reading through it stays a documented manual check (AGENTS.md).
 *
 * Preconditions (satisfied by `make reset-device`): app data cleared, so no
 * tree is authorized when this suite starts.
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

const MESSAGE_SHARED_READ = 'Read the file /shared/notes.txt';

async function openSettings(driver) {
  const back = await waitForElement(driver, 'Back', 15000);
  await back.click();
  await waitForElement(driver, 'sidebar_drawer', 10000);
  const settings = await waitForElement(driver, 'drawer_settings', 10000);
  await settings.click();
}

async function sendMessage(driver, message) {
  const input = await waitForElement(driver, 'chat_input', 15000);
  await input.click();
  const textField = await driver.$('//android.widget.EditText');
  await typeAndCommit(driver, textField, message);
  const send = await waitForElement(driver, 'Send', 5000);
  await send.click();
}

describe('Shared Storage (/shared)', function () {
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
      await takeScreenshot(driver, `FAIL_shared_${label}`);
    }
  });

  it('TC-SHARED-001: Settings offers the grant and reports it unauthorized', async function () {
    await resetToChat(driver);
    await openSettings(driver);

    const row = await waitForElement(driver, 'settings_shared_storage', 15000);
    assert.ok(await row.isDisplayed(), 'shared storage row should be in Settings');

    // With app data cleared there is no grant, so the row says so and offers
    // no revoke action.
    const unauthorized = await driver.$(textXPath('未授权'));
    assert.ok(await unauthorized.isExisting(), 'row should report the unauthorized state');
    const revoke = await driver.$('~settings_shared_storage_release');
    assert.isFalse(await revoke.isExisting(), 'revoke should be hidden without a grant');
    await takeScreenshot(driver, 'TC-SHARED-001_settings');
  });

  it('TC-SHARED-002: /shared without a grant returns shared_not_authorized', async function () {
    await resetToChat(driver);
    await sendMessage(driver, MESSAGE_SHARED_READ);

    // The tool call itself must happen — the router, not the model, is what
    // refuses — and the failure surfaces as a readable error hint.
    const refused = await waitForElement(driver, textXPath('shared_not_authorized'), 300000);
    assert.ok(await refused.isDisplayed(), 'tool result should be shared_not_authorized');
    await takeScreenshot(driver, 'TC-SHARED-002_refused');
  });
});
