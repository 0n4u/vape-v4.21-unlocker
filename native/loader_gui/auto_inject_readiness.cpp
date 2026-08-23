#include "auto_inject_readiness.h"

#include <algorithm>
#include <initializer_list>
#include <string>
#include <vector>

namespace {
wchar_t asciiLower(wchar_t ch) {
    if (ch >= L'A' && ch <= L'Z') {
        return static_cast<wchar_t>(ch - L'A' + L'a');
    }
    return ch;
}

std::wstring lowerCopy(std::wstring value) {
    std::transform(value.begin(), value.end(), value.begin(), asciiLower);
    return value;
}

bool isAsciiWhitespace(wchar_t ch) {
    return ch == L' ' || ch == L'\t' || ch == L'\r'
        || ch == L'\n' || ch == L'\f' || ch == L'\v';
}

std::wstring baseName(const std::wstring& path) {
    const std::wstring::size_type separator = path.find_last_of(L"/\\");
    return separator == std::wstring::npos ? path : path.substr(separator + 1);
}

bool startsWith(const std::wstring& value, const wchar_t* prefix) {
    return value.rfind(prefix, 0) == 0;
}

bool endsWith(const std::wstring& value, const wchar_t* suffix) {
    const std::wstring marker(suffix);
    return value.size() >= marker.size()
        && value.compare(value.size() - marker.size(), marker.size(), marker) == 0;
}

std::vector<std::wstring> tokenizeCommandLine(const std::wstring& commandLine) {
    std::vector<std::wstring> tokens;
    std::wstring token;
    bool quoted = false;
    for (wchar_t ch : commandLine) {
        if (ch == L'"') {
            quoted = !quoted;
            continue;
        }
        if (!quoted && isAsciiWhitespace(ch)) {
            if (!token.empty()) {
                tokens.push_back(token);
                token.clear();
            }
            continue;
        }
        token.push_back(ch);
    }
    if (!token.empty()) {
        tokens.push_back(token);
    }
    return tokens;
}

bool equalsAny(const std::wstring& value,
    std::initializer_list<const wchar_t*> markers) {
    for (const wchar_t* marker : markers) {
        if (value == marker) {
            return true;
        }
    }
    return false;
}

bool startsWithAny(const std::wstring& value,
    std::initializer_list<const wchar_t*> prefixes) {
    for (const wchar_t* prefix : prefixes) {
        if (startsWith(value, prefix)) {
            return true;
        }
    }
    return false;
}

bool isMinecraftOptionValue(const std::vector<std::wstring>& tokens,
    std::size_t index) {
    if (index == 0) {
        return false;
    }
    return equalsAny(tokens[index - 1], {
        L"--username",
        L"--uuid",
        L"--accesstoken",
        L"--usertype",
        L"--versiontype",
        L"--gamedir",
        L"--assetsdir",
        L"--assetindex",
        L"--version",
    });
}

bool isRejectedCommandLine(const std::vector<std::wstring>& tokens) {
    for (std::size_t index = 0; index < tokens.size(); ++index) {
        const std::wstring& token = tokens[index];
        if (startsWithAny(token, {
                L"org.gradle.",
                L"org.jetbrains.",
                L"com.intellij.",
                L"org.eclipse.",
                L"eclipse.equinox.",
                L"org.netbeans.",
                L"com.lunarclient.launcher.",
                L"net.minecraft.bootstrap.",
            })) {
            return true;
        }
        if (startsWith(token, L"-javaagent:") && endsWith(token, L"idea_rt.jar")) {
            return true;
        }

        const std::wstring fileName = baseName(token);
        if ((startsWith(fileName, L"gradle-") && endsWith(fileName, L".jar"))
            || fileName == L"gradlew" || fileName == L"gradlew.bat") {
            return true;
        }
        if (!isMinecraftOptionValue(tokens, index)
            && (fileName == L"server.jar"
                || token == L"nogui"
                || token == L"dedicatedserver"
                || endsWith(token, L".dedicatedserver")
                || token == L"net.minecraft.server"
                || startsWith(token, L"net.minecraft.server."))) {
            return true;
        }

        if (!isMinecraftOptionValue(tokens, index)
            && equalsAny(token, {
                L"gradle",
                L"runclient",
                L"tooling",
                L"netbeans",
                L"vscode",
                L"jdt.ls",
                L"lunarclientlauncher",
                L"lunar client launcher",
                L"minecraftlauncher",
            })) {
            return true;
        }
    }
    return false;
}

bool containsLunarGameJarPath(std::wstring token) {
    std::replace(token.begin(), token.end(), L'/', L'\\');
    constexpr const wchar_t* marker =
        L".lunarclient\\offline\\multiver\\lunar.jar";
    const std::size_t markerLength = std::char_traits<wchar_t>::length(marker);
    std::size_t position = token.find(marker);
    while (position != std::wstring::npos) {
        const bool boundedBefore = position == 0 || token[position - 1] == L'\\'
            || token[position - 1] == L';';
        const std::size_t after = position + markerLength;
        const bool boundedAfter = after == token.size() || token[after] == L';';
        if (boundedBefore && boundedAfter) {
            return true;
        }
        position = token.find(marker, position + 1);
    }
    return false;
}

bool hasOptionWithValue(const std::vector<std::wstring>& tokens,
    const wchar_t* option) {
    for (std::size_t index = 0; index + 1 < tokens.size(); ++index) {
        if (tokens[index] == option && !tokens[index + 1].empty()
            && tokens[index + 1][0] != L'-') {
            return true;
        }
    }
    return false;
}

bool hasPositiveGameCommandLine(const std::vector<std::wstring>& tokens) {
    for (const std::wstring& token : tokens) {
        if (containsLunarGameJarPath(token)
            || (startsWith(token, L"com.moonsworth.lunar.")
                && token.size() > std::wstring(L"com.moonsworth.lunar.").size())
            || token == L"net.minecraft.client.main.main"
            || token == L"net.minecraft.client.minecraft") {
            return true;
        }
    }

    const bool hasGameDirectory = hasOptionWithValue(tokens, L"--gamedir");
    const bool hasAssets = hasOptionWithValue(tokens, L"--assetsdir")
        || hasOptionWithValue(tokens, L"--assetindex");
    const bool hasVersion = hasOptionWithValue(tokens, L"--version");
    return hasGameDirectory && hasAssets && hasVersion;
}

bool hasNonWhitespace(const std::wstring& value) {
    return std::any_of(value.begin(), value.end(), [](wchar_t ch) {
        return !isAsciiWhitespace(ch);
    });
}

bool isGameWindow(const ProcessObservation& observation) {
    const std::wstring windowClass = lowerCopy(observation.windowClass);
    const bool knownWindowClass = windowClass == L"lwjgl"
        || windowClass == L"lwjgl3"
        || windowClass == L"glfw30";

    const std::wstring title = lowerCopy(observation.title);
    if (title.find(L"minecraft server") != std::wstring::npos) {
        return false;
    }
    const bool knownTitle = title.find(L"minecraft") != std::wstring::npos
        || title.find(L"lunar") != std::wstring::npos;
    return knownWindowClass || knownTitle;
}

AutoInjectEvaluation reject(AutoInjectReadinessReason reason) {
    return {false, reason};
}
} 

