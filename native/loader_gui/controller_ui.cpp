#include "controller_ui.h"
#include "resource_ids.h"



#include <objidl.h>

#include <shellapi.h>
#include <shlwapi.h>
#include <windowsx.h>

#include <algorithm>
#include <cmath>
#include <cstring>

namespace {
constexpr float CanvasWidth = 824.0f;
constexpr float CanvasHeight = 484.0f;
constexpr UINT WM_CONTROLLER_STATE = WM_APP + 41;

struct ResourceView {
    const void* data{};
    DWORD size{};
};

ResourceView resourceView(HINSTANCE instance, int resourceId) {
    HRSRC resource = FindResourceW(instance, MAKEINTRESOURCEW(resourceId), RT_RCDATA);
    HGLOBAL loaded = resource == nullptr ? nullptr : LoadResource(instance, resource);
    return {loaded == nullptr ? nullptr : LockResource(loaded),
        resource == nullptr ? 0 : SizeofResource(instance, resource)};
}


static float easeTo(float current, float target, float rate, float dt) {
    const float k = 1.0f - std::exp(-rate * dt);
    return current + (target - current) * k;
}

static Gdiplus::Color lerpColor(const Gdiplus::Color& a, const Gdiplus::Color& b, float t) {
    t = std::clamp(t, 0.0f, 1.0f);
    return Gdiplus::Color(
        static_cast<BYTE>(a.GetA() + (b.GetA() - a.GetA()) * t),
        static_cast<BYTE>(a.GetR() + (b.GetR() - a.GetR()) * t),
        static_cast<BYTE>(a.GetG() + (b.GetG() - a.GetG()) * t),
        static_cast<BYTE>(a.GetB() + (b.GetB() - a.GetB()) * t));
}


static Gdiplus::Color scaleAlpha(const Gdiplus::Color& c, float mult) {
    return Gdiplus::Color(static_cast<BYTE>(c.GetA() * std::clamp(mult, 0.0f, 1.0f)),
        c.GetR(), c.GetG(), c.GetB());
}

void addRoundedPath(Gdiplus::GraphicsPath& path, float x, float y,
                    float width, float height, float radius) {
    const float diameter = radius * 2.0f;
    path.AddArc(x, y, diameter, diameter, 180.0f, 90.0f);
    path.AddArc(x + width - diameter, y, diameter, diameter, 270.0f, 90.0f);
    path.AddArc(x + width - diameter, y + height - diameter, diameter, diameter, 0.0f, 90.0f);
    path.AddArc(x, y + height - diameter, diameter, diameter, 90.0f, 90.0f);
    path.CloseFigure();
}
}

ControllerUi::ControllerUi(HINSTANCE instance, ControllerModel& model)
    : instance_(instance), model_(model) {
    Gdiplus::GdiplusStartupInput input;
    Gdiplus::GdiplusStartup(&gdiplusToken_, &input, nullptr);
    const ResourceView regular = resourceView(instance_, IDR_ASSET_PROXIMA_REGULAR);
    const ResourceView semibold = resourceView(instance_, IDR_ASSET_PROXIMA_SEMIBOLD);
    if (regular.data != nullptr && regular.size != 0) {
        fonts_.AddMemoryFont(regular.data, static_cast<INT>(regular.size));
    }
    if (semibold.data != nullptr && semibold.size != 0) {
        fonts_.AddMemoryFont(semibold.data, static_cast<INT>(semibold.size));
    }
    logo_ = loadImage(IDR_ASSET_LOGO);
    maskTop_ = loadImage(IDR_ASSET_MASK_TOP);
    maskBottom_ = loadImage(IDR_ASSET_MASK_BOTTOM);
    maskLeft_ = loadImage(IDR_ASSET_MASK_LEFT);
    maskRight_ = loadImage(IDR_ASSET_MASK_RIGHT);
    roundedRect_ = loadImage(IDR_ASSET_ROUNDED_RECT);
}

ControllerUi::~ControllerUi() {
    logo_.reset();
    maskTop_.reset();
    maskBottom_.reset();
    maskLeft_.reset();
    maskRight_.reset();
    roundedRect_.reset();
    for (IStream* stream : imageStreams_) stream->Release();
    imageStreams_.clear();
    if (gdiplusToken_) Gdiplus::GdiplusShutdown(gdiplusToken_);
}

std::unique_ptr<Gdiplus::Image> ControllerUi::loadImage(int resourceId) {
    const ResourceView resource = resourceView(instance_, resourceId);
    if (resource.data == nullptr || resource.size == 0) return {};
    IStream* stream = SHCreateMemStream(static_cast<const BYTE*>(resource.data), resource.size);
    if (stream == nullptr) return {};
    auto image = std::make_unique<Gdiplus::Image>(stream);
    if (image->GetLastStatus() != Gdiplus::Ok) {
        image.reset();
        stream->Release();
        return {};
    }
    imageStreams_.push_back(stream);
    return image;
}

int ControllerUi::run(int showCommand) {
    WNDCLASSEXW klass{sizeof(klass)};
    klass.style = CS_HREDRAW | CS_VREDRAW;
    klass.lpfnWndProc = windowProc;
    klass.hInstance = instance_;
    klass.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    
    
    klass.hIcon = LoadIconW(instance_, MAKEINTRESOURCEW(300));
    if (klass.hIcon == nullptr) {
        klass.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
    }
    klass.hIconSm = klass.hIcon;
    klass.lpszClassName = L"VapeV4ControllerRewrite";
    RegisterClassExW(&klass);

    const UINT dpi = GetDpiForSystem();
    const int clientWidth = MulDiv(824, dpi, 96);
    const int clientHeight = MulDiv(484, dpi, 96);
    RECT bounds{0, 0, clientWidth, clientHeight};
    AdjustWindowRectExForDpi(&bounds, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU |
        WS_MINIMIZEBOX, FALSE, 0, dpi);
    window_ = CreateWindowExW(0, klass.lpszClassName, L"Vape v4",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT, bounds.right - bounds.left,
        bounds.bottom - bounds.top, nullptr, nullptr, instance_, this);
    if (!window_) return 1;
    lastFrame_ = std::chrono::steady_clock::now();
    pageChanged_ = lastFrame_;
    lastPage_ = model_.page();
    SetTimer(window_, 1, 16, nullptr);
    ShowWindow(window_, showCommand);
    UpdateWindow(window_);

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return static_cast<int>(message.wParam);
}

