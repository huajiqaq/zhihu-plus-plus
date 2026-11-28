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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
#include <windows.h>
#include <aclapi.h>
#include <sddl.h>
#include <shlobj.h>
#include <NotificationActivationCallback.h>
#include <wrl.h>
#include <jni.h>
#include <winrt/Windows.Data.Xml.Dom.h>
#include <winrt/Windows.UI.Notifications.h>
#include <array>
#include <filesystem>
#include <mutex>
#include <string>
#include <string_view>
#include <vector>

namespace fs = std::filesystem;
using namespace winrt::Windows::UI::Notifications;
using Microsoft::WRL::ClassicCom;
using Microsoft::WRL::Make;
using Microsoft::WRL::RuntimeClass;
using Microsoft::WRL::RuntimeClassFlags;

namespace {
constexpr wchar_t appId[] = L"com.github.zly2006.zhihu.desktop";
constexpr wchar_t classIdText[] = L"{B7243AF7-29EA-49D5-8AF2-66AA06C78D55}";
constexpr CLSID classId{0xb7243af7, 0x29ea, 0x49d5, {0x8a, 0xf2, 0x66, 0xaa, 0x06, 0xc7, 0x8d, 0x55}};
constexpr size_t maxStateBytes = 16 * 1024 * 1024;
std::mutex gate;
fs::path directory;
fs::path helper;
fs::path application;
std::vector<BYTE> tokenData;
HANDLE ownerLock = INVALID_HANDLE_VALUE;
bool connected = false;
#ifdef ZHIHU_NOTIFICATION_ACTIVATOR
HANDLE activated = nullptr;
#endif

struct Handle {
    HANDLE value = INVALID_HANDLE_VALUE;
    explicit Handle(HANDLE handle) : value(handle) {}
    ~Handle() { if (value != INVALID_HANDLE_VALUE && value) CloseHandle(value); }
};
struct Apartment {
    HRESULT status = RoInitialize(RO_INIT_MULTITHREADED);
    Apartment() { if (status != RPC_E_CHANGED_MODE) winrt::check_hresult(status); }
    ~Apartment() { if (SUCCEEDED(status)) RoUninitialize(); }
};
struct LocalMemory {
    HLOCAL value;
    explicit LocalMemory(HLOCAL memory) : value(memory) {}
    ~LocalMemory() { if (value) LocalFree(value); }
};
void require(bool ok) { if (!ok) winrt::throw_hresult(E_ACCESSDENIED); }
PSID userSid() { return reinterpret_cast<TOKEN_USER*>(tokenData.data())->User.Sid; }
bool trustedSid(PSID sid) {
    return EqualSid(sid, userSid()) || IsWellKnownSid(sid, WinLocalSystemSid) || IsWellKnownSid(sid, WinBuiltinAdministratorsSid);
}
constexpr bool validReceipt(std::wstring_view receipt) {
    if (receipt.size() != 32) return false;
    for (auto c : receipt) if (!((c >= L'0' && c <= L'9') || (c >= L'a' && c <= L'f'))) return false;
    return true;
}
static_assert(validReceipt(L"0123456789abcdef0123456789abcdef"));
static_assert(!validReceipt(L"https://example.com/notification"));
static_assert(!validReceipt(L"0123456789abcdef0123456789abcdeF"));

void noReparse(const fs::path& path) {
    require(path.is_absolute() && path == path.lexically_normal());
    auto part = path.root_path();
    for (const auto& item : path.relative_path()) {
        part /= item;
        auto attributes = GetFileAttributesW(part.c_str());
        require(attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_REPARSE_POINT));
    }
}

