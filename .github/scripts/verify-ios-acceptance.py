#!/usr/bin/env python3
# Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
# Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation (version 3 only).
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
"""Opt-in hosted-runner acceptance; the XCTest target never enters the production project."""

import argparse
import copy
import json
import os
import platform
import plistlib
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

TEST_TARGET = 'IOSAcceptanceTests'
BUNDLE_ID = 'com.github.zly2006.zhihu.ios'
SWIFT_TEST = r'''
import XCTest

final class IOSAcceptanceTests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "com.github.zly2006.zhihu.ios")

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func evidence(_ name: String, screenshot: Bool = false) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name + "-accessibility"
        tree.lifetime = .keepAlways
        add(tree)
        if screenshot {
            let image = XCTAttachment(screenshot: app.screenshot())
            image.name = name
            image.lifetime = .keepAlways
            add(image)
        }
    }

    private func node(_ tag: String) -> XCUIElement {
        let matches = app.descendants(matching: .any).matching(identifier: tag)
        let element = matches.firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 20), "Missing accessibility identifier: \(tag)")
        XCTAssertEqual(matches.count, 1, "Ambiguous accessibility identifier: \(tag)")
        return element
    }

    private func state(_ tag: String, _ value: String) {
        let element = node(tag)
        let predicate = NSPredicate { _, _ in
            element.exists && (element.label + " " + String(describing: element.value ?? "")).contains(value)
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: element)], timeout: 15), .completed,
                       "Missing accessibility state \(value) for \(tag)")
        evidence(tag + "-" + value)
    }

    private func tap(_ tag: String) {
        evidence("before-" + tag)
        let element = node(tag)
        let hittable = NSPredicate { _, _ in element.exists && element.isHittable }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: hittable, object: element)], timeout: 15), .completed,
                       "Semantic target is not hittable: \(tag)")
        element.tap()
    }

    func testGuestNavigation() {
        app.launch()
        defer {
            evidence("final", screenshot: true)
            app.terminate()
        }
        for index in 1...3 {
            let tag = "login_notice_step_\(index)"
            state(tag, "已收起")
            tap(tag)
            state(tag, "已展开")
            if index == 1 { evidence("notice-expanded", screenshot: true) }
            tap(tag)
            state(tag, "已收起")
        }
        let confirm = node("login_notice_confirm")
        XCTAssertFalse(confirm.isEnabled)
        tap("login_notice_agreement")
        let enabled = NSPredicate(format: "enabled == true")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: enabled, object: confirm)], timeout: 10), .completed)
        tap("login_notice_confirm")
        tap("login_guest")
        tap("home_account_button")
        tap("accountSettings.appearance")
        _ = node("appearance_settings_back_button")
        evidence("guest-appearance", screenshot: true)
        tap("appearance_settings_back_button")
        let gone = NSPredicate(format: "exists == false")
        let back = app.descendants(matching: .any).matching(identifier: "appearance_settings_back_button").firstMatch
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: gone, object: back)], timeout: 10), .completed)
        // The home account sheet dismisses before opening Appearance; back restores its home entry.
        let account = node("home_account_button")
        let restored = NSPredicate { _, _ in account.exists && account.isHittable }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: restored, object: account)], timeout: 15), .completed,
                       "Appearance back did not restore the hittable home account entry")
        evidence("guest-returned", screenshot: true)
    }
}
'''


