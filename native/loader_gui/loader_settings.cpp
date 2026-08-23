#include "loader_settings.h"

#include <windows.h>

#include <algorithm>
#include <charconv>
#include <filesystem>
#include <fstream>
#include <limits>
#include <string>

namespace {
std::string trim(std::string value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) {
        return {};
    }
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

bool parseBoolean(const std::string& value, bool& destination) {
    if (value == "true" || value == "1") {
        destination = true;
        return true;
    }
    if (value == "false" || value == "0") {
        destination = false;
        return true;
    }
    return false;
}

bool parseClampedInteger(const std::string& value, int minimum, int maximum,
        int& destination) {
    int parsed = 0;
    const auto result = std::from_chars(value.data(),
        value.data() + value.size(), parsed);
    if (result.ec != std::errc{} || result.ptr != value.data() + value.size()) {
        return false;
    }
    destination = std::clamp(parsed, minimum, maximum);
    return true;
}

std::wstring decodeText(const std::string& value, UINT codePage, DWORD flags) {
    if (value.empty()
            || value.size() > static_cast<std::size_t>(
                std::numeric_limits<int>::max())) {
        return {};
    }
    const int sourceLength = static_cast<int>(value.size());
    const int required = MultiByteToWideChar(codePage, flags, value.data(),
        sourceLength, nullptr, 0);
    if (required <= 0) {
        return {};
    }
    std::wstring decoded(static_cast<std::size_t>(required), L'\0');
    if (MultiByteToWideChar(codePage, flags, value.data(), sourceLength,
            decoded.data(), required) != required) {
        return {};
    }
    return decoded;
}

std::wstring decodeLanguage(const std::string& value) {
    std::wstring decoded = decodeText(value, CP_UTF8, MB_ERR_INVALID_CHARS);
    if (!decoded.empty()) {
        return decoded;
    }
    return decodeText(value, CP_ACP, 0);
}

std::string encodeLanguage(const std::wstring& value) {
    if (value.empty()
            || value.size() > static_cast<std::size_t>(
                std::numeric_limits<int>::max())) {
        return {};
    }
    const int sourceLength = static_cast<int>(value.size());
    const int required = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS,
        value.data(), sourceLength, nullptr, 0, nullptr, nullptr);
    if (required <= 0) {
        return {};
    }
    std::string encoded(static_cast<std::size_t>(required), '\0');
    if (WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
            sourceLength, encoded.data(), required, nullptr, nullptr) != required) {
        return {};
    }
    return encoded;
}

const char* booleanText(bool value) {
    return value ? "true" : "false";
}

std::string serialize(const LoaderSettings& settings) {
    const std::string language = encodeLanguage(settings.language);
    if (!settings.language.empty() && language.empty()) {
        return {};
    }

    std::string output;
    output.reserve(384);
    output += "auto_inject=";
    output += booleanText(settings.autoInjectEnabled);
    output += "\nreadiness=";
    output += settings.readiness == AutoInjectReadiness::WindowStable
        ? "window-stable" : "conservative";
    output += "\nsettle_delay_ms=";
    output += std::to_string(std::clamp(settings.settleDelayMs, 2000, 15000));
    output += "\nminimum_process_age_ms=";
    output += std::to_string(std::clamp(settings.minimumProcessAgeMs, 5000, 60000));
    output += "\nclose_after_success=";
    output += booleanText(settings.closeAfterSuccess);
    output += "\nunlock_cosmetics=";
    output += booleanText(settings.unlockCosmetics);
    output += "\nunlock_badges=";
    output += booleanText(settings.unlockBadges);
    output += "\nunlock_emotes=";
    output += booleanText(settings.unlockEmotes);
    output += "\nunlock_sprays=";
    output += booleanText(settings.unlockSprays);
    output += "\nunlock_jams=";
    output += booleanText(settings.unlockJams);
    output += "\nlunar_plus_appearance=";
    output += booleanText(settings.lunarPlusAppearance);
    output += "\ndebug_logging=";
    output += booleanText(settings.debugLogging);
    output += "\nlanguage=";
    output += language;
    output += '\n';
    return output;
}