LRESULT CALLBACK ControllerUi::windowProc(HWND window, UINT message,
                                          WPARAM wParam, LPARAM lParam) {
    ControllerUi* self = reinterpret_cast<ControllerUi*>(
        GetWindowLongPtrW(window, GWLP_USERDATA));
    if (message == WM_NCCREATE) {
        const auto* create = reinterpret_cast<CREATESTRUCTW*>(lParam);
        self = static_cast<ControllerUi*>(create->lpCreateParams);
        self->window_ = window;
        SetWindowLongPtrW(window, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
    }
    return self ? self->handleMessage(message, wParam, lParam)
                : DefWindowProcW(window, message, wParam, lParam);
}

bool ControllerUi::hit(float logicalX, float logicalY, float x, float y,
                       float width, float height) const {
    return logicalX >= x && logicalX <= x + width &&
        logicalY >= y && logicalY <= y + height;
}

bool ControllerUi::pointerIn(float x, float y, float width, float height) const {
    return hit(mouseX_, mouseY_, x, y, width, height);
}

void ControllerUi::updateFrame() {
    const auto now = std::chrono::steady_clock::now();
    const double delta = std::clamp(std::chrono::duration<double>(now - lastFrame_).count(),
        0.0, 0.1);
    lastFrame_ = now;
    model_.tick();

    const ControllerPage page = model_.page();
    if (page != lastPage_) {
        lastPage_ = page;
        pageChanged_ = now;
        if (page == ControllerPage::Loading) {
            loadingProgress_ = 0.05f;
            previousLoadingStage_ = model_.loadingStage();
        }
    }

    float logoTarget = page == ControllerPage::Login ? 1.0f : 0.0f;
    if (page == ControllerPage::MinecraftSelection && !model_.minecraftProcesses().empty()) {
        logoTarget = 1.0f;
    }
    logoPosition_ += (logoTarget - logoPosition_) *
        static_cast<float>(1.0 - std::exp(-8.0 * delta));

    if (page == ControllerPage::BrowserAuth) {
        spinnerAccumulator_ += delta;
        while (spinnerAccumulator_ >= 0.020) {
            spinnerAccumulator_ -= 0.020;
            for (int index = 0; index < 4; ++index) {
                spinnerAlpha_[index] += index == spinnerIndex_ ? 0.15f : -0.075f;
                spinnerAlpha_[index] = std::clamp(spinnerAlpha_[index], 0.0f, 1.0f);
            }
            if (spinnerAlpha_[spinnerIndex_] >= 1.0f) {
                spinnerIndex_ = (spinnerIndex_ + 1) % 4;
                spinnerAlpha_[spinnerIndex_] = 0.0f;
            }
        }
    }

    if (page == ControllerPage::Loading) {
        const int stage = model_.loadingStage();
        if (stage != previousLoadingStage_) previousLoadingStage_ = stage;
        const float target = std::max(static_cast<float>(stage) / 29.0f, 0.05f);
        if (loadingProgress_ < target) {
            loadingProgress_ += 0.01f * (1.0f - loadingProgress_ / target);
            loadingProgress_ = std::min(loadingProgress_, target);
        }
    }

    
    const bool gearHot = page != ControllerPage::Settings &&
        pointerIn(GearX, GearY, GearSize, GearSize);
    gearHover_ = easeTo(gearHover_, gearHot ? 1.0f : 0.0f, 16.0f, delta);

    const bool backHot = page == ControllerPage::Settings && pointerIn(28, 22, 32, 32);
    backHover_ = easeTo(backHover_, backHot ? 1.0f : 0.0f, 16.0f, delta);

    settingsOpen_ = easeTo(settingsOpen_, page == ControllerPage::Settings ? 1.0f : 0.0f,
        14.0f, delta);
    settingsScroll_ = easeTo(settingsScroll_, settingsScrollTarget_, 18.0f, delta);
    if (settingsScroll_ < 0.0f) settingsScroll_ = 0.0f;
    if (settingsScroll_ > settingsMaxScroll_) settingsScroll_ = settingsMaxScroll_;

    
    const std::vector<SettingsSection> sections = settingsSections();
    for (const auto& section : sections) {
        for (const auto& item : section.items) {
            const float target = item.value ? 1.0f : 0.0f;
            auto it = switchAnim_.find(item.focus);
            const float cur = it == switchAnim_.end() ? target : it->second;
            switchAnim_[item.focus] = easeTo(cur, target, 18.0f, delta);

            auto tgt = rowHoverTarget_.find(item.focus);
            const float t = tgt == rowHoverTarget_.end() ? 0.0f : tgt->second;
            auto rh = rowHoverAnim_.find(item.focus);
            const float rc = rh == rowHoverAnim_.end() ? 0.0f : rh->second;
            rowHoverAnim_[item.focus] = easeTo(rc, t, 16.0f, delta);
        }
    }
    selectHover_ = easeTo(selectHover_, selectHoverTarget_, 16.0f, delta);
    rowHoverTarget_.clear();
    selectHoverTarget_ = 0.0f;

    InvalidateRect(window_, nullptr, FALSE);
}

void ControllerUi::drawTransitionMasks(Gdiplus::Graphics& graphics) {
    const double elapsed = std::chrono::duration<double>(
        std::chrono::steady_clock::now() - pageChanged_).count();
    if (elapsed >= 0.45 || elapsed < 0.0) return;
    const float t = static_cast<float>(elapsed / 0.45);
    const float opacity = 1.0f - t;
    Gdiplus::ColorMatrix matrix{{
        {1, 0, 0, 0, 0}, {0, 1, 0, 0, 0}, {0, 0, 1, 0, 0},
        {0, 0, 0, opacity, 0}, {0, 0, 0, 0, 1}}};
    Gdiplus::ImageAttributes attributes;
    attributes.SetColorMatrix(&matrix);
    if (maskLeft_ && maskLeft_->GetLastStatus() == Gdiplus::Ok) {
        const float x = -154.0f * t;
        graphics.DrawImage(maskLeft_.get(), Gdiplus::RectF(x, 149, 154, 185),
            0, 0, 154, 185, Gdiplus::UnitPixel, &attributes);
    }
    if (maskRight_ && maskRight_->GetLastStatus() == Gdiplus::Ok) {
        const float x = 713.0f + 111.0f * t;
        graphics.DrawImage(maskRight_.get(), Gdiplus::RectF(x, 161, 111, 162),
            0, 0, 111, 162, Gdiplus::UnitPixel, &attributes);
    }
}

LRESULT ControllerUi::handleMessage(UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_PAINT:
        paint();
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_CONTROLLER_STATE:
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    case WM_TIMER:
        if (wParam == 1) updateFrame();
        return 0;
    case WM_VSCROLL: {
        if (model_.page() == ControllerPage::Settings) {
            const int code = LOWORD(wParam);
            float step = 28.0f;
            if (code == SB_LINEUP) settingsScrollTarget_ -= step;
            else if (code == SB_LINEDOWN) settingsScrollTarget_ += step;
            else if (code == SB_PAGEUP) settingsScrollTarget_ -= 120.0f;
            else if (code == SB_PAGEDOWN) settingsScrollTarget_ += 120.0f;
            if (settingsScrollTarget_ < 0.0f) settingsScrollTarget_ = 0.0f;
            if (settingsScrollTarget_ > settingsMaxScroll_) settingsScrollTarget_ = settingsMaxScroll_;
            InvalidateRect(window_, nullptr, FALSE);
        }
        return 0;
    }
    case WM_MOUSEWHEEL: {
        if (model_.page() == ControllerPage::Settings) {
            const int delta = GET_WHEEL_DELTA_WPARAM(wParam);
            settingsScrollTarget_ -= static_cast<float>(delta) / 120.0f * 28.0f;
            if (settingsScrollTarget_ < 0.0f) settingsScrollTarget_ = 0.0f;
            if (settingsScrollTarget_ > settingsMaxScroll_) settingsScrollTarget_ = settingsMaxScroll_;
            InvalidateRect(window_, nullptr, FALSE);
        }
        return 0;
    }
    case WM_MOUSEMOVE: {
        mouseX_ = static_cast<float>(GET_X_LPARAM(lParam)) / scaleX_;
        mouseY_ = static_cast<float>(GET_Y_LPARAM(lParam)) / scaleY_;
        if (!trackingMouse_) {
            TRACKMOUSEEVENT tracking{sizeof(tracking), TME_LEAVE, window_, 0};
            TrackMouseEvent(&tracking);
            trackingMouse_ = true;
        }
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    }
    case WM_MOUSELEAVE:
        mouseX_ = mouseY_ = -1.0f;
        trackingMouse_ = false;
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    case WM_LBUTTONDOWN: {
        const float x = static_cast<float>(GET_X_LPARAM(lParam)) / scaleX_;
        const float y = static_cast<float>(GET_Y_LPARAM(lParam)) / scaleY_;
        const auto page = model_.page();

        
        if (page != ControllerPage::Settings && hit(x, y, GearX, GearY, GearSize, GearSize)) {
            settingsReturnPage_ = page;
            settingsScroll_ = 0.0f;
            settingsScrollTarget_ = 0.0f;
            model_.setPage(ControllerPage::Settings);
            InvalidateRect(window_, nullptr, FALSE);
            return 0;
        }

        if (page == ControllerPage::Settings) {
            
            if (hit(x, y, 28, 22, 32, 32)) {
                model_.setPage(settingsReturnPage_);
                InvalidateRect(window_, nullptr, FALSE);
                return 0;
            }
            const float cy = y + settingsScroll_;
            const std::vector<SettingsSection> sections = settingsSections();
            float yy = HeaderBottom;
            for (const auto& section : sections) {
                yy += 26;
                const float cardX = ContentX;
                const float cardW = ContentW;
                const float cardY = yy;
                const int n = static_cast<int>(section.items.size());
                const float cardH = static_cast<float>(n) * RowH + CardPad * 2;
                float ry = cardY + CardPad;
                for (const auto& item : section.items) {
                    const float ctrlW = 140.0f;
                    const float ctrlX = cardX + cardW - CardPad - ctrlW;
                    const bool isSelect = item.description != nullptr && item.description[0] == L'\0';
                    if (isSelect) {
                        const float selY = ry + (RowH - 36.0f) / 2.0f;
                        if (hit(x, cy, ctrlX, selY, ctrlW, 36.0f)) {
                            cycleLanguage();
                            return 0;
                        }
                    } else {
                        if (hit(x, cy, cardX, ry, cardW, RowH)) {
                            toggleSettingsItem(static_cast<Focus>(item.focus));
                            return 0;
                        }
                    }
                    ry += RowH;
                }
                yy = cardY + cardH + 18;
            }
            focus_ = Focus::None;
            InvalidateRect(window_, nullptr, FALSE);
            return 0;
        }

        if (page == ControllerPage::Login) {
            if (hit(x, y, 278, 183, 268, 36)) focus_ = Focus::Username;
            else if (hit(x, y, 278, 231, 268, 36)) focus_ = Focus::Password;
            else if (hit(x, y, 328, 399, 160, 28))
                model_.beginBrowserAuthentication(window_);
            else if (hit(x, y, 352, 302.4f, 112.8f, 36) &&
                     !model_.username().empty()) {
                model_.submitCredentialAuthentication();
            }
            else focus_ = Focus::None;
        } else if (page == ControllerPage::BrowserAuth) {
            if (hit(x, y, 363, 306, 50, 25)) model_.reopenBrowserAuthentication();
            else if (hit(x, y, 420, 306, 50, 25)) model_.cancelBrowserAuthentication();
        } else if (page == ControllerPage::MinecraftSelection) {
            const auto processes = model_.minecraftProcesses();
            if (processes.empty()) {
                model_.refreshMinecraftProcesses();
            } else {
                float rowY = 210.0f;
                for (const auto& process : processes) {
                    if (hit(x, y, 252, rowY, 320, 48) && !process.alreadyInjected) {
                        model_.injectMinecraft(process.pid);
                        break;
                    }
                    rowY += 58.0f;
                    if (rowY > 410.0f) break;
                }
            }
        } else if (page == ControllerPage::LoadingComplete && hit(x, y, 356, 348, 112, 36)) {
            DestroyWindow(window_);
        } else if (page == ControllerPage::Error) {
            if (hit(x, y, 356, 300, 112, 36)) {
                const auto detail = model_.status();
                const auto text = L"Stage " + std::to_wstring(model_.loadingStage()) +
                    L"\r\nError start\r\n====================\r\n" + detail +
                    L"\r\n====================\r\nError end";
                const SIZE_T bytes = (text.size() + 1) * sizeof(wchar_t);
                HGLOBAL memory = GlobalAlloc(GMEM_MOVEABLE, bytes);
                if (memory) {
                    void* target = GlobalLock(memory);
                    memcpy(target, text.c_str(), bytes);
                    GlobalUnlock(memory);
                    if (OpenClipboard(window_)) {
                        EmptyClipboard();
                        SetClipboardData(CF_UNICODETEXT, memory);
                        CloseClipboard();
                        memory = nullptr;
                    }
                    if (memory) GlobalFree(memory);
                }
            }
        }
        SetFocus(window_);
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    }
    case WM_CHAR: {
        std::wstring* field = focus_ == Focus::Username ? &model_.username()
            : focus_ == Focus::Password ? &model_.password() : nullptr;
        if (!field) return 0;
        if (wParam == VK_BACK) {
            if (!field->empty()) field->pop_back();
        } else if (wParam >= 32 && wParam != 127 &&
                   field->size() < (focus_ == Focus::Username ? 49u : 255u)) {
            field->push_back(static_cast<wchar_t>(wParam));
        }
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    }
    case WM_KEYDOWN:
        if (model_.page() == ControllerPage::Login) {
            if (wParam == VK_TAB) {
                focus_ = focus_ == Focus::Username ? Focus::Password : Focus::Username;
            } else if (wParam == VK_RETURN && !model_.username().empty()) {
                model_.submitCredentialAuthentication();
            } else if (wParam == 'V' && (GetKeyState(VK_CONTROL) & 0x8000) != 0 &&
                       focus_ != Focus::None && OpenClipboard(window_)) {
                const HANDLE data = GetClipboardData(CF_UNICODETEXT);
                if (data) {
                    const auto* value = static_cast<const wchar_t*>(GlobalLock(data));
                    if (value) {
                        auto& target = focus_ == Focus::Username ? model_.username() : model_.password();
                        const std::size_t limit = focus_ == Focus::Username ? 49u : 255u;
                        target.append(value, std::min<std::size_t>(wcslen(value), limit - target.size()));
                        GlobalUnlock(data);
                    }
                }
                CloseClipboard();
            }
            InvalidateRect(window_, nullptr, FALSE);
        }
        return 0;
    case WM_DPICHANGED: {
        const auto* suggested = reinterpret_cast<RECT*>(lParam);
        SetWindowPos(window_, nullptr, suggested->left, suggested->top,
            suggested->right - suggested->left, suggested->bottom - suggested->top,
            SWP_NOACTIVATE | SWP_NOZORDER);
        return 0;
    }
    case WM_DESTROY:
        KillTimer(window_, 1);
        model_.cancelBrowserAuthentication();
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcW(window_, message, wParam, lParam);
    }
}

