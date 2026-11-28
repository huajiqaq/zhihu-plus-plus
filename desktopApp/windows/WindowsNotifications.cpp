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
#include <appmodel.h>
#include <aclapi.h>
#include <sddl.h>
#include <bcrypt.h>
#include <NotificationActivationCallback.h>
#include <wrl.h>
#include <jni.h>
#include <winrt/Windows.ApplicationModel.h>
#include <winrt/Windows.ApplicationModel.Resources.Core.h>
#include <winrt/Windows.ApplicationModel.Resources.h>
#include <winrt/Windows.Data.Xml.Dom.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.UI.Notifications.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <filesystem>
#include <mutex>
#include <string>
#include <string_view>
#include <vector>
#include "package_config.h"

namespace fs = std::filesystem;
using namespace winrt::Windows::UI::Notifications;
using Microsoft::WRL::Make;
using Microsoft::WRL::RuntimeClass;
using Microsoft::WRL::RuntimeClassFlags;
using Microsoft::WRL::ClassicCom;

namespace {
// 此 CLSID 只属于本应用；清单必须使用同一值，不提供 ProgID、协议或自注册入口。
constexpr CLSID activatorClsid{0xa198161d, 0xc0d7, 0x4500, {0x87, 0xf0, 0xac, 0x2a, 0x79, 0x15, 0x70, 0xe1}};
constexpr std::wstring_view appId = L"Zhihu";
constexpr std::string_view bridgeClass = "com/github/zly2006/zhihu/notification/WindowsNotificationBridge";
constexpr std::string_view mainClass = "com/github/zly2006/zhihu/desktop/MainKt";

struct Handle {
    HANDLE value{};
    ~Handle() { if (value && value != INVALID_HANDLE_VALUE) CloseHandle(value); }
    void reset(HANDLE next = nullptr) { if (value && value != INVALID_HANDLE_VALUE) CloseHandle(value); value = next; }
};
struct LocalMemory {
    HLOCAL value{};
    explicit LocalMemory(HLOCAL memory) : value(memory) {}
    LocalMemory(const LocalMemory&) = delete;
    LocalMemory& operator=(const LocalMemory&) = delete;
    ~LocalMemory() { if (value) LocalFree(value); }
};
struct Apartment {
    HRESULT result{RoInitialize(RO_INIT_MULTITHREADED)};
    Apartment() { if (result != RPC_E_CHANGED_MODE) winrt::check_hresult(result); }
    ~Apartment() { if (SUCCEEDED(result)) RoUninitialize(); }
};

std::mutex gate;
std::condition_variable clicksReady;
std::deque<std::wstring> clicks;
bool accepting = false;
DWORD registration = 0;
Handle lease;
std::wstring aumid;
fs::path packageRoot;
fs::path storageRoot;
std::vector<BYTE> userToken;

void require(bool value) { if (!value) winrt::throw_hresult(E_ACCESSDENIED); }
PSID currentUser() { return reinterpret_cast<TOKEN_USER*>(userToken.data())->User.Sid; }
bool systemPrincipal(PSID sid) {
    PSID installer = nullptr;
    require(ConvertStringSidToSidW(L"S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464", &installer) != FALSE);
    LocalMemory memory{installer};
    return IsWellKnownSid(sid, WinLocalSystemSid) || IsWellKnownSid(sid, WinBuiltinAdministratorsSid) || EqualSid(sid, installer);
}
bool trusted(PSID sid) {
    return EqualSid(sid, currentUser()) || IsWellKnownSid(sid, WinLocalSystemSid) || IsWellKnownSid(sid, WinBuiltinAdministratorsSid);
}

constexpr bool validReceipt(std::wstring_view value) {
    if (value.size() != 32) return false;
    for (wchar_t c : value) if (!((c >= L'0' && c <= L'9') || (c >= L'a' && c <= L'f'))) return false;
    return true;
}
static_assert(validReceipt(L"0123456789abcdef0123456789abcdef"));
static_assert(!validReceipt(L"0123456789abcdef0123456789abcdeF"));
static_assert(!validReceipt(L"https://example.com/notification"));
static_assert(!validReceipt(L"0123456789abcdef0123456789abcdef0"));

void noReparse(const fs::path& path) {
    require(path.is_absolute() && path == path.lexically_normal());
    auto part = path.root_path();
    for (const auto& name : path.relative_path()) {
        part /= name;
        const DWORD attributes = GetFileAttributesW(part.c_str());
        require(attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_REPARSE_POINT));
    }
}