def add_test_target(project, source):
    """A fixed UI-test overlay, with no production build-setting changes."""
    objects = project['objects']
    owner = objects[project['rootObject']]
    app_ids = [key for key in owner['targets'] if objects[key].get('productType') == 'com.apple.product-type.application']
    if len(app_ids) != 1 or objects[app_ids[0]]['name'] != 'ZhihuPlusPlus':
        raise RuntimeError('Expected the single ZhihuPlusPlus application target')
    app_id = app_ids[0]
    ids = [f'FA{i:022X}' for i in range(10)]
    if any(key in objects for key in ids):
        raise RuntimeError('Temporary XCTest object IDs collide with the application project')
    target, product, file_ref, build_file, sources, frameworks, config, configs, dependency, proxy = ids
    objects.update({
        target: dict(isa='PBXNativeTarget', name=TEST_TARGET, productName=TEST_TARGET,
                     productType='com.apple.product-type.bundle.ui-testing', productReference=product,
                     buildConfigurationList=configs, buildPhases=[sources, frameworks], buildRules=[], dependencies=[dependency]),
        product: dict(isa='PBXFileReference', explicitFileType='wrapper.cfbundle', path=TEST_TARGET + '.xctest', sourceTree='BUILT_PRODUCTS_DIR'),
        file_ref: dict(isa='PBXFileReference', lastKnownFileType='sourcecode.swift', path=str(source), sourceTree='<absolute>'),
        build_file: dict(isa='PBXBuildFile', fileRef=file_ref),
        sources: dict(isa='PBXSourcesBuildPhase', buildActionMask=2147483647, files=[build_file], runOnlyForDeploymentPostprocessing=0),
        frameworks: dict(isa='PBXFrameworksBuildPhase', buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0),
        config: dict(isa='XCBuildConfiguration', name='Debug', buildSettings={
            'SDKROOT': 'iphonesimulator', 'SUPPORTED_PLATFORMS': 'iphonesimulator', 'ARCHS': 'arm64',
            'IPHONEOS_DEPLOYMENT_TARGET': '16.0', 'TARGETED_DEVICE_FAMILY': '1,2', 'SWIFT_VERSION': '5.0',
            'CLANG_ENABLE_MODULES': 'YES', 'GENERATE_INFOPLIST_FILE': 'YES',
            'PRODUCT_NAME': '$(TARGET_NAME)', 'PRODUCT_BUNDLE_IDENTIFIER': BUNDLE_ID + '.acceptance',
            'TEST_TARGET_NAME': 'ZhihuPlusPlus',
            'LD_RUNPATH_SEARCH_PATHS': ['$(inherited)', '@executable_path/Frameworks', '@loader_path/Frameworks'],
        }),
        configs: dict(isa='XCConfigurationList', buildConfigurations=[config], defaultConfigurationIsVisible=0, defaultConfigurationName='Debug'),
        dependency: dict(isa='PBXTargetDependency', target=app_id, targetProxy=proxy),
        proxy: dict(isa='PBXContainerItemProxy', containerPortal=project['rootObject'], proxyType=1, remoteGlobalIDString=app_id, remoteInfo='ZhihuPlusPlus'),
    })
    owner['targets'].append(target)
    owner.setdefault('attributes', {}).setdefault('TargetAttributes', {})[target] = {'TestTargetID': app_id}
    objects[owner['mainGroup']]['children'].append(file_ref)
    objects[owner['productRefGroup']]['children'].append(product)
    return target


def add_test_action(scheme, target):
    reference = dict(BuildableIdentifier='primary', BlueprintIdentifier=target, BuildableName=TEST_TARGET + '.xctest',
                     BlueprintName=TEST_TARGET, ReferencedContainer='container:ZhihuPlusPlus.xcodeproj')
    entries = scheme.find('BuildAction/BuildActionEntries')
    if entries is None or scheme.find('TestAction') is not None:
        raise RuntimeError('Unexpected production scheme; refuse to replace existing tests')
    entry = ET.SubElement(entries, 'BuildActionEntry', buildForTesting='YES', buildForRunning='NO', buildForProfiling='NO', buildForArchiving='NO', buildForAnalyzing='NO')
    ET.SubElement(entry, 'BuildableReference', reference)
    action = ET.SubElement(scheme, 'TestAction', buildConfiguration='Debug', shouldUseLaunchSchemeArgsEnv='YES')
    testable = ET.SubElement(ET.SubElement(action, 'Testables'), 'TestableReference', skipped='NO', parallelizable='NO')
    ET.SubElement(testable, 'BuildableReference', reference)


def verify_summary(summary):
    if any(summary.get(key) != value for key, value in {'totalTestCount': 1, 'passedTests': 1, 'failedTests': 0, 'skippedTests': 0}.items()):
        raise RuntimeError('XCTest must execute and pass the one guest-navigation test, with no skipped tests')


def self_check():
    project = {'rootObject': 'project', 'objects': {
        'project': {'targets': ['app'], 'mainGroup': 'main', 'productRefGroup': 'products'},
        'app': {'name': 'ZhihuPlusPlus', 'productType': 'com.apple.product-type.application'},
        'main': {'children': []}, 'products': {'children': []},
    }}
    original = copy.deepcopy(project['objects']['app'])
    target = add_test_target(project, Path('/tmp/IOSAcceptanceTests.swift'))
    assert project['objects']['app'] == original
    assert project['objects'][project['rootObject']]['targets'] == ['app', target]
    assert plistlib.loads(plistlib.dumps(project)) == project
    scheme = ET.fromstring('<Scheme><BuildAction><BuildActionEntries/></BuildAction></Scheme>')
    add_test_action(scheme, target)
    assert scheme.find('TestAction/Testables/TestableReference/BuildableReference').get('BlueprintIdentifier') == target
    summary = dict(totalTestCount=1, passedTests=1, failedTests=0, skippedTests=0)
    verify_summary(summary)
    for bad in ({}, {**summary, 'totalTestCount': 0}, {**summary, 'skippedTests': 1}, {**summary, 'failedTests': 1}):
        try:
            verify_summary(bad)
        except RuntimeError:
            continue
        raise AssertionError('Incomplete or failing XCTest report accepted')
    print('XCTest overlay and fail-closed report checks passed (no Apple tools executed)')


