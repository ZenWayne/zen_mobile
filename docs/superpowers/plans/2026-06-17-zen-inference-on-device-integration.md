# On-Device Inference Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run real on-device streaming chat in `zen_mobile` through the `zen` agentflow lib on an arm64-v8a phone.

**Architecture:** Bazel cross-compiles `libagentflow_jni.so` for arm64-v8a (linking LiteRT-LM static archives built for Android via its CMake `android-arm64` preset). A new `zen/android-inference` AAR packages that `.so` plus the ported Kotlin DSL. `zen_mobile` consumes the AAR behind an `InferenceClient` seam; a `ChatViewModel` collects `JsonWorkflow.streamTokens(): Flow<String>` into the existing Chat screen, with Stop wired to flow cancellation.

**Tech Stack:** Kotlin, Jetpack Compose, Bazel + Android NDK, CMake (LiteRT-LM), JNI, kotlinx-coroutines (`Flow`), JUnit5 (lib) / JUnit4 + Robolectric-free JVM unit tests (app).

## Global Constraints

- Target ABI: **arm64-v8a only**. No x86_64/emulator, no GPU/OpenCL plugins (CPU/XNNPACK path only).
- LiteRT-LM Android CMake preset `android-arm64` targets **API 28**, STL `c++_shared`, and expects **NDK r26d** at `$HOME/android-ndk/android-ndk-r26d`. Installed NDK is 28.2 at `/opt/android-sdk/ndk` — resolve the version (Task 1, Step 1).
- App: `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35` (matches existing `zen_mobile/app/build.gradle.kts`).
- App package: `com.zenwayne.zenagent`. App-specific model dir on device: `getExternalFilesDir("models")`, model file name `gemma-4-E2B-it.litertlm`.
- Reuse `agentflow/*` C++ and `jni/agentflow_jni.cc` **unchanged** — native work is toolchain config only.
- Model is delivered by **adb push** (no APK bundling, no in-app download this slice).
- The agentflow C++/JNI contract must stay green: zen's existing host JVM test (`kotlin/.../WorkflowJsonTest.kt`, `SmokeTest.kt`) must still pass.
- Workflow JSON schema (verified in `zen/kotlin/src/test/kotlin/agentflow/WorkflowJsonTest.kt`):
  ```json
  {"schema_version":1,"name":"chat","version":"v1",
   "state":{"kind":"dynamic_json","fields":{}},
   "agents":{"main":{"system_prompt":"<text>","model":{"max_output_tokens":512},"tools":[]}},
   "main":"main"}
  ```

---

## Phase 1 — Native arm64 build (retire the dominant risk first)

> Phase 1 produces an arm64 `libagentflow_jni.so`. Do not start Phase 2 until
> `file bazel-bin/jni/libagentflow_jni.so` reports `ELF 64-bit ... ARM aarch64`.
> All Phase 1 work is in the **`/home/wayne/tools/zen`** repo.

### Task 1: Cross-compile LiteRT-LM static archives for Android arm64

**Files:**
- Use: `zen/LiteRT-LM/CMakePresets.json` (preset `android-arm64`, already present)
- Create: `zen/third_party/litert_lm/lib/arm64-v8a/` (destination for arm64 `.a` archives)
- Reference: `zen/third_party/litert_lm/BUILD.bazel` (host archives `cc_import`, names `libce_staging.a` / `libce_external.a` / `libkissfft-float.so.131`)

**Interfaces:**
- Produces: arm64 static archives at `zen/third_party/litert_lm/lib/arm64-v8a/{libce_staging.a, libce_external.a}` and the arm64 `libkissfft-float.so.131`, each verified as `ARM aarch64`. Task 2 imports these by path.

- [ ] **Step 1: Resolve the NDK version the preset needs**

The preset hard-codes NDK r26d. The installed NDK is 28.2. Install r26d (LiteRT-LM is verified against it) rather than risk an untested NDK:
```bash
mkdir -p "$HOME/android-ndk" && cd "$HOME/android-ndk"
curl -fL -o ndk.zip https://dl.google.com/android/repository/android-ndk-r26d-linux.zip
unzip -q ndk.zip && rm ndk.zip
ls "$HOME/android-ndk/android-ndk-r26d/source.properties"   # must exist
export ANDROID_NDK_HOME="$HOME/android-ndk/android-ndk-r26d"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
```
Expected: `source.properties` lists `Pkg.Revision = 26.3.x` (r26d).