void verifyAcl(const fs::path& path, bool privateData, bool immutable = false) {
    noReparse(path);
    PSID owner = nullptr;
    PACL acl = nullptr;
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    winrt::check_win32(GetNamedSecurityInfoW(path.c_str(), SE_FILE_OBJECT,
        OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION, &owner, nullptr, &acl, nullptr, &descriptor));
    LocalMemory memory{descriptor};
    require(owner && acl && (privateData ? EqualSid(owner, currentUser()) : immutable ? systemPrincipal(owner) : trusted(owner)));
    for (DWORD i = 0; i < acl->AceCount; ++i) {
        void* raw = nullptr;
        require(GetAce(acl, i, &raw) != FALSE);
        const auto* header = static_cast<ACE_HEADER*>(raw);
        if (header->AceFlags & INHERIT_ONLY_ACE) continue;
        if (header->AceType == ACCESS_DENIED_ACE_TYPE || header->AceType == ACCESS_DENIED_OBJECT_ACE_TYPE ||
            header->AceType == ACCESS_DENIED_CALLBACK_ACE_TYPE || header->AceType == ACCESS_DENIED_CALLBACK_OBJECT_ACE_TYPE) continue;
        require(header->AceType == ACCESS_ALLOWED_ACE_TYPE || header->AceType == ACCESS_ALLOWED_OBJECT_ACE_TYPE ||
            header->AceType == ACCESS_ALLOWED_CALLBACK_ACE_TYPE || header->AceType == ACCESS_ALLOWED_CALLBACK_OBJECT_ACE_TYPE);
        const auto* allow = static_cast<ACCESS_ALLOWED_ACE*>(raw);
        const DWORD writes = FILE_WRITE_DATA | FILE_APPEND_DATA | FILE_WRITE_EA | FILE_WRITE_ATTRIBUTES |
            FILE_DELETE_CHILD | DELETE | WRITE_DAC | WRITE_OWNER | GENERIC_WRITE | GENERIC_ALL | MAXIMUM_ALLOWED;
        // 包目录可有条件只读 ACE；私有数据或任何写授权必须能直接按 SID 证明。
        if (allow->Mask && (privateData || (allow->Mask & writes))) {
            require(header->AceType == ACCESS_ALLOWED_ACE_TYPE);
            auto sid = const_cast<DWORD*>(&allow->SidStart);
            require(immutable ? systemPrincipal(sid) : trusted(sid));
        }
    }
}

LocalMemory privateDescriptor() {
    LPWSTR sid = nullptr;
    require(ConvertSidToStringSidW(currentUser(), &sid) != FALSE);
    LocalMemory sidMemory{sid};
    const std::wstring sddl = L"O:" + std::wstring(sid) + L"D:P(A;;GA;;;" + sid + L")(A;;GA;;;SY)(A;;GA;;;BA)";
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    require(ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.c_str(), SDDL_REVISION_1, &descriptor, nullptr) != FALSE);
    return LocalMemory{descriptor};
}

void prepareStorage() {
    auto cache = fs::path(winrt::Windows::Storage::ApplicationData::Current().LocalCacheFolder().Path().c_str());
    verifyAcl(cache, false);
    storageRoot = cache / L"zhihu-system-notifications";
    auto security = privateDescriptor();
    SECURITY_ATTRIBUTES attributes{sizeof(SECURITY_ATTRIBUTES), security.value, FALSE};
    if (!CreateDirectoryW(storageRoot.c_str(), &attributes)) require(GetLastError() == ERROR_ALREADY_EXISTS);
    verifyAcl(storageRoot, true);
}

void checkStorage(const fs::path& path, bool directory) {
    require(!storageRoot.empty() && (directory ? path == storageRoot : path.parent_path() == storageRoot));
    verifyAcl(storageRoot, true);
    verifyAcl(path, true);
    const DWORD attributes = GetFileAttributesW(path.c_str());
    require(((attributes & FILE_ATTRIBUTE_DIRECTORY) != 0) == directory);
    if (!directory) {
        Handle file{CreateFileW(path.c_str(), FILE_READ_ATTRIBUTES, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            nullptr, OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT, nullptr)};
        require(file.value != INVALID_HANDLE_VALUE);
        BY_HANDLE_FILE_INFORMATION information{};
        require(GetFileInformationByHandle(file.value, &information) && information.nNumberOfLinks == 1);
    }
}

