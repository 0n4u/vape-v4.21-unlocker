#pragma once

#include <string>

enum class AutoInjectReadiness {
    Conservative,
    WindowStable,
};

struct LoaderSettings {
    bool autoInjectEnabled = false;
    AutoInjectReadiness readiness = AutoInjectReadiness::Conservative;
    int settleDelayMs = 5000;
    int minimumProcessAgeMs = 15000;
    bool closeAfterSuccess = true;

    bool unlockCosmetics = true;
    bool unlockBadges = true;
    bool unlockEmotes = true;
    bool unlockSprays = true;
    bool unlockJams = true;
    bool lunarPlusAppearance = true;
    bool debugLogging = false;

    std::wstring language = L"English";

    static std::wstring defaultPath();
    static LoaderSettings load();
    static LoaderSettings loadFrom(const std::wstring& path);
    bool save() const;
    bool saveTo(const std::wstring& path) const;
};