- [ ] **Step 2: Discover how the host archives were produced**

The host `.a`s are gitignored and machine-built; the split into `staging`/`external` is non-obvious. Recover the exact recipe before building arm64:
```bash
cd /home/wayne/tools/zen
git log --oneline -- third_party/litert_lm/BUILD.bazel | head
grep -rn "libce_staging\|libce_external\|cmake --preset\|--target" \
  docs/superpowers/plans .litert_build 2>/dev/null | head -40
cat .litert_build/CMakeCache.txt | grep -iE "PRESET|TARGET|INSTALL|ANDROID" | head
```
Expected: identify the CMake targets/install step that yielded the two host archives (the same targets are rebuilt for arm64 below). If no recipe is found, treat the `litert_lm_main` link line in `LiteRT-LM/CMakeLists.txt` as the source of which component libs become `staging` vs `external`.

- [ ] **Step 3: Configure + build LiteRT-LM for arm64 via the preset**

```bash
cd /home/wayne/tools/zen/LiteRT-LM
cmake --preset android-arm64
cmake --build --preset android-arm64 -j"$(nproc)" 2>&1 | tail -30
```
Expected: a `build-android-arm64/` (or preset `binaryDir`) tree containing `.a`/`.so` outputs. If the build fails, the failure is the Phase 1 risk materializing — fix toolchain/version issues here before proceeding.

- [ ] **Step 4: Stage the arm64 archives where Bazel will import them**

Mirror the host layout under an `arm64-v8a/` subdir (replace the two source paths below with the actual archive paths found in Step 2/3):
```bash
cd /home/wayne/tools/zen
mkdir -p third_party/litert_lm/lib/arm64-v8a
cp LiteRT-LM/build-android-arm64/<path>/libce_staging.a  third_party/litert_lm/lib/arm64-v8a/
cp LiteRT-LM/build-android-arm64/<path>/libce_external.a third_party/litert_lm/lib/arm64-v8a/
cp LiteRT-LM/build-android-arm64/<path>/libkissfft-float.so.131 third_party/litert_lm/lib/arm64-v8a/
```

- [ ] **Step 5: Verify the archives are arm64**

```bash
cd /home/wayne/tools/zen
for f in third_party/litert_lm/lib/arm64-v8a/libce_*.a; do
  echo "$f"; ar t "$f" | head -1 | xargs -I{} sh -c "ar p '$f' {} | file -"
done
file third_party/litert_lm/lib/arm64-v8a/libkissfft-float.so.131
```
Expected: object members report `ELF 64-bit LSB ... ARM aarch64`; the `.so` reports `ARM aarch64`.

- [ ] **Step 6: Commit the build recipe (not the archives)**

Archives are large/machine-built; keep them gitignored like the host ones. Commit a short build note instead:
```bash
cd /home/wayne/tools/zen
cat >> third_party/litert_lm/README.android.md <<'EOF'
# Android arm64 archives
Built from LiteRT-LM via: `cmake --preset android-arm64 && cmake --build --preset android-arm64`
NDK r26d (ANDROID_NDK_HOME). Output staged to lib/arm64-v8a/ (gitignored).
EOF
echo "third_party/litert_lm/lib/arm64-v8a/" >> .gitignore
git add third_party/litert_lm/README.android.md .gitignore
git commit -m "build: document LiteRT-LM arm64 archive build (android-arm64 preset)"
```

### Task 2: Bazel `--config=android_arm64` builds `libagentflow_jni.so` for arm64

**Files:**
- Modify: `zen/.bazelrc` (add `android_arm64` config)
- Modify: `zen/MODULE.bazel` (register Android NDK toolchain)
- Modify: `zen/third_party/litert_lm/BUILD.bazel` (select arm64 archives under the Android platform)
- Reference: `zen/jni/BUILD.bazel` (`//jni:libagentflow_jni.so` — unchanged)

**Interfaces:**
- Consumes: arm64 archives from Task 1 at `third_party/litert_lm/lib/arm64-v8a/`.
- Produces: `bazel-bin/jni/libagentflow_jni.so` as an arm64 ELF. Task 3 copies this into the AAR's `jniLibs/arm64-v8a/`.