void ControllerUi::paint() {
    PAINTSTRUCT paint{};
    HDC target = BeginPaint(window_, &paint);
    RECT client{};
    GetClientRect(window_, &client);
    const int width = client.right - client.left;
    const int height = client.bottom - client.top;
    HDC bufferDc = CreateCompatibleDC(target);
    HBITMAP buffer = CreateCompatibleBitmap(target, width, height);
    const auto previous = SelectObject(bufferDc, buffer);
    Gdiplus::Graphics graphics(bufferDc);
    graphics.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
    graphics.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    graphics.SetTextRenderingHint(Gdiplus::TextRenderingHintAntiAliasGridFit);
    scaleX_ = static_cast<float>(width) / CanvasWidth;
    scaleY_ = static_cast<float>(height) / CanvasHeight;
    graphics.ScaleTransform(scaleX_, scaleY_);
    Gdiplus::SolidBrush background(Gdiplus::Color(255, 26, 25, 26));
    graphics.FillRectangle(&background, 0.0f, 0.0f, CanvasWidth, CanvasHeight);

    switch (model_.page()) {
    case ControllerPage::Login: drawLogin(graphics); break;
    case ControllerPage::BrowserAuth: drawBrowserAuth(graphics); break;
    case ControllerPage::MinecraftSelection: drawMinecraftSelection(graphics); break;
    case ControllerPage::Loading: drawLoading(graphics); break;
    case ControllerPage::CachePrompt: drawCachePrompt(graphics); break;
    case ControllerPage::LoadingComplete: drawLoadingComplete(graphics); break;
    case ControllerPage::OutdatedLauncher: drawOutdated(graphics); break;
    case ControllerPage::Error: drawError(graphics); break;
    case ControllerPage::Settings: drawSettings(graphics); break;
    }
    drawTransitionMasks(graphics);
    if (model_.page() != ControllerPage::Settings) drawGearButton(graphics);

    BitBlt(target, 0, 0, width, height, bufferDc, 0, 0, SRCCOPY);
    SelectObject(bufferDc, previous);
    DeleteObject(buffer);
    DeleteDC(bufferDc);
    EndPaint(window_, &paint);
}

