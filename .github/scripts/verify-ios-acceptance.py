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
"""Opt-in hosted builds and acceptance; only simulator-test launches an isolated Simulator."""

import argparse
import copy
import io
import json
import os
import platform
import plistlib
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
import xml.etree.ElementTree as ET
from contextlib import suppress
from hashlib import file_digest
from pathlib import Path, PurePosixPath
from zipfile import ZipFile, ZipInfo

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
        app.launchArguments += ["-AppleLanguages", "(zh-Hans)", "-AppleLocale", "zh_CN"]
        app.launch()
        defer {
            evidence("final", screenshot: true)
            app.terminate()
        }
        for index in 1...3 {
            let tag = "login_notice_step_\(index)"
            // The expanded row contains links; toggle through its real title instead of the whole row's center.
            let titleTag = tag + "_title"
            state(tag, "已收起")
            tap(titleTag)
            state(tag, "已展开")
            if index == 1 { evidence("notice-expanded", screenshot: true) }
            tap(titleTag)
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


def add_test_action(scheme, target, project_path):
    # SRCROOT points at production sources; scheme buildables must still resolve to the temporary project.
    container = 'container:' + project_path.as_posix()
    for buildable in scheme.iter('BuildableReference'):
        buildable.set('ReferencedContainer', container)
    reference = dict(BuildableIdentifier='primary', BlueprintIdentifier=target, BuildableName=TEST_TARGET + '.xctest',
                     BlueprintName=TEST_TARGET, ReferencedContainer=container)
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


def live_progress(line):
    """Only diagnostics and progress, never Xcode's exported environment or command invocations."""
    text = line.strip()
    if re.fullmatch(r'> Task :[\w.:-]+(?: (?:UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?', text):
        return text
    if re.match(r'^(?:error:|e: |xcodebuild: error:|[^\s=]+:\d+(?::\d+)?: error:)', text):
        if re.search(r'\b[A-Z_][A-Z_0-9]*\\?=|\b(?:export|xcodebuild|gradlew|swift-frontend)\s', text):
            return 'Error (command or environment omitted; see stage log)'
        return text[:800]
    if re.match(r'^(?:\*\* (?:BUILD|TEST)|BUILD (?:SUCCESSFUL|FAILED)|FAILURE:|Test (?:Case|Suite) |Testing (?:started|failed))', text):
        return text[:800]
    phase = re.match(r'^(PhaseScriptExecution|SwiftCompile|SwiftEmitModule|CompileC|Ld|ProcessInfoPlistFile|CodeSign)\s', text)
    return phase.group(1) if phase else None


def publish_native_build(github_output):
    with github_output.open('a', encoding='utf-8') as stream:
        stream.write('native-build-succeeded=true\n')


def simulator_test(xcode, command, xctestrun, udid, result):
    command('xcrun', 'simctl', 'boot', udid)
    try:
        command('xcrun', 'simctl', 'bootstatus', udid, '-b')
        xcode('simulator-ui.log', '-xctestrun', xctestrun, '-destination', f'platform=iOS Simulator,id={udid}',
              '-resultBundlePath', result, '-parallel-testing-enabled', 'NO', '-test-timeouts-enabled', 'YES',
              '-maximum-test-execution-time-allowance', '180',
              f'-only-testing:{TEST_TARGET}/{TEST_TARGET}/testGuestNavigation', 'test-without-building')
    finally:
        # The caller deletes and verifies this UDID even if shutdown fails.
        with suppress(subprocess.CalledProcessError, subprocess.TimeoutExpired):
            command('xcrun', 'simctl', 'shutdown', udid, timeout=120)


def stage_heap_gib(stage, machine):
    if stage == 'release' and machine in ('x86_64', 'arm64'):
        return 8
    if stage in ('simulator-build', 'simulator-package') and machine == 'arm64':
        return 4
    if stage == 'simulator-test' and machine == 'arm64':
        return None  # No compiler or Gradle in the consumer.
    raise RuntimeError(f'Unsupported iOS acceptance stage/host: {stage}/{machine}')


def verify_test_products(products, command):
    plans = list(products.glob('*.xctestrun'))
    if len(plans) != 1 or plans[0].is_symlink():
        raise RuntimeError('Expected one regular .xctestrun at the Build/Products root')
    plan = plistlib.loads(plans[0].read_bytes())

    def check_path(value, *, test_runtime=False):
        for token in re.split(r'''[:=;\s"']+''', value):
            # Apple's test host uses these simulator-root paths, not the build machine's workspace.
            platform_path = token.startswith(('/Applications/', '/Library/Developer/', '/System/', '/usr/')) or (
                test_runtime and value in ('/Developer/Library/Frameworks', '/Developer/usr/lib',
                    '/Developer/Library/PrivateFrameworks/DTXConnectionServices.framework/DTXConnectionServices'))
            if ((token.startswith('/') and not platform_path)
                    or (token.startswith(('/', '__')) and '..' in PurePosixPath(token).parts)):
                raise RuntimeError(f'Non-relocatable test product runtime path: {token}')

    def check_paths(value):
        if isinstance(value, dict):
            for key, child in value.items():
                # Coverage source locations are diagnostics, not runtime dependencies.
                if key != 'CodeCoverageBuildableInfos':
                    check_paths(child)
        elif isinstance(value, list):
            for child in value:
                check_paths(child)
        elif isinstance(value, str):
            # Includes path lists inside TestingEnvironmentVariables, not just the three bundles.
            check_path(value)

    check_paths(plan)
    if 'TestConfigurations' in plan:
        targets = [target for config in plan['TestConfigurations'] for target in config['TestTargets']]
    else:
        targets = [value for key, value in plan.items() if key != '__xctestrun_metadata__']
    if len(targets) != 1 or targets[0].get('BlueprintName') != TEST_TARGET or targets[0].get('IsUITestBundle') is not True:
        raise RuntimeError('Expected only the generated UI test target in .xctestrun')
    target = targets[0]

    def product_path(value, host=None):
        prefix, sep, suffix = value.partition('/')
        base = {'__TESTROOT__': plans[0].parent, '__TESTHOST__': host}.get(prefix)
        path = PurePosixPath(suffix)
        if base is None or not sep or not path.parts or path.is_absolute() or '..' in path.parts or '\\' in suffix:
            raise RuntimeError(f'Expected a product-relative .xctestrun path: {value}')
        resolved = (base / path).resolve(strict=True)
        if not resolved.is_relative_to(products):
            raise RuntimeError(f'Test product escapes Build/Products: {value}')
        return resolved

    host = product_path(target['TestHostPath'])
    bundle = product_path(target['TestBundlePath'], host)
    app = product_path(target['UITargetAppPath'], host)
    for value in target.get('DependentProductPaths', []):
        product_path(value, host)
    if bundle.name != TEST_TARGET + '.xctest' or host.suffix != '.app' or app.name != 'ZhihuPlusPlus.app':
        raise RuntimeError('Unexpected simulator test, host or application bundle')
    for directory in (host, bundle, app):
        info = plistlib.loads((directory / 'Info.plist').read_bytes())
        if directory == app and (info.get('CFBundleIdentifier') != BUNDLE_ID or info.get('CFBundleSupportedPlatforms') != ['iPhoneSimulator']):
            raise RuntimeError('Wrong UI target app identity or platform')
        name = info['CFBundleExecutable']
        if not name or PurePosixPath(name).name != name or name in ('.', '..') or '\\' in name:
            raise RuntimeError('Invalid simulator bundle executable name')
        executable = (directory / name).resolve(strict=True)
        if not executable.is_relative_to(products) or not executable.is_file() or not os.access(executable, os.X_OK):
            raise RuntimeError(f'Missing or non-executable simulator product: {name}')
        slices = command('/usr/bin/lipo', '-archs', executable, capture=True).split()
        if 'arm64' not in slices or (directory != host and slices != ['arm64']):
            raise RuntimeError(f'Unexpected simulator architectures for {name}: {slices}')
    # Source/debug records may name the compiler's tree; only runtime load paths must be portable.
    for path in products.rglob('*'):
        if path.is_file() and not path.is_symlink():
            with path.open('rb') as stream:
                magic = stream.read(4)
            if magic in (b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf', b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca', b'\xca\xfe\xba\xbf', b'\xbf\xba\xfe\xca'):
                commands = command('/usr/bin/otool', '-l', path, capture=True)
                for value in re.findall(r'^\s+(?:name|path) (.+) \(offset \d+\)$', commands, flags=re.MULTILINE):
                    check_path(value, test_runtime=path.resolve().is_relative_to(host) and not path.resolve().is_relative_to(app))
    return plans[0]


def product_member(member, destination):
    # data_filter confines link chains and preserves executable bits; it strips leading slashes, which we reject.
    path = PurePosixPath(member.name)
    if (not path.parts or path.as_posix() != member.name or path.is_absolute() or '..' in path.parts
            or ':' in member.name or '\\' in member.name or not (member.isfile() or member.isdir() or member.issym() or member.islnk())):
        raise ValueError(f'Invalid simulator archive member: {member.name}')
    if member.issym() or member.islnk():
        if not member.linkname or PurePosixPath(member.linkname).is_absolute() or ':' in member.linkname or '\\' in member.linkname:
            raise ValueError(f'Non-relocatable simulator archive link: {member.name}')
    return tarfile.data_filter(member, str(destination))


def products_digest(archive):
    with archive.open('rb') as stream:
        return file_digest(stream, 'sha256').hexdigest()


def pack_products(products, handoff, identity, command):
    verify_test_products(products, command)
    handoff.mkdir(exist_ok=False)
    archive = handoff / 'products.tar'
    def checked(member):
        product_member(member, products)
        return member

    with tarfile.open(archive, 'x') as package:
        for entry in sorted(products.iterdir()):
            package.add(entry, arcname=entry.name, filter=checked)
    manifest = dict(identity, sha256=products_digest(archive))
    (handoff / 'products.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    return manifest


def unpack_products(handoff, products, identity, expected_digest, command):
    manifest = json.loads((handoff / 'products.json').read_text(encoding='utf-8'))
    attempt = manifest.get('runAttempt')
    if type(attempt) is not int or not 1 <= attempt <= identity['runAttempt']:
        raise RuntimeError('Invalid simulator producer run attempt')
    # Rerun failed jobs keeps the successful producer and its outputs from the same run.
    if manifest != dict(identity, runAttempt=attempt, sha256=expected_digest):
        raise RuntimeError('Simulator producer commit, run, toolchain, architecture or checksum does not match')
    archive = handoff / 'products.tar'
    if not re.fullmatch('[0-9a-f]{64}', expected_digest) or products_digest(archive) != expected_digest:
        raise RuntimeError('Simulator product archive SHA-256 mismatch')
    products.mkdir(parents=True, exist_ok=False)
    with tarfile.open(archive, 'r:') as package:
        names = [member.name.casefold() for member in package.getmembers()]
        if len(names) != len(set(names)):
            raise ValueError('Duplicate simulator archive members')
        package.extractall(products, filter=product_member)
    return verify_test_products(products, command)


def ios_identity(variant):
    if variant not in ('full', 'lite'):
        raise ValueError(f'Unknown iOS variant: {variant}')
    suffix = 'Lite' if variant == 'lite' else ''
    return 'ZhihuPlusPlus' + suffix, BUNDLE_ID + ('.lite' if suffix else ''), suffix


def verify_ort_linkage(executable, link_map, symbols, variant):
    """The final link map remains authoritative when Release strips its nm table."""
    text = link_map.read_text(encoding='utf-8')
    linked_path = re.search(r'^# Path: (.+)$', text, re.MULTILINE)
    if (not linked_path or Path(linked_path[1]).resolve() != executable.resolve()
            or not re.search(r'^# Arch: arm64$', text, re.MULTILINE)):
        raise RuntimeError('Link map does not belong to the current arm64 app executable')
    live = []
    active = False
    for line in text.splitlines():
        if line.startswith('# '):
            if line == '# Symbols:':
                active = True
            elif not line.startswith('# Address'):
                active = False
        elif active:
            match = re.fullmatch(r'0x[0-9a-fA-F]+\s+0x([0-9a-fA-F]+)\s+\[\s*\d+\]\s+(.+)', line)
            if match and int(match[1], 16) > 0:
                live.append(match[2])
    if not live:
        raise RuntimeError('Final link map has no live symbol evidence')
    native_ort = re.compile(r'^(?:_?Ort[A-Z]\w*|__?Z\S*onnxruntime)')
    live_ort = [name for name in live if native_ort.search(name)]
    nm_ort = [name for name in symbols.splitlines() if native_ort.search(name)]
    if variant == 'full':
        if '_OrtGetApiBase' not in live:
            raise RuntimeError('Full app has no live static OrtGetApiBase in its final link map')
    elif live_ort or nm_ort:
        raise RuntimeError('Lite app contains linked ONNX Runtime symbols')
    return dict(variant=variant, architecture='arm64', liveOrtSymbols=len(live_ort), nmOrtSymbols=len(nm_ort),
                executableSha256=products_digest(executable), linkMapSha256=products_digest(link_map))


def verify_app_resources(root, app, variant, target):
    manifest = root / f'shared/build/generated/apple-licenses/{variant}/{target}/aboutlibraries.json'
    resources = [(root / 'LICENSE', app / 'LICENSE'),
                 (root / 'misc/emoji_mapping.json', app / 'misc/emoji_mapping.json'),
                 (manifest, app / 'aboutlibraries.json')]
    for name in ('article_export_template.html', 'footnotes.js'):
        resources.append((root / 'shared-domain/src/commonMain/resources' / name, app / 'export' / name))
    for origin, packaged in [(root / 'misc/emojis', app / 'misc/emojis'),
                             (root / f'shared/build/kotlin-multiplatform-resources/aggregated-resources/{target}', app / 'compose-resources')]:
        files = [path for path in origin.rglob('*') if path.is_file()]
        if not files:
            raise RuntimeError(f'No generated resources at {origin}')
        resources.extend((path, packaged / path.relative_to(origin)) for path in files)
    licenses = json.loads((app / 'aboutlibraries.json').read_text(encoding='utf-8'))
    if not licenses.get('libraries') or not isinstance(licenses.get('licenses'), dict):
        raise RuntimeError('App has no usable target-specific dependency licenses')
    ort = [item for item in licenses['libraries'] if re.search(r'onnx[ -]?runtime', item.get('uniqueId', '') + ' ' + item.get('name', ''), re.IGNORECASE)]
    notices = root / 'shared-domain/build/generated/onnxruntime-apple/licenses'
    if variant == 'full':
        native = [item for item in ort if item.get('uniqueId') == 'native:onnxruntime']
        if len(native) != 1:
            raise RuntimeError('Full license manifest must contain one native ONNX Runtime entry')
        content = '\n\n'.join(licenses['licenses'][key].get('content', '') for key in native[0]['licenses'])
        for name in ('LICENSE', 'ThirdPartyNotices.txt'):
            source = notices / name
            original = source.read_text(encoding='utf-8')
            if not original.strip() or original not in content:
                raise RuntimeError(f'Full manifest omits original ORT {name}')
            resources.append((source, app / 'licenses/onnxruntime' / name))
    elif ort or any('onnxruntime' in key.lower() for key in licenses['licenses']) or any('onnxruntime' in p.name.lower() for p in app.rglob('*')):
        raise RuntimeError('Lite bundle or license manifest includes ONNX Runtime')
    for origin, packaged in resources:
        if origin.read_bytes() != packaged.read_bytes():
            raise RuntimeError(f'App resource mismatch: {packaged.relative_to(app)}')
    return [str(path.relative_to(app)) for _, path in resources]


def verify_app_archive(archive, app, variant='full', simulator=False):
    product, bundle_id, _ = ios_identity(variant)
    if app.name != product + '.app':
        raise RuntimeError('Unexpected app bundle name')
    prefix = ('' if simulator else 'Payload/') + app.name + '/'
    expected = {prefix + path.relative_to(app).as_posix(): path for path in app.rglob('*') if path.is_file()}
    if prefix + 'Info.plist' not in expected:
        raise RuntimeError('App archive source is missing Info.plist')
    with ZipFile(archive) as package:
        names = package.namelist()
        if len(names) != len(set(names)) or package.testzip() is not None:
            raise RuntimeError('App archive contains duplicate members or invalid CRCs')
        directories = (prefix,) if simulator else ('Payload/', prefix)
        for item in package.infolist():
            path = PurePosixPath(item.filename)
            if (path.is_absolute() or '..' in path.parts or '\\' in item.filename
                    or (item.filename not in directories and not item.filename.startswith(prefix))
                    or (item.external_attr >> 16) & 0o170000 == 0o120000):
                raise RuntimeError(f'Invalid app archive member: {item.filename}')
        files = {item.filename for item in package.infolist() if not item.is_dir()}
        if files != expected.keys():
            raise RuntimeError(f'Archive must contain exactly the verified app under {prefix}')
        info = plistlib.loads(package.read(prefix + 'Info.plist'))
        if (info.get('CFBundleIdentifier') != bundle_id
                or info.get('CFBundleSupportedPlatforms') != ['iPhoneSimulator' if simulator else 'iPhoneOS']
                or info.get('CFBundleExecutable') != product):
            raise RuntimeError('App archive has the wrong identity, executable or platform')
        if prefix + product not in files:
            raise RuntimeError('App archive has no executable')
        if simulator and not (package.getinfo(prefix + product).external_attr >> 16) & 0o111:
            raise RuntimeError('Simulator app archive lost executable permissions')
        for name, source in expected.items():
            with source.open('rb') as original, package.open(name) as packed:
                if file_digest(original, 'sha256').digest() != file_digest(packed, 'sha256').digest():
                    raise RuntimeError(f'App archive content mismatch: {name}')
    with archive.open('rb') as stream:
        return file_digest(stream, 'sha256').hexdigest()


def self_check_products():
    def reject(action):
        try:
            action()
        except (RuntimeError, ValueError, FileNotFoundError, tarfile.TarError):
            return
        raise AssertionError('Invalid simulator handoff accepted')

    with tempfile.TemporaryDirectory(prefix='zhpp-products-self-check-') as temporary:
        work = Path(temporary).resolve()
        products = work / 'producer/Build/Products'
        host = 'Debug-iphonesimulator/IOSAcceptanceTests-Runner.app'
        app = 'Debug-iphonesimulator/ZhihuPlusPlus.app'
        bundle = host + '/PlugIns/IOSAcceptanceTests.xctest'
        for name in (host, app, bundle):
            directory = products / name
            directory.mkdir(parents=True)
            executable = directory / 'Executable'
            executable.write_bytes(b'\xcf\xfa\xed\xfe' + b'synthetic arm64')
            executable.chmod(0o755)
            (directory / 'Info.plist').write_bytes(plistlib.dumps(dict(CFBundleIdentifier=BUNDLE_ID,
                CFBundleSupportedPlatforms=['iPhoneSimulator'], CFBundleExecutable=executable.name)))
        framework = products / host / 'Frameworks/XCTestCore.framework/XCTestCore'
        framework.parent.mkdir(parents=True)
        framework.write_bytes(b'\xcf\xfa\xed\xfe' + b'synthetic test framework')
        diagnostic_source = '/Users/runner/work/iosApp/ZhihuPlusPlusApp.swift'
        plan = {TEST_TARGET: dict(BlueprintName=TEST_TARGET, IsUITestBundle=True, TestHostPath='__TESTROOT__/' + host,
                TestBundlePath='__TESTHOST__/PlugIns/IOSAcceptanceTests.xctest', UITargetAppPath='__TESTROOT__/' + app,
                DependentProductPaths=['__TESTROOT__/' + host, '__TESTROOT__/' + app],
                CodeCoverageBuildableInfos=[dict(SourceFiles=[diagnostic_source])],
                TestingEnvironmentVariables={'DYLD_FRAMEWORK_PATH': '__TESTROOT__/Debug-iphonesimulator:__PLATFORMS__/iPhoneSimulator.platform/Developer/Library/Frameworks'})}
        xctestrun = products / 'Acceptance.xctestrun'
        xctestrun.write_bytes(plistlib.dumps(plan))
        identity = dict(version=1, commit='a' * 40, repository='owner/repo', runId='123', runAttempt=1,
                        workflow='owner/repo/.github/workflows/pr.yml@refs/heads/test', machine='arm64', xcode='Xcode 26\nBuild 1',
                        developer='/Applications/Xcode.app/Contents/Developer', sdk='26.0', sdkBuild='23A1')

        def command(tool, option, executable, capture):
            assert capture and executable.is_relative_to(work)
            if tool == '/usr/bin/lipo':
                assert option == '-archs'
                return 'arm64\n'
            assert (tool, option) == ('/usr/bin/otool', '-l')
            return '     path @executable_path/Frameworks (offset 12)\n'

        assert verify_test_products(products, command) == xctestrun
        version_two = dict(TestConfigurations=[dict(TestTargets=[plan[TEST_TARGET]])], __xctestrun_metadata__=dict(FormatVersion=2))
        xctestrun.write_bytes(plistlib.dumps(version_two))
        assert verify_test_products(products, command) == xctestrun
        xctestrun.write_bytes(plistlib.dumps(plan))
        for bad_path in ('/private/tmp/producer/App.app', '__TESTROOT__/../escape', '__TESTROOT__/missing',
                         '__TESTROOT__//absolute', '__TESTROOT__/C:\\escape'):
            bad = copy.deepcopy(plan)
            bad[TEST_TARGET]['UITargetAppPath'] = bad_path
            xctestrun.write_bytes(plistlib.dumps(bad))
            reject(lambda: verify_test_products(products, command))
        bad = copy.deepcopy(plan)
        bad[TEST_TARGET]['TestingEnvironmentVariables']['DYLD_FRAMEWORK_PATH'] = diagnostic_source
        xctestrun.write_bytes(plistlib.dumps(bad))
        reject(lambda: verify_test_products(products, command))
        xctestrun.write_bytes(plistlib.dumps(plan))
        for runtime_path in ('/Developer/Library/Frameworks', '/Developer/usr/lib',
                             '/Developer/Library/PrivateFrameworks/DTXConnectionServices.framework/DTXConnectionServices'):
            def runtime_command(tool, option, executable, capture):
                if tool == '/usr/bin/otool' and executable.is_relative_to(products / host):
                    return f'     path {runtime_path} (offset 12)\n'
                return command(tool, option, executable, capture)
            assert verify_test_products(products, runtime_command) == xctestrun
            reject(lambda: verify_test_products(products, lambda *args, **kwargs:
                'arm64' if args[0].endswith('lipo') else f'     path {runtime_path} (offset 12)\n'))
            bad = copy.deepcopy(plan)
            bad[TEST_TARGET]['TestingEnvironmentVariables']['DYLD_FRAMEWORK_PATH'] = runtime_path
            xctestrun.write_bytes(plistlib.dumps(bad))
            reject(lambda: verify_test_products(products, command))
            xctestrun.write_bytes(plistlib.dumps(plan))
        for runtime_path in ('/Developer/Library/Frameworks-elsewhere', '/Developer/usr/lib/evil.dylib',
                             '/Developer/Library/PrivateFrameworks/Other.framework/Other',
                             '/Developer/Library/Frameworks/../escape', '/Developer/tmp', '/Developer/usr/lib extra',
                             '/Developer/usr/lib:/Developer/Library/Frameworks'):
            reject(lambda: verify_test_products(products, runtime_command))
        reject(lambda: verify_test_products(products, lambda *args, **kwargs: 'x86_64'))
        reject(lambda: verify_test_products(products, lambda *args, **kwargs: 'arm64' if args[0].endswith('lipo') else '     path /private/tmp/frameworks (offset 12)\n'))
        duplicate = products / 'Other.xctestrun'
        duplicate.write_bytes(xctestrun.read_bytes())
        reject(lambda: verify_test_products(products, command))
        duplicate.unlink()

        handoff = work / 'handoff'
        manifest = pack_products(products, handoff, identity, command)
        consumer = dict(identity, runAttempt=2)
        destination = work / 'consumer/Build/Products'
        restored = unpack_products(handoff, destination, consumer, manifest['sha256'], command)
        assert restored == destination / xctestrun.name
        assert (destination / app / 'Executable').read_bytes() == (products / app / 'Executable').read_bytes()
        with tarfile.open(handoff / 'products.tar') as package:
            if os.name != 'nt':
                assert package.getmember(app + '/Executable').mode & 0o111 == 0o111
                assert (destination / app / 'Executable').stat().st_mode & 0o111 == 0o111
        for key in identity.keys() - {'runAttempt'}:
            reject(lambda: unpack_products(handoff, work / 'rejected', dict(consumer, **{key: 'wrong'}), manifest['sha256'], command))
        for attempt in (0, 3, '1', True):
            (handoff / 'products.json').write_text(json.dumps(dict(manifest, runAttempt=attempt)))
            reject(lambda: unpack_products(handoff, work / 'rejected', consumer, manifest['sha256'], command))
        (handoff / 'products.json').write_text(json.dumps(manifest))
        with (handoff / 'products.tar').open('ab') as stream:
            stream.write(b'tampered')
        reject(lambda: unpack_products(handoff, work / 'rejected', consumer, manifest['sha256'], command))

        # Exercise tar confinement independently of a real Mac or Windows symlink privileges.
        for index, (name, kind, link) in enumerate([
            ('../escape', tarfile.REGTYPE, ''), ('/absolute', tarfile.REGTYPE, ''),
            ('C:/escape', tarfile.REGTYPE, ''), ('safe\\escape', tarfile.REGTYPE, ''),
            ('link', tarfile.SYMTYPE, '../escape'), ('link', tarfile.SYMTYPE, '/tmp/escape'),
            ('link', tarfile.LNKTYPE, '../escape'), ('pipe', tarfile.FIFOTYPE, ''),
            ('duplicate', tarfile.REGTYPE, ''),
        ]):
            with tarfile.open(handoff / 'products.tar', 'w') as package:
                item = tarfile.TarInfo(name)
                item.type, item.linkname = kind, link
                package.addfile(item, io.BytesIO())
                if name == 'duplicate':
                    package.addfile(item, io.BytesIO())
            digest = products_digest(handoff / 'products.tar')
            (handoff / 'products.json').write_text(json.dumps(dict(manifest, sha256=digest)))
            reject(lambda: unpack_products(handoff, work / f'bad-{index}', consumer, digest, command))
        inside = tarfile.TarInfo('framework/Current')
        inside.type, inside.linkname = tarfile.SYMTYPE, '../version'
        assert Path(product_member(inside, work).linkname).as_posix() == '../version'
        executable = tarfile.TarInfo('Executable')
        executable.mode = 0o755
        assert product_member(executable, work).mode & 0o111 == 0o111
        inside.type, inside.linkname = tarfile.LNKTYPE, 'Executable'
        assert product_member(inside, work).linkname == 'Executable'
        if os.name != 'nt':
            (products / 'ExecutableLink').symlink_to(app + '/Executable')
            linked = work / 'linked-handoff'
            link_manifest = pack_products(products, linked, identity, command)
            target = work / 'linked-products'
            unpack_products(linked, target, consumer, link_manifest['sha256'], command)
            assert (target / 'ExecutableLink').is_symlink()
        else:
            print('Windows: link confinement and tar modes checked; POSIX symlink/chmod round-trip requires a Mac')


def self_check_variants():
    def reject(action):
        try:
            action()
        except RuntimeError:
            return
        raise AssertionError('Invalid variant package accepted')

    with tempfile.TemporaryDirectory(prefix='zhpp-variants-self-check-') as temporary:
        root = Path(temporary).resolve()
        for name in ('LICENSE', 'misc/emoji_mapping.json', 'misc/emojis/example.png',
                     'shared-domain/src/commonMain/resources/article_export_template.html',
                     'shared-domain/src/commonMain/resources/footnotes.js'):
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name, encoding='utf-8')
        notice_dir = root / 'shared-domain/build/generated/onnxruntime-apple/licenses'
        notice_dir.mkdir(parents=True)
        for name in ('LICENSE', 'ThirdPartyNotices.txt'):
            (notice_dir / name).write_text('ORT ' + name, encoding='utf-8')
        for variant in ('full', 'lite'):
            product, bundle_id, suffix = ios_identity(variant)
            assert suffix == ('Lite' if variant == 'lite' else '')
            for simulator in (False, True):
                app = root / f'{variant}-{simulator}' / (product + '.app')
                app.mkdir(parents=True)
                (app / 'Info.plist').write_bytes(plistlib.dumps(dict(CFBundleIdentifier=bundle_id,
                    CFBundleSupportedPlatforms=['iPhoneSimulator' if simulator else 'iPhoneOS'], CFBundleExecutable=product)))
                executable = app / product
                executable.write_bytes(b'synthetic executable')
                executable.chmod(0o755)
                link_map = root / 'app-link.map'
                header = f'# Path: {executable}\n# Arch: arm64\n# Symbols:\n# Address\tSize\tFile Name\n'
                main_symbol = '0x1000 0x10 [ 1] _main\n'
                ort_symbol = '0x2000 0x20 [ 2] _OrtGetApiBase\n'
                valid_map = header + main_symbol + (ort_symbol if variant == 'full' else '') + '# Dead Stripped Symbols:\n' + ort_symbol
                link_map.write_text(valid_map, encoding='utf-8')
                assert verify_ort_linkage(executable, link_map, '', variant)['liveOrtSymbols'] == int(variant == 'full')
                for wrong_map in (valid_map.replace(str(executable), str(root / 'another')), valid_map.replace('arm64', 'x86_64'),
                                  header + '# Dead Stripped Symbols:\n' + main_symbol + ort_symbol,
                                  header + main_symbol + (ort_symbol if variant == 'lite' else '')):
                    link_map.write_text(wrong_map, encoding='utf-8')
                    reject(lambda: verify_ort_linkage(executable, link_map, '', variant))
                link_map.write_text(valid_map, encoding='utf-8')
                if variant == 'lite':
                    reject(lambda: verify_ort_linkage(executable, link_map, '_OrtGetApiBase\n', variant))
                target = 'iosSimulatorArm64' if simulator else 'iosArm64'
                manifest = dict(libraries=[dict(uniqueId='kotlin', name='Kotlin')], licenses={})
                if variant == 'full':
                    manifest['libraries'].append(dict(uniqueId='native:onnxruntime', name='ONNX Runtime C API', licenses=['ort']))
                    manifest['licenses']['ort'] = dict(content='ORT LICENSE\n\nORT ThirdPartyNotices.txt')
                    shutil.copytree(notice_dir, app / 'licenses/onnxruntime')
                source_manifest = root / f'shared/build/generated/apple-licenses/{variant}/{target}/aboutlibraries.json'
                source_manifest.parent.mkdir(parents=True)
                source_manifest.write_text(json.dumps(manifest), encoding='utf-8')
                shutil.copyfile(source_manifest, app / 'aboutlibraries.json')
                for name in ('LICENSE', 'misc/emoji_mapping.json', 'misc/emojis/example.png'):
                    (app / name).parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(root / name, app / name)
                shutil.copytree(root / 'shared-domain/src/commonMain/resources', app / 'export')
                generated = root / f'shared/build/kotlin-multiplatform-resources/aggregated-resources/{target}'
                generated.mkdir(parents=True, exist_ok=True)
                (generated / 'resource.bin').write_bytes(b'compose resource')
                shutil.copytree(generated, app / 'compose-resources')
                assert 'aboutlibraries.json' in verify_app_resources(root, app, variant, target)
                (app / 'aboutlibraries.json').write_text(json.dumps(dict(manifest, libraries=[])), encoding='utf-8')
                reject(lambda: verify_app_resources(root, app, variant, target))
                shutil.copyfile(source_manifest, app / 'aboutlibraries.json')
                if variant == 'lite':
                    (app / 'onnxruntime.framework').mkdir()
                    reject(lambda: verify_app_resources(root, app, variant, target))
                    (app / 'onnxruntime.framework').rmdir()
                archive = root / 'package.zip'
                for executable_mode in (0o100755, 0o100644):
                    with ZipFile(archive, 'w') as package:
                        for path in app.rglob('*'):
                            if path.is_file():
                                name = ('' if simulator else 'Payload/') + app.name + '/' + path.relative_to(app).as_posix()
                                member = ZipInfo(name)
                                member.external_attr = (executable_mode if path == executable else 0o100644) << 16
                                package.writestr(member, path.read_bytes())
                    if simulator and executable_mode == 0o100644:
                        reject(lambda: verify_app_archive(archive, app, variant, simulator))
                    else:
                        assert len(verify_app_archive(archive, app, variant, simulator)) == 64
                    reject(lambda: verify_app_archive(archive, app, 'full' if variant == 'lite' else 'lite', simulator))


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
    scheme = ET.fromstring('<Scheme><BuildAction><BuildActionEntries><BuildActionEntry><BuildableReference BlueprintIdentifier="app" ReferencedContainer="container:ZhihuPlusPlus.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction><LaunchAction><BuildableProductRunnable><BuildableReference BlueprintIdentifier="app" ReferencedContainer="container:ZhihuPlusPlus.xcodeproj"/></BuildableProductRunnable></LaunchAction></Scheme>')
    temporary_project = Path('/tmp/acceptance/ZhihuPlusPlus.xcodeproj')
    add_test_action(scheme, target, temporary_project)
    assert scheme.find('TestAction/Testables/TestableReference/BuildableReference').get('BlueprintIdentifier') == target
    for reference in scheme.iter('BuildableReference'):
        assert reference.get('ReferencedContainer') == 'container:' + temporary_project.as_posix()
        assert reference.get('BlueprintIdentifier') in project['objects']['project']['targets']
    test_target = project['objects'][target]
    configurations = project['objects'][test_target['buildConfigurationList']]['buildConfigurations']
    settings = project['objects'][configurations[0]]['buildSettings']
    assert settings['SUPPORTED_PLATFORMS'] == 'iphonesimulator' and settings['ARCHS'] == 'arm64'
    assert settings['TEST_TARGET_NAME'] == project['objects']['app']['name']
    assert project['objects'][test_target['dependencies'][0]]['target'] == 'app'
    summary = dict(totalTestCount=1, passedTests=1, failedTests=0, skippedTests=0)
    verify_summary(summary)
    for bad in ({}, {**summary, 'totalTestCount': 0}, {**summary, 'skippedTests': 1}, {**summary, 'failedTests': 1}):
        try:
            verify_summary(bad)
        except RuntimeError:
            continue
        raise AssertionError('Incomplete or failing XCTest report accepted')
    for stage, machine, heap in [('release', 'x86_64', 8), ('release', 'arm64', 8), ('simulator-package', 'arm64', 4), ('simulator-build', 'arm64', 4), ('simulator-test', 'arm64', None)]:
        assert stage_heap_gib(stage, machine) == heap
    for stage, machine in [('', 'arm64'), ('other', 'arm64'), ('simulator-build', 'x86_64'), ('simulator-test', 'x86_64'), ('release', 'unknown')]:
        try:
            stage_heap_gib(stage, machine)
        except RuntimeError:
            continue
        raise AssertionError('Invalid stage/host accepted')
    for line in ('export SECRET=value', '    export PATH=/private', 'Command line invocation:',
                 '/usr/bin/xcodebuild TOKEN=secret', '    JAVA_HOME = /private', 'ZHIHU_GRADLE_JVMARGS=-Xmx8g'):
        assert live_progress(line) is None
    for line in ('> Task :shared:linkReleaseFrameworkIosArm64', '> Task :shared:compileKotlinIosArm64 FROM-CACHE',
                 'error: Compilation failed', '/source/Test.swift:4:2: error: failed', '** TEST FAILED **'):
        assert live_progress(line) == line
    assert live_progress('PhaseScriptExecution Build Shared /private/build/script.sh') == 'PhaseScriptExecution'
    with tempfile.TemporaryDirectory(prefix='zhpp-ipa-self-check-') as temporary:
        work = Path(temporary)
        test = 'test-without-building'
        plan = work / 'test.xctestrun'
        result = work / 'ui.xcresult'
        complete = ['boot', 'bootstatus', test, 'shutdown']
        for failure, expected in (('boot', ['boot']), ('bootstatus', ['boot', 'bootstatus', 'shutdown']),
                                  (test, complete), (None, complete)):
            calls = []

            def record(action):
                calls.append(action)
                if action == failure:
                    raise subprocess.CalledProcessError(65, action)

            def fake_xcode(log, *arguments):
                assert arguments[-1] == test
                assert list(arguments[:4]) == ['-xctestrun', plan, '-destination', 'platform=iOS Simulator,id=isolated']
                assert arguments.count('-destination') == 1 and arguments[arguments.index('-resultBundlePath') + 1] == result
                assert f'-only-testing:{TEST_TARGET}/{TEST_TARGET}/testGuestNavigation' in arguments
                assert '-project' not in arguments and '-scheme' not in arguments
                assert arguments[arguments.index('-parallel-testing-enabled') + 1] == 'NO'
                assert arguments[arguments.index('-maximum-test-execution-time-allowance') + 1] == '180'
                record(arguments[-1])

            def fake_command(tool, cmd, action, device, *extra, **kwargs):
                assert (tool, cmd, device) == ('xcrun', 'simctl', 'isolated')
                assert extra == (('-b',) if action == 'bootstatus' else ())
                record(action)

            try:
                simulator_test(fake_xcode, fake_command, plan, 'isolated', result)
            except subprocess.CalledProcessError:
                assert failure is not None
            else:
                assert failure is None
            assert calls == expected
        app = work / 'ZhihuPlusPlus.app'
        app.mkdir()
        (app / 'Info.plist').write_bytes(plistlib.dumps(dict(CFBundleIdentifier=BUNDLE_ID, CFBundleSupportedPlatforms=['iPhoneOS'], CFBundleExecutable='ZhihuPlusPlus')))
        (app / 'ZhihuPlusPlus').write_bytes(b'synthetic executable')
        archive = work / 'test.ipa'
        prefix = 'Payload/ZhihuPlusPlus.app/'
        for variant in ('valid', 'missing', 'outside', 'wrong-content', 'wrong-identity', 'bad-crc'):
            with ZipFile(archive, 'w') as package:
                package.writestr(prefix + 'Info.plist', (app / 'Info.plist').read_bytes() if variant != 'wrong-identity' else plistlib.dumps({}))
                if variant != 'missing':
                    package.writestr(prefix + 'ZhihuPlusPlus', b'wrong' if variant == 'wrong-content' else (app / 'ZhihuPlusPlus').read_bytes())
                if variant == 'outside':
                    package.writestr('../outside', b'not an app resource')
            if variant == 'bad-crc':
                archive.write_bytes(archive.read_bytes().replace(b'synthetic executable', b'corrupted executable'))
            try:
                digest = verify_app_archive(archive, app)
            except RuntimeError:
                assert variant != 'valid', variant
            else:
                assert variant == 'valid' and len(digest) == 64, variant
    self_check_products()
    self_check_variants()
    print('Stage/host, variant resources/linkage/archives, portable products and XCTest fail-closed checks passed (no Apple tools executed)')


def run_acceptance(root, output, stage, handoff, variant='full'):
    product, bundle_id, suffix = ios_identity(variant)
    if variant != 'full' and stage in ('simulator-build', 'simulator-test'):
        raise RuntimeError('The existing XCTest acceptance stages only support Full')
    heap_gib = stage_heap_gib(stage, platform.machine())
    if platform.system() != 'Darwin' or os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise RuntimeError('Acceptance is restricted to an explicitly selected GitHub-hosted Mac runner')
    runner_temp = Path(os.environ['RUNNER_TEMP']).resolve()
    if output == runner_temp or not output.is_relative_to(runner_temp):
        raise RuntimeError('Evidence must be in a dedicated RUNNER_TEMP subdirectory')
    output.mkdir(parents=True, exist_ok=False)

    def command(*args, capture=False, timeout=600):
        print('+', ' '.join(map(str, args)), flush=True)
        if capture:
            return subprocess.check_output(list(map(str, args)), cwd=root, text=True, timeout=timeout)
        subprocess.run(list(map(str, args)), cwd=root, check=True, timeout=timeout)

    commit = command('git', 'rev-parse', 'HEAD', capture=True).strip()
    if commit != os.environ['GITHUB_SHA']:
        raise RuntimeError('Checkout does not match the selected acceptance commit')
    memory_bytes = int(command('/usr/sbin/sysctl', '-n', 'hw.memsize', capture=True))
    gc_log = f'-Xlog:gc,gc+init,gc+cpu:file={output}/gradle-gc-%p.log:time,uptime,level,tags:filecount=2,filesize=2M'
    # Gradle splits org.gradle.jvmargs again; keep a log path with spaces in one VM option.
    jvm_args = f'-Xmx{heap_gib}g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 {shlex.quote(gc_log)}' if heap_gib else None
    (output / 'host-budget.json').write_text(json.dumps(dict(stage=stage, commit=commit, machine=platform.machine(),
        hostMemoryBytes=memory_bytes, configuredGradleJvmArgs=jvm_args, maxWorkers=1 if heap_gib else None, heapMeasured=False), indent=2), encoding='utf-8')
    (output / 'COMMIT_SHA').write_text(commit + '\n', encoding='utf-8')
    if heap_gib and memory_bytes < (heap_gib + 2) * 1024**3:
        raise RuntimeError('Runner memory cannot cover the configured heap, metaspace and host reserve')
    # Run Script phases only receive exported build settings, not the entire setup-actions environment.
    build_tools = []
    if heap_gib:
        build_tools = [f'ZHIHU_PYTHON_BIN_DIR={Path(sys.executable).parent}',
                       f'ZHIHU_GRADLE_JVMARGS={jvm_args}', 'ZHIHU_GRADLE_MAX_WORKERS=1', 'ZHIHU_GRADLE_PROFILE=true']
        for name in ('ZHIHU_TIQIAN_TOOLCHAIN', 'JAVA_HOME', 'ANDROID_HOME'):
            build_tools.append(f'{name}={Path(os.environ[name]).resolve(strict=True)}')

    github_output = Path(os.environ['GITHUB_OUTPUT'])
    profiles = root / 'build/reports/profile'

    def xcode(log, *args):
        started = time.monotonic()
        heartbeat = started + 30
        action = args[-1]
        before = {p: p.stat().st_mtime_ns for p in profiles.glob('profile-*.html')} if action != 'test-without-building' else None

        def status(message):
            print(f'[{stage}/{action} +{time.monotonic() - started:.1f}s] {message}', flush=True)

        status(f'Starting; full output: {log}')
        path = output / log
        with path.open('wb') as stream, path.open('rb') as reader:
            proc = subprocess.Popen(['xcodebuild', *build_tools, *map(str, args)], cwd=root, stdout=stream,
                                    stderr=subprocess.STDOUT, start_new_session=True)
            try:
                while True:
                    code = proc.poll()
                    available = os.fstat(stream.fileno()).st_size
                    while reader.tell() < available:
                        position = reader.tell()
                        line = reader.readline()
                        if not line or (code is None and not line.endswith(b'\n')):
                            reader.seek(position)
                            break
                        if message := live_progress(line.decode('utf-8', errors='replace')):
                            status(message)
                    if code is not None:
                        if code != 0:
                            raise subprocess.CalledProcessError(code, f'xcodebuild {action}; see {log}')
                        break
                    now = time.monotonic()
                    if now - started >= 5400:
                        raise subprocess.TimeoutExpired(f'xcodebuild {action}; see {log}', 5400)
                    if now >= heartbeat:
                        status(f'In progress; Xcode PID {proc.pid}, {reader.tell()} log bytes')
                        heartbeat = now + 30
                    with suppress(subprocess.TimeoutExpired):
                        proc.wait(timeout=1)
            except BaseException:
                if proc.returncode is None:
                    # Only this fresh session's process group; a detached Gradle daemon is not in it.
                    status('Terminating this Xcode process group; full log retained')
                    with suppress(ProcessLookupError):
                        os.killpg(proc.pid, signal.SIGTERM)
                    time.sleep(2)
                    with suppress(ProcessLookupError):
                        os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait(timeout=10)
                raise
            finally:
                elapsed = f'{log}: {time.monotonic() - started:.1f}s\n'
                status(elapsed.rstrip())
                with (output / 'timings.txt').open('a', encoding='utf-8') as timing:
                    timing.write(elapsed)
                if before is not None:
                    changed = [p for p in profiles.glob('profile-*.html') if before.get(p) != p.stat().st_mtime_ns]
                    if changed:
                        target = output / 'gradle-profile' / f'{stage}-{action}'
                        target.mkdir(parents=True)
                        for report in changed:
                            shutil.copy2(report, target)
                        for assets in ('css', 'js'):
                            if (profiles / assets).is_dir():
                                shutil.copytree(profiles / assets, target / assets)
                    else:
                        status('No new Gradle profile report was produced')

    xcode_version = command('xcodebuild', '-version', capture=True)
    (output / 'xcode.txt').write_text(xcode_version, encoding='utf-8')
    if stage in ('simulator-build', 'simulator-test'):
        if handoff is None or handoff == runner_temp or not handoff.is_relative_to(runner_temp) or handoff == output:
            raise RuntimeError('Simulator handoff requires a separate RUNNER_TEMP subdirectory')
        if os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch':
            raise RuntimeError('Simulator handoff is restricted to the trusted manual workflow')
        identity = dict(version=1, commit=commit, repository=os.environ['GITHUB_REPOSITORY'], runId=os.environ['GITHUB_RUN_ID'],
                        runAttempt=int(os.environ['GITHUB_RUN_ATTEMPT']), workflow=os.environ['GITHUB_WORKFLOW_REF'],
                        machine=platform.machine(), xcode=xcode_version,
                        developer=command('xcode-select', '-p', capture=True).strip(),
                        sdk=command('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version', capture=True).strip(),
                        sdkBuild=command('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-build-version', capture=True).strip())
        (output / 'toolchain.json').write_text(json.dumps(identity, indent=2), encoding='utf-8')
    with tempfile.TemporaryDirectory(prefix='zhpp-ios-acceptance-', dir=runner_temp) as temporary:
        work = Path(temporary)
        production = root / 'iosApp/ZhihuPlusPlus.xcodeproj'
        if stage in ('release', 'simulator-package'):
            simulator = stage == 'simulator-package'
            configuration = ('Debug' if simulator else 'Release') + suffix
            sdk = 'iphonesimulator' if simulator else 'iphoneos'
            target = 'iosSimulatorArm64' if simulator else 'iosArm64'
            derived = work / f'{variant}-{sdk}'
            link_map = output / 'app-link.map'
            # Disable only the build-only Debug trampoline, so the map belongs to the packaged executable.
            debug_flags = ['ENABLE_DEBUG_DYLIB=NO'] if simulator else []
            xcode(stage + '-build.log', '-project', production, '-scheme', product, '-configuration', configuration,
                  '-sdk', sdk, '-destination', 'generic/platform=iOS Simulator' if simulator else 'generic/platform=iOS',
                  '-derivedDataPath', derived, 'ARCHS=arm64', 'CODE_SIGNING_ALLOWED=NO',
                  'LD_GENERATE_MAP_FILE=YES', f'LD_MAP_FILE_PATH={link_map}', *debug_flags, 'build')
            # 编译缓存与产物验收分开；后续校验失败仍必须使任务失败。
            publish_native_build(github_output)
            app = derived / f'Build/Products/{configuration}-{sdk}/{product}.app'
            info = plistlib.loads((app / 'Info.plist').read_bytes())
            if (info.get('CFBundleIdentifier') != bundle_id or info.get('CFBundleExecutable') != product
                    or info.get('CFBundleSupportedPlatforms') != ['iPhoneSimulator' if simulator else 'iPhoneOS']):
                raise RuntimeError('App has the wrong variant identity, executable or platform')
            executable = app / product
            if not executable.is_file() or not os.access(executable, os.X_OK):
                raise RuntimeError('App executable is missing or not executable')
            if command('/usr/bin/lipo', '-archs', executable, capture=True).strip() != 'arm64':
                raise RuntimeError('App executable is not arm64')
            if any(app.rglob('*.xctest')) or (not simulator and any(marker in executable.read_bytes() for marker in (b'ZHPP_BACKGROUND_UI_DEBUG_V1', b'IOSAcceptanceTests'))):
                raise RuntimeError('Packaged app contains acceptance/debug controls')
            symbols = command('/usr/bin/nm', '-U', '-j', executable, capture=True)
            (output / 'app-symbols.txt').write_text(symbols, encoding='utf-8')
            linkage = verify_ort_linkage(executable, link_map, symbols, variant)
            (output / 'app-linkage.json').write_text(json.dumps(linkage, indent=2), encoding='utf-8')
            resources = verify_app_resources(root, app, variant, target)
            (output / 'app-resources.json').write_text(json.dumps(resources, ensure_ascii=False, indent=2), encoding='utf-8')
            if simulator:
                archive = output / f'{product}-ios-{variant}-simulator-arm64-debug.app.zip'
                command('/usr/bin/ditto', '-c', '-k', '--norsrc', '--keepParent', app, archive)
            else:
                payload = work / 'Payload'
                payload.mkdir()
                command('/usr/bin/ditto', '--norsrc', app, payload / app.name)
                archive = output / f'{product}-ios-{variant}-arm64-unsigned.ipa'
                command('/usr/bin/ditto', '-c', '-k', '--norsrc', '--keepParent', payload, archive)
            digest = verify_app_archive(archive, app, variant, simulator)
            (output / 'SHA256SUMS').write_text(f'{digest}  {archive.name}\n', encoding='utf-8')

        elif stage == 'simulator-build':
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
            add_test_action(scheme.getroot(), target, project)
            scheme.write(scheme_path, encoding='utf-8', xml_declaration=True)

            debug = work / 'debug'
            xcode('simulator-build.log', '-project', project, '-scheme', 'ZhihuPlusPlus', '-configuration', 'Debug',
                  '-derivedDataPath', debug, 'CODE_SIGNING_ALLOWED=NO', f'SRCROOT={root / "iosApp"}',
                  '-destination', 'generic/platform=iOS Simulator', 'ARCHS=arm64', 'build-for-testing')
            publish_native_build(github_output)
            manifest = pack_products(debug / 'Build/Products', handoff, identity, command)
            (output / 'products.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
            with github_output.open('a', encoding='utf-8') as stream:
                stream.write(f'products-sha256={manifest["sha256"]}\n')

        else:
            xctestrun = unpack_products(handoff, work / 'Build/Products', identity, os.environ['PRODUCTS_SHA256'], command)
            receipt = dict(json.loads((handoff / 'products.json').read_text(encoding='utf-8')), artifactId=os.environ['PRODUCTS_ID'])
            (output / 'products.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
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
                result = output / 'guest-navigation.xcresult'
                simulator_test(xcode, command, xctestrun, udid, result)
                summary = json.loads(command('xcrun', 'xcresulttool', 'get', 'test-results', 'summary', '--path', result, capture=True))
                (output / 'test-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
                verify_summary(summary)
            finally:
                command('xcrun', 'simctl', 'delete', udid)
                remaining = json.loads(command('xcrun', 'simctl', 'list', '-j', 'devices', capture=True))['devices']
                if any(entry['udid'] == udid for entries in remaining.values() for entry in entries):
                    raise RuntimeError('Acceptance simulator was not deleted')
                (output / 'simulator-cleanup.txt').write_text('The task-owned simulator was deleted.\n', encoding='utf-8')
    message = (f'iOS {variant} {stage}: ' + ('unsigned iosArm64 Release, resources, ORT linkage and Payload IPA integrity passed.' if stage == 'release'
               else 'unsigned arm64 Debug Simulator app resources, ORT linkage and archive verified; no Simulator or tests started.' if stage == 'simulator-package'
               else 'Debug Simulator build-for-testing products verified; no UI tests executed here.' if stage == 'simulator-build'
               else 'isolated Debug Simulator XCTest guest navigation passed.') +
               ' No real-device, signing, publishing or IME acceptance is claimed.\n')
    (output / 'result.txt').write_text(message, encoding='utf-8')
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as stream:
        stream.write(message)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--stage', choices=('release', 'simulator-package', 'simulator-build', 'simulator-test'))
    parser.add_argument('--variant', choices=('full', 'lite'), default='full')
    parser.add_argument('--handoff', type=Path)
    parser.add_argument('--self-check', action='store_true')
    args = parser.parse_args()
    if args.self_check:
        self_check()
    elif args.root and args.output and args.stage:
        run_acceptance(args.root.resolve(), args.output.resolve(), args.stage, args.handoff.resolve() if args.handoff else None, args.variant)
    else:
        parser.error('--root, --output and --stage are required outside --self-check')