AutoInjectEvaluation evaluateAutoInjectReadiness(
    const ProcessObservation& observation,
    const AutoInjectPolicy& policy) {
    if (policy.minimumAge.count() < 0 || policy.settleDelay.count() < 0) {
        return reject(AutoInjectReadinessReason::InvalidPolicy);
    }
    if (observation.identity.pid == 0
        || observation.identity.creationTime100ns == 0) {
        return reject(AutoInjectReadinessReason::InvalidIdentity);
    }
    if (observation.alreadyInjected) {
        return reject(AutoInjectReadinessReason::AlreadyInjected);
    }
    if (observation.alreadyAttempted) {
        return reject(AutoInjectReadinessReason::AlreadyAttempted);
    }

    const std::wstring executable = lowerCopy(baseName(observation.exeName));
    if (executable != L"java.exe" && executable != L"javaw.exe") {
        return reject(AutoInjectReadinessReason::NotJavaExecutable);
    }

    const bool windowStableMode = policy.mode
        == AutoInjectReadiness::WindowStable;
    if (!observation.commandLineReadable) {
        if (!windowStableMode) {
            return reject(AutoInjectReadinessReason::CommandLineUnreadable);
        }
    } else {
        const std::vector<std::wstring> commandLine = tokenizeCommandLine(
            lowerCopy(observation.commandLine));
        if (isRejectedCommandLine(commandLine)) {
            return reject(AutoInjectReadinessReason::RejectedCommandLine);
        }
        if (!windowStableMode && !hasPositiveGameCommandLine(commandLine)) {
            return reject(AutoInjectReadinessReason::GameCommandLineNotFound);
        }
    }

    if (!observation.visible) {
        return reject(AutoInjectReadinessReason::WindowNotVisible);
    }
    if (!observation.responsive) {
        return reject(AutoInjectReadinessReason::WindowNotResponsive);
    }
    if (!hasNonWhitespace(observation.title)) {
        return reject(AutoInjectReadinessReason::WindowTitleBlank);
    }
    if (!isGameWindow(observation)) {
        return reject(AutoInjectReadinessReason::GameWindowNotFound);
    }
    if (observation.processAge < policy.minimumAge) {
        return reject(AutoInjectReadinessReason::ProcessTooYoung);
    }
    if (observation.titleStableFor < policy.settleDelay) {
        return reject(AutoInjectReadinessReason::WindowNotStable);
    }

    return {true, AutoInjectReadinessReason::Ready};
}

std::optional<ProcessIdentity> selectAutoInjectCandidate(
    const std::vector<ProcessObservation>& observations,
    const AutoInjectPolicy& policy) {
    std::optional<ProcessIdentity> selected;
    for (const ProcessObservation& observation : observations) {
        if (!evaluateAutoInjectReadiness(observation, policy).ready) {
            continue;
        }

        const ProcessIdentity& candidate = observation.identity;
        const bool newer = !selected
            || candidate.creationTime100ns > selected->creationTime100ns;
        const bool deterministicTieBreak = selected
            && candidate.creationTime100ns == selected->creationTime100ns
            && candidate.pid < selected->pid;
        if (newer || deterministicTieBreak) {
            selected = candidate;
        }
    }
    return selected;
}
