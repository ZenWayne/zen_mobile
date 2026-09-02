'use strict';

/**
 * 01_conversation_states.test.js — Walk seeded design states via the drawer.
 *
 * SampleData seeds conversations c1..c7 + cantstart/corrupt. This suite opens
 * the drawer (Back), selects each state conversation, and asserts the FRD
 * design element renders (approval card, sub-agent card, failure banner,
 * stopped banner, T5/T6 full screens).
 */

const { remote } = require('webdriverio');
const { assert } = require('chai');
const { getRemoteOptions, resolveDeviceCaps } = require('../config/capabilities');
const { waitForElement, descXPath, textXPath, takeScreenshot, resetToChat } = require('../helpers/gestures');

async function openDrawer(driver) {
  const back = await waitForElement(driver, 'Back', 15000);
  await back.click();
  const drawer = await waitForElement(driver, 'sidebar_drawer', 10000);
  assert.ok(await drawer.isDisplayed(), 'drawer should open');
}

// Fallback: open the drawer by tapping the header hamburger button directly
// (some screens' Back is an in-chat back rather than the drawer toggle).
async function openDrawerByToggle(driver) {
  const toggle = await waitForElement(driver, descXPath('sidebar_drawer'), 10000);
  await toggle.click();
}

async function selectConversation(driver, agentName) {
  const item = await waitForElement(driver, descXPath(`conversation:${agentName}`), 10000);
  await item.click();
}

async function backToDrawer(driver) {
  const back = await waitForElement(driver, 'Back', 15000);
  await back.click();
  await waitForElement(driver, 'sidebar_drawer', 10000);
}