void verifyPrivate(const fs::path& path, bool isDirectory) {
    noReparse(path);
    auto attributes = GetFileAttributesW(path.c_str());
    require(((attributes & FILE_ATTRIBUTE_DIRECTORY) != 0) == isDirectory);
    PSID owner = nullptr;
    PACL acl = nullptr;
    PSECURITY_DESCRIPTOR security = nullptr;
    winrt::check_win32(GetNamedSecurityInfoW(path.c_str(), SE_FILE_OBJECT, OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
        &owner, nullptr, &acl, nullptr, &security));
    LocalMemory memory{security};
    require(owner && EqualSid(owner, userSid()) && acl);
    for (DWORD i = 0; i < acl->AceCount; ++i) {
        void* raw = nullptr;
        require(GetAce(acl, i, &raw) != FALSE);
        const auto* header = static_cast<ACE_HEADER*>(raw);
        if (header->AceFlags & INHERIT_ONLY_ACE) continue;
        if (header->AceType == ACCESS_DENIED_ACE_TYPE) continue;
        require(header->AceType == ACCESS_ALLOWED_ACE_TYPE);
        const auto* allow = static_cast<ACCESS_ALLOWED_ACE*>(raw);
        if (allow->Mask) require(trustedSid(const_cast<DWORD*>(&allow->SidStart)));
    }
    if (!isDirectory) {
        Handle file{CreateFileW(path.c_str(), FILE_READ_ATTRIBUTES, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            nullptr, OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT, nullptr)};
        require(file.value != INVALID_HANDLE_VALUE);
        BY_HANDLE_FILE_INFORMATION info{};
        require(GetFileInformationByHandle(file.value, &info) && info.nNumberOfLinks == 1);
    }
}

PSECURITY_DESCRIPTOR privateSecurity() {
    LPWSTR sid = nullptr;
    require(ConvertSidToStringSidW(userSid(), &sid) != FALSE);
    LocalMemory sidMemory{sid};
    auto sddl = L"O:" + std::wstring(sid) + L"D:P(A;;GA;;;" + sid + L")(A;;GA;;;SY)(A;;GA;;;BA)";
    PSECURITY_DESCRIPTOR security = nullptr;
    require(ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.c_str(), SDDL_REVISION_1, &security, nullptr) != FALSE);
    return security;
}

std::wstring registryText(const std::wstring& key, const wchar_t* value) {
    DWORD bytes = 0;
    winrt::check_win32(RegGetValueW(HKEY_CURRENT_USER, key.c_str(), value, RRF_RT_REG_SZ, nullptr, nullptr, &bytes));
    require(bytes > sizeof(wchar_t) && bytes < 65536);
    std::wstring result(bytes / sizeof(wchar_t), L'\0');
    winrt::check_win32(RegGetValueW(HKEY_CURRENT_USER, key.c_str(), value, RRF_RT_REG_SZ, nullptr, result.data(), &bytes));
    result.resize(wcsnlen(result.c_str(), result.size()));
    return result;
}

void initializePaths() {
    Handle token{nullptr};
    require(OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token.value) != FALSE);
    TOKEN_ELEVATION elevated{};
    DWORD bytes = 0;
    require(GetTokenInformation(token.value, TokenElevation, &elevated, sizeof(elevated), &bytes) && !elevated.TokenIsElevated);
    GetTokenInformation(token.value, TokenUser, nullptr, 0, &bytes);
    require(bytes > 0);
    tokenData.resize(bytes);
    require(GetTokenInformation(token.value, TokenUser, tokenData.data(), bytes, &bytes) != FALSE);

    HMODULE module = nullptr;
    require(GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        reinterpret_cast<LPCWSTR>(&initializePaths), &module) != FALSE);
    std::array<wchar_t, 32768> modulePath{};
    const auto length = GetModuleFileNameW(module, modulePath.data(), static_cast<DWORD>(modulePath.size()));
    require(length > 0 && length < modulePath.size());
    const auto libraryDirectory = fs::path(modulePath.data()).parent_path();
    require(libraryDirectory.filename() == L"native-notifications");
    helper = libraryDirectory / L"ZhihuNotificationActivator.exe";
    application = libraryDirectory.parent_path().parent_path().parent_path() / L"Zhihu++.exe";
    noReparse(helper);
    noReparse(application);
    const auto registration = registryText(L"Software\\Classes\\CLSID\\" + std::wstring(classIdText) + L"\\LocalServer32", nullptr);
    require(registration == L"\"" + helper.wstring() + L"\"");
    require(registryText(L"Software\\Classes\\AppUserModelId\\" + std::wstring(appId), L"CustomActivator") == classIdText);

    PWSTR localAppData = nullptr;
    winrt::check_hresult(SHGetKnownFolderPath(FOLDERID_LocalAppData, KF_FLAG_DEFAULT, nullptr, &localAppData));
    const auto parent = fs::path(localAppData);
    CoTaskMemFree(localAppData);
    noReparse(parent);
    directory = parent / L"ZhihuPlusPlus-SystemNotifications";
    LocalMemory security{privateSecurity()};
    SECURITY_ATTRIBUTES attributes{sizeof(attributes), security.value, FALSE};
    if (!CreateDirectoryW(directory.c_str(), &attributes)) require(GetLastError() == ERROR_ALREADY_EXISTS);
    verifyPrivate(directory, true);
}

