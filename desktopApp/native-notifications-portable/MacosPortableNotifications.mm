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
#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <UserNotifications/UserNotifications.h>
#include <jni.h>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>

namespace {
std::mutex gate;
NSURL* stateDirectory = nil;
UNUserNotificationCenter* center = nil;
int lease = -1;
std::atomic_bool connected{false};
constexpr size_t maxStateBytes = 16 * 1024 * 1024;

void require(bool value) { if (!value) throw std::runtime_error("Native notification operation failed"); }
bool validReceipt(NSString* receipt) {
    if (receipt.length != 32) return false;
    for (NSUInteger i = 0; i < receipt.length; ++i) {
        unichar c = [receipt characterAtIndex:i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
    }
    return true;
}
void mainSync(dispatch_block_t block) {
    if (NSThread.isMainThread) block(); else dispatch_sync(dispatch_get_main_queue(), block);
}
void fail(JNIEnv* env) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native notification operation failed");
}
NSString* fromJava(JNIEnv* env, jstring value) {
    require(value != nullptr);
    auto length = env->GetStringLength(value);
    auto chars = env->GetStringChars(value, nullptr);
    require(chars != nullptr);
    NSString* result = [[NSString alloc] initWithCharacters:reinterpret_cast<const unichar*>(chars) length:length];
    env->ReleaseStringChars(value, chars);
    require([result rangeOfString:[NSString stringWithFormat:@"%C", (unichar)0]].location == NSNotFound);
    return result;
}
jstring toJava(JNIEnv* env, NSString* value) {
    std::u16string text(value.length, 0);
    [value getCharacters:reinterpret_cast<unichar*>(text.data()) range:NSMakeRange(0, value.length)];
    return env->NewString(reinterpret_cast<const jchar*>(text.data()), static_cast<jsize>(text.size()));
}
void verify(NSURL* url, bool directory) {
    struct stat info{};
    require(lstat(url.fileSystemRepresentation, &info) == 0 && info.st_uid == getuid());
    require(directory ? S_ISDIR(info.st_mode) : S_ISREG(info.st_mode) && info.st_nlink == 1);
    require((info.st_mode & 0777) == (directory ? 0700 : 0600));
}
bool exists(NSURL* url) {
    struct stat info{};
    if (lstat(url.fileSystemRepresentation, &info) == 0) return true;
    require(errno == ENOENT);
    return false;
}
void writeBytes(NSURL* url, NSData* data) {
    verify(stateDirectory, true);
    int fd = open(url.fileSystemRepresentation, O_CREAT | O_EXCL | O_WRONLY | O_NOFOLLOW | O_CLOEXEC, 0600);
    require(fd >= 0);
    bool ok = true;
    auto bytes = static_cast<const char*>(data.bytes);
    size_t remaining = data.length;
    while (remaining) {
        auto count = write(fd, bytes, remaining);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { ok = false; break; }
        bytes += count;
        remaining -= static_cast<size_t>(count);
    }
    if (ok) ok = fsync(fd) == 0;
    if (close(fd) != 0) ok = false;
    require(ok);
}
void queueClick(NSString* receipt) {
    std::lock_guard guard(gate);
    if (!stateDirectory || !validReceipt(receipt)) return;
    NSURL* path = [stateDirectory URLByAppendingPathComponent:[receipt stringByAppendingString:@".click"]];
    if (exists(path)) verify(path, false); else writeBytes(path, NSData.data);
}
void prepareStorage() {
    NSFileManager* files = NSFileManager.defaultManager;
    NSURL* cache = [files URLForDirectory:NSCachesDirectory inDomain:NSUserDomainMask appropriateForURL:nil create:YES error:nil];
    require(cache != nil);
    NSString* bundle = NSBundle.mainBundle.bundleIdentifier;
    require(bundle.length > 0 && [bundle rangeOfString:@"/"].location == NSNotFound);
    stateDirectory = [cache URLByAppendingPathComponent:[bundle stringByAppendingString:@".jvm-system-notifications"] isDirectory:YES];
    if (!exists(stateDirectory)) {
        require([files createDirectoryAtURL:stateDirectory withIntermediateDirectories:NO attributes:@{NSFilePosixPermissions: @0700} error:nil]);
    }
    verify(stateDirectory, true);
    require([[stateDirectory URLByResolvingSymlinksInPath].path isEqualToString:stateDirectory.path]);
    require([stateDirectory setResourceValue:@YES forKey:NSURLIsExcludedFromBackupKey error:nil]);
    NSURL* lock = [stateDirectory URLByAppendingPathComponent:@"owner.lock"];
    if (exists(lock)) verify(lock, false);
    lease = open(lock.fileSystemRepresentation, O_CREAT | O_RDWR | O_NOFOLLOW | O_CLOEXEC, 0600);
    require(lease >= 0 && flock(lease, LOCK_EX | LOCK_NB) == 0);
}
}

