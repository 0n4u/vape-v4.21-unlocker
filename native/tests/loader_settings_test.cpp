#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

#include <windows.h>

#include "../loader_gui/loader_settings.h"

static int tests_passed = 0;
static int tests_failed = 0;

#define TEST(name) std::cout << "  TEST: " << name << " ... "
#define PASS() do { std::cout << "PASS\n"; tests_passed++; } while (0)
#define FAIL(msg) do { std::cout << "FAIL: " << msg << "\n"; tests_failed++; } while (0)
#define ASSERT(cond, msg) do { if (!(cond)) { FAIL(msg); return; } } while (0)

namespace {

std::wstring tempSettingsPath() {
    wchar_t tempDir[MAX_PATH + 1] = {};
    DWORD length = GetTempPathW(MAX_PATH, tempDir);
    if (length == 0 || length >= MAX_PATH) {
        return L"";
    }
    return std::wstring(tempDir) + L"vape_settings_test_" + std::to_wstring(GetCurrentProcessId()) + L".settings";
}

void writeFile(const std::wstring& path, const std::string& contents) {
    std::ofstream file(std::filesystem::path(path), std::ios::binary);
    file << contents;
}

}

static void test_defaults() {
    TEST("defaults — all fields have safe defaults");
    LoaderSettings settings;
    ASSERT(settings.autoInjectEnabled == false, "autoInject should default false");
    ASSERT(settings.readiness == AutoInjectReadiness::Conservative, "readiness should default Conservative");
    ASSERT(settings.settleDelayMs == 5000, "settle delay should default 5000");
    ASSERT(settings.minimumProcessAgeMs == 15000, "min process age should default 15000");
    ASSERT(settings.closeAfterSuccess == true, "closeAfterSuccess should default true");
    ASSERT(settings.unlockCosmetics == true, "unlockCosmetics should default true");
    ASSERT(settings.unlockBadges == true, "unlockBadges should default true");
    ASSERT(settings.unlockEmotes == true, "unlockEmotes should default true");
    ASSERT(settings.unlockSprays == true, "unlockSprays should default true");
    ASSERT(settings.unlockJams == true, "unlockJams should default true");
    ASSERT(settings.lunarPlusAppearance == true, "lunarPlusAppearance should default true");
    ASSERT(settings.debugLogging == false, "debugLogging should default false");
    ASSERT(settings.language == L"English", "language should default English");
    PASS();
}

static void test_round_trip() {
    TEST("save/load round-trip — all fields preserved");
    const std::wstring path = tempSettingsPath();
    LoaderSettings original;
    original.autoInjectEnabled = true;
    original.readiness = AutoInjectReadiness::WindowStable;
    original.settleDelayMs = 8000;
    original.minimumProcessAgeMs = 30000;
    original.closeAfterSuccess = false;
    original.unlockCosmetics = false;
    original.unlockBadges = false;
    original.unlockEmotes = true;
    original.unlockSprays = true;
    original.unlockJams = false;
    original.lunarPlusAppearance = false;
    original.debugLogging = true;
    original.language = L"Chinese";
    ASSERT(original.saveTo(path), "saveTo should succeed");
    LoaderSettings loaded = LoaderSettings::loadFrom(path);
    ASSERT(loaded.autoInjectEnabled == true, "autoInject not preserved");
    ASSERT(loaded.readiness == AutoInjectReadiness::WindowStable, "readiness not preserved");
    ASSERT(loaded.settleDelayMs == 8000, "settle delay not preserved");
    ASSERT(loaded.minimumProcessAgeMs == 30000, "min process age not preserved");
    ASSERT(loaded.closeAfterSuccess == false, "closeAfterSuccess not preserved");
    ASSERT(loaded.unlockCosmetics == false, "unlockCosmetics not preserved");
    ASSERT(loaded.unlockBadges == false, "unlockBadges not preserved");
    ASSERT(loaded.unlockEmotes == true, "unlockEmotes not preserved");
    ASSERT(loaded.unlockSprays == true, "unlockSprays not preserved");
    ASSERT(loaded.unlockJams == false, "unlockJams not preserved");
    ASSERT(loaded.lunarPlusAppearance == false, "lunarPlusAppearance not preserved");
    ASSERT(loaded.debugLogging == true, "debugLogging not preserved");
    ASSERT(loaded.language == L"Chinese", "language not preserved");
    std::filesystem::remove(std::filesystem::path(path));
    PASS();
}

