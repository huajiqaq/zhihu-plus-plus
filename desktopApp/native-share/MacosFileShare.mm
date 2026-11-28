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

#import <AppKit/AppKit.h>
#import <dispatch/dispatch.h>
#include <jni.h>

@interface ZhihuFileShare : NSObject <NSSharingServicePickerDelegate>
@property(nonatomic, strong) NSSharingServicePicker *picker;
@property(nonatomic, strong) dispatch_semaphore_t completion;
@property(nonatomic) BOOL selected;
@end

// AppKit 的 delegate 是弱引用；只在系统选择面板存在期间保留它。
static ZhihuFileShare *activeSharing;

@implementation ZhihuFileShare
- (void)sharingServicePicker:(NSSharingServicePicker *)picker didChooseSharingService:(NSSharingService *)service {
    self.selected = service != nil;
    dispatch_semaphore_signal(self.completion);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (activeSharing == self) activeSharing = nil;
        self.picker.delegate = nil;
        self.picker = nil;
    });
}
@end

static NSString *fromJava(JNIEnv *env, jstring value) {
    if (!value) return nil;
    const jchar *characters = env->GetStringChars(value, nullptr);
    if (!characters) return nil;
    NSString *result = [[NSString alloc] initWithCharacters:reinterpret_cast<const unichar *>(characters)
                                                    length:env->GetStringLength(value)];
    env->ReleaseStringChars(value, characters);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_zly2006_zhihu_desktop_DesktopFileShareBridge_shareContent(
    JNIEnv *env, jobject, jobject, jstring payload, jstring, jboolean isFile, jobject cancelled) {
    @autoreleasepool {
        jclass flagClass = env->GetObjectClass(cancelled);
        if (!flagClass) return JNI_FALSE;
        jmethodID getCancelled = env->GetMethodID(flagClass, "get", "()Z");
        env->DeleteLocalRef(flagClass);
        if (!getCancelled || env->CallBooleanMethod(cancelled, getCancelled) == JNI_TRUE || env->ExceptionCheck()) return JNI_FALSE;
        NSString *value = fromJava(env, payload);
        if (env->ExceptionCheck()) return JNI_FALSE;
        if (NSThread.isMainThread) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "System sharing must be requested from a background worker.");
            return JNI_FALSE;
        }
        __block NSString *failure = nil;
        __block ZhihuFileShare *request = nil;
        void (^show)(void) = ^{
            @try {
                if (activeSharing) {
                    failure = @"Close the current sharing panel and try again.";
                    return;
                }
                NSWindow *window = NSApp.keyWindow ?: NSApp.mainWindow;
                NSView *view = window.contentView;
                if (!view || !window.visible || !value.length) {
                    failure = @"The content or application window is unavailable.";
                    return;
                }
                id item = value;
                if (isFile == JNI_TRUE) {
                    BOOL isDirectory = NO;
                    if (!value.isAbsolutePath || ![NSFileManager.defaultManager fileExistsAtPath:value isDirectory:&isDirectory] || isDirectory) {
                        failure = @"The shared file is unavailable.";
                        return;
                    }
                    item = [NSURL fileURLWithPath:value isDirectory:NO];
                }
                activeSharing = [ZhihuFileShare new];
                request = activeSharing;
                request.completion = dispatch_semaphore_create(0);
                activeSharing.picker = [[NSSharingServicePicker alloc] initWithItems:@[item]];
                activeSharing.picker.delegate = activeSharing;
                [activeSharing.picker showRelativeToRect:NSMakeRect(NSMidX(view.bounds), NSMidY(view.bounds), 1, 1)
                                                 ofView:view preferredEdge:NSMinYEdge];
            } @catch (NSException *exception) {
                activeSharing = nil;
                failure = exception.reason ?: @"System file sharing failed.";
            }
        };
        dispatch_sync(dispatch_get_main_queue(), show);
        if (failure) {
            env->ThrowNew(env->FindClass("java/io/IOException"), failure.UTF8String);
            return JNI_FALSE;
        }
        while (dispatch_semaphore_wait(request.completion, dispatch_time(DISPATCH_TIME_NOW, 50 * NSEC_PER_MSEC)) != 0) {
            if (env->CallBooleanMethod(cancelled, getCancelled) == JNI_TRUE || env->ExceptionCheck()) {
                // 不撤销已开始的接收方操作；尚在选择时让系统面板继续管理自身生命周期。
                return JNI_FALSE;
            }
        }
        return request.selected ? JNI_TRUE : JNI_FALSE;
    }
}