void ControllerUi::drawRoundedRect(Gdiplus::Graphics& graphics, float x, float y,
                                   float width, float height, float radius,
                                   Gdiplus::Color fill, Gdiplus::Color border) {
    Gdiplus::GraphicsPath path;
    addRoundedPath(path, x, y, width, height, radius);
    Gdiplus::SolidBrush brush(fill);
    graphics.FillPath(&brush, &path);
    if (border.GetA() != 0) {
        Gdiplus::Pen pen(border, 1.0f);
        graphics.DrawPath(&pen, &path);
    }
}





std::wstring ControllerUi::ellipsize(Gdiplus::Graphics& graphics,
                                     const std::wstring& text, float fontSize,
                                     float maxWidth) const {
    if (text.empty() || maxWidth <= 0.0f) return text;
    Gdiplus::FontFamily loaded[4];
    int found = 0;
    fonts_.GetFamilies(4, loaded, &found);
    const Gdiplus::FontFamily* family = Gdiplus::FontFamily::GenericSansSerif();
    for (int index = 0; index < found; ++index) {
        wchar_t familyName[LF_FACESIZE]{};
        loaded[index].GetFamilyName(familyName);
        if (std::wstring(familyName).find(L"Semi") != std::wstring::npos) {
            family = &loaded[index];
            break;
        }
    }
    Gdiplus::Font font(family, fontSize, Gdiplus::FontStyleRegular,
        Gdiplus::UnitPixel);
    Gdiplus::StringFormat format;
    format.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
    const Gdiplus::RectF layout(0, 0, 100000.0f, 100.0f);

    Gdiplus::RectF measuredFull;
    if (graphics.MeasureString(text.c_str(), static_cast<INT>(text.size()),
            &font, layout, &format, &measuredFull) != Gdiplus::Ok
            || measuredFull.Width <= maxWidth) {
        return text;
    }

    
    
    const float safeWidth = maxWidth - 4.0f;
    
    const std::wstring ellipsis = L"\u2026";
    size_t low = 0;
    size_t high = text.size();
    while (low < high) {
        const size_t mid = low + (high - low + 1) / 2;
        const std::wstring candidate = text.substr(0, mid) + ellipsis;
        Gdiplus::RectF measured;
        if (graphics.MeasureString(candidate.c_str(),
                static_cast<INT>(candidate.size()), &font, layout, &format,
                &measured) == Gdiplus::Ok && measured.Width <= safeWidth) {
            low = mid;
        } else {
            high = mid - 1;
        }
    }
    if (low == 0) return ellipsis;
    return text.substr(0, low) + ellipsis;
}