std::wstring executablePath() {
    std::wstring buffer(MAX_PATH, L'\0');
    for (;;) {
        const DWORD length = GetModuleFileNameW(nullptr, buffer.data(),
            static_cast<DWORD>(buffer.size()));
        if (length == 0) {
            return {};
        }
        if (length < buffer.size()) {
            buffer.resize(length);
            return buffer;
        }
        if (buffer.size() >= 32768) {
            return {};
        }
        buffer.resize(std::min<std::size_t>(buffer.size() * 2, 32768));
    }
}
} 

std::wstring LoaderSettings::defaultPath() {
    const std::wstring executable = executablePath();
    if (executable.empty()) {
        return {};
    }
    return (std::filesystem::path(executable).parent_path()
        / L".vapeclient" / L"loader.settings").wstring();
}

LoaderSettings LoaderSettings::load() {
    const std::wstring path = defaultPath();
    return path.empty() ? LoaderSettings{} : loadFrom(path);
}

LoaderSettings LoaderSettings::loadFrom(const std::wstring& path) {
    LoaderSettings settings;
    std::ifstream file(std::filesystem::path(path), std::ios::binary);
    if (!file) {
        return settings;
    }

    std::string line;
    while (std::getline(file, line)) {
        const auto separator = line.find('=');
        if (separator == std::string::npos) {
            continue;
        }
        const std::string key = trim(line.substr(0, separator));
        const std::string value = trim(line.substr(separator + 1));
        if (key.empty()) {
            continue;
        }

        if (key == "auto_inject") {
            parseBoolean(value, settings.autoInjectEnabled);
        } else if (key == "readiness") {
            if (value == "conservative") {
                settings.readiness = AutoInjectReadiness::Conservative;
            } else if (value == "window-stable") {
                settings.readiness = AutoInjectReadiness::WindowStable;
            }
        } else if (key == "settle_delay_ms") {
            parseClampedInteger(value, 2000, 15000, settings.settleDelayMs);
        } else if (key == "minimum_process_age_ms") {
            parseClampedInteger(value, 5000, 60000,
                settings.minimumProcessAgeMs);
        } else if (key == "close_after_success") {
            parseBoolean(value, settings.closeAfterSuccess);
        } else if (key == "unlock_cosmetics") {
            parseBoolean(value, settings.unlockCosmetics);
        } else if (key == "unlock_badges") {
            parseBoolean(value, settings.unlockBadges);
        } else if (key == "unlock_emotes") {
            parseBoolean(value, settings.unlockEmotes);
        } else if (key == "unlock_sprays") {
            parseBoolean(value, settings.unlockSprays);
        } else if (key == "unlock_jams") {
            parseBoolean(value, settings.unlockJams);
        } else if (key == "lunar_plus_appearance") {
            parseBoolean(value, settings.lunarPlusAppearance);
        } else if (key == "debug_logging") {
            parseBoolean(value, settings.debugLogging);
        } else if (key == "language" && !value.empty()) {
            const std::wstring decoded = decodeLanguage(value);
            if (!decoded.empty()) {
                settings.language = decoded;
            }
        }
    }
    return settings;
}

bool LoaderSettings::save() const {
    const std::wstring path = defaultPath();
    return !path.empty() && saveTo(path);
}

bool LoaderSettings::saveTo(const std::wstring& path) const {
    if (path.empty()) {
        return false;
    }
    const std::string contents = serialize(*this);
    if (contents.empty()) {
        return false;
    }

    const std::filesystem::path destination(path);
    const std::filesystem::path parent = destination.parent_path();
    if (!parent.empty()) {
        std::error_code error;
        std::filesystem::create_directories(parent, error);
        if (error) {
            return false;
        }
    }

    const std::wstring temporary = path + L".tmp";
    HANDLE file = CreateFileW(temporary.c_str(), GENERIC_WRITE, 0, nullptr,
        CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        return false;
    }

    const DWORD byteCount = static_cast<DWORD>(contents.size());
    DWORD bytesWritten = 0;
    bool written = WriteFile(file, contents.data(), byteCount, &bytesWritten,
        nullptr) != FALSE && bytesWritten == byteCount;
    if (written) {
        written = FlushFileBuffers(file) != FALSE;
    }
    if (CloseHandle(file) == FALSE) {
        written = false;
    }
    if (!written) {
        DeleteFileW(temporary.c_str());
        return false;
    }

    if (MoveFileExW(temporary.c_str(), path.c_str(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH) == FALSE) {
        DeleteFileW(temporary.c_str());
        return false;
    }
    return true;
}
