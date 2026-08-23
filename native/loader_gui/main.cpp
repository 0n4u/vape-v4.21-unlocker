#include "controller_model.h"
#include "controller_ui.h"

#include <windows.h>
#include <shellapi.h>




extern "C" int console_main(int argc, wchar_t **argv);





static int console_argv(wchar_t **outArgv, wchar_t (*outBuf)[128],
        size_t outCount) {
    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv == nullptr || argc < 3) {
        if (argv != nullptr) LocalFree(argv);
        return 0;
    }
    
    const int stripped = argc - 2;
    const size_t count = static_cast<size_t>(stripped) < outCount
            ? static_cast<size_t>(stripped) : outCount;
    for (size_t i = 0; i < count; ++i) {
        wcsncpy_s(outBuf[i], 128, argv[i + 2], _TRUNCATE);
        outArgv[i] = outBuf[i];
    }
    LocalFree(argv);
    return static_cast<int>(count);
}

int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE, wchar_t*, int showCommand) {
    
    
    
    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    const bool noGui = argv != nullptr && argc >= 2
            && _wcsicmp(argv[1], L"-nogui") == 0;
    if (argv != nullptr) {
        LocalFree(argv);
    }

    if (noGui) {
        
        
        
        
        
        AllocConsole();
        FILE *dummy = nullptr;
        freopen_s(&dummy, "CONOUT$", "w", stdout);
        freopen_s(&dummy, "CONOUT$", "w", stderr);
        freopen_s(&dummy, "CONIN$", "r", stdin);
        wchar_t *consoleArgv[8]{};
        wchar_t consoleArgBuf[8][128]{};
        const int consoleArgc = console_argv(consoleArgv, consoleArgBuf,
                sizeof(consoleArgv) / sizeof(consoleArgv[0]));
        return console_main(consoleArgc, consoleArgv);
    }

    ControllerModel model;
    ControllerUi ui(instance, model);
    return ui.run(showCommand);
}