void ControllerUi::drawText(Gdiplus::Graphics& graphics, const std::wstring& text,
                            float x, float y, float width, float height, float size,
                            Gdiplus::Color color, bool semibold,
                            Gdiplus::StringAlignment alignment) {
    Gdiplus::FontFamily loaded[4];
    int found = 0;
    fonts_.GetFamilies(4, loaded, &found);
    const Gdiplus::FontFamily* family = Gdiplus::FontFamily::GenericSansSerif();
    for (int index = 0; index < found; ++index) {
        wchar_t familyName[LF_FACESIZE]{};
        loaded[index].GetFamilyName(familyName);
        const std::wstring name(familyName);
        const bool isSemibold = name.find(L"Lt") != std::wstring::npos ||
            name.find(L"Semi") != std::wstring::npos;
        if (isSemibold == semibold) {
            family = &loaded[index];
            break;
        }
    }
    Gdiplus::Font font(family, size, Gdiplus::FontStyleRegular,
        Gdiplus::UnitPixel);
    Gdiplus::SolidBrush brush(color);
    Gdiplus::StringFormat format;
    format.SetAlignment(alignment);
    format.SetLineAlignment(Gdiplus::StringAlignmentCenter);
    format.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
    const Gdiplus::RectF box(x, y, width, height);
    graphics.DrawString(text.c_str(), static_cast<INT>(text.size()), &font, box, &format, &brush);
}

void ControllerUi::drawLogo(Gdiplus::Graphics& graphics, float y) {
    if (logo_ && logo_->GetLastStatus() == Gdiplus::Ok) {
        graphics.DrawImage(logo_.get(), 352.5f, y + 3.0f, 111.0f, 22.0f);
    }
}

