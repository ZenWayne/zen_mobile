# ZenAgent — build & Appium test orchestration (modeled on tarot/ai_tarot/Makefile).

APPIUM_DIR  := tests/appium
APPIUM_PORT := 4723

APK := app/build/outputs/apk/debug/app-debug.apk
DEVICES := QV7808CA8G

# AAR is NOT checked in (built artifact). Rebuild it from the zen repo and
# stage it into app/libs before the first `make build` on a fresh checkout.
AAR_SRC := /home/wayne/tools/zen/android-inference/build/outputs/aar/android-inference-debug.aar
AAR_DST := app/libs/agentflow-android.aar

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
#   suite  : smoke | states | inference | stop | approval | all
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