def run_acceptance(root, output):
    if platform.system() != 'Darwin' or platform.machine() != 'arm64' or os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise RuntimeError('Acceptance is restricted to an explicitly selected GitHub-hosted arm64 Mac runner')
    runner_temp = Path(os.environ['RUNNER_TEMP']).resolve()
    if output == runner_temp or not output.is_relative_to(runner_temp):
        raise RuntimeError('Evidence must be in a dedicated RUNNER_TEMP subdirectory')
    output.mkdir(parents=True, exist_ok=False)

    def command(*args, capture=False, timeout=600):
        print('+', ' '.join(map(str, args)), flush=True)
        if capture:
            return subprocess.check_output(list(map(str, args)), cwd=root, text=True, timeout=timeout)
        subprocess.run(list(map(str, args)), cwd=root, check=True, timeout=timeout)

    # Run Script phases only receive exported build settings, not the entire setup-actions environment.
    build_tools = [f'ZHIHU_PYTHON_BIN_DIR={Path(sys.executable).parent}']
    for name in ('ZHIHU_TIQIAN_TOOLCHAIN', 'JAVA_HOME', 'ANDROID_HOME'):
        build_tools.append(f'{name}={Path(os.environ[name]).resolve(strict=True)}')

    def xcode(log, *args):
        started = time.monotonic()
        print(f'Starting {log}', flush=True)
        with (output / log).open('w', encoding='utf-8') as stream:
            try:
                subprocess.run(['xcodebuild', *build_tools, *map(str, args)], cwd=root, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=5400)
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                print(f'Xcode failed; inspect the isolated artifact {log}', flush=True)
                raise
            finally:
                elapsed = f'{log}: {time.monotonic() - started:.1f}s\n'
                print(elapsed, end='', flush=True)
                with (output / 'timings.txt').open('a', encoding='utf-8') as timing:
                    timing.write(elapsed)

    (output / 'xcode.txt').write_text(command('xcodebuild', '-version', capture=True), encoding='utf-8')
    with tempfile.TemporaryDirectory(prefix='zhpp-ios-acceptance-', dir=runner_temp) as temporary:
        work = Path(temporary)
        production = root / 'iosApp/ZhihuPlusPlus.xcodeproj'
        project = work / production.name
        shutil.copytree(production, project)
        source = work / (TEST_TARGET + '.swift')
        source.write_text(SWIFT_TEST, encoding='utf-8')
        document = json.loads(command('/usr/bin/plutil', '-convert', 'json', '-o', '-', production / 'project.pbxproj', capture=True))
        owner = document['objects'][document['rootObject']]
        owner['projectDirPath'] = str(root / 'iosApp')
        document['objects'][owner['mainGroup']]['path'] = str(root / 'iosApp')
        target = add_test_target(document, source)
        (project / 'project.pbxproj').write_bytes(plistlib.dumps(document))
        scheme_path = project / 'xcshareddata/xcschemes/ZhihuPlusPlus.xcscheme'
        scheme = ET.parse(scheme_path)
        add_test_action(scheme.getroot(), target)
        scheme.write(scheme_path, encoding='utf-8', xml_declaration=True)

        # Build Release from the unmodified production project, never from the XCTest overlay.
        release = work / 'release'
        xcode('release-build.log', '-project', production, '-scheme', 'ZhihuPlusPlus', '-configuration', 'Release',
              '-destination', 'generic/platform=iOS', '-derivedDataPath', release, 'CODE_SIGNING_ALLOWED=NO', 'build')
        app = release / 'Build/Products/Release-iphoneos/ZhihuPlusPlus.app'
        with (app / 'Info.plist').open('rb') as stream:
            info = plistlib.load(stream)
        if info.get('CFBundleIdentifier') != BUNDLE_ID or info.get('CFBundleSupportedPlatforms') != ['iPhoneOS']:
            raise RuntimeError('Release app has the wrong identity or platform')
        executable = app / info['CFBundleExecutable']
        if command('/usr/bin/lipo', '-archs', executable, capture=True).strip() != 'arm64':
            raise RuntimeError('Release executable is not arm64')
        if any(marker in executable.read_bytes() for marker in (b'ZHPP_BACKGROUND_UI_DEBUG_V1', b'IOSAcceptanceTests')):
            raise RuntimeError('Release app contains acceptance/debug controls')
        resources = [(root / 'LICENSE', app / 'LICENSE'),
                     (root / 'misc/emoji_mapping.json', app / 'misc/emoji_mapping.json'),
                     (root / 'app/build/generated/aboutLibraries/liteDebug/res/raw/aboutlibraries.json', app / 'aboutlibraries.json')]
        # KGP resolveResources uses aggregated-resources/<target>; Compose IosResources syncs it into the app's compose-resources.
        for origin, packaged in [(root / 'misc/emojis', app / 'misc/emojis'),
                                 (root / 'shared/build/kotlin-multiplatform-resources/aggregated-resources/iosArm64', app / 'compose-resources')]:
            files = [path for path in origin.rglob('*') if path.is_file()]
            if not files:
                raise RuntimeError(f'No generated resources at {origin}')
            resources.extend((path, packaged / path.relative_to(origin)) for path in files)
        for origin, packaged in resources:
            if origin.read_bytes() != packaged.read_bytes():
                raise RuntimeError(f'Release resource mismatch: {packaged.relative_to(app)}')
        (output / 'release-resources.json').write_text(json.dumps([str(path.relative_to(app)) for _, path in resources], ensure_ascii=False, indent=2), encoding='utf-8')
        command('/usr/bin/ditto', '-c', '-k', '--keepParent', app, output / 'ios-arm64-unsigned.app.zip')

        devices = json.loads(command('xcrun', 'simctl', 'list', '-j', 'devices', 'available', capture=True))['devices']
        # Clone the type of an installed iPad into a new device; never reuse or erase another simulator.
        candidates = [(runtime, device) for runtime, entries in devices.items() if '.iOS-' in runtime
                      for device in entries if device.get('isAvailable') and device['name'].startswith('iPad') and device.get('deviceTypeIdentifier')]
        if not candidates:
            raise RuntimeError('No installed iPad simulator runtime; UI acceptance is blocked (no runtime downloads)')
        runtime, device = sorted(candidates, key=lambda item: tuple(int(part) for part in item[0].split('.iOS-')[1].split('-')), reverse=True)[0]
        udid = command('xcrun', 'simctl', 'create', work.name, device['deviceTypeIdentifier'], runtime, capture=True).strip()
        try:
            (output / 'simulator.json').write_text(json.dumps(dict(udid=udid, runtime=runtime, deviceType=device['deviceTypeIdentifier'], dataMode='isolated', realAccount=False), indent=2), encoding='utf-8')
            command('xcrun', 'simctl', 'boot', udid)
            command('xcrun', 'simctl', 'bootstatus', udid, '-b')
            result = output / 'guest-navigation.xcresult'
            xcode('simulator-ui.log', '-project', project, '-scheme', 'ZhihuPlusPlus', '-configuration', 'Debug',
                  '-destination', f'platform=iOS Simulator,id={udid}', '-derivedDataPath', work / 'debug',
                  '-resultBundlePath', result, '-parallel-testing-enabled', 'NO', '-test-timeouts-enabled', 'YES',
                  '-maximum-test-execution-time-allowance', '180',
                  f'-only-testing:{TEST_TARGET}/{TEST_TARGET}/testGuestNavigation',
                  'CODE_SIGNING_ALLOWED=NO', f'SRCROOT={root / "iosApp"}', 'test')
            summary = json.loads(command('xcrun', 'xcresulttool', 'get', 'test-results', 'summary', '--path', result, capture=True))
            (output / 'test-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
            verify_summary(summary)
        finally:
            subprocess.run(['xcrun', 'simctl', 'shutdown', udid], cwd=root, check=False, timeout=120)
            command('xcrun', 'simctl', 'delete', udid)
            remaining = json.loads(command('xcrun', 'simctl', 'list', '-j', 'devices', capture=True))['devices']
            if any(entry['udid'] == udid for entries in remaining.values() for entry in entries):
                raise RuntimeError('Acceptance simulator was not deleted')
            (output / 'simulator-cleanup.txt').write_text('The task-owned simulator was deleted.\n', encoding='utf-8')
    message = 'iOS: isolated Simulator XCTest guest navigation passed; unsigned iosArm64 Release linked and bundled resources matched. No real-device, signing, publishing or IME acceptance is claimed.\n'
    (output / 'result.txt').write_text(message, encoding='utf-8')
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as stream:
        stream.write(message)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--self-check', action='store_true')
    args = parser.parse_args()
    if args.self_check:
        self_check()
    elif args.root and args.output:
        run_acceptance(args.root.resolve(), args.output.resolve())
    else:
        parser.error('--root and --output are required outside --self-check')