- [ ] **Step 1: Register the NDK toolchain in MODULE.bazel**

Append to `zen/MODULE.bazel`:
```python
# ── Android NDK toolchain (arm64-v8a on-device builds) ─────────────
bazel_dep(name = "rules_android_ndk", version = "0.1.3")
android_ndk_repository_extension = use_extension(
    "@rules_android_ndk//:extension.bzl", "android_ndk_repository_extension",
)
use_repo(android_ndk_repository_extension, "androidndk")
register_toolchains("@androidndk//:all")
```
(`ANDROID_NDK_HOME` from Task 1 Step 1 must be exported in the build shell.)

- [ ] **Step 2: Add the `android_arm64` config to .bazelrc**

Append to `zen/.bazelrc`:
```
# Android arm64-v8a cross-compile (on-device inference).
build:android_arm64 --platforms=//bazel/platforms:android_arm64
build:android_arm64 --android_platforms=//bazel/platforms:android_arm64
build:android_arm64 --copt=-DANDROID --cxxopt=-std=c++20 --host_cxxopt=-std=c++20
```

- [ ] **Step 3: Declare the arm64 platform**

Create `zen/bazel/platforms/BUILD.bazel`:
```python
package(default_visibility = ["//visibility:public"])
platform(
    name = "android_arm64",
    constraint_values = [
        "@platforms//os:android",
        "@platforms//cpu:arm64",
    ],
)
```

- [ ] **Step 4: Select arm64 archives under the Android platform**

Add a config_setting and switch the `cc_import` paths in `zen/third_party/litert_lm/BUILD.bazel` so the Android build uses `lib/arm64-v8a/`:
```python
config_setting(
    name = "android_build",
    constraint_values = ["@platforms//os:android"],
)
# staging:
cc_import(
    name = "c_engine_staging",
    static_library = select({
        ":android_build": "lib/arm64-v8a/libce_staging.a",
        "//conditions:default": "lib/libce_staging.a",
    }),
    alwayslink = True,
)
# external:
cc_import(
    name = "c_engine_external",
    static_library = select({
        ":android_build": "lib/arm64-v8a/libce_external.a",
        "//conditions:default": "lib/libce_external.a",
    }),
)
# kissfft:
cc_import(
    name = "kissfft",
    shared_library = select({
        ":android_build": "lib/arm64-v8a/libkissfft-float.so.131",
        "//conditions:default": "lib/libkissfft-float.so.131",
    }),
)
```

- [ ] **Step 5: Build the JNI lib for arm64**

```bash
cd /home/wayne/tools/zen
export ANDROID_NDK_HOME="$HOME/android-ndk/android-ndk-r26d"
bazel build //jni:libagentflow_jni.so --config=android_arm64 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`. Link errors here are almost always abseil/protobuf one-definition issues — the host link already handles these via `-Wl,--allow-multiple-definition` in `third_party/litert_lm/BUILD.bazel:c_engine.linkopts`; keep that flag.

- [ ] **Step 6: Verify the output is arm64**

```bash
file /home/wayne/tools/zen/bazel-bin/jni/libagentflow_jni.so
```
Expected: `ELF 64-bit LSB shared object, ARM aarch64, ... dynamically linked`.

- [ ] **Step 7: Confirm the host build still works (no regression)**

```bash
cd /home/wayne/tools/zen
bazel build //jni:libagentflow_jni.so 2>&1 | tail -5
file bazel-bin/jni/libagentflow_jni.so   # x86-64
```
Expected: host build still `x86-64` (the `select` default path).

- [ ] **Step 8: Commit**

```bash
cd /home/wayne/tools/zen
git add .bazelrc MODULE.bazel bazel/platforms/BUILD.bazel third_party/litert_lm/BUILD.bazel
git commit -m "build: bazel android_arm64 config for libagentflow_jni.so"
```

---

## Phase 2 — `zen/android-inference` AAR (zen repo)

### Task 3: Create the Android library module that ports the DSL and packages the arm64 `.so`

