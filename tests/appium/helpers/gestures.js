'use strict';

/**
 * gestures.js — Reusable helpers for ZenAgent Appium tests.
 *
 * All element lookups use the Composed semantics labels from
 * ui/TestTags.kt (exposed as content-desc). Fall back to text-based XPath
 * when a label is unavailable.
 */

const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || '/tmp/zenagent-shots';
const fs = require('fs');
const path = require('path');

/** Wait for an element by accessibility id or XPath; throws on timeout. */
async function waitForElement(driver, locator, timeoutMs = 15000) {
  const isXpath = locator.startsWith('/') || locator.startsWith('(');
  const element = isXpath ? await driver.$(locator) : await driver.$(`~${locator}`);
  await element.waitForExist({ timeout: timeoutMs, interval: 500 });
  await element.waitForDisplayed({ timeout: timeoutMs });
  return element;
}

/** Wait for an element to disappear. */
async function waitForElementToDisappear(driver, locator, timeoutMs = 10000) {
  const isXpath = locator.startsWith('/') || locator.startsWith('(');
  const element = isXpath ? await driver.$(locator) : await driver.$(`~${locator}`);
  await element.waitForDisplayed({ timeout: timeoutMs, reverse: true });
}

/** XPath matching a content-desc prefix (Compose semantics label). */
function descXPath(label) {
  return `//*[contains(@content-desc,"${label}")]`;
}

/** XPath matching visible text. */
function textXPath(text) {
  return `//*[contains(@text,"${text}")]`;
}

/** Tap the center of an element. */
async function tap(driver, locator, timeoutMs = 15000) {
  const el = await waitForElement(driver, locator, timeoutMs);
  await el.click();
  return el;
}

/** Tap by explicit coordinates. */
async function tapAt(driver, x, y) {
  await driver.touchAction([{ action: 'tap', x, y }]);
}

/** Swipe up (scroll down the list). */
async function swipeUp(driver, duration = 400) {
  const size = await driver.getWindowSize();
  const x = size.width / 2;
  const y1 = size.height * 0.7;
  const y2 = size.height * 0.3;
  await driver.touchAction([{ action: 'press', x, y: y1 }, { action: 'wait', ms: duration }, { action: 'moveTo', x, y: y2 }, { action: 'release' }]);
}

/** Capture a screenshot (best effort — directory auto-created). */
async function takeScreenshot(driver, name) {
  try {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    const png = await driver.takeScreenshot();
    const file = path.join(SCREENSHOT_DIR, `${name}.png`);
    fs.writeFileSync(file, Buffer.from(png, 'base64'));
    return file;
  } catch (e) {
    return null;
  }
}

/**
 * Force the app into a clean chat screen regardless of prior suite state:
 *   - Kill the process first: conversation state is in-memory only, so a
 *     fresh process = clean ViewModel (default c1 conversation, empty input,
 *     no leftover run banners). This is the per-test isolation that makes
 *     suites order-independent.
 *   - If a T5/T6 full screen is up, dismiss it (Close / Start fresh / Back).
 *   - If the drawer is open, close it.
 *   - If still not on chat, relaunch the activity.
 *
 * Must be called from the owner suite's `before` — suites run in one Appium
 * session sequence and cannot assume the previous suite left the chat screen.
 */
async function resetToChat(driver) {
  await driver.terminateApp('com.zenwayne.zenagent');
  await new Promise((r) => setTimeout(r, 800));
  await driver.activateApp('com.zenwayne.zenagent');
  await new Promise((r) => setTimeout(r, 2500));

  const isT6 = await driver.$('//*[contains(@content-desc,"t6_restore_error")]').isExisting();
  if (isT6) {
    const fresh = await driver.$('//*[contains(@content-desc,"t6_start_fresh")]');
    if (await fresh.isExisting()) await fresh.click();
    await new Promise((r) => setTimeout(r, 1200));
  }

  const isT5 = await driver.$('//*[contains(@content-desc,"t5_cant_start")]').isExisting();
  if (isT5) {
    const close = await driver.$('//*[contains(@content-desc,"Close")]');
    if (await close.isExisting()) await close.click();
    await new Promise((r) => setTimeout(r, 1200));
  }

  // Close drawer if open (Back first opens it, app Back dismisses — detect by Back icon).
  const onChat = await driver.$('//*[contains(@content-desc,"Back")]').isExisting();
  if (!onChat) {
    await driver.activateApp('com.zenwayne.zenagent');
    await new Promise((r) => setTimeout(r, 1500));
  }
}

/**
 * Type into a Compose text field and wait until the text is committed.
 *
 * Compose commits IME text asynchronously; hiding the keyboard right after
 * setValue drops the composing text and the field ends up empty (send then
 * no-ops). Poll the field's text until it matches, then hide the keyboard.
 */
async function typeAndCommit(driver, textField, text) {
  await textField.click();
  await textField.setValue(text);
  const deadline = Date.now() + 10000;
  for (;;) {
    const current = await textField.getText();
    if (current === text) break;
    if (Date.now() > deadline) {
      throw new Error(`text not committed: got '${current}' want '${text}'`);
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  await driver.hideKeyboard();
}

module.exports = {
  waitForElement,
  waitForElementToDisappear,
  descXPath,
  textXPath,
  tap,
  tapAt,
  swipeUp,
  takeScreenshot,
  resetToChat,
  typeAndCommit,
  SCREENSHOT_DIR,
};