void saveBytes(const fs::path& path, const void* bytes, DWORD length, bool replace) {
    verifyPrivate(directory, true);
    LocalMemory security{privateSecurity()};
    SECURITY_ATTRIBUTES attributes{sizeof(attributes), security.value, FALSE};
    Handle file{CreateFileW(path.c_str(), GENERIC_WRITE, 0, &attributes, replace ? CREATE_ALWAYS : CREATE_NEW,
        FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT, nullptr)};
    if (!replace && file.value == INVALID_HANDLE_VALUE && GetLastError() == ERROR_FILE_EXISTS) {
        verifyPrivate(path, false);
        return;
    }
    require(file.value != INVALID_HANDLE_VALUE);
    DWORD written = 0;
    require(WriteFile(file.value, bytes, length, &written, nullptr) && written == length && FlushFileBuffers(file.value));
}

std::wstring fromJava(JNIEnv* env, jstring value) {
    require(value != nullptr);
    const auto length = env->GetStringLength(value);
    const auto* text = env->GetStringChars(value, nullptr);
    require(text != nullptr);
    std::wstring result(reinterpret_cast<const wchar_t*>(text), length);
    env->ReleaseStringChars(value, text);
    require(result.find(L'\0') == std::wstring::npos);
    return result;
}
jstring toJava(JNIEnv* env, const std::wstring& text) {
    return env->NewString(reinterpret_cast<const jchar*>(text.data()), static_cast<jsize>(text.size()));
}
void fail(JNIEnv* env) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native notification operation failed");
}
ToastNotifier notifier() { return ToastNotificationManager::CreateToastNotifier(appId); }
std::wstring xmlText(const std::wstring& text) {
    std::wstring output;
    for (auto c : text) {
        switch (c) {
            case L'&': output += L"&amp;"; break;
            case L'<': output += L"&lt;"; break;
            case L'>': output += L"&gt;"; break;
            case L'\"': output += L"&quot;"; break;
            default: require(c >= 0x20 || c == L'\n' || c == L'\t'); output += c;
        }
    }
    return output;
}
}