void checkPackage() {
    Handle token;
    require(OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token.value) != FALSE);
    DWORD bytes = 0;
    GetTokenInformation(token.value, TokenUser, nullptr, 0, &bytes);
    require(bytes > 0);
    userToken.resize(bytes);
    require(GetTokenInformation(token.value, TokenUser, userToken.data(), bytes, &bytes) != FALSE);

    const auto package = winrt::Windows::ApplicationModel::Package::Current();
    require(!package.IsDevelopmentMode() && package.Status().VerifyIsOK());
    require(package.SignatureKind() != winrt::Windows::ApplicationModel::PackageSignatureKind::None);
    require(package.Id().Name() == PackageName && package.Id().Publisher() == PackagePublisher);
    packageRoot = fs::path(package.InstalledLocation().Path().c_str());
    std::array<wchar_t, 32768> module{};
    const auto length = GetModuleFileNameW(nullptr, module.data(), static_cast<DWORD>(module.size()));
    require(length > 0 && length < module.size());
    require(fs::path(module.data()) == packageRoot / L"ZhihuNotifications.exe");
    verifyAcl(packageRoot, false, true);
    verifyAcl(packageRoot / L"ZhihuNotifications.exe", false, true);
    UINT32 count = 0;
    require(GetCurrentApplicationUserModelId(&count, nullptr) == ERROR_INSUFFICIENT_BUFFER);
    std::wstring current(count, L'\0');
    winrt::check_win32(GetCurrentApplicationUserModelId(&count, current.data()));
    current.resize(count - 1);
    aumid = std::wstring(package.Id().FamilyName()) + L"!" + std::wstring(appId);
    require(current == aumid);
}

class Callback final : public RuntimeClass<RuntimeClassFlags<ClassicCom>, INotificationActivationCallback> {
    HRESULT STDMETHODCALLTYPE Activate(LPCWSTR source, LPCWSTR arguments, const NOTIFICATION_USER_INPUT_DATA*, ULONG count) noexcept override {
        try {
            require(source && arguments && count == 0);
            require(wcsnlen_s(source, aumid.size() + 1) == aumid.size() && aumid == source);
            require(wcsnlen_s(arguments, 33) == 32 && validReceipt(std::wstring_view(arguments, 32)));
            std::lock_guard lock(gate);
            require(accepting && clicks.size() < 64);
            if (std::find(clicks.begin(), clicks.end(), arguments) == clicks.end()) clicks.emplace_back(arguments);
            clicksReady.notify_all();
            return S_OK;
        } catch (...) { return winrt::to_hresult(); }
    }
};

class Factory final : public RuntimeClass<RuntimeClassFlags<ClassicCom>, IClassFactory> {
    HRESULT STDMETHODCALLTYPE CreateInstance(IUnknown* outer, REFIID iid, void** object) noexcept override {
        if (!object) return E_POINTER;
        *object = nullptr;
        if (outer) return CLASS_E_NOAGGREGATION;
        auto callback = Make<Callback>();
        return callback ? callback.CopyTo(iid, object) : E_OUTOFMEMORY;
    }
    HRESULT STDMETHODCALLTYPE LockServer(BOOL) noexcept override { return S_OK; }
};

void connect() {
    auto security = privateDescriptor();
    SECURITY_ATTRIBUTES attributes{sizeof(SECURITY_ATTRIBUTES), security.value, FALSE};
    LPWSTR sid = nullptr;
    require(ConvertSidToStringSidW(currentUser(), &sid) != FALSE);
    LocalMemory memory{sid};
    const std::wstring name = L"Global\\ZhihuPlusPlus-Notifications-" + std::wstring(sid) + L"-" + aumid;
    lease.value = CreateMutexW(&attributes, FALSE, name.c_str());
    require(lease.value != nullptr);
    if (GetLastError() == ERROR_ALREADY_EXISTS) { lease.reset(); return; }
    prepareStorage();
    auto factory = Make<Factory>();
    require(factory != nullptr);
    std::lock_guard lock(gate);
    accepting = true;
    winrt::check_hresult(CoRegisterClassObject(activatorClsid, factory.Get(), CLSCTX_LOCAL_SERVER, REGCLS_MULTIPLEUSE, &registration));
}

