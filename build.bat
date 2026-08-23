@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Vape v4.21 - One-Click Build
cd /d "%~dp0"

set "SCRIPT_DIR=%~dp0"
set "LOG=%SCRIPT_DIR%build.log"
set "SRC_DIR=%SCRIPT_DIR%"
set "BUILT_DIR=%SCRIPT_DIR%..\"

set "CHECK_ONLY=0"
set "JAVA_ONLY=0"
if /i "%~1"=="check"    set "CHECK_ONLY=1"
if /i "%~1"=="javaonly" set "JAVA_ONLY=1"

set "NEED_ADMIN=0"
set "JDK_HOME="
set "VS_PATH="

echo.
echo ============================================================
echo   Vape v4.21 - one-click build
echo ============================================================
echo.

where winget >nul 2>&1
if errorlevel 1 (
    echo [FAIL] winget not found. This script needs the App Installer.
    echo        Get it from: https://aka.ms/getwinget
    goto :fail_no_pause
)

:check_git
set "GIT_MISSING=0"
where git >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\Git\cmd\git.exe" (
        set "PATH=C:\Program Files\Git\cmd;%PATH%"
    ) else (
        set "GIT_MISSING=1"
        if "%CHECK_ONLY%"=="1" (echo [check] git: MISSING - will install) else (echo [setup] git: missing - will install)
        set "NEED_ADMIN=1"
    )
)
if "%GIT_MISSING%"=="0" if "%CHECK_ONLY%"=="1" (echo [check] git: OK)

:check_jdk
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if not defined JDK_HOME set "JDK_HOME=%%d"
if not defined JDK_HOME for /d %%d in ("C:\Program Files\Java\jdk-17*") do if not defined JDK_HOME set "JDK_HOME=%%d"
if not defined JDK_HOME (
    if "%CHECK_ONLY%"=="1" (echo [check] JDK 17: MISSING - will install Temurin 17) else (echo [setup] JDK 17: missing - will install)
    set "NEED_ADMIN=1"
) else (
    if "%CHECK_ONLY%"=="1" (echo [check] JDK 17: OK - !JDK_HOME!)
)

if "%JAVA_ONLY%"=="1" goto :check_src

set "CMAKE_MISSING=0"
where cmake >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\CMake\bin\cmake.exe" (
        set "PATH=C:\Program Files\CMake\bin;%PATH%"
    ) else (
        set "CMAKE_MISSING=1"
        if "%CHECK_ONLY%"=="1" (echo [check] CMake: MISSING - will install) else (echo [setup] CMake: missing - will install)
        set "NEED_ADMIN=1"
    )
)
if "%CMAKE_MISSING%"=="0" if "%CHECK_ONLY%"=="1" (echo [check] CMake: OK)

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
    for /f "usebackq delims=" %%v in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2^>nul`) do set "VS_PATH=%%v"
)
if not defined VS_PATH (
    if "%CHECK_ONLY%"=="1" (echo [check] VS 2022 Build Tools ^(C++ x64^): MISSING - will install ~2-4 GB) else (echo [setup] VS 2022 Build Tools: missing - will install)
    set "NEED_ADMIN=1"
) else (
    if "%CHECK_ONLY%"=="1" (echo [check] VS 2022 Build Tools: OK)
)

:check_src
if exist "%SRC_DIR%\build.gradle" (
    if "%CHECK_ONLY%"=="1" (echo [check] source: OK - %SRC_DIR%)
) else (
    if "%CHECK_ONLY%"=="1" (echo [check] source: MISSING - %SRC_DIR% not found)
)

if "%CHECK_ONLY%"=="1" (
    echo.
    echo [check] Summary complete. Run without "check" to install missing deps and build.
    exit /b 0
)

if "%NEED_ADMIN%"=="1" (
    net session >nul 2>&1
    if not "!errorlevel!"=="0" (
        echo [setup] Administrator rights are required to install build tools.
        echo [setup] Relaunching elevated...
        powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '%*' -Verb RunAs"
        exit /b 0
    )
    echo [setup] Running with admin rights.
)

if "%GIT_MISSING%"=="1" (
    echo [setup] Installing git...
    winget install --id Git.Git -e --silent --accept-package-agreements --accept-source-agreements
    set "PATH=C:\Program Files\Git\cmd;%PATH%"
)

if not defined JDK_HOME (
    echo [setup] Installing JDK 17 ^(Temurin^)...
    winget install --id EclipseAdoptium.Temurin.17.JDK -e --silent --accept-package-agreements --accept-source-agreements
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if not defined JDK_HOME set "JDK_HOME=%%d"
    if not defined JDK_HOME (
        echo [FAIL] JDK 17 was not found after install. Try running again.
        goto :fail
    )
)

if "%JAVA_ONLY%"=="1" goto :ready

if "%CMAKE_MISSING%"=="1" (
    echo [setup] Installing CMake...
    winget install --id Kitware.CMake -e --silent --accept-package-agreements --accept-source-agreements
    set "PATH=C:\Program Files\CMake\bin;%PATH%"
)

if not defined VS_PATH (
    echo [setup] Installing VS 2022 Build Tools ^(C++ x64 workload, ~2-4 GB - this takes 5-15 minutes^)...
    winget install --id Microsoft.VisualStudio.2022.BuildTools -e --silent --accept-package-agreements --accept-source-agreements --override "--add Microsoft.VisualStudio.Workload.VCTools --includeRecommended --passive --norestart --wait"
    echo [setup] Waiting for VS installation to finish...
    set /a vs_tries=0
    :vs_poll
    set "VS_PATH="
    for /f "usebackq delims=" %%v in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2^>nul`) do set "VS_PATH=%%v"
    if not defined VS_PATH (
        set /a vs_tries+=1
        if !vs_tries! GEQ 60 (
            echo [FAIL] VS Build Tools did not finish installing within 30 minutes.
            echo        Close any Visual Studio installer windows and run this script again.
            goto :fail
        )
        echo [setup]   ...still installing ^(!vs_tries!/60^)...
        timeout /t 30 /nobreak >nul
        goto :vs_poll
    )
    echo [setup] VS Build Tools installed: !VS_PATH!
)