#ifndef ZHIHU_NOTIFICATION_ACTIVATOR
#define JNI_METHOD(name) Java_com_github_zly2006_zhihu_notification_PortableNotificationBridge_##name
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(connect)(JNIEnv*, jobject) {
    std::lock_guard guard(gate);
    try {
        if (connected) return true;
        initializePaths();
        const auto lockPath = directory / L"owner.lock";
        if (fs::exists(lockPath)) verifyPrivate(lockPath, false);
        LocalMemory security{privateSecurity()};
        SECURITY_ATTRIBUTES attributes{sizeof(attributes), security.value, FALSE};
        ownerLock = CreateFileW(lockPath.c_str(), GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ,
            &attributes, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT, nullptr);
        require(ownerLock != INVALID_HANDLE_VALUE);
        Apartment apartment;
        notifier();
        connected = true;
        return true;
    } catch (...) {
        if (ownerLock != INVALID_HANDLE_VALUE) { CloseHandle(ownerLock); ownerLock = INVALID_HANDLE_VALUE; }
        return false;
    }
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(authorization)(JNIEnv*, jobject) {
    std::lock_guard guard(gate);
    try { require(connected); Apartment apartment; return notifier().Setting() == NotificationSetting::Enabled ? 1 : 0; }
    catch (...) { return -1; }
}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(requestAuthorization)(JNIEnv* env, jobject self) {
    return JNI_METHOD(authorization)(env, self) == 1;
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(loadState)(JNIEnv* env, jobject) {
    std::lock_guard guard(gate);
    try {
        require(connected);
        const auto filePath = directory / L"state.json";
        if (!fs::exists(filePath)) return nullptr;
        verifyPrivate(filePath, false);
        Handle file{CreateFileW(filePath.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT, nullptr)};
        require(file.value != INVALID_HANDLE_VALUE);
        LARGE_INTEGER size{};
        require(GetFileSizeEx(file.value, &size) && size.QuadPart > 0 && size.QuadPart <= maxStateBytes && size.QuadPart % sizeof(wchar_t) == 0);
        std::wstring text(static_cast<size_t>(size.QuadPart) / sizeof(wchar_t), L'\0');
        DWORD read = 0;
        require(ReadFile(file.value, text.data(), static_cast<DWORD>(size.QuadPart), &read, nullptr) && read == size.QuadPart);
        return toJava(env, text);
    } catch (...) { fail(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(saveState)(JNIEnv* env, jobject, jstring state) {
    std::lock_guard guard(gate);
    try {
        require(connected);
        const auto text = fromJava(env, state);
        require(text.size() * sizeof(wchar_t) <= maxStateBytes);
        const auto pending = directory / L"state.pending";
        if (fs::exists(pending)) { verifyPrivate(pending, false); require(DeleteFileW(pending.c_str()) != FALSE); }
        const auto target = directory / L"state.json";
        if (fs::exists(target)) verifyPrivate(target, false);
        saveBytes(pending, text.data(), static_cast<DWORD>(text.size() * sizeof(wchar_t)), false);
        require(MoveFileExW(pending.c_str(), target.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH) != FALSE);
    } catch (...) { fail(env); }
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(takeReceipt)(JNIEnv* env, jobject) {
    std::lock_guard guard(gate);
    try {
        require(connected);
        verifyPrivate(directory, true);
        for (const auto& item : fs::directory_iterator(directory)) {
            const auto path = item.path();
            if (path.extension() == L".click" && validReceipt(path.stem().wstring())) {
                verifyPrivate(path, false);
                return toJava(env, path.stem().wstring());
            }
        }
        return nullptr;
    } catch (...) { fail(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(confirmReceipt)(JNIEnv* env, jobject, jstring receipt) {
    std::lock_guard guard(gate);
    try {
        require(connected);
        const auto value = fromJava(env, receipt);
        require(validReceipt(value));
        const auto path = directory / (value + L".click");
        if (fs::exists(path)) { verifyPrivate(path, false); require(DeleteFileW(path.c_str()) != FALSE); }
    } catch (...) { fail(env); }
}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(publish)(JNIEnv* env, jobject, jstring receipt, jstring title, jstring body) {
    std::lock_guard guard(gate);
    try {
        require(connected);
        const auto token = fromJava(env, receipt);
        require(validReceipt(token));
        Apartment apartment;
        winrt::Windows::Data::Xml::Dom::XmlDocument document;
        document.LoadXml(L"<toast launch=\"" + token + L"\"><visual><binding template=\"ToastGeneric\"><text>" +
            xmlText(fromJava(env, title)) + L"</text><text>" + xmlText(fromJava(env, body)) + L"</text></binding></visual></toast>");
        ToastNotification notification(document);
        // Windows tag/group 各限16字符，二者合起来保持完整随机回执身份。
        notification.Tag(token.substr(0, 16));
        notification.Group(token.substr(16));
        auto service = notifier();
        require(service.Setting() == NotificationSetting::Enabled);
        service.Show(notification);
        return true;
    } catch (...) { fail(env); return false; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(withdraw)(JNIEnv* env, jobject, jstring receipt) {
    std::lock_guard guard(gate);
    try {
        require(!directory.empty());
        const auto value = fromJava(env, receipt);
        require(validReceipt(value));
        Apartment apartment;
        ToastNotificationManager::History().Remove(value.substr(0, 16), value.substr(16), appId);
    } catch (...) { fail(env); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(disconnect)(JNIEnv*, jobject) {
    std::lock_guard guard(gate);
    connected = false;
    if (ownerLock != INVALID_HANDLE_VALUE) { CloseHandle(ownerLock); ownerLock = INVALID_HANDLE_VALUE; }
}
#else
namespace {
class Activator final : public RuntimeClass<RuntimeClassFlags<ClassicCom>, INotificationActivationCallback> {
    HRESULT STDMETHODCALLTYPE Activate(LPCWSTR applicationId, LPCWSTR invokedArgs, const NOTIFICATION_USER_INPUT_DATA*, ULONG inputCount) override {
        try {
            require(applicationId && invokedArgs && std::wstring_view(applicationId) == appId && inputCount == 0 && validReceipt(invokedArgs));
            std::lock_guard guard(gate);
            saveBytes(directory / (std::wstring(invokedArgs) + L".click"), "", 0, false);
            // 活进程通过回执轮询唤醒原窗口；冷启动只执行安装器登记目录下固定的程序。
            Handle lock{CreateFileW((directory / L"owner.lock").c_str(), GENERIC_READ | GENERIC_WRITE, 0,
                nullptr, OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT, nullptr)};
            const auto error = lock.value == INVALID_HANDLE_VALUE ? GetLastError() : ERROR_SUCCESS;
            if (error != ERROR_SHARING_VIOLATION) {
                require(error == ERROR_SUCCESS || error == ERROR_FILE_NOT_FOUND);
                if (lock.value != INVALID_HANDLE_VALUE) { CloseHandle(lock.value); lock.value = INVALID_HANDLE_VALUE; }
                auto command = L"\"" + application.wstring() + L"\"";
                STARTUPINFOW startup{sizeof(startup)};
                PROCESS_INFORMATION process{};
                require(CreateProcessW(application.c_str(), command.data(), nullptr, nullptr, FALSE, 0, nullptr,
                    application.parent_path().c_str(), &startup, &process) != FALSE);
                CloseHandle(process.hProcess);
                CloseHandle(process.hThread);
            }
            SetEvent(activated);
            return S_OK;
        } catch (...) { SetEvent(activated); return E_ACCESSDENIED; }
    }
};
class Factory final : public RuntimeClass<RuntimeClassFlags<ClassicCom>, IClassFactory> {
    HRESULT STDMETHODCALLTYPE CreateInstance(IUnknown* outer, REFIID iid, void** output) override {
        if (outer) return CLASS_E_NOAGGREGATION;
        return Make<Activator>()->QueryInterface(iid, output);
    }
    HRESULT STDMETHODCALLTYPE LockServer(BOOL) override { return S_OK; }
};
}
int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR arguments, int) {
    try {
        const std::wstring_view mode(arguments ? arguments : L"");
        require(mode == L"-Embedding" || mode == L"/Embedding" || mode.empty());
        Apartment apartment;
        initializePaths();
        Handle completion{CreateEventW(nullptr, TRUE, FALSE, nullptr)};
        require(completion.value != nullptr);
        activated = completion.value;
        DWORD registration = 0;
        auto factory = Make<Factory>();
        winrt::check_hresult(CoRegisterClassObject(classId, factory.Get(), CLSCTX_LOCAL_SERVER, REGCLS_MULTIPLEUSE, &registration));
        const auto wait = WaitForSingleObject(activated, 30000);
        CoRevokeClassObject(registration);
        return wait == WAIT_OBJECT_0 ? 0 : 1;
    } catch (...) { return 1; }
}
#endif
