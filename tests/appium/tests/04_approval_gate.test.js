'use strict';

/**
 * 04_approval_gate.test.js — Deny / Approve & run interaction on the gate.
 *
 * Uses the seeded c2 conversation (ApprovalRequest book_hotel). Deny must
 * transition the run to Failed (FRD Q2); Approve & run appends a success tool
 * card and continues (Running).
 *
 * NOTE: the AppRoot demo wiring replays these transitions on the in-memory
 * conversation; assertions target the resulting UI state.
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const { waitForElement, textXPath, takeScreenshot, resetToChat } = require('../helpers/gestures');

async function openApprovalConversation(driver) {
  const back = await waitForElement(driver, 'Back', 15000);
  await back.click();
  await waitForElement(driver, 'sidebar_drawer', 10000);
  const item = await waitForElement(driver, textXPath('Awaiting approval'), 10000);
  await item.click();
  await waitForElement(driver, 'card_approval', 10000);
}

describe('Approval Gate', function () {
  this.timeout(120000);
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
      await takeScreenshot(driver, `FAIL_approval_${label}`);
    }
  });

  it('TC-APPROVAL-001: approval card shows tool + args', async function () {
    await openApprovalConversation(driver);
    const card = await waitForElement(driver, 'card_approval', 10000);
    assert.ok(await card.isDisplayed(), 'approval card visible');

    const tool = await waitForElement(driver, textXPath('book_hotel'), 5000);
    const args = await waitForElement(driver, textXPath('¥48,000'), 5000);
    assert.ok(await tool.isDisplayed(), 'tool name should render');
    assert.ok(await args.isDisplayed(), 'args should render');
    await takeScreenshot(driver, 'TC-APPROVAL-001_card');
  });

  it('TC-APPROVAL-002: Deny transitions run to Failed (FRD Q2)', async function () {
    await openApprovalConversation(driver);
    const deny = await waitForElement(driver, 'approval_deny', 10000);
    await deny.click();

    const status = await waitForElement(driver, 'header_status', 15000);
    const statusText = await status.getText();
    assert.strictEqual(statusText, 'Run failed', 'status should be Run failed after deny');

    const banner = await waitForElement(driver, 'banner_failure', 15000);
    assert.ok(await banner.isDisplayed(), 'failure banner should render after deny');
    await takeScreenshot(driver, 'TC-APPROVAL-002_denied');
  });
});