**Files:**
- Create: `zen/android-inference/build.gradle.kts`
- Create: `zen/android-inference/src/main/AndroidManifest.xml`
- Create: `zen/android-inference/src/main/kotlin/agentflow/jni/NativeBridge.kt` (copy of `zen/kotlin/.../jni/NativeBridge.kt`)
- Create: `zen/android-inference/src/main/kotlin/agentflow/dsl/Workflow.kt` (copy)
- Create: `zen/android-inference/src/main/kotlin/agentflow/dsl/JsonWorkflow.kt` (copy)
- Create: `zen/android-inference/src/main/jniLibs/arm64-v8a/libagentflow_jni.so` (from Task 2; gitignored)
- Create: `zen/android-inference/src/main/jniLibs/arm64-v8a/libkissfft-float.so.131` (from Task 1; gitignored)
- Create: `zen/android-inference/settings.gradle.kts`

**Interfaces:**
- Consumes: arm64 `.so` from Task 2; arm64 `libkissfft-float.so.131` from Task 1.
- Produces: package `agentflow.dsl` with `fun loadWorkflow(modelPath: String, json: String): JsonWorkflow` and `JsonWorkflow.streamTokens(query: String): kotlinx.coroutines.flow.Flow<String>`. Emits `agentflow-android-debug.aar`.

- [ ] **Step 1: Create the module Gradle config**