void disconnect() {
    DWORD cookie;
    {
        std::lock_guard lock(gate);
        accepting = false;
        cookie = registration;
        registration = 0;
        clicks.clear();
        clicksReady.notify_all();
    }
    if (cookie) CoRevokeClassObject(cookie);
    lease.reset();
}

std::wstring fromJava(JNIEnv* env, jstring value) {
    require(value != nullptr);
    const jsize size = env->GetStringLength(value);
    require(size >= 0 && size <= 32767);
    const jchar* text = env->GetStringChars(value, nullptr);
    if (!text) winrt::throw_hresult(E_OUTOFMEMORY);
    std::wstring result(reinterpret_cast<const wchar_t*>(text), static_cast<size_t>(size));
    env->ReleaseStringChars(value, text);
    require(result.find(L'\0') == std::wstring::npos);
    return result;
}
jstring toJava(JNIEnv* env, const std::wstring& value) {
    return env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size()));
}
void fail(JNIEnv* env) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Windows notification bridge unavailable");
}
void requireConnected() { std::lock_guard lock(gate); require(accepting && registration != 0); }

jboolean JNICALL connected(JNIEnv*, jobject) { std::lock_guard lock(gate); return accepting && registration != 0; }
jstring JNICALL storageDirectory(JNIEnv* env, jobject) {
    try { requireConnected(); checkStorage(storageRoot, true); return toJava(env, storageRoot.wstring()); }
    catch (...) { fail(env); return nullptr; }
}
void JNICALL verifyStorage(JNIEnv* env, jobject, jstring path, jboolean directory) {
    try { requireConnected(); checkStorage(fs::path(fromJava(env, path)), directory != JNI_FALSE); }
    catch (...) { fail(env); }
}
jstring JNICALL newReceipt(JNIEnv* env, jobject) {
    try {
        requireConnected();
        std::array<BYTE, 16> bytes{};
        require(BCryptGenRandom(nullptr, bytes.data(), static_cast<ULONG>(bytes.size()), BCRYPT_USE_SYSTEM_PREFERRED_RNG) == 0);
        constexpr wchar_t digits[] = L"0123456789abcdef";
        std::wstring receipt;
        for (BYTE byte : bytes) { receipt += digits[byte >> 4]; receipt += digits[byte & 15]; }
        return toJava(env, receipt);
    } catch (...) { fail(env); return nullptr; }
}
jboolean JNICALL permissionGranted(JNIEnv* env, jobject) {
    try { Apartment apartment; requireConnected(); return ToastNotificationManager::CreateToastNotifier(aumid).Setting() == NotificationSetting::Enabled; }
    catch (...) { fail(env); return JNI_FALSE; }
}
jstring JNICALL takeReceipt(JNIEnv* env, jobject) {
    try {
        std::unique_lock lock(gate);
        clicksReady.wait_for(lock, std::chrono::seconds(1), [] { return !accepting || !clicks.empty(); });
        if (!accepting || clicks.empty()) return nullptr;
        auto receipt = clicks.front();
        clicks.pop_front();
        return toJava(env, receipt);
    } catch (...) { fail(env); return nullptr; }
}
void JNICALL publish(JNIEnv* env, jobject, jstring value, jboolean chinese) {
    try {
        Apartment apartment;
        requireConnected();
        auto receipt = fromJava(env, value);
        require(validReceipt(receipt));
        auto notifier = ToastNotificationManager::CreateToastNotifier(aumid);
        require(notifier.Setting() == NotificationSetting::Enabled);
        using namespace winrt::Windows::ApplicationModel::Resources::Core;
        const auto context = ResourceContext::GetForViewIndependentUse().Clone();
        context.Languages({chinese == JNI_TRUE ? L"zh-CN" : L"en-US"});
        const auto strings = ResourceManager::Current().MainResourceMap().GetSubtree(L"Resources");
        winrt::Windows::Data::Xml::Dom::XmlDocument xml;
        xml.LoadXml(L"<toast activationType=\"foreground\" launch=\"" + receipt +
            L"\"><visual><binding template=\"ToastGeneric\"><text/><text/></binding></visual></toast>");
        const auto text = xml.GetElementsByTagName(L"text");
        text.Item(0).AppendChild(xml.CreateTextNode(strings.GetValue(L"AppName", context).ValueAsString()));
        text.Item(1).AppendChild(xml.CreateTextNode(strings.GetValue(L"NewMessage", context).ValueAsString()));
        ToastNotification toast(xml);
        // 保守采用 Tag / Group 各 16 字符；两段无损表示同一随机回执，不用业务 key。
        toast.Tag(receipt.substr(0, 16));
        toast.Group(receipt.substr(16, 16));
        notifier.Show(toast);
    } catch (...) { fail(env); }
}
void JNICALL withdraw(JNIEnv* env, jobject, jstring value) {
    try {
        Apartment apartment;
        auto receipt = fromJava(env, value);
        require(validReceipt(receipt) && !aumid.empty());
        ToastNotificationManager::History().Remove(receipt.substr(0, 16), receipt.substr(16, 16), aumid);
    } catch (...) { fail(env); }
}
void JNICALL closeBridge(JNIEnv* env, jobject) {
    try { Apartment apartment; disconnect(); }
    catch (...) { fail(env); }
}