static void test_clamping_on_save() {
    TEST("out-of-range values clamped on save");
    const std::wstring path = tempSettingsPath();
    LoaderSettings settings;
    settings.settleDelayMs = 1;
    settings.minimumProcessAgeMs = 999999;
    ASSERT(settings.saveTo(path), "saveTo should succeed");
    LoaderSettings loaded = LoaderSettings::loadFrom(path);
    ASSERT(loaded.settleDelayMs == 2000, "settle delay should clamp to 2000");
    ASSERT(loaded.minimumProcessAgeMs == 60000, "min process age should clamp to 60000");
    std::filesystem::remove(std::filesystem::path(path));
    PASS();
}

static void test_missing_file_returns_defaults() {
    TEST("missing file — loadFrom returns defaults");
    LoaderSettings settings = LoaderSettings::loadFrom(L"C:\\nonexistent\\path\\loader.settings");
    ASSERT(settings.autoInjectEnabled == false, "should default autoInject");
    ASSERT(settings.language == L"English", "should default language");
    PASS();
}

static void test_malformed_lines_ignored() {
    TEST("malformed lines are ignored, valid lines parsed");
    const std::wstring path = tempSettingsPath();
    writeFile(path,
        "this line has no equals\n"
        "=starts with equals\n"
        "auto_inject=true\n"
        "settle_delay_ms=abc\n"
        "unknown_key=whatever\n"
        "readiness=window-stable\n");
    LoaderSettings loaded = LoaderSettings::loadFrom(path);
    ASSERT(loaded.autoInjectEnabled == true, "auto_inject should parse");
    ASSERT(loaded.readiness == AutoInjectReadiness::WindowStable, "readiness should parse");
    ASSERT(loaded.settleDelayMs == 5000, "non-numeric settle should keep default");
    std::filesystem::remove(std::filesystem::path(path));
    PASS();
}

static void test_clamping_on_load() {
    TEST("out-of-range values clamped on load");
    const std::wstring path = tempSettingsPath();
    writeFile(path, "settle_delay_ms=100\nminimum_process_age_ms=999999\n");
    LoaderSettings loaded = LoaderSettings::loadFrom(path);
    ASSERT(loaded.settleDelayMs == 2000, "settle delay should clamp to min 2000");
    ASSERT(loaded.minimumProcessAgeMs == 60000, "min age should clamp to max 60000");
    std::filesystem::remove(std::filesystem::path(path));
    PASS();
}

static void test_unicode_language_round_trip() {
    TEST("non-ASCII language (Chinese) survives save/load");
    const std::wstring path = tempSettingsPath();
    LoaderSettings settings;
    settings.language = L"\u4e2d\u6587";
    ASSERT(settings.saveTo(path), "saveTo should succeed");
    LoaderSettings loaded = LoaderSettings::loadFrom(path);
    ASSERT(loaded.language == L"\u4e2d\u6587", "Chinese language not preserved");
    std::filesystem::remove(std::filesystem::path(path));
    PASS();
}

static void test_empty_path_rejected() {
    TEST("empty path rejected by saveTo");
    LoaderSettings settings;
    ASSERT(!settings.saveTo(L""), "saveTo empty path should fail");
    PASS();
}

int main() {
    std::cout << "=== LoaderSettingsTest ===\n";
    test_defaults();
    test_round_trip();
    test_clamping_on_save();
    test_missing_file_returns_defaults();
    test_malformed_lines_ignored();
    test_clamping_on_load();
    test_unicode_language_round_trip();
    test_empty_path_rejected();
    std::cout << "\n---\n";
    std::cout << "Tests:  " << tests_passed << " passed, " << tests_failed << " failed\n";
    return tests_failed > 0 ? 1 : 0;
}