`zen/android-inference/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}
android {
    namespace = "agentflow.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

- [ ] **Step 2: Add the manifest and copy the DSL sources verbatim**

`zen/android-inference/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```
Copy the three files unchanged (the JVM DSL has no JVM-only deps — only `System.loadLibrary` + coroutines, both valid on Android):
```bash
cd /home/wayne/tools/zen
mkdir -p android-inference/src/main/kotlin/agentflow/jni android-inference/src/main/kotlin/agentflow/dsl
cp kotlin/src/main/kotlin/agentflow/jni/NativeBridge.kt  android-inference/src/main/kotlin/agentflow/jni/
cp kotlin/src/main/kotlin/agentflow/dsl/Workflow.kt      android-inference/src/main/kotlin/agentflow/dsl/
cp kotlin/src/main/kotlin/agentflow/dsl/JsonWorkflow.kt  android-inference/src/main/kotlin/agentflow/dsl/
```

- [ ] **Step 3: Stage the native libraries**

```bash
cd /home/wayne/tools/zen
mkdir -p android-inference/src/main/jniLibs/arm64-v8a
cp bazel-bin/jni/libagentflow_jni.so                          android-inference/src/main/jniLibs/arm64-v8a/
cp third_party/litert_lm/lib/arm64-v8a/libkissfft-float.so.131 android-inference/src/main/jniLibs/arm64-v8a/
echo "android-inference/src/main/jniLibs/" >> .gitignore
```

- [ ] **Step 4: Build the AAR**

`zen/android-inference/settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "android-inference"
```
Provide an Android SDK pointer and build:
```bash
cd /home/wayne/tools/zen/android-inference
printf 'sdk.dir=/opt/android-sdk\n' > local.properties
gradle :assembleDebug --no-validate-url 2>&1 | tail -8
ls build/outputs/aar/*.aar
```
Expected: `android-inference-debug.aar` (or `-debug.aar`) exists.

- [ ] **Step 5: Verify the AAR carries the arm64 `.so`**

```bash
cd /home/wayne/tools/zen/android-inference
unzip -l build/outputs/aar/*.aar | grep -E "jni/arm64-v8a|classes.jar"
```
Expected: lists `jni/arm64-v8a/libagentflow_jni.so`, `jni/arm64-v8a/libkissfft-float.so.131`, and `classes.jar`.

- [ ] **Step 6: Commit**

```bash
cd /home/wayne/tools/zen
git add android-inference/build.gradle.kts android-inference/settings.gradle.kts \
        android-inference/src/main/AndroidManifest.xml \
        android-inference/src/main/kotlin .gitignore
git commit -m "feat(android-inference): AAR module wrapping agentflow DSL + arm64 jni"
```

---

## Phase 3 — `zen_mobile` inference seam, ViewModel, and UI wiring

> Phase 3 is mostly device-free (TDD against a fake). Only Task 6 needs the
> phone + model. All Phase 3 work is in **`/home/wayne/tools/zen_mobile`**.

### Task 4: `InferenceClient` seam + `ChatViewModel` (TDD with a fake)

**Files:**
- Create: `app/src/main/java/com/zenwayne/zenagent/data/inference/InferenceClient.kt`
- Create: `app/src/main/java/com/zenwayne/zenagent/data/inference/InferenceError.kt`
- Create: `app/src/main/java/com/zenwayne/zenagent/ui/chat/ChatViewModel.kt`
- Test: `app/src/test/java/com/zenwayne/zenagent/ui/chat/ChatViewModelTest.kt`
- Modify: `app/build.gradle.kts` (add coroutines-test + junit test deps)

**Interfaces:**
- Produces: `interface InferenceClient { fun streamTokens(prompt: String): Flow<String> }`;
  `class ChatViewModel(client: InferenceClient, agent: Agent)` exposing
  `val uiState: StateFlow<ChatUiState>`, `fun send(text: String)`, `fun stop()`.
  `data class ChatUiState(val messages: List<Message>, val runState: RunState, val error: String?)`.

- [ ] **Step 1: Add test dependencies**

In `app/build.gradle.kts` `dependencies {}` add:
```kotlin
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
```

- [ ] **Step 2: Write the failing ViewModel test**

`app/src/test/java/com/zenwayne/zenagent/ui/chat/ChatViewModelTest.kt`:
```kotlin
package com.zenwayne.zenagent.ui.chat

import com.zenwayne.zenagent.data.Role
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.data.SampleData
import com.zenwayne.zenagent.data.inference.InferenceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val agent = SampleData.conversations.first().agent

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun clientEmitting(vararg deltas: String) = object : InferenceClient {
        override fun streamTokens(prompt: String): Flow<String> = flow {
            deltas.forEach { emit(it) }
        }
    }

    @Test fun `send streams deltas into the agent bubble and completes`() = runTest(dispatcher) {
        val vm = ChatViewModel(clientEmitting("Hel", "lo", "!"), agent)
        vm.send("hi")
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(RunState.Succeeded, s.runState)
        assertEquals("hi", s.messages.first { it.role == Role.User }.text)
        assertEquals("Hello!", s.messages.last { it.role == Role.Agent }.text)
    }

    @Test fun `error maps to Failed state`() = runTest(dispatcher) {
        val failing = object : InferenceClient {
            override fun streamTokens(prompt: String): Flow<String> = flow { throw RuntimeException("boom") }
        }
        val vm = ChatViewModel(failing, agent)
        vm.send("hi")
        advanceUntilIdle()
        assertEquals(RunState.Failed, vm.uiState.value.runState)
        assertTrue(vm.uiState.value.error!!.contains("boom"))
    }

    @Test fun `stop cancels and sets Stopped`() = runTest(dispatcher) {
        val vm = ChatViewModel(clientEmitting("a", "b", "c"), agent)
        vm.send("hi")
        vm.stop()
        advanceUntilIdle()
        assertEquals(RunState.Stopped, vm.uiState.value.runState)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ChatViewModelTest*'`
Expected: FAIL — `ChatViewModel` / `InferenceClient` unresolved.

- [ ] **Step 4: Create the seam interface and error type**

`app/src/main/java/com/zenwayne/zenagent/data/inference/InferenceClient.kt`:
```kotlin
package com.zenwayne.zenagent.data.inference

import kotlinx.coroutines.flow.Flow

/** Streams generated text deltas for [prompt]; completes when the run ends. */
interface InferenceClient {
    fun streamTokens(prompt: String): Flow<String>
}
```
`app/src/main/java/com/zenwayne/zenagent/data/inference/InferenceError.kt`:
```kotlin
package com.zenwayne.zenagent.data.inference

sealed class InferenceError(message: String) : Exception(message) {
    class ModelNotFound(path: String) : InferenceError("Model not found at $path")
    class EngineInit(cause: String) : InferenceError("Engine init failed: $cause")
    class InferenceFailed(cause: String) : InferenceError("Inference failed: $cause")
    class NativeUnavailable(cause: String) : InferenceError("Native engine unavailable: $cause")
}
```

- [ ] **Step 5: Implement `ChatViewModel`**

`app/src/main/java/com/zenwayne/zenagent/ui/chat/ChatViewModel.kt`:
```kotlin
package com.zenwayne.zenagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenwayne.zenagent.data.Agent
import com.zenwayne.zenagent.data.Message
import com.zenwayne.zenagent.data.Role
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.data.inference.InferenceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val runState: RunState = RunState.Idle,
    val error: String? = null,
)