describe('Conversation Design States', function () {
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
      await takeScreenshot(driver, `FAIL_states_${label}`);
    }
  });

  it('TC-STATES-001: approval card renders (c2)', async function () {
    await openDrawer(driver);
    await selectConversation(driver, 'Travel Planner'); // first Travel Planner = c2 approval? fall through
    // c1 is first Travel Planner; assert chat opened, then re-navigate.
    await waitForElement(driver, 'Back', 10000);

    // The approval card belongs to the 'Awaiting approval…' conversation —
    // select it by its seeded lastMessage text in the drawer.
    await backToDrawer(driver);
    const awaiting = await waitForElement(driver, textXPath('Awaiting approval'), 10000);
    await awaiting.click();

    const approval = await waitForElement(driver, 'card_approval', 10000);
    assert.ok(await approval.isDisplayed(), 'approval card should render');
    const deny = await waitForElement(driver, 'approval_deny', 5000);
    const approve = await waitForElement(driver, 'approval_approve', 5000);
    assert.ok(await deny.isDisplayed() && await approve.isDisplayed(), 'deny/approve buttons visible');

    await takeScreenshot(driver, 'TC-STATES-001_approval_card');
  });

  it('TC-STATES-002: sub-agent card renders (c7 succeeded)', async function () {
    await backToDrawer(driver);
    const item = await waitForElement(driver, textXPath('Sub-agent returned'), 10000);
    await item.click();

    const subagent = await waitForElement(driver, 'card_subagent', 10000);
    assert.ok(await subagent.isDisplayed(), 'sub-agent card should render');
    const goal = await waitForElement(driver, textXPath('Goal: find top-rated'), 5000);
    assert.ok(await goal.isDisplayed(), 'sub-agent goal should render');

    await takeScreenshot(driver, 'TC-STATES-002_subagent_card');
  });

  it('TC-STATES-003: failure banner renders with Retry (c5 OOM)', async function () {
    await backToDrawer(driver);
    const item = await waitForElement(driver, textXPath('Out of memory'), 10000);
    await item.click();

    const banner = await waitForElement(driver, 'banner_failure', 10000);
    assert.ok(await banner.isDisplayed(), 'failure banner should render');
    const retry = await waitForElement(driver, 'action_retry', 5000);
    const logs = await waitForElement(driver, 'action_view_logs', 5000);
    assert.ok(await retry.isDisplayed() && await logs.isDisplayed(), 'retry + view logs visible');

    await takeScreenshot(driver, 'TC-STATES-003_failure_banner');
  });

  it('TC-STATES-004: stopped banner renders with Resume (c6)', async function () {
    await backToDrawer(driver);
    const item = await waitForElement(driver, textXPath('Cancelled after'), 10000);
    await item.click();

    const banner = await waitForElement(driver, 'banner_stopped', 10000);
    assert.ok(await banner.isDisplayed(), 'stopped banner should render');
    const restart = await waitForElement(driver, 'action_restart', 5000);
    const resume = await waitForElement(driver, 'action_resume', 5000);
    assert.ok(await restart.isDisplayed() && await resume.isDisplayed(), 'restart + resume visible');

    await takeScreenshot(driver, 'TC-STATES-004_stopped_banner');
  });

  it("TC-STATES-005: T5 can't-start full screen via cantstart entry", async function () {
    await backToDrawer(driver);
    // Scroll the drawer list until "Can't start" is visible (cantstart is near
    // the bottom of the seeded conversation list).
    const size = await driver.getWindowSize();
    const startY = Math.round(size.height * 0.75);
    const endY = Math.round(size.height * 0.25);
    for (let i = 0; i < 4; i++) {
      const found = await driver.$(`//*[contains(@text,"Can't start")]`);
      if (await found.isExisting()) break;
      await driver.performActions([
        { type: 'pointer', id: 'finger1', parameters: { pointerType: 'touch' },
          actions: [
            { type: 'pointerMove', duration: 0, x: Math.round(size.width / 2), y: startY },
            { type: 'pointerDown', button: 0 },
            { type: 'pointerMove', duration: 400, x: Math.round(size.width / 2), y: endY },
            { type: 'pointerUp', button: 0 },
          ] },
      ]);
      await new Promise((r) => setTimeout(r, 600));
    }
    const item = await waitForElement(driver, textXPath("Can't start"), 10000);
    await item.click();

    const screen = await waitForElement(driver, 't5_cant_start', 10000);
    assert.ok(await screen.isDisplayed(), 'T5 screen should render');
    const edit = await waitForElement(driver, 't5_edit_workflow', 5000);
    assert.ok(await edit.isDisplayed(), 'edit workflow button visible');

    await takeScreenshot(driver, 'TC-STATES-005_t5_screen');
  });

  it('TC-STATES-006: T6 restore-error full screen via corrupt entry', async function () {
    // Dismiss the T5 full screen with the back key (its close icon's a11y
    // node is flaky on this device), then continue to the drawer.
    await driver.pressKeyCode(4);
    await new Promise((r) => setTimeout(r, 1200));
    const back = await waitForElement(driver, 'Back', 10000);
    await back.click(); // open drawer
    await waitForElement(driver, 'sidebar_drawer', 10000);

    // corrupt is at the drawer bottom — scroll until visible.
    const size = await driver.getWindowSize();
    const startY = Math.round(size.height * 0.75);
    const endY = Math.round(size.height * 0.25);
    for (let i = 0; i < 4; i++) {
      const found = await driver.$(`//*[contains(@text,"Session corrupt")]`);
      if (await found.isExisting()) break;
      await driver.performActions([
        { type: 'pointer', id: 'finger1', parameters: { pointerType: 'touch' },
          actions: [
            { type: 'pointerMove', duration: 0, x: Math.round(size.width / 2), y: startY },
            { type: 'pointerDown', button: 0 },
            { type: 'pointerMove', duration: 400, x: Math.round(size.width / 2), y: endY },
            { type: 'pointerUp', button: 0 },
          ] },
      ]);
      await new Promise((r) => setTimeout(r, 600));
    }

    const item = await waitForElement(driver, textXPath('Session corrupt'), 10000);
    await item.click();

    const screen = await waitForElement(driver, 't6_restore_error', 10000);
    assert.ok(await screen.isDisplayed(), 'T6 screen should render');
    const fresh = await waitForElement(driver, 't6_start_fresh', 5000);
    const pick = await waitForElement(driver, 't6_pick_session', 5000);
    assert.ok(await fresh.isDisplayed() && await pick.isDisplayed(), 'start fresh + pick visible');

    await takeScreenshot(driver, 'TC-STATES-006_t6_screen');
  });
});
