#include <chrono>
#include <cstdio>
#include <iostream>
#include <string>
#include <vector>

#include "../loader_gui/auto_inject_readiness.h"
#include "../loader_gui/loader_settings.h"

static int tests_passed = 0;
static int tests_failed = 0;

#define TEST(name) std::cout << "  TEST: " << name << " ... "
#define PASS() do { std::cout << "PASS\n"; tests_passed++; } while (0)
#define FAIL(msg) do { std::cout << "FAIL: " << msg << "\n"; tests_failed++; } while (0)
#define ASSERT(cond, msg) do { if (!(cond)) { FAIL(msg); return; } } while (0)

using namespace std::chrono_literals;

namespace {

ProcessObservation readyObservation() {
    ProcessObservation observation;
    observation.identity.pid = 1234;
    observation.identity.creationTime100ns = 133300000000000000ULL;
    observation.exeName = L"java.exe";
    observation.commandLine = L"javaw.exe --username foo --uuid 1234 "
        L"--accesstoken abc --usertype mojang --versiontype release "
        L"--gamedir C:\\mc --assetsdir C:\\mc\\assets --assetindex 1.19 "
        L"--version 1.19 com.moonsworth.lunar.genesis.Genesis";
    observation.commandLineReadable = true;
    observation.title = L"Lunar Client 1.19";
    observation.windowClass = L"LWJGL";
    observation.visible = true;
    observation.responsive = true;
    observation.processAge = 60s;
    observation.titleStableFor = 10s;
    return observation;
}

AutoInjectPolicy readyPolicy() {
    AutoInjectPolicy policy;
    policy.mode = AutoInjectReadiness::Conservative;
    policy.settleDelay = 5000ms;
    policy.minimumAge = 15000ms;
    return policy;
}

}

static void test_ready_process() {
    TEST("ready java+Lunar process evaluates Ready");
    const AutoInjectEvaluation result = evaluateAutoInjectReadiness(readyObservation(), readyPolicy());
    ASSERT(result.ready, "should be ready");
    ASSERT(result.reason == AutoInjectReadinessReason::Ready, "reason should be Ready");
    PASS();
}

static void test_rejects_non_java() {
    TEST("non-java executable rejected");
    ProcessObservation observation = readyObservation();
    observation.exeName = L"chrome.exe";
    const AutoInjectEvaluation result = evaluateAutoInjectReadiness(observation, readyPolicy());
    ASSERT(!result.ready, "should not be ready");
    ASSERT(result.reason == AutoInjectReadinessReason::NotJavaExecutable, "reason should be NotJavaExecutable");
    PASS();
}

static void test_rejects_gradle_ide() {
    TEST("gradle/IDE/tooling command lines rejected");
    const wchar_t* badCommandLines[] = {
        L"java.exe org.gradle.launcher.GradleMain",
        L"java.exe -javaagent:C:\\idea_rt.jar com.intellij.rt",
        L"java.exe C:\\gradle-8.8\\lib\\gradle-launcher-8.8.jar",
        L"java.exe net.minecraft.server.MinecraftServer nogui",
    };
    for (const wchar_t* commandLine : badCommandLines) {
        ProcessObservation observation = readyObservation();
        observation.commandLine = commandLine;
        const AutoInjectEvaluation result = evaluateAutoInjectReadiness(observation, readyPolicy());
        if (result.ready) { FAIL("accepted rejected command line"); return; }
        ASSERT(result.reason == AutoInjectReadinessReason::RejectedCommandLine, "reason should be RejectedCommandLine");
    }
    PASS();
}

static void test_rejects_hidden_window() {
    TEST("invisible window rejected");
    ProcessObservation observation = readyObservation();
    observation.visible = false;
    const AutoInjectEvaluation result = evaluateAutoInjectReadiness(observation, readyPolicy());
    ASSERT(!result.ready, "should not be ready");
    ASSERT(result.reason == AutoInjectReadinessReason::WindowNotVisible, "reason should be WindowNotVisible");
    PASS();
}

static void test_rejects_young_process() {
    TEST("process younger than minimumAge rejected");
    ProcessObservation observation = readyObservation();
    observation.processAge = 5s;
    const AutoInjectEvaluation result = evaluateAutoInjectReadiness(observation, readyPolicy());
    ASSERT(!result.ready, "should not be ready");
    ASSERT(result.reason == AutoInjectReadinessReason::ProcessTooYoung, "reason should be ProcessTooYoung");
    PASS();
}

static void test_select_candidate_picks_newest() {
    TEST("selectAutoInjectCandidate picks newest ready process");
    ProcessObservation older = readyObservation();
    older.identity.pid = 100;
    older.identity.creationTime100ns = 1000;
    ProcessObservation newer = readyObservation();
    newer.identity.pid = 200;
    newer.identity.creationTime100ns = 2000;
    ProcessObservation notReady = readyObservation();
    notReady.identity.pid = 300;
    notReady.identity.creationTime100ns = 3000;
    notReady.visible = false;
    const auto selected = selectAutoInjectCandidate({older, notReady, newer}, readyPolicy());
    ASSERT(selected.has_value(), "should select a candidate");
    ASSERT(selected->pid == 200, "should select newest ready (pid 200)");
    PASS();
}

static void test_select_candidate_empty() {
    TEST("selectAutoInjectCandidate on empty list returns nullopt");
    ASSERT(!selectAutoInjectCandidate({}, readyPolicy()).has_value(), "empty list should yield nullopt");
    PASS();
}

int main() {
    std::cout << "=== AutoInjectReadinessTest ===\n";
    test_ready_process();
    test_rejects_non_java();
    test_rejects_gradle_ide();
    test_rejects_hidden_window();
    test_rejects_young_process();
    test_select_candidate_picks_newest();
    test_select_candidate_empty();
    std::cout << "\n---\n";
    std::cout << "Tests:  " << tests_passed << " passed, " << tests_failed << " failed\n";
    return tests_failed > 0 ? 1 : 0;
}