'use strict';

/**
 * capabilities.js — Appium 2.x desired capabilities for ZenAgent Android.
 *
 * Target device: Sony XQ-BC72 (QV7808CA8G), Android 12, arm64-v8a — the only
 * device with the native agentflow .so + on-device Gemma model.
 *
 * Environment variables (all optional):
 *   APPIUM_HOST       — Appium server hostname (default: 127.0.0.1)
 *   APPIUM_PORT       — Appium server port      (default: 4723)
 *   DEVICE            — Target device preset    (default: bc72)
 *                       Valid values: bc72 | emu
 *   DEVICE_ID         — Override ADB serial for the selected preset
 *   ANDROID_VERSION   — Override Android platform version string
 *
 * Compose semantics mapping: `Modifier.semantics { contentDescription }` is
 * exposed to UiAutomator2 as accessibility content-desc, so `~label` resolves
 * via driver.$('~label') — same convention as the Flutter test stack.
 */

const APPIUM_HOST = process.env.APPIUM_HOST || '127.0.0.1';
const APPIUM_PORT = parseInt(process.env.APPIUM_PORT || '4723', 10);

const androidCommonCaps = {
  platformName: 'Android',
  'appium:automationName': 'UiAutomator2',
  'appium:appPackage': 'com.zenwayne.zenagent',
  'appium:appActivity': '.MainActivity',

  'appium:noReset': true,
  'appium:fullReset': false,

  'appium:newCommandTimeout': 120,
  'appium:autoLaunch': false,

  // Compose renders into a single window; UiAutomator2 reads content-desc.
  'appium:ensureWebviewsHavePages': false,
  'appium:settings[waitForIdleTimeout]': 1000,
};

const bc72Caps = {
  ...androidCommonCaps,
  'appium:deviceName': 'XQ-BC72',
  'appium:udid': process.env.DEVICE_ID || 'QV7808CA8G',
  'appium:platformVersion': process.env.ANDROID_VERSION || '12',

  _meta: {
    platform: 'android',
    role: 'real device (arm64, native inference)',
  },
};

const emuCaps = {
  ...androidCommonCaps,
  'appium:deviceName': 'Pixel 7 Emulator',
  'appium:udid': process.env.DEVICE_ID || 'emulator-5554',
  'appium:platformVersion': process.env.ANDROID_VERSION || '31',

  _meta: {
    platform: 'android',
    role: 'x86_64 emulator (UI-only suites)',
  },
};

function getRemoteOptions(caps) {
  const { _meta, ...appiumCaps } = caps;
  return {
    hostname: APPIUM_HOST,
    port: APPIUM_PORT,
    path: '/',
    logLevel: 'warn',
    capabilities: appiumCaps,
  };
}

function resolveDeviceCaps() {
  const device = (process.env.DEVICE || 'bc72').toLowerCase();
  switch (device) {
    case 'emu':
      return emuCaps;
    case 'bc72':
    default:
      return bc72Caps;
  }
}

module.exports = {
  bc72Caps,
  emuCaps,
  getRemoteOptions,
  resolveDeviceCaps,
  APPIUM_HOST,
  APPIUM_PORT,
};