std::string utf8(const std::wstring& value) {
    if (value.empty()) return {};
    const auto size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
    require(size > 0);
    std::string result(static_cast<size_t>(size), '\0');
    require(WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()), result.data(), size, nullptr, nullptr) == size);
    return result;
}

int runApplication() {
    const auto image = packageRoot / L"app-image";
    const auto runtimeBin = image / L"runtime" / L"bin";
    const auto jvm = runtimeBin / L"server" / L"jvm.dll";
    verifyAcl(image, false, true);
    verifyAcl(image / L"app", false, true);
    verifyAcl(image / L"runtime", false, true);
    verifyAcl(runtimeBin, false, true);
    verifyAcl(runtimeBin / L"server", false, true);
    verifyAcl(jvm, false, true);
    require(SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32 | LOAD_LIBRARY_SEARCH_USER_DIRS) != FALSE);
    const auto dllDirectory = AddDllDirectory(runtimeBin.c_str());
    require(dllDirectory != nullptr);
    const auto module = LoadLibraryExW(jvm.c_str(), nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_SYSTEM32 | LOAD_LIBRARY_SEARCH_USER_DIRS);
    require(module != nullptr);
    const auto createVm = reinterpret_cast<jint(JNICALL*)(JavaVM**, void**, void*)>(GetProcAddress(module, "JNI_CreateJavaVM"));
    require(createVm != nullptr);

    std::wstring classpath;
    for (const auto* jar : ApplicationJars) {
        const auto path = image / L"app" / jar;
        verifyAcl(path, false, true);
        require(fs::is_regular_file(path));
        if (!classpath.empty()) classpath += L';';
        classpath += path.wstring();
    }
    // 仅固定本项目需要的选项；不读取环境、命令行或可写配置中的 JVM 参数。
    const auto appDirectory = image / L"app";
    verifyAcl(appDirectory / L"skiko-windows-x64.dll", false, true);
    std::vector<std::string> options{
        "-Djava.class.path=" + utf8(classpath),
        "-Dcompose.application.resources.dir=" + utf8((appDirectory / L"resources").wstring()),
        "-Dskiko.library.path=" + utf8(appDirectory.wstring()),
        "-Djpackage.app-path=" + utf8((packageRoot / L"ZhihuNotifications.exe").wstring()),
        "-Djpackage.app-version=" + utf8(ApplicationVersion),
    };
    for (const auto* option : ApplicationOptions) options.emplace_back(option);
    std::vector<JavaVMOption> nativeOptions;
    for (auto& option : options) nativeOptions.push_back(JavaVMOption{option.data(), nullptr});
    JavaVMInitArgs arguments{};
    arguments.version = JNI_VERSION_10;
    arguments.nOptions = static_cast<jint>(nativeOptions.size());
    arguments.options = nativeOptions.data();
    arguments.ignoreUnrecognized = JNI_FALSE;
    JavaVM* vm = nullptr;
    JNIEnv* env = nullptr;
    for (const auto* variable : {L"JAVA_TOOL_OPTIONS", L"_JAVA_OPTIONS", L"JDK_JAVA_OPTIONS"}) {
        if (!SetEnvironmentVariableW(variable, nullptr)) require(GetLastError() == ERROR_ENVVAR_NOT_FOUND);
    }
    require(createVm(&vm, reinterpret_cast<void**>(&env), &arguments) == JNI_OK);

    JNINativeMethod methods[]{
        {const_cast<char*>("connected"), const_cast<char*>("()Z"), reinterpret_cast<void*>(connected)},
        {const_cast<char*>("storageDirectory"), const_cast<char*>("()Ljava/lang/String;"), reinterpret_cast<void*>(storageDirectory)},
        {const_cast<char*>("verifyStorage"), const_cast<char*>("(Ljava/lang/String;Z)V"), reinterpret_cast<void*>(verifyStorage)},
        {const_cast<char*>("newReceipt"), const_cast<char*>("()Ljava/lang/String;"), reinterpret_cast<void*>(newReceipt)},
        {const_cast<char*>("permissionGranted"), const_cast<char*>("()Z"), reinterpret_cast<void*>(permissionGranted)},
        {const_cast<char*>("takeReceipt"), const_cast<char*>("()Ljava/lang/String;"), reinterpret_cast<void*>(takeReceipt)},
        {const_cast<char*>("publish"), const_cast<char*>("(Ljava/lang/String;Z)V"), reinterpret_cast<void*>(publish)},
        {const_cast<char*>("withdraw"), const_cast<char*>("(Ljava/lang/String;)V"), reinterpret_cast<void*>(withdraw)},
        {const_cast<char*>("disconnect"), const_cast<char*>("()V"), reinterpret_cast<void*>(closeBridge)},
    };
    const auto bridge = env->FindClass(bridgeClass.data());
    if (!bridge || env->RegisterNatives(bridge, methods, static_cast<jint>(std::size(methods))) != JNI_OK) {
        env->ExceptionClear();
        disconnect();
    }
    const auto main = env->FindClass(mainClass.data());
    const auto method = main ? env->GetStaticMethodID(main, "main", "()V") : nullptr;
    if (method) env->CallStaticVoidMethod(main, method);
    const bool failed = env->ExceptionCheck();
    if (failed) env->ExceptionDescribe();
    disconnect();
    vm->DestroyJavaVM();
    RemoveDllDirectory(dllDirectory);
    // HotSpot 及 AWT 可能仍持有本机线程，不能提前卸载 jvm.dll。
    return failed ? 1 : 0;
}
} // namespace

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR command, int) {
    try {
        Apartment apartment;
        const std::wstring_view mode(command ? command : L"");
        const bool notification = mode == L"-ToastActivated" || mode == L"-ToastActivated -Embedding";
        require(mode.empty() || notification);
        const auto security = CoInitializeSecurity(nullptr, -1, nullptr, nullptr, RPC_C_AUTHN_LEVEL_PKT_PRIVACY,
            RPC_C_IMP_LEVEL_IDENTIFY, nullptr, EOAC_DISABLE_AAA, nullptr);
        checkPackage();
        try { winrt::check_hresult(security); connect(); } catch (...) { disconnect(); }
        if (notification) {
            std::unique_lock lock(gate);
            if (!accepting || !clicksReady.wait_for(lock, std::chrono::seconds(45), [] { return !clicks.empty(); })) return 0;
        }
        return runApplication();
    } catch (...) {
        try {
            Apartment apartment;
            const auto strings = winrt::Windows::ApplicationModel::Resources::ResourceLoader::GetForViewIndependentUse();
            MessageBoxW(nullptr, strings.GetString(L"LaunchFailure").c_str(), strings.GetString(L"AppName").c_str(), MB_OK | MB_ICONERROR);
        } catch (...) {
            // 包资源本身也可能损坏；不要为错误提示加载外部文件。
            MessageBoxW(nullptr, L"Could not start Zhihu++. / 无法启动知乎++。", L"Zhihu++", MB_OK | MB_ICONERROR);
        }
        return 1;
    }
}