void ControllerUi::drawLogin(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 182.0f - 80.0f * logoPosition_);
    const auto input = [&](float y, const std::wstring& placeholder,
                           const std::wstring& value, bool password, bool focused) {
        drawRoundedRect(graphics, 278, y, 268, 36, 6,
            Gdiplus::Color(255, 26, 25, 26),
            focused ? Gdiplus::Color(255, 48, 47, 48)
                    : Gdiplus::Color(255, 34, 33, 34));
        std::wstring shown = password && !value.empty() ? std::wstring(value.size(), L'*') : value;
        if (shown.empty()) shown = placeholder;
        drawText(graphics, shown, 294, y, 236, 36, 13,
            value.empty() ? Gdiplus::Color(255, 105, 102, 106)
                          : Gdiplus::Color(255, 195, 192, 196));
        const auto blink = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() / 500;
        if (focused && (blink & 1) == 0) {
            const float caretX = 294.0f + std::min(224.0f,
                static_cast<float>(shown.empty() ? 0 : shown.size()) * 6.8f);
            Gdiplus::Pen caret(Gdiplus::Color(255, 196, 193, 197), 1.0f);
            graphics.DrawLine(&caret, caretX, y + 10, caretX, y + 26);
        }
    };
    input(183, L"Username / Email", model_.username(), false, focus_ == Focus::Username);
    input(231, L"Password", model_.password(), true, focus_ == Focus::Password);
    const bool enabled = !model_.username().empty();
    const bool loginHover = enabled && pointerIn(352, 302.4f, 112.8f, 36);
    drawRoundedRect(graphics, 352, 302.4f, 112.8f, 36, 3,
        enabled ? (loginHover ? Gdiplus::Color(255, 49, 130, 97)
                              : Gdiplus::Color(255, 43, 112, 84))
                : Gdiplus::Color(255, 51, 51, 51));
    drawText(graphics, L"Login", 352, 302.4f, 112.8f, 36, 13,
        enabled ? Gdiplus::Color(255, 220, 225, 222) : Gdiplus::Color(255, 116, 113, 117), true,
        Gdiplus::StringAlignmentCenter);

    Gdiplus::Pen line(Gdiplus::Color(255, 34, 33, 34), 1.0f);
    graphics.DrawLine(&line, 278.0f, 374.0f, 392.0f, 374.0f);
    graphics.DrawLine(&line, 432.0f, 374.0f, 546.0f, 374.0f);
    drawText(graphics, L"or", 392, 362, 40, 24, 12,
        Gdiplus::Color(255, 192, 189, 193), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Log in via browser", 328, 399, 160, 28, 12,
        pointerIn(328, 399, 160, 28) ? Gdiplus::Color(255, 79, 146, 241)
                                    : Gdiplus::Color(255, 46, 120, 227),
        true, Gdiplus::StringAlignmentCenter);
    const auto status = model_.status();
    if (!status.empty()) drawText(graphics, status, 250, 438, 324, 24, 12,
        Gdiplus::Color(255, 175, 75, 75), false, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawBrowserAuth(Gdiplus::Graphics& graphics) {
    Gdiplus::GraphicsPath cardClip;
    addRoundedPath(cardClip, 278, 66, 268, 352, 6);
    Gdiplus::Region previousClip;
    graphics.GetClip(&previousClip);
    graphics.SetClip(&cardClip, Gdiplus::CombineModeReplace);
    Gdiplus::SolidBrush panel(Gdiplus::Color(255, 227, 237, 250));
    graphics.FillRectangle(&panel, 278.0f, 66.0f, 268.0f, 352.0f);
    if (maskTop_ && maskTop_->GetLastStatus() == Gdiplus::Ok)
        graphics.DrawImage(maskTop_.get(), 276.0f, 54.0f, 268.0f, 210.0f);
    if (maskBottom_ && maskBottom_->GetLastStatus() == Gdiplus::Ok)
        graphics.DrawImage(maskBottom_.get(), 276.0f, 372.0f, 91.0f, 46.0f);
    graphics.SetClip(&previousClip, Gdiplus::CombineModeReplace);

    drawText(graphics, L"Logging in", 278, 103, 268, 40, 18,
        Gdiplus::Color(255, 20, 20, 20), true, Gdiplus::StringAlignmentCenter);
    if (roundedRect_ && roundedRect_->GetLastStatus() == Gdiplus::Ok)
        graphics.DrawImage(roundedRect_.get(), 376.0f, 170.0f, 80.0f, 80.0f);
    constexpr float spinnerX[4]{400, 418, 418, 400};
    constexpr float spinnerY[4]{194, 194, 212, 212};
    for (int index = 0; index < 4; ++index) {
        const BYTE shade = static_cast<BYTE>(214.0f * (1.0f - spinnerAlpha_[index]));
        Gdiplus::SolidBrush square(Gdiplus::Color(255, shade, shade, shade));
        graphics.FillRectangle(&square, spinnerX[index], spinnerY[index], 12.0f, 12.0f);
    }

    drawText(graphics, L"Follow the prompts in your browser", 300, 272, 224, 40, 13,
        Gdiplus::Color(255, 20, 20, 20), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Reopen", 363, 306, 50, 25, 13,
        pointerIn(363, 306, 50, 25) ? Gdiplus::Color(255, 44, 111, 207)
                                    : Gdiplus::Color(255, 76, 140, 231),
        true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Cancel", 420, 306, 50, 25, 13,
        pointerIn(420, 306, 50, 25) ? Gdiplus::Color(255, 178, 38, 40)
                                    : Gdiplus::Color(255, 209, 51, 53),
        true, Gdiplus::StringAlignmentCenter);
    const auto status = model_.status();
    if (!status.empty()) drawText(graphics, status, 300, 340, 224, 24, 11,
        Gdiplus::Color(255, 175, 75, 75), false, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawMinecraftSelection(Gdiplus::Graphics& graphics) {
    const auto processes = model_.minecraftProcesses();
    if (processes.empty()) {
        drawLogo(graphics, 182.0f - 80.0f * logoPosition_);
        drawText(graphics, L"Minecraft not found", 295, 234, 234, 24, 13,
            Gdiplus::Color(255, 218, 215, 219), true, Gdiplus::StringAlignmentCenter);
        drawText(graphics, L"Please open Minecraft first", 295, 251, 234, 22, 12,
            Gdiplus::Color(255, 116, 113, 117), false, Gdiplus::StringAlignmentCenter);
        return;
    }
    drawLogo(graphics, 182.0f - 80.0f * logoPosition_);
    drawText(graphics, L"Select the Minecraft to use", 220, 150, 384, 38, 18,
        Gdiplus::Color(255, 218, 215, 219), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Make sure the game is fully loaded", 220, 179, 384, 22, 12,
        Gdiplus::Color(255, 116, 113, 117), false, Gdiplus::StringAlignmentCenter);
    float y = 210.0f;
    for (const auto& process : processes) {
        const bool hovered = !process.alreadyInjected && pointerIn(252, y, 320, 48);
        drawRoundedRect(graphics, 252, y, 320, 48, 4,
            process.alreadyInjected ? Gdiplus::Color(255, 28, 27, 28)
                : hovered ? Gdiplus::Color(255, 38, 37, 38) : Gdiplus::Color(255, 31, 30, 31),
            Gdiplus::Color(255, 43, 42, 43));
        
        
        const float titleWidth = 560.0f - 268.0f;
        drawText(graphics, ellipsize(graphics, process.title, 13.0f, titleWidth),
            268, y + 4, titleWidth, 22, 13, Gdiplus::Color(255, 202, 199, 203));
        drawText(graphics, (process.alreadyInjected ? L"injected [" : L"PID ") +
            std::to_wstring(process.pid) + (process.alreadyInjected ? L"]" : L""),
            268, y + 24, titleWidth, 18, 11,
            Gdiplus::Color(255, 105, 102, 106));
        y += 58.0f;
        if (y > 410.0f) break;
    }
}

void ControllerUi::drawLoading(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179.0f - 80.0f * logoPosition_);
    const float trackX = 292.0f;
    const float trackY = 265.0f;
    const float trackWidth = 240.0f;
    drawRoundedRect(graphics, trackX, trackY, trackWidth, 6, 3,
        Gdiplus::Color(255, 31, 32, 32));
    drawRoundedRect(graphics, trackX, trackY,
        std::max(6.0f, trackWidth * std::clamp(loadingProgress_, 0.0f, 1.0f)), 6, 3,
        Gdiplus::Color(255, 5, 139, 111));

    const double stageElapsed = model_.stageElapsedSeconds();
    if (stageElapsed >= 5.0) {
        drawText(graphics, L"Stage " + std::to_wstring(model_.loadingStage()) + L"/30",
            300, 286, 224, 24, 12, Gdiplus::Color(255, 139, 136, 140), false,
            Gdiplus::StringAlignmentCenter);
    }
    if (stageElapsed >= 10.0) {
        drawText(graphics,
            L"This stage is taking unusually long\nContact support\nNote: on 26+ versions, inject after opening a world",
            180, 310, 464, 60, 12, Gdiplus::Color(255, 176, 73, 73), false,
            Gdiplus::StringAlignmentCenter);
    }
}

void ControllerUi::drawCachePrompt(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179.0f - 80.0f * logoPosition_);
    drawText(graphics, L"Cache local files to speed up loading?",
        180, 232, 464, 48, 15, Gdiplus::Color(255, 210, 207, 211), true,
        Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Files will be stored at", 250, 245, 324, 22, 12,
        Gdiplus::Color(255, 112, 109, 113), false, Gdiplus::StringAlignmentCenter);
    drawText(graphics, model_.cacheDirectory(), 150, 267, 524, 24, 12,
        Gdiplus::Color(255, 151, 148, 152), false, Gdiplus::StringAlignmentCenter);
    drawRoundedRect(graphics, 290, 330, 112, 36, 3,
        pointerIn(290, 330, 112, 36) ? Gdiplus::Color(255, 49, 130, 97)
                                     : Gdiplus::Color(255, 43, 112, 84));
    drawRoundedRect(graphics, 422, 330, 112, 36, 3,
        pointerIn(422, 330, 112, 36) ? Gdiplus::Color(255, 65, 64, 65)
                                     : Gdiplus::Color(255, 52, 51, 52));
    drawText(graphics, L"Yes", 290, 330, 112, 36, 13, Gdiplus::Color(255, 224, 228, 225), true,
        Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"No", 422, 330, 112, 36, 13, Gdiplus::Color(255, 174, 171, 175), true,
        Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawLoadingComplete(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179.0f - 80.0f * logoPosition_);
    drawText(graphics, L"Vape load complete", 220, 232, 384, 30, 13,
        Gdiplus::Color(255, 218, 215, 219), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Press Right Shift (default) in-game to open the menu", 140, 254, 544, 24,
        12, Gdiplus::Color(255, 116, 113, 117), false, Gdiplus::StringAlignmentCenter);
    drawRoundedRect(graphics, 356, 348, 112, 36, 3,
        pointerIn(356, 348, 112, 36) ? Gdiplus::Color(255, 65, 64, 65)
                                     : Gdiplus::Color(255, 52, 51, 52));
    drawText(graphics, L"Close window", 356, 348, 112, 36, 13,
        Gdiplus::Color(255, 174, 171, 175), true, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawOutdated(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179);
    const Gdiplus::Color red(255, 204, 51, 51);
    drawText(graphics, L"Launcher version too old", 240, 253, 320, 24, 13,
        red, true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, L"Please re-download from the official site", 240, 270, 320, 24, 13,
        red, true, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawError(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179);
    drawText(graphics, model_.status().empty() ? L"Load error. Stage 0" : model_.status(),
        250, 235, 324, 48, 13, Gdiplus::Color(255, 203, 200, 204), true,
        Gdiplus::StringAlignmentCenter);
    drawRoundedRect(graphics, 356, 300, 112, 36, 3, Gdiplus::Color(255, 51, 51, 51));
    drawText(graphics, L"Copy error", 356, 300, 112, 36, 13,
        Gdiplus::Color(255, 174, 171, 175), true, Gdiplus::StringAlignmentCenter);
}

std::vector<ControllerUi::SettingsSection> ControllerUi::settingsSections() const {
    const LoaderSettings s = model_.loaderSettings();
    std::vector<SettingsSection> sections;

    sections.push_back({L"GENERAL", {
        {L"Auto-inject on launch", L"Automatically loads when Lunar Client starts",
            s.autoInjectEnabled, static_cast<int>(Focus::SettingsAutoInject), false},
    }});

    sections.push_back({L"UNLOCKS", {
        {L"Cosmetics", nullptr, s.unlockCosmetics, static_cast<int>(Focus::SettingsCosmetics), false},
        {L"Badges", nullptr, s.unlockBadges, static_cast<int>(Focus::SettingsBadges), false},
        {L"Emotes", nullptr, s.unlockEmotes, static_cast<int>(Focus::SettingsEmotes), false},
        {L"Sprays", nullptr, s.unlockSprays, static_cast<int>(Focus::SettingsSprays), false},
        {L"Jams", nullptr, s.unlockJams, static_cast<int>(Focus::SettingsJams), false},
    }});

    sections.push_back({L"APPEARANCE", {
        {L"Lunar+ Appearance", nullptr, s.lunarPlusAppearance, static_cast<int>(Focus::SettingsLunarPlus), false},
        {L"Language", nullptr, true, static_cast<int>(Focus::SettingsLanguage), false},
    }});

    sections.push_back({L"ADVANCED", {
        {L"Debug logging", L"Technical. Only enable when troubleshooting",
            s.debugLogging, static_cast<int>(Focus::SettingsDebug), true},
    }});

    auto& lang = sections[2].items[1];
    lang.description = L"";
    return sections;
}

void ControllerUi::cycleLanguage() {
    LoaderSettings s = model_.loaderSettings();
    static const wchar_t* langs[] = {L"English", L"Chinese", L"Spanish",
        L"Portuguese", L"French"};
    int idx = 0;
    for (int i = 0; i < 5; ++i) if (s.language == langs[i]) { idx = i; break; }
    s.language = langs[(idx + 1) % 5];
    model_.applyLoaderSettings(s);
    InvalidateRect(window_, nullptr, FALSE);
}

void ControllerUi::toggleSettingsItem(Focus focus) {
    LoaderSettings s = model_.loaderSettings();
    switch (focus) {
    case Focus::SettingsAutoInject: s.autoInjectEnabled = !s.autoInjectEnabled; break;
    case Focus::SettingsCosmetics: s.unlockCosmetics = !s.unlockCosmetics; break;
    case Focus::SettingsBadges: s.unlockBadges = !s.unlockBadges; break;
    case Focus::SettingsEmotes: s.unlockEmotes = !s.unlockEmotes; break;
    case Focus::SettingsSprays: s.unlockSprays = !s.unlockSprays; break;
    case Focus::SettingsJams: s.unlockJams = !s.unlockJams; break;
    case Focus::SettingsLunarPlus: s.lunarPlusAppearance = !s.lunarPlusAppearance; break;
    case Focus::SettingsDebug: s.debugLogging = !s.debugLogging; break;
    default: break;
    }
    model_.applyLoaderSettings(s);
    InvalidateRect(window_, nullptr, FALSE);
}

void ControllerUi::drawSwitch(Gdiplus::Graphics& graphics, float x, float y, float on) {
    constexpr float w = 44.0f, h = 24.0f, r = 12.0f;
    const Gdiplus::Color track = lerpColor(
        Gdiplus::Color(255, 43, 42, 43), Gdiplus::Color(255, 5, 139, 111), on);
    drawRoundedRect(graphics, x, y, w, h, r, track);
    const float thumb = 18.0f;
    const float minX = x + 3.0f;
    const float maxX = x + w - thumb - 3.0f;
    const float tx = minX + (maxX - minX) * on;
    const float ty = y + (h - thumb) / 2.0f;
    Gdiplus::SolidBrush knob(Gdiplus::Color(255, 244, 244, 245));
    graphics.FillEllipse(&knob, tx, ty, thumb, thumb);
}

void ControllerUi::drawSelect(Gdiplus::Graphics& graphics, float x, float y, float w,
                              float h, const std::wstring& text, float hover) {
    const Gdiplus::Color fill = lerpColor(
        Gdiplus::Color(255, 31, 30, 31), Gdiplus::Color(255, 38, 37, 38), hover);
    drawRoundedRect(graphics, x, y, w, h, 8, fill,
        Gdiplus::Color(255, 43, 42, 43));
    drawText(graphics, text, x + 12, y, w - 28, h, 13, Gdiplus::Color(255, 244, 244, 245),
        false, Gdiplus::StringAlignmentNear);
    Gdiplus::SolidBrush chev(Gdiplus::Color(255, 116, 113, 117));
    Gdiplus::PointF pts[3]{
        {x + w - 16, y + h / 2 - 3},
        {x + w - 9, y + h / 2 + 3},
        {x + w - 2, y + h / 2 - 3},
    };
    graphics.FillPolygon(&chev, pts, 3);
}

void ControllerUi::drawGearButton(Gdiplus::Graphics& graphics) {
    const float hov = gearHover_;
    drawRoundedRect(graphics, GearX, GearY, GearSize, GearSize, 8,
        lerpColor(Gdiplus::Color(255, 31, 30, 31), Gdiplus::Color(255, 38, 37, 38), hov),
        Gdiplus::Color(255, 43, 42, 43));
    const float cx = GearX + GearSize / 2.0f;
    const float cy = GearY + GearSize / 2.0f;
    const float rOuter = 9.0f;
    const float rInner = 4.0f;
    Gdiplus::SolidBrush fill(lerpColor(
        Gdiplus::Color(255, 116, 113, 117), Gdiplus::Color(255, 5, 139, 111), hov));
    graphics.FillEllipse(&fill, cx - rOuter, cy - rOuter, rOuter * 2, rOuter * 2);
    Gdiplus::SolidBrush hole(Gdiplus::Color(255, 26, 25, 26));
    graphics.FillEllipse(&hole, cx - rInner, cy - rInner, rInner * 2, rInner * 2);
    Gdiplus::Pen teeth(lerpColor(
        Gdiplus::Color(255, 116, 113, 117), Gdiplus::Color(255, 5, 139, 111), hov), 2.0f);
    for (int i = 0; i < 8; ++i) {
        const float a = static_cast<float>(i) * 3.14159265f / 4.0f;
        const float x1 = cx + std::cos(a) * (rOuter + 1.0f);
        const float y1 = cy + std::sin(a) * (rOuter + 1.0f);
        const float x2 = cx + std::cos(a) * (rOuter + 5.0f);
        const float y2 = cy + std::sin(a) * (rOuter + 5.0f);
        graphics.DrawLine(&teeth, x1, y1, x2, y2);
    }
}

void ControllerUi::drawSettings(Gdiplus::Graphics& graphics) {
    const float open = settingsOpen_;
    if (open <= 0.001f) return;
    const float slide = (1.0f - open) * 24.0f;
    const auto withOpen = [&](const Gdiplus::Color& c) {
        return scaleAlpha(c, open);
    };

    const float backAlpha = backHover_;
    drawRoundedRect(graphics, 28.0f, 22.0f, 32.0f, 32.0f, 8,
        lerpColor(Gdiplus::Color(255, 31, 30, 31), Gdiplus::Color(255, 38, 37, 38), backAlpha),
        Gdiplus::Color(255, 43, 42, 43));
    Gdiplus::SolidBrush arrow(withOpen(Gdiplus::Color(255, 244, 244, 245)));
    Gdiplus::PointF pts[3]{{44, 30}, {36, 38}, {44, 46}};
    graphics.FillPolygon(&arrow, pts, 3);

    drawText(graphics, L"Settings", ContentX, 20, ContentW, 30, 26,
        withOpen(Gdiplus::Color(255, 218, 215, 219)), true, Gdiplus::StringAlignmentNear);

    constexpr float viewTop = HeaderBottom;
    constexpr float viewBottom = CanvasHeight - 8;
    const float viewH = viewBottom - viewTop;

    const std::vector<SettingsSection> sections = settingsSections();
    float contentH = 0.0f;
    for (const auto& section : sections) {
        contentH += 26;
        const int n = static_cast<int>(section.items.size());
        contentH += static_cast<float>(n) * RowH + CardPad * 2 + 18;
    }
    settingsMaxScroll_ = std::max(0.0f, contentH - viewH);
    if (settingsScroll_ > settingsMaxScroll_) settingsScroll_ = settingsMaxScroll_;
    if (settingsScroll_ < 0.0f) settingsScroll_ = 0.0f;

    Gdiplus::Region clip(Gdiplus::RectF(0, viewTop, CanvasWidth, viewH));
    graphics.SetClip(&clip);
    graphics.TranslateTransform(0.0f, -settingsScroll_ + slide);

    float y = viewTop;
    for (const auto& section : sections) {
        drawText(graphics, section.title, ContentX, y, ContentW, 20, 12,
            withOpen(Gdiplus::Color(255, 105, 102, 106)), true, Gdiplus::StringAlignmentNear);
        y += 26;

        const float cardX = ContentX;
        const float cardW = ContentW;
        const float cardY = y;
        const int n = static_cast<int>(section.items.size());
        const float cardH = static_cast<float>(n) * RowH + CardPad * 2;
        drawRoundedRect(graphics, cardX, cardY, cardW, cardH, 12,
            withOpen(Gdiplus::Color(255, 31, 30, 31)), Gdiplus::Color(255, 43, 42, 43));

        float ry = cardY + CardPad;
        for (const auto& item : section.items) {
            auto rh = rowHoverAnim_.find(item.focus);
            const float rhv = rh == rowHoverAnim_.end() ? 0.0f : rh->second;
            if (rhv > 0.001f) {
                drawRoundedRect(graphics, cardX + 6, ry + 2, cardW - 12, RowH - 4, 8,
                    lerpColor(withOpen(Gdiplus::Color(255, 31, 30, 31)),
                        Gdiplus::Color(255, 38, 37, 38), rhv));
            }
            const float labelX = cardX + CardPad;
            const float labelW = cardW - CardPad * 2 - 200.0f;
            const bool hasDesc = item.description != nullptr && item.description[0] != L'\0';
            if (hasDesc) {
                drawText(graphics, item.label, labelX, ry + 8, labelW, 20, 14,
                    withOpen(Gdiplus::Color(255, 218, 215, 219)), true,
                    Gdiplus::StringAlignmentNear);
                drawText(graphics, item.description, labelX, ry + 28, labelW, 16, 12,
                    withOpen(Gdiplus::Color(255, 105, 102, 106)), false,
                    Gdiplus::StringAlignmentNear);
            } else {
                drawText(graphics, item.label, labelX, ry, labelW, RowH, 14,
                    withOpen(Gdiplus::Color(255, 218, 215, 219)), true,
                    Gdiplus::StringAlignmentNear);
            }
            const float ctrlW = 140.0f;
            const float ctrlX = cardX + cardW - CardPad - ctrlW;
            const float ctrlY = ry + (RowH - 24.0f) / 2.0f;
            if (item.description != nullptr && item.description[0] == L'\0') {
                const float selY = ry + (RowH - 36.0f) / 2.0f;
                auto sh = rowHoverAnim_.find(item.focus);
                selectHoverTarget_ = std::max(selectHoverTarget_,
                    sh == rowHoverAnim_.end() ? 0.0f : sh->second);
                drawSelect(graphics, ctrlX, selY, ctrlW, 36.0f,
                    model_.loaderSettings().language, selectHover_);
            } else {
                auto sa = switchAnim_.find(item.focus);
                const float on = sa == switchAnim_.end() ? (item.value ? 1.0f : 0.0f)
                                                         : sa->second;
                drawSwitch(graphics, ctrlX + ctrlW - 44.0f, ctrlY, on);
            }
            
            const float cy = (mouseY_ + settingsScroll_ - slide);
            const bool isSelect = item.description != nullptr && item.description[0] == L'\0';
            if (isSelect) {
                if (hit(mouseX_, cy, ctrlX, ry + (RowH - 36.0f) / 2.0f, ctrlW, 36.0f))
                    rowHoverTarget_[item.focus] = 1.0f;
            } else {
                if (hit(mouseX_, cy, cardX, ry, cardW, RowH))
                    rowHoverTarget_[item.focus] = 1.0f;
            }
            ry += RowH;
        }
        y = cardY + cardH + 18;
    }

    graphics.ResetTransform();
    graphics.ResetClip();
}
