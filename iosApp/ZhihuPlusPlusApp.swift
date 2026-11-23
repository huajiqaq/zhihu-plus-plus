// Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
// Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
// SPDX-License-Identifier: AGPL-3.0-only

import OSLog
import Shared
import SwiftUI
import UIKit

@main
struct ZhihuPlusPlusApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeRoot().ignoresSafeArea(.all)
        }
    }
}

private final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        if !IosZhihuMainKt.installIosSystemNotifications() {
            Logger().error("System notification delegate unavailable")
        }
        return true
    }
}

private struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosZhihuMainKt.makeIosViewController()
    }

    // SwiftUI has no state to push into the shared navigation or its controller.
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
