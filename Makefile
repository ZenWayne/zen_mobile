# ZenAgent — build & Appium test orchestration (modeled on tarot/ai_tarot/Makefile).

APPIUM_DIR  := tests/appium
APPIUM_PORT := 4723

APK := app/build/outputs/apk/debug/app-debug.apk
DEVICES := QV7808CA8G

# AAR is NOT checked in (built artifact). Rebuild it from the zen repo and
# stage it into app/libs before the first `make build` on a fresh checkout.
AAR_SRC := /home/wayne/tools/zen/android-inference/build/outputs/aar/android-inference-debug.aar
AAR_DST := app/libs/agentflow-android.aar

# On-device model fixture (host source + device staging path). The staging
# copy lives OUTSIDE app data (/data/local/tmp) so `pm clear` never destroys
# it — reset-device restores the model from staging instead of re-pushing
# 2.6 GB over USB.
MODEL_SRC := /home/wayne/tools/zen/models/gemma-4-E2B-it.litertlm
MODEL_STAGE := /data/local/tmp/gemma-4-E2B-it.litertlm
MODEL_DEV := /sdcard/Android/data/com.zenwayne.zenagent/files/models/gemma-4-E2B-it.litertlm
WORKSPACE_DEV := /sdcard/Android/data/com.zenwayne.zenagent/files/workspace

# ── Device reset (test hygiene) ───────────────────────────────────────────────

.PHONY: stage-model reset-device reset-device-full e2e

# One-time: stage the model on-device outside app data.
stage-model:
	@adb -s $(DEVICES) shell "test -s $(MODEL_STAGE)" 2>/dev/null || \
		adb -s $(DEVICES) push $(MODEL_SRC) $(MODEL_STAGE)
	@echo "→ model staged at $(MODEL_STAGE)"

# Reset the state a suite must not inherit, WITHOUT touching files/models/.
#
# `pm clear` is simpler but wipes models/ too, which costs a 2.6 GB on-device
# copy back from staging on every run AND destroys the ~790 MB .xnnpack_cache,
# so the first inference test then pays to regenerate it. Deleting only what
# tests actually carry over keeps both.
#
# Cleared: shared_prefs (incl. the /shared SAF grant the app stores), caches,
# databases, internal files, and the workspace fixture — then the workspace is
# re-seeded. Internal app data is reachable only via `run-as`, which works
# because the suite runs debug builds.
#
# CAVEAT vs pm clear: a persisted SAF URI grant lives in the system, not in app
# data, so it survives this. The app reads its grant from shared_prefs, which IS
# cleared, so /shared still reports unauthorized — but the system-side grant
# lingers. Use `reset-device-full` when that matters.
reset-device: stage-model
	adb -s $(DEVICES) shell am force-stop com.zenwayne.zenagent
	adb -s $(DEVICES) shell run-as com.zenwayne.zenagent \
	  rm -rf shared_prefs cache code_cache databases files no_backup
	adb -s $(DEVICES) shell rm -rf $(WORKSPACE_DEV)
	@adb -s $(DEVICES) shell "test -s $(MODEL_DEV)" 2>/dev/null \
	  || adb -s $(DEVICES) shell "mkdir -p $(dir $(MODEL_DEV)) \
	    && cp $(MODEL_STAGE) $(MODEL_DEV) && chmod 644 $(MODEL_DEV)"
	adb -s $(DEVICES) shell "mkdir -p $(WORKSPACE_DEV) \
	  && echo 'ZenAgent workspace smoke test file.' > $(WORKSPACE_DEV)/hello.txt"
	@echo "→ device reset: app state cleared, model + xnnpack cache kept, workspace seeded"

# Nuclear reset: wipes EVERYTHING including the model, the xnnpack cache and any
# persisted SAF grants, then restores the model from staging. Slow (on-device
# 2.6 GB copy + cache regeneration) — reach for it when a leaked SAF grant or a
# suspect cache is what you are trying to rule out.
reset-device-full: stage-model
	adb -s $(DEVICES) shell pm clear com.zenwayne.zenagent
	@sleep 2
	adb -s $(DEVICES) shell "mkdir -p \$$(dirname $(MODEL_DEV)) \
	  && cp $(MODEL_STAGE) $(MODEL_DEV) \
	  && chmod 644 $(MODEL_DEV) \
	  && mkdir -p $(WORKSPACE_DEV) \
	  && echo 'ZenAgent workspace smoke test file.' > $(WORKSPACE_DEV)/hello.txt \
	  && ls -la \$$(dirname $(MODEL_DEV))"
	@echo "→ device reset (full): all app data cleared, model restored, workspace seeded"

# Full on-device E2E from a clean app-data state (Appium must be running).
e2e: reset-device
	cd $(APPIUM_DIR) && npm run test:all

# ── Build & install ───────────────────────────────────────────────────────────

.PHONY: aar build install install-bc72 build-install

# Copy the agentflow AAR (built by zen/android-inference Bazel) into app/libs.
aar:
	@if [ ! -f $(AAR_SRC) ]; then \
		echo "ERROR: AAR not found at $(AAR_SRC)"; \
		echo "       Build it first in /home/wayne/tools/zen/android-inference (gradle assembleDebug)"; \
		exit 1; \
	fi
	mkdir -p app/libs
	cp $(AAR_SRC) $(AAR_DST)
	@echo "→ AAR staged -> $(AAR_DST)"

build: aar
	./gradlew :app:assembleDebug

install: install-bc72

install-bc72:
	adb -s QV7808CA8G install -r $(APK)

build-install: build install

# ── Appium server ─────────────────────────────────────────────────────────────

.PHONY: appium wait-appium

appium:
	cd $(APPIUM_DIR) && npx appium --port $(APPIUM_PORT) &

wait-appium:
	@echo "Waiting for Appium on port $(APPIUM_PORT)..."
	@until curl -sf http://127.0.0.1:$(APPIUM_PORT)/status > /dev/null 2>&1; do sleep 1; done
	@echo "Appium is ready."

# ── Dynamic pattern rules ──────────────────────────────────────────────────────
#
# Format:  make <suite>-<device>
#
#   suite  : smoke | states | inference | stop | approval |
#            toolmode | toolmode-se | python | shared | all
#   device : bc72 (real, arm64 + model) | emu (x86_64, UI-only)
#
#   emu  runs UI-only suites (smoke/states/approval) — inference/stop require
#        the arm64 .so + model, so they must run on bc72.
#   start-<target> launches Appium first, then the suite.
#
# Examples:
#   make smoke-bc72
#   make inference-bc72
#   make approval-emu
#   make start-all-bc72       # Appium + full suite on the real device

.PHONY: start-%

%-bc72 %-emu:
	@scripts/run-appium-test.sh $@

start-%:
	-cd $(APPIUM_DIR) && npx appium --port $(APPIUM_PORT) > /tmp/appium.log 2>&1 &
	$(MAKE) wait-appium
	$(MAKE) $*
