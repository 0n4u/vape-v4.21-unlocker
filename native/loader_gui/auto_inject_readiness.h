#pragma once

#include "loader_settings.h"

#include <chrono>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

struct ProcessIdentity {
    std::uint32_t pid = 0;
    std::uint64_t creationTime100ns = 0;
};

struct AutoInjectPolicy {
    AutoInjectReadiness mode = AutoInjectReadiness::Conservative;
    std::chrono::milliseconds settleDelay{5000};
    std::chrono::milliseconds minimumAge{15000};
};

struct ProcessObservation {
    ProcessIdentity identity;
    std::wstring exeName;
    std::wstring commandLine;
    bool commandLineReadable = false;
    std::wstring title;
    std::wstring windowClass;
    bool visible = false;
    bool responsive = false;
    std::chrono::milliseconds processAge{0};
    std::chrono::milliseconds titleStableFor{0};
    bool alreadyInjected = false;
    bool alreadyAttempted = false;
};

enum class AutoInjectReadinessReason {
    Ready,
    InvalidObservation,
    InvalidPolicy,
    InvalidIdentity,
    AlreadyInjected,
    AlreadyAttempted,
    NotJavaExecutable,
    CommandLineUnreadable,
    RejectedCommandLine,
    GameCommandLineNotFound,
    WindowNotVisible,
    WindowNotResponsive,
    WindowTitleBlank,
    GameWindowNotFound,
    ProcessTooYoung,
    WindowNotStable,
};

struct AutoInjectEvaluation {
    bool ready = false;
    AutoInjectReadinessReason reason = AutoInjectReadinessReason::InvalidObservation;
};

AutoInjectEvaluation evaluateAutoInjectReadiness(
    const ProcessObservation& observation,
    const AutoInjectPolicy& policy);



std::optional<ProcessIdentity> selectAutoInjectCandidate(
    const std::vector<ProcessObservation>& observations,
    const AutoInjectPolicy& policy);