:ready
if not exist "%SRC_DIR%\build.gradle" (
    echo [FAIL] Source not found at %SRC_DIR% (expected vape-src/build.gradle).
    goto :fail
)

cd /d "%SRC_DIR%"

set "JAVA_HOME=%JDK_HOME%"
set "PATH=C:\Program Files\CMake\bin;C:\Program Files\Git\cmd;%PATH%"
echo [build] JAVA_HOME=!JAVA_HOME!
echo [build] Building from %SRC_DIR%... (first run downloads Gradle 8.8 + dependencies, ~3-8 min)

if "%JAVA_ONLY%"=="1" (
    echo [build] Target: Java injection payload only
    call gradlew.bat clean build verifyInjectionPayload > "%LOG%" 2>&1
    if errorlevel 1 goto :fail_build
    if not exist "build\libs\vape421-product-recovery-4.21-recovered-injection.jar" goto :fail_build
    echo [build] Java payload OK:
    dir /b build\libs\*-injection.jar
) else (
    echo [build] Target: full Windows x64 bundle
    call gradlew.bat clean prepareInjectionBundle -PtargetRelease=8 "-PnativeJavaHome=%JDK_HOME%" > "%LOG%" 2>&1
    if errorlevel 1 goto :fail_build
    if not exist "build\injection\Vape-v4.21.exe" goto :fail_build
)

mkdir "%BUILT_DIR%" 2>nul
if exist "build\injection\Vape-v4.21.exe" (
    copy /y "build\injection\Vape-v4.21.exe" "%BUILT_DIR%" >nul
    copy /y "build\injection\Vape-v4.21Native.dll" "%BUILT_DIR%" >nul
    echo.
    echo ============================================================
    echo   BUILD COMPLETE
    echo   Output: %BUILT_DIR%
    echo ============================================================
    echo.
    echo   Next steps:
    echo     1. Launch Minecraft ^(64-bit JVM^)
    echo     2. Run Vape-v4.21.exe, pick the Minecraft process, inject
    echo     3. In-game press Right Shift to open the menu
    echo.
) else (
    echo [FAIL] Build finished but no Vape-v4.21.exe was produced.
    goto :fail
)
pause
exit /b 0

:fail_build
echo.
echo [FAIL] Build failed. Last 30 lines of the build log:
echo ------------------------------------------------------------
powershell -NoProfile -Command "Get-Content -LiteralPath '%LOG%' -Tail 30"
echo ------------------------------------------------------------
echo [FAIL] Full log: %LOG%
goto :fail

:fail
echo.
echo [FAIL] Setup did not complete. See messages above.
pause
exit /b 1

:fail_no_pause
exit /b 1
