#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include "../loader_bootstrap.h"

 
static int tests_passed = 0;
static int tests_failed = 0;

#define TEST(name) do { printf("  TEST: %s ... ", name); } while(0)
#define PASS() do { printf("PASS\n"); tests_passed++; } while(0)
#define FAIL(msg) do { printf("FAIL: %s\n", msg); tests_failed++; } while(0)
#define ASSERT(cond, msg) do { if (!(cond)) { FAIL(msg); return; } } while(0)

static void test_standalone(void) {
    TEST("standalone mode — no bootstrap mapping");
    int ok = vape_loader_bootstrap_initialize();
    ASSERT(ok != 0, "initialize should succeed (standalone)");
    const char *token = vape_loader_access_token();
    ASSERT(token != NULL, "token should not be NULL");
    ASSERT(strcmp(token, "0") == 0, "standalone token should be \"0\"");
    int failed = vape_loader_bootstrap_failed();
    ASSERT(failed == 0, "bootstrap should not be marked failed");
    PASS();
}

static void test_initialize_twice(void) {
    TEST("initialize twice — idempotent");
    vape_loader_bootstrap_initialize();
    const char *token1 = vape_loader_access_token();
    vape_loader_bootstrap_initialize();
    const char *token2 = vape_loader_access_token();
    ASSERT(token1 == token2 || strcmp(token1, token2) == 0,
            "token should be stable across calls");
    PASS();
}

static void test_clear_restores_state(void) {
    TEST("clear zeroes token");
    vape_loader_bootstrap_initialize();
    vape_loader_bootstrap_clear();
    const char *token = vape_loader_access_token();
    ASSERT(token != NULL, "token should not be NULL after clear");
    ASSERT(strcmp(token, "0") == 0 || token[0] == '\0',
            "token should be zeroed or \"0\" after clear");
    PASS();
}

static void test_report_progress_no_crash(void) {
    TEST("report progress — no controller socket");
    vape_loader_report_progress(1);
    vape_loader_report_progress(50);
    PASS();
}

static void test_report_completed_no_crash(void) {
    TEST("report completed — no controller socket");
    vape_loader_report_completed();
    PASS();
}

static void test_report_failure_no_crash(void) {
    TEST("report failure — no controller socket");
    vape_loader_report_failure("test error message");
    vape_loader_report_failure(NULL);
    PASS();
}

int main(int argc, char **argv) {
    int run_all = (argc < 2);
    printf("=== Vape421BootstrapTest ===\n");
    if (run_all || (argc >= 2 && strcmp(argv[1], "standalone") == 0)) {
        printf("\nSuite: Vape421BootstrapStandalone\n");
        test_standalone();
    }
    if (run_all || (argc >= 2 && strcmp(argv[1], "handoff") == 0)) {
        printf("\nSuite: Vape421BootstrapHandoff\n");
        test_initialize_twice();
    }
    if (run_all || (argc >= 2 && strcmp(argv[1], "invalid") == 0)) {
        printf("\nSuite: Vape421BootstrapRejectsInvalid\n");
        test_clear_restores_state();
        test_report_progress_no_crash();
        test_report_completed_no_crash();
        test_report_failure_no_crash();
    }
    printf("\n---\n");
    printf("Tests:  %d passed, %d failed\n", tests_passed, tests_failed);
    return tests_failed > 0 ? 1 : 0;
}