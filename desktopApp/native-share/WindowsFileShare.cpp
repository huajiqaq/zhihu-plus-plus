/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <windows.h>
#include <shobjidl.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#include <winrt/Windows.ApplicationModel.DataTransfer.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Foundation.Metadata.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.Storage.Streams.h>
#include <atomic>
#include <chrono>
#include <future>
#include <memory>
#include <string>

using namespace winrt::Windows::ApplicationModel::DataTransfer;
using namespace winrt::Windows::Storage;

namespace {
struct Apartment {
    explicit Apartment(winrt::apartment_type type) { winrt::init_apartment(type); }
    ~Apartment() { winrt::uninit_apartment(); }
};

std::wstring fromJava(JNIEnv* env, jstring value) {
    if (!value) winrt::throw_hresult(E_INVALIDARG);
    const jchar* text = env->GetStringChars(value, nullptr);
    if (!text) winrt::throw_hresult(E_OUTOFMEMORY);
    std::wstring result(reinterpret_cast<const wchar_t*>(text), env->GetStringLength(value));
    env->ReleaseStringChars(value, text);
    if (result.empty() || result.find(L'\0') != std::wstring::npos) winrt::throw_hresult(E_INVALIDARG);
    return result;
}

HWND windowHandle(JNIEnv* env, jobject window) {
    JAWT awt{};
    awt.version = JAWT_VERSION_1_4;
    if (!JAWT_GetAWT(env, &awt)) winrt::throw_hresult(E_NOINTERFACE);
    JAWT_DrawingSurface* surface = awt.GetDrawingSurface(env, window);
    if (!surface) winrt::throw_hresult(E_HANDLE);
    const jint lock = surface->Lock(surface);
    if ((lock & JAWT_LOCK_ERROR) != 0) {
        awt.FreeDrawingSurface(surface);
        winrt::throw_hresult(E_HANDLE);
    }
    JAWT_DrawingSurfaceInfo* info = surface->GetDrawingSurfaceInfo(surface);
    HWND hwnd = nullptr;
    if (info) {
        auto* platform = static_cast<JAWT_Win32DrawingSurfaceInfo*>(info->platformInfo);
        if (platform) hwnd = GetAncestor(platform->hwnd, GA_ROOT);
        surface->FreeDrawingSurfaceInfo(info);
    }
    surface->Unlock(surface);
    awt.FreeDrawingSurface(surface);
    DWORD process = 0;
    if (!IsWindow(hwnd) || !GetWindowThreadProcessId(hwnd, &process) || process != GetCurrentProcessId()) {
        winrt::throw_hresult(E_HANDLE);
    }
    return hwnd;
}

struct SharingState {
    std::atomic<bool> supplied{false};
    std::atomic<bool> targetChosen{false};
    std::atomic<bool> cancelled{false};
    std::atomic<bool> completed{false};
    std::atomic<HRESULT> failure{S_OK};
};

struct Sharing {
    DataTransferManager manager{nullptr};
    winrt::event_token requested{};
    winrt::event_token chosen{};
    bool requestedSubscribed = false;
    bool chosenSubscribed = false;
    ~Sharing() {
        if (requestedSubscribed) {
            try { manager.DataRequested(requested); } catch (...) { }
        }
        if (chosenSubscribed) {
            try { manager.TargetApplicationChosen(chosen); } catch (...) { }
        }
    }
};
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_zly2006_zhihu_desktop_DesktopFileShareBridge_shareContent(
    JNIEnv* env, jobject, jobject window, jstring payload, jstring title, jboolean isFile, jobject cancelled) {
    try {
        const auto hwnd = windowHandle(env, window);
        const auto value = fromJava(env, payload);
        const auto shareTitle = fromJava(env, title);
        jclass flagClass = env->GetObjectClass(cancelled);
        if (!flagClass) return JNI_FALSE;
        const auto getCancelled = env->GetMethodID(flagClass, "get", "()Z");
        env->DeleteLocalRef(flagClass);
        if (!getCancelled) return JNI_FALSE;
        auto isCancelled = [&]() {
            return env->CallBooleanMethod(cancelled, getCancelled) == JNI_TRUE || env->ExceptionCheck();
        };
        if (isCancelled()) return JNI_FALSE;

        // StorageFile 的同步等待仅在 MTA 执行；文本不写临时文件，直接交付 SetText。
        winrt::agile_ref<StorageFile> fileReference;
        if (isFile == JNI_TRUE) {
            auto pendingFile = std::async(std::launch::async, [value]() {
                Apartment apartment(winrt::apartment_type::multi_threaded);
                return winrt::agile_ref<StorageFile>(StorageFile::GetFileFromPathAsync(value).get());
            });
            fileReference = pendingFile.get();
        }
        if (isCancelled()) return JNI_FALSE;
        Apartment apartment(winrt::apartment_type::single_threaded);
        if (!DataTransferManager::IsSupported()) winrt::throw_hresult(E_NOTIMPL);
        auto interop = winrt::get_activation_factory<DataTransferManager, IDataTransferManagerInterop>();
        Sharing sharing;
        const auto state = std::make_shared<SharingState>();
        winrt::check_hresult(interop->GetForWindow(hwnd, winrt::guid_of<DataTransferManager>(), winrt::put_abi(sharing.manager)));
        const StorageFile file = isFile == JNI_TRUE ? fileReference.get() : nullptr;
        const bool cancelEventAvailable = winrt::Windows::Foundation::Metadata::ApiInformation::IsEventPresent(
            L"Windows.ApplicationModel.DataTransfer.DataPackage", L"ShareCanceled");
        sharing.chosen = sharing.manager.TargetApplicationChosen([state](auto const&, auto const&) {
            state->targetChosen = true;
        });
        sharing.chosenSubscribed = true;
        sharing.requested = sharing.manager.DataRequested([state, file, value, shareTitle, cancelEventAvailable](auto const&, DataRequestedEventArgs const& args) {
            try {
                auto data = args.Request().Data();
                data.Properties().Title(shareTitle);
                if (file) data.SetStorageItems(winrt::multi_threaded_vector<IStorageItem>({file}), true);
                else data.SetText(value);
                data.ShareCompleted([state](auto const&, auto const&) { state->completed = true; });
                if (cancelEventAvailable) {
                    data.ShareCanceled([state](auto const&, auto const&) { state->cancelled = true; });
                }
                state->supplied = true;
            } catch (...) {
                state->failure = winrt::to_hresult();
                // 不向 Java 回报已交付空包。
                try { args.Request().FailWithDisplayText(L"The content could not be shared."); } catch (...) { }
            }
        });
        sharing.requestedSubscribed = true;
        winrt::check_hresult(interop->ShowShareUIForWindow(hwnd));
        const auto deadline = std::chrono::steady_clock::now() + std::chrono::minutes(5);
        while (!state->targetChosen && !state->completed && !state->cancelled && SUCCEEDED(state->failure.load())) {
            if (isCancelled() || !IsWindow(hwnd)) return JNI_FALSE;
            // 旧系统不保证发送关闭事件；已交付时到期只撤销桥接，未交付则报告超时。
            if (std::chrono::steady_clock::now() >= deadline) {
                if (!state->supplied) winrt::throw_hresult(HRESULT_FROM_WIN32(ERROR_TIMEOUT));
                return JNI_FALSE;
            }
            MSG message{};
            while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
                if (message.message == WM_QUIT) return JNI_FALSE;
                TranslateMessage(&message);
                DispatchMessageW(&message);
            }
            MsgWaitForMultipleObjectsEx(0, nullptr, 50, QS_ALLINPUT, MWMO_INPUTAVAILABLE);
        }
        winrt::check_hresult(state->failure.load());
        // 用户选择了系统接收方；这不是接收方发送成功的确认。
        return state->supplied && !state->cancelled ? JNI_TRUE : JNI_FALSE;
    } catch (const winrt::hresult_error& error) {
        if (!env->ExceptionCheck()) {
            const auto text = winrt::to_string(error.message());
            env->ThrowNew(env->FindClass("java/io/IOException"), text.c_str());
        }
    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
    } catch (...) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), "System file sharing failed");
    }
    return JNI_FALSE;
}