@interface ZhihuJvmNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end
@implementation ZhihuJvmNotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter*)notificationCenter willPresentNotification:(UNNotification*)notification
        withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completion {
    (void)notificationCenter;
    completion(notification.request.trigger == nil && validReceipt(notification.request.identifier)
        ? UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList | UNNotificationPresentationOptionSound : 0);
}
- (void)userNotificationCenter:(UNUserNotificationCenter*)notificationCenter didReceiveNotificationResponse:(UNNotificationResponse*)response
        withCompletionHandler:(void (^)(void))completion {
    (void)notificationCenter;
    try {
        if ([response.actionIdentifier isEqualToString:UNNotificationDefaultActionIdentifier] && response.notification.request.trigger == nil) {
            queueClick(response.notification.request.identifier);
        }
    } catch (...) { /* 未持久化时不向共用运行时伪报已收到点击。 */ }
    completion();
}
@end
static ZhihuJvmNotificationDelegate* notificationDelegate = nil;

#define JNI_METHOD(name) Java_com_github_zly2006_zhihu_notification_PortableNotificationBridge_##name
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(connect)(JNIEnv*, jobject) {
    @autoreleasepool {
        std::unique_lock guard(gate);
        try {
            if (connected) return true;
            NSBundle* bundle = NSBundle.mainBundle;
            require([bundle.bundlePath.pathExtension.lowercaseString isEqualToString:@"app"] && bundle.bundleIdentifier.length > 0 &&
                [[bundle objectForInfoDictionaryKey:@"CFBundlePackageType"] isEqual:@"APPL"]);
            prepareStorage();
            // 不持有存储锁等主线程，早到的通知回调也需要这把锁。
            guard.unlock();
            __block bool installed = false;
            mainSync(^{
                center = UNUserNotificationCenter.currentNotificationCenter;
                if (!notificationDelegate) notificationDelegate = [ZhihuJvmNotificationDelegate new];
                if (center.delegate == nil || center.delegate == notificationDelegate) {
                    center.delegate = notificationDelegate;
                    installed = true;
                }
            });
            guard.lock();
            require(installed);
            connected = true;
            return true;
        } catch (...) {
            if (!guard.owns_lock()) guard.lock();
            if (lease >= 0) { close(lease); lease = -1; }
            return false;
        }
    }
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(authorization)(JNIEnv*, jobject) {
    @autoreleasepool {
        if (!connected || NSThread.isMainThread) return -1;
        auto finished = dispatch_semaphore_create(0);
        auto value = std::make_shared<std::atomic_int>(-1);
        mainSync(^{
            if (center.delegate != notificationDelegate) { dispatch_semaphore_signal(finished); return; }
            [center getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings* settings) {
                const bool granted = settings.authorizationStatus == UNAuthorizationStatusAuthorized || settings.authorizationStatus == UNAuthorizationStatusProvisional;
                value->store(granted && (settings.alertSetting == UNNotificationSettingEnabled || settings.notificationCenterSetting == UNNotificationSettingEnabled) ? 1 : 0);
                dispatch_semaphore_signal(finished);
            }];
        });
        return dispatch_semaphore_wait(finished, dispatch_time(DISPATCH_TIME_NOW, 30 * NSEC_PER_SEC)) == 0 ? value->load() : -1;
    }
}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(requestAuthorization)(JNIEnv*, jobject) {
    @autoreleasepool {
        if (!connected || NSThread.isMainThread) return false;
        auto finished = dispatch_semaphore_create(0);
        auto granted = std::make_shared<std::atomic_bool>(false);
        mainSync(^{
            [center requestAuthorizationWithOptions:UNAuthorizationOptionAlert | UNAuthorizationOptionSound completionHandler:^(BOOL accepted, NSError* error) {
                granted->store(accepted && error == nil);
                dispatch_semaphore_signal(finished);
            }];
        });
        return dispatch_semaphore_wait(finished, dispatch_time(DISPATCH_TIME_NOW, 60 * NSEC_PER_SEC)) == 0 && granted->load();
    }
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(loadState)(JNIEnv* env, jobject) {
    @autoreleasepool {
        std::lock_guard guard(gate);
        try {
            require(connected);
            NSURL* path = [stateDirectory URLByAppendingPathComponent:@"state.json"];
            if (!exists(path)) return nullptr;
            verify(path, false);
            NSData* data = [NSData dataWithContentsOfURL:path options:NSDataReadingUncached error:nil];
            require(data && data.length <= maxStateBytes);
            NSString* value = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            require(value != nil);
            return toJava(env, value);
        } catch (...) { fail(env); return nullptr; }
    }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(saveState)(JNIEnv* env, jobject, jstring state) {
    @autoreleasepool {
        std::lock_guard guard(gate);
        try {
            require(connected);
            NSData* data = [fromJava(env, state) dataUsingEncoding:NSUTF8StringEncoding];
            require(data && data.length <= maxStateBytes);
            NSURL* pending = [stateDirectory URLByAppendingPathComponent:@"state.pending"];
            NSURL* target = [stateDirectory URLByAppendingPathComponent:@"state.json"];
            if (exists(pending)) { verify(pending, false); require(unlink(pending.fileSystemRepresentation) == 0); }
            if (exists(target)) verify(target, false);
            writeBytes(pending, data);
            require([pending setResourceValue:@YES forKey:NSURLIsExcludedFromBackupKey error:nil]);
            require(rename(pending.fileSystemRepresentation, target.fileSystemRepresentation) == 0);
        } catch (...) { fail(env); }
    }
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(takeReceipt)(JNIEnv* env, jobject) {
    @autoreleasepool {
        std::lock_guard guard(gate);
        try {
            require(connected);
            verify(stateDirectory, true);
            NSArray<NSURL*>* entries = [NSFileManager.defaultManager contentsOfDirectoryAtURL:stateDirectory includingPropertiesForKeys:nil options:0 error:nil];
            require(entries != nil);
            for (NSURL* entry in entries) {
                NSString* receipt = entry.lastPathComponent.stringByDeletingPathExtension;
                if ([entry.pathExtension isEqualToString:@"click"] && validReceipt(receipt)) { verify(entry, false); return toJava(env, receipt); }
            }
            return nullptr;
        } catch (...) { fail(env); return nullptr; }
    }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(confirmReceipt)(JNIEnv* env, jobject, jstring receipt) {
    @autoreleasepool {
        std::lock_guard guard(gate);
        try {
            require(connected);
            NSString* value = fromJava(env, receipt);
            require(validReceipt(value));
            NSURL* path = [stateDirectory URLByAppendingPathComponent:[value stringByAppendingString:@".click"]];
            if (exists(path)) { verify(path, false); require(unlink(path.fileSystemRepresentation) == 0); }
        } catch (...) { fail(env); }
    }
}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(publish)(JNIEnv* env, jobject, jstring receipt, jstring title, jstring body) {
    @autoreleasepool {
        try {
            require(connected && !NSThread.isMainThread);
            NSString* value = fromJava(env, receipt);
            require(validReceipt(value));
            UNMutableNotificationContent* content = [UNMutableNotificationContent new];
            content.title = fromJava(env, title);
            content.body = fromJava(env, body);
            content.sound = UNNotificationSound.defaultSound;
            auto finished = dispatch_semaphore_create(0);
            auto outcome = std::make_shared<std::atomic_int>(0);
            mainSync(^{
                [center addNotificationRequest:[UNNotificationRequest requestWithIdentifier:value content:content trigger:nil] withCompletionHandler:^(NSError* error) {
                    int waiting = 0;
                    if (!outcome->compare_exchange_strong(waiting, error == nil ? 1 : 2)) {
                        // 超时之后才完成的原生投递仍精确撤回，不能在退出或换号后残留。
                        [center removePendingNotificationRequestsWithIdentifiers:@[value]];
                        [center removeDeliveredNotificationsWithIdentifiers:@[value]];
                    }
                    dispatch_semaphore_signal(finished);
                }];
            });
            if (dispatch_semaphore_wait(finished, dispatch_time(DISPATCH_TIME_NOW, 25 * NSEC_PER_SEC)) != 0) {
                outcome->store(3);
                mainSync(^{
                    [center removePendingNotificationRequestsWithIdentifiers:@[value]];
                    [center removeDeliveredNotificationsWithIdentifiers:@[value]];
                });
                return false;
            }
            return outcome->load() == 1;
        } catch (...) { fail(env); return false; }
    }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(withdraw)(JNIEnv* env, jobject, jstring receipt) {
    @autoreleasepool {
        try {
            require(center != nil);
            NSString* value = fromJava(env, receipt);
            require(validReceipt(value));
            mainSync(^{
                [center removePendingNotificationRequestsWithIdentifiers:@[value]];
                [center removeDeliveredNotificationsWithIdentifiers:@[value]];
            });
        } catch (...) { fail(env); }
    }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(disconnect)(JNIEnv*, jobject) {
    std::lock_guard guard(gate);
    connected = false;
    if (lease >= 0) { close(lease); lease = -1; }
}