class ChatViewModel(
    private val client: InferenceClient,
    private val agent: Agent,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var counter = 0

    fun send(text: String) {
        if (text.isBlank()) return
        val userMsg = Message(id = "u${counter++}", role = Role.User, text = text)
        val agentId = "a${counter++}"
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg +
                Message(id = agentId, role = Role.Agent, text = ""),
            runState = RunState.Running,
            error = null,
        )
        runJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                client.streamTokens(text)
                    .onCompletion { cause ->
                        if (cause == null) setRunState(RunState.Succeeded)
                    }
                    .collect { delta ->
                        sb.append(delta)
                        updateAgentText(agentId, sb.toString())
                    }
            } catch (c: CancellationException) {
                setRunState(RunState.Stopped)
                throw c
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    runState = RunState.Failed, error = t.message ?: "Inference failed",
                )
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        setRunState(RunState.Stopped)
    }

    private fun updateAgentText(id: String, text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == id) it.copy(text = text) else it
            },
        )
    }

    private fun setRunState(state: RunState) {
        _uiState.value = _uiState.value.copy(runState = state)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ChatViewModelTest*'`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
cd /home/wayne/tools/zen_mobile
git add app/build.gradle.kts app/src/main/java/com/zenwayne/zenagent/data/inference \
        app/src/main/java/com/zenwayne/zenagent/ui/chat/ChatViewModel.kt \
        app/src/test/java/com/zenwayne/zenagent/ui/chat/ChatViewModelTest.kt
git commit -m "feat(chat): InferenceClient seam + ChatViewModel (TDD with fake)"
```

### Task 5: `ModelLocator` + `AgentflowInferenceClient` (real client over the AAR)

**Files:**
- Create: `app/src/main/java/com/zenwayne/zenagent/data/inference/ModelLocator.kt`
- Create: `app/src/main/java/com/zenwayne/zenagent/data/inference/AgentflowInferenceClient.kt`
- Test: `app/src/test/java/com/zenwayne/zenagent/data/inference/ModelLocatorTest.kt`

**Interfaces:**
- Consumes: `agentflow.dsl.loadWorkflow` + `JsonWorkflow.streamTokens` from the Task 3 AAR (wired in Task 6); `InferenceClient` from Task 4.
- Produces: `class ModelLocator(filesDir: File)` with `fun modelPathOrNull(): String?` and `const val MODEL_FILE`; `class AgentflowInferenceClient(modelPath: String) : InferenceClient`.

- [ ] **Step 1: Write the failing ModelLocator test**

`app/src/test/java/com/zenwayne/zenagent/data/inference/ModelLocatorTest.kt`:
```kotlin
package com.zenwayne.zenagent.data.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelLocatorTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `returns null when model absent`() {
        assertNull(ModelLocator(tmp.root).modelPathOrNull())
    }

    @Test fun `returns path when model present`() {
        val models = tmp.newFolder("models")
        val f = java.io.File(models, ModelLocator.MODEL_FILE).apply { writeText("x") }
        assertEquals(f.absolutePath, ModelLocator(tmp.root).modelPathOrNull())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ModelLocatorTest*'`
Expected: FAIL — `ModelLocator` unresolved.

- [ ] **Step 3: Implement `ModelLocator`**

`app/src/main/java/com/zenwayne/zenagent/data/inference/ModelLocator.kt`:
```kotlin
package com.zenwayne.zenagent.data.inference

import java.io.File

/**
 * Resolves the on-device model path under <filesDir>/models. [filesDir] is the
 * app-specific external dir (Context.getExternalFilesDir(null)). Push the model
 * with:
 *   adb push gemma-4-E2B-it.litertlm \
 *     /sdcard/Android/data/com.zenwayne.zenagent/files/models/
 */
class ModelLocator(private val filesDir: File) {
    fun modelPathOrNull(): String? {
        val f = File(File(filesDir, "models"), MODEL_FILE)
        return if (f.exists()) f.absolutePath else null
    }
    companion object { const val MODEL_FILE = "gemma-4-E2B-it.litertlm" }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ModelLocatorTest*'`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement the real client (compiles against the AAR API)**

`app/src/main/java/com/zenwayne/zenagent/data/inference/AgentflowInferenceClient.kt`:
```kotlin
package com.zenwayne.zenagent.data.inference

import agentflow.dsl.JsonWorkflow
import agentflow.dsl.loadWorkflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Real [InferenceClient] backed by the on-device agentflow engine (AAR). */
class AgentflowInferenceClient(modelPath: String) : InferenceClient {
    private val workflow: JsonWorkflow = loadWorkflow(modelPath, SINGLE_AGENT_JSON)

    override fun streamTokens(prompt: String): Flow<String> =
        workflow.streamTokens(prompt).catch { t -> throw map(t) }

    private fun map(t: Throwable): Throwable = when (t) {
        is UnsatisfiedLinkError -> InferenceError.NativeUnavailable(t.message ?: "missing .so")
        else -> InferenceError.InferenceFailed(t.message ?: t.toString())
    }

    private companion object {
        // Schema verified in zen/kotlin/.../WorkflowJsonTest.kt.
        const val SINGLE_AGENT_JSON = """
        {"schema_version":1,"name":"chat","version":"v1",
         "state":{"kind":"dynamic_json","fields":{}},
         "agents":{"main":{"system_prompt":"You are ZenAgent, a concise on-device assistant.",
                  "model":{"max_output_tokens":512},"tools":[]}},
         "main":"main"}
        """
    }
}
```

- [ ] **Step 6: Commit**

```bash
cd /home/wayne/tools/zen_mobile
git add app/src/main/java/com/zenwayne/zenagent/data/inference/ModelLocator.kt \
        app/src/main/java/com/zenwayne/zenagent/data/inference/AgentflowInferenceClient.kt \
        app/src/test/java/com/zenwayne/zenagent/data/inference/ModelLocatorTest.kt
git commit -m "feat(inference): ModelLocator + AgentflowInferenceClient over the AAR"
```

> Note: `AgentflowInferenceClient.kt` references `agentflow.dsl.*`, which only
> resolves once the AAR dependency is added in Task 6. Compile/test it as part of
> Task 6 Step 4; Task 5's gate is the `ModelLocator` unit tests.

### Task 6: Consume the AAR, wire the Chat screen, verify on-device

**Files:**
- Modify: `zen_mobile/settings.gradle.kts` (include the AAR)
- Modify: `app/build.gradle.kts` (depend on the AAR; coroutines already present)
- Modify: `app/src/main/java/com/zenwayne/zenagent/ui/AppRoot.kt` (build VM, pass to Chat)
- Modify: `app/src/main/java/com/zenwayne/zenagent/ui/chat/ChatScreen.kt` (drive from `ChatUiState`, wire input + Stop)

**Interfaces:**
- Consumes: `agentflow-android` AAR (Task 3); `ChatViewModel`, `ChatUiState` (Task 4); `ModelLocator`, `AgentflowInferenceClient` (Task 5).

- [ ] **Step 1: Make the AAR resolvable**

Copy the AAR into the app and add a flatDir repo. In `zen_mobile/settings.gradle.kts`, under `dependencyResolutionManagement { repositories { ... } }` add:
```kotlin
        flatDir { dirs("libs") }
```
Then:
```bash
cd /home/wayne/tools/zen_mobile
mkdir -p app/libs
cp /home/wayne/tools/zen/android-inference/build/outputs/aar/*.aar app/libs/agentflow-android.aar
```

- [ ] **Step 2: Depend on the AAR**

In `app/build.gradle.kts` `dependencies {}` add:
```kotlin
    implementation(name = "agentflow-android", ext = "aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
```

- [ ] **Step 3: Drive `ChatScreen` from `ChatUiState`**

Change the `ChatScreen` signature to accept UI state + callbacks (replacing the static-`Conversation` rendering of message text/run-state; keep all existing composables — `MessageItem`, `InputBar`, header):
```kotlin
@Composable
fun ChatScreen(
    agentName: String,
    state: ChatUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // header uses agentName + state.runState (runStateLabel/runStateColor as today)
    // LazyColumn renders state.messages via the existing MessageItem(...)
    // InputBar: editable TextField -> onSend(text); Stop button -> onStop()
}
```
Add an editable field: replace the placeholder `Text` in `InputBar` with a `androidx.compose.material3.BasicTextField`/`TextField` whose `onValueChange` updates local `remember { mutableStateOf("") }`, and on send calls `onSend(value)` then clears it. The Stop button (already red when running) calls `onStop`.

- [ ] **Step 4: Build the VM and model-missing state in `AppRoot`**

In `AppRoot.kt`, resolve the model and construct the client + VM; show a model-missing message when absent:
```kotlin
val context = androidx.compose.ui.platform.LocalContext.current
val modelPath = remember { ModelLocator(context.getExternalFilesDir(null)!!).modelPathOrNull() }
// when modelPath == null: render a centered "Model not found — adb push the .litertlm" screen
// else: val vm = viewModel { ChatViewModel(AgentflowInferenceClient(modelPath), selected.agent) }
//       val state by vm.uiState.collectAsStateWithLifecycle()
//       ChatScreen(selected.agent.name, state, onBack = {...}, onSend = vm::send, onStop = vm::stop)
```
(Add `androidx.lifecycle:lifecycle-runtime-compose` for `collectAsStateWithLifecycle`, or use `collectAsState()`.)

- [ ] **Step 5: Build the APK (compiles Task 5's client against the AAR)**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`; resolves `agentflow.dsl.*` from the AAR.

- [ ] **Step 6: Run the full unit suite (no regressions)**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -8`
Expected: PASS (ChatViewModel + ModelLocator tests).

- [ ] **Step 7: Push the model and install on-device**

```bash
adb -s HQ657J0757 shell 'mkdir -p /sdcard/Android/data/com.zenwayne.zenagent/files/models'
adb -s HQ657J0757 push /home/wayne/tools/zen/models/gemma-4-E2B-it.litertlm \
    /sdcard/Android/data/com.zenwayne.zenagent/files/models/
./gradlew :app:installDebug -PandroidSerial=HQ657J0757 2>&1 | tail -5 \
  || adb -s HQ657J0757 install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: model push completes (~2.6 GB), install `Success`.

- [ ] **Step 8: Verify streaming on the device**

```bash
adb -s HQ657J0757 shell am start -n com.zenwayne.zenagent/.MainActivity
adb -s HQ657J0757 logcat -c
# In the app: type "Say hello." and send. Then capture native logs:
adb -s HQ657J0757 logcat -d | grep -iE "agentflow|litert|zenagent" | tail -30
```
Expected: the agent bubble fills in token-by-token; tapping Stop halts generation promptly; no crash. If the model is absent the app shows the model-not-found screen instead.

- [ ] **Step 9: Commit**

```bash
cd /home/wayne/tools/zen_mobile
git add settings.gradle.kts app/build.gradle.kts app/libs/agentflow-android.aar \
        app/src/main/java/com/zenwayne/zenagent/ui/AppRoot.kt \
        app/src/main/java/com/zenwayne/zenagent/ui/chat/ChatScreen.kt
git commit -m "feat(chat): wire on-device agentflow streaming into Chat screen"
```

---

## Self-Review

**Spec coverage:**
- Bazel arm64 config + LiteRT-LM arm64 build → Tasks 1–2.
- `android-inference` AAR (ported DSL + arm64 `.so`) → Task 3.
- `InferenceClient` seam + `ChatViewModel` → Task 4.
- Single-agent streaming + Stop into existing Chat screen → Tasks 4 + 6.
- adb model delivery + model-missing / failed states → Tasks 5 (`ModelLocator`) + 6 (Step 4 UI) + `InferenceError`/`RunState.Failed` (Tasks 4–5).
- Testing tiers (JVM fake, host smoke, on-device) → Task 4/5 unit tests, Task 2 Step 7 host check, Task 6 Step 8 device check.
- Non-goals (tools/approval/sub-agents, x86_64, GPU, download, persistence, host bridge) → not implemented; seam left for host bridge. ✔

**Placeholder scan:** Native tasks use real commands + verification gates; the only `<path>` tokens are in Task 1 Step 4, explicitly resolved by Step 2's discovery (the host archive recipe is machine-built and not in-repo, so it must be discovered, not invented). App-layer tasks contain full code. ✔

**Type consistency:** `InferenceClient.streamTokens(prompt): Flow<String>`, `ChatViewModel(client, agent)` / `uiState: StateFlow<ChatUiState>` / `send` / `stop`, `ChatUiState(messages, runState, error)`, `ModelLocator(filesDir).modelPathOrNull()` + `MODEL_FILE`, `AgentflowInferenceClient(modelPath)` are used identically across Tasks 4–6. Reuses existing `Message`/`Role`/`RunState`/`Agent` from `data/Models.kt`. ✔
