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
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

"""Check shipped Web/desktop resources and native macOS deployment metadata without launching apps."""

import argparse
import hashlib
import json
import plistlib
import re
from pathlib import Path, PurePosixPath
import subprocess
import tempfile
from zipfile import ZipFile

FONTS = ('pdf-fonts/NotoSansSC-Regular.ttf', 'pdf-fonts/NotoSansSC-Bold.ttf')
RUNTIME_RELEASE = {'windows': 'runtime/release', 'linux': 'lib/runtime/release', 'macos': 'Contents/runtime/Contents/Home/release'}
WEB_FILES = (
    'index.html', 'aboutlibraries.json', 'LICENSE', 'zhihu-notifications-sw.js', 'zhihu-app-bootstrap.js', 'zhihu-app-cache.js',
    'manifest.webmanifest', 'icon-192.png', 'icon-512.png',
    'voice-codec/ffmpeg-core.js', 'voice-codec/ffmpeg-core.wasm', 'voice-codec/licenses/sources.json',
    'export/katex/katex.min.css', 'export/katex/LICENSE', 'export/html2canvas-pro/LICENSE', 'export/fflate/LICENSE',
    'nlp/offline-nlp-worker.mjs', 'nlp/ort/ort.wasm.min.mjs', 'nlp/ort/ort-wasm-simd-threaded.mjs',
    'nlp/ort/ort-wasm-simd-threaded.wasm', 'nlp/ort/package.json', 'nlp/ort/LICENSE', 'nlp/ort/ThirdPartyNotices.txt',
    'nlp/hashes/sha2.js', 'nlp/hashes/_md.js', 'nlp/hashes/_u64.js', 'nlp/hashes/utils.js',
    'nlp/hashes/package.json', 'nlp/hashes/LICENSE',
)


def relative_name(name):
    path = PurePosixPath(name)
    if not path.parts or path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:
        raise RuntimeError(f'Invalid packaged resource path: {name}')
    return path.as_posix()


def file_reader(root):
    root = root.resolve(strict=True)

    def read(name):
        path = (root / relative_name(name)).resolve(strict=True)
        if not path.is_relative_to(root):
            raise RuntimeError(f'Resource escapes package root: {name}')
        data = path.read_bytes()
        if not data:
            raise RuntimeError(f'Empty packaged resource: {name}')
        return data

    return read


def check_license(manifest, identifier, originals):
    libraries = [item for item in manifest.get('libraries', []) if item.get('uniqueId') == identifier]
    if len(libraries) != 1 or not libraries[0].get('licenses'):
        raise RuntimeError(f'Missing or duplicate packaged license entry: {identifier}')
    texts = [manifest['licenses'][key].get('content', '') for key in libraries[0]['licenses']]
    content = '\n\n'.join(texts).replace('\r\n', '\n').replace('\r', '\n')
    originals = [text.replace('\r\n', '\n').replace('\r', '\n') for text in originals]
    if any(not original.strip() or original not in content for original in originals):
        raise RuntimeError(f'Original notices missing from displayed license: {identifier}')
    return libraries[0]


def check_components(read, manifest, directory, platform=None, id_prefix=''):
    sources = json.loads(read(directory + '/sources.json'))
    if not sources.get('components'):
        raise RuntimeError(f'Empty packaged license provenance: {directory}')
    for item in sources['components']:
        if not item.get('licenseFiles'):
            raise RuntimeError(f'Missing original license paths: {item["id"]}')
        originals = [read(directory + '/' + relative_name(name)).decode('utf-8') for name in item['licenseFiles']]
        if platform is None or platform in item.get('platforms', [platform]):
            check_license(manifest, id_prefix + item['id'], originals)
    return sources


def verify_web_inventory(root):
    read = file_reader(root)
    pointer_bytes = read('web-version.json')
    pointer = json.loads(pointer_bytes)
    if (len(pointer_bytes) > 2048 or pointer.get('schema') != 1 or
            type(pointer.get('versionCode')) is not int or pointer['versionCode'] < 1 or
            not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,63}', pointer.get('versionName', '')) or
            not re.fullmatch(r'[a-f0-9]{16,64}', pointer.get('buildId', ''))):
        raise RuntimeError('Invalid published Web version pointer')
    base = f'app-resources/{pointer["versionCode"]}-{pointer["versionName"]}/'
    if pointer.get('manifest') != base + 'app-assets.json':
        raise RuntimeError('Web inventory is not version-qualified')
    manifest_bytes = read(pointer['manifest'])
    if hashlib.sha256(manifest_bytes).hexdigest() != pointer.get('sha256'):
        raise RuntimeError('Web inventory integrity differs from version pointer')
    manifest = json.loads(manifest_bytes)
    if (manifest.get('schema') != 1 or manifest.get('base') != base or
            any(manifest.get(key) != pointer[key] for key in ('versionName', 'versionCode', 'buildId'))):
        raise RuntimeError('Web inventory and published version disagree')
    assets = manifest.get('assets', [])
    if not assets or len(assets) > 10000 or len(manifest_bytes) > 4194304:
        raise RuntimeError('Invalid Web inventory size')
    paths = set()
    for asset in assets:
        path = relative_name(asset['path'])
        if (path != asset['path'] or re.search(r'[%?#\x00-\x1f\x7f]', path) or path in paths or
                path.endswith(('.map', '.onnx', '.onnx_data')) or asset.get('group') not in ('core', 'lazy')):
            raise RuntimeError(f'Invalid or duplicate Web app asset: {path}')
        paths.add(path)
        data = read(base + path)
        if type(asset.get('bytes')) is not int or asset['bytes'] != len(data) or hashlib.sha256(data).hexdigest() != asset.get('sha256'):
            raise RuntimeError(f'Web app resource integrity mismatch: {path}')
        if path.startswith(('voice-codec/', 'nlp/', 'export/')) and asset['group'] != 'lazy':
            raise RuntimeError(f'Runtime resource must remain lazy: {path}')
    actual = {path.relative_to(root / base).as_posix() for path in (root / base).rglob('*') if path.is_file()}
    if actual != paths | {'app-assets.json'}:
        raise RuntimeError('Web inventory does not cover the finalized distribution')
    if not all(any(asset['path'] == path and asset['group'] == 'core' for asset in assets)
               for path in (manifest.get('entry'), 'index.html', 'zhihu-app-bootstrap.js')):
        raise RuntimeError('Web bootstrap or entry missing from atomic core')
    if not re.fullmatch(r'zhihu\.[a-f0-9]+\.js', manifest['entry']):
        raise RuntimeError('Missing content-addressed Web entry')
    shell = read('index.html').decode('utf-8')
    for marker in ('name="zhihu-cache-mode" content="production"',
                   f'name="zhihu-resource-base" content="{base}"',
                   f'name="zhihu-app-entry" content="{manifest["entry"]}"',
                   f'name="zhihu-version-name" content="{pointer["versionName"]}"',
                   f'name="zhihu-version-code" content="{pointer["versionCode"]}"'):
        if marker not in shell:
            raise RuntimeError(f'Web shell is missing metadata: {marker}')
    if re.search(r'<base\b', shell, re.I) or re.search(r'<script[^>]+src=["\'][^"\']*zhihu\.', shell, re.I):
        raise RuntimeError('Web main must start after cache selection without changing the page base')
    stable = {'index.html', 'zhihu-app-bootstrap.js', 'zhihu-app-cache.js', 'zhihu-notifications-sw.js',
              'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'LICENSE'}
    for path in stable:
        if path not in paths or read(path) != read(base + path):
            raise RuntimeError(f'Missing or mismatched stable Web bootstrap: {path}')
    for file in root.rglob('*'):
        if not file.is_file():
            continue
        path = file.relative_to(root).as_posix()
        if path == 'web-version.json' or path.startswith(base):
            continue
        if path not in stable and not path.endswith('.user.js'):
            raise RuntimeError(f'Unversioned or stale Web resource: {path}')
        if path not in paths or read(path) != read(base + path):
            raise RuntimeError(f'Root bootstrap differs from versioned inventory: {path}')
    return root / base


def verify_web(root):
    resources = verify_web_inventory(root)
    read = file_reader(resources)
    for name in WEB_FILES:
        read(name)
    if not any(path.is_file() and path.stat().st_size for path in (resources / 'export/katex/fonts').glob('*.woff2')):
        raise RuntimeError('Missing local KaTeX fonts')
    for name in ('voice-codec/ffmpeg-core.wasm', 'nlp/ort/ort-wasm-simd-threaded.wasm'):
        if read(name)[:8] != b'\0asm\x01\0\0\0':
            raise RuntimeError(f'Invalid packaged WASM runtime: {name}')
    manifest = json.loads(read('aboutlibraries.json'))
    for directory, name, notices in (
        ('nlp/ort', 'onnxruntime-web', ('LICENSE', 'ThirdPartyNotices.txt')),
        ('nlp/hashes', '@noble/hashes', ('LICENSE',)),
    ):
        package = json.loads(read(directory + '/package.json'))
        entry = check_license(manifest, 'npm:' + name, [read(directory + '/' + notice).decode('utf-8') for notice in notices])
        if package.get('name') != name or not package.get('version') or entry.get('artifactVersion') != package['version']:
            raise RuntimeError(f'Runtime and license metadata version disagree: {name}')
    check_components(read, manifest, 'voice-codec/licenses', id_prefix='native:ffmpegwasm-')
    print('Web versioned inventory, sizes/hashes, bootstrap, lazy runtimes, fonts and original/displayed licenses verified')


def verify_desktop(root, platform):
    runtime = file_reader(root)(RUNTIME_RELEASE[platform]).decode('utf-8')
    if not re.search(r'^JAVA_VERSION="25(?:[.+\-][^"\r\n]*)?"\r?$', runtime, re.MULTILINE):
        raise RuntimeError('Packaged desktop runtime must be Java 25')
    modules = re.search(r'^MODULES="([^"\r\n]*)"\r?$', runtime, re.MULTILINE)
    if not modules or not {'jdk.xml.dom', 'jdk.jsobject'}.issubset(modules[1].split()):
        raise RuntimeError('Packaged desktop runtime is missing JavaFX modules jdk.xml.dom or jdk.jsobject')
    if platform == 'macos':
        info = plistlib.loads(file_reader(root)('Contents/Info.plist'))
        if info.get('LSMinimumSystemVersion') != '13.4':
            raise RuntimeError('JVM macOS app must declare the bundled ORT minimum macOS 13.4')
    jar_directory = root / {'windows': 'app', 'linux': 'lib/app', 'macos': 'Contents/app'}[platform]
    candidates = []
    for jar in jar_directory.glob('*.jar'):
        with ZipFile(jar) as archive:
            if FONTS[0] in archive.namelist():
                candidates.append(jar)
    if len(candidates) != 1:
        raise RuntimeError(f'Expected one packaged JAR with Noto Sans SC PDF fonts, found {len(candidates)}')
    with ZipFile(candidates[0]) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError('Packaged application JAR contains duplicate entries')

        def read(name):
            data = archive.read(relative_name(name))
            if not data:
                raise RuntimeError(f'Empty packaged JAR resource: {name}')
            return data

        manifest = json.loads(read('aboutlibraries.json'))
        source = check_components(read, manifest, 'META-INF/licenses/noto-sans-sc')
        generated = source.get('generated', [])
        if len(generated) != 2 or {item['file'] for item in generated} != set(FONTS):
            raise RuntimeError('PDF font provenance must describe the actual Regular/Bold SC files')
        for item in generated:
            font = read(item['file'])
            if font[:4] != b'\0\x01\0\0' or hashlib.sha256(font).hexdigest() != item['sha256']:
                raise RuntimeError(f'Packaged static TrueType font differs from provenance: {item["file"]}')
        check_components(read, manifest, 'META-INF/licenses/bytedeco', 'macosx' if platform == 'macos' else platform)
        check_components(read, manifest, 'META-INF/licenses/onnxruntime')
    print(f'Desktop {platform}: Java 25, PDF fonts and original/displayed native licenses verified in {candidates[0].name}')


def macos_minimum(load_commands):
    minimums = []
    for block in re.split(r'Load command \d+\n', load_commands):
        if re.search(r'^\s*cmd LC_BUILD_VERSION$', block, re.MULTILINE):
            if not re.search(r'^\s*platform (?:1|macOS|MACOS)$', block, re.MULTILINE):
                raise RuntimeError('Native macOS binary targets a different Apple platform')
            match = re.search(r'^\s*minos ([0-9.]+)$', block, re.MULTILINE)
        elif re.search(r'^\s*cmd LC_VERSION_MIN_MACOSX$', block, re.MULTILINE):
            match = re.search(r'^\s*version ([0-9.]+)$', block, re.MULTILINE)
        else:
            continue
        if not match:
            raise RuntimeError('Missing macOS minimum deployment version in Mach-O command')
        minimums.append(match[1])
    if len(minimums) != 1:
        raise RuntimeError('Expected one macOS deployment command per thin arm64 binary')
    return minimums[0]


def os_version(value):
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?', value):
        raise RuntimeError(f'Invalid macOS deployment version: {value}')
    return tuple(int(part) for part in (value.split('.') + ['0'])[:3])


def verify_native_macos(app, lock_path, evidence):
    if evidence.exists():
        raise RuntimeError('Native macOS evidence path must be new')
    evidence.mkdir(parents=True)
    info = plistlib.loads((app / 'Contents/Info.plist').read_bytes())
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    declared = info['LSMinimumSystemVersion']
    executable = relative_name(info['CFBundleExecutable'])
    if '/' in executable:
        raise RuntimeError('Invalid native macOS executable name')
    paths = {'app': app / 'Contents/MacOS' / executable,
             'onnxruntime': app / 'Contents/Frameworks' / relative_name(lock['macosArm64']['fileName'])}
    minimums = {}
    for name, path in paths.items():
        if subprocess.check_output(['/usr/bin/lipo', '-archs', str(path)], text=True).split() != ['arm64']:
            raise RuntimeError(f'Expected native macOS arm64 binary: {name}')
        commands = subprocess.check_output(['/usr/bin/otool', '-l', str(path)], text=True)
        (evidence / f'{name}-load-commands.txt').write_text(commands, encoding='utf-8')
        minimums[name] = macos_minimum(commands)
    record = dict(bundleMinimumOS=declared, binaryMinimumOS=minimums, lockedOrtMinimumOS=lock['macosArm64']['minimumOS'])
    (evidence / 'minimum-os.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    if os_version(declared) < max(os_version(version) for version in (*minimums.values(), record['lockedOrtMinimumOS'])):
        raise RuntimeError('Native macOS bundle advertises an OS below its executable or runtime requirement')
    read = file_reader(app / 'Contents/Resources')
    manifest = json.loads(read('aboutlibraries.json'))
    check_license(manifest, 'native:onnxruntime', [read('licenses/onnxruntime/' + name).decode('utf-8') for name in ('LICENSE', 'ThirdPartyNotices.txt')])
    print('Native macOS arm64 deployment requirements and ORT licenses verified: ' + json.dumps(record))


def self_check():
    def reject(action):
        try:
            action()
        except (RuntimeError, FileNotFoundError, KeyError):
            return
        raise AssertionError('Incomplete package accepted')

    modern = 'Load command 10\n      cmd LC_BUILD_VERSION\n platform 1\n    minos 14.0\n'
    legacy = 'Load command 11\n      cmd LC_VERSION_MIN_MACOSX\n  version 12.0\n'
    assert macos_minimum(modern) == '14.0'
    assert macos_minimum(legacy) == '12.0'
    assert os_version('14.0') == os_version('14.0.0') > os_version('12.0')
    for invalid in ('', modern + legacy, modern.replace('platform 1', 'platform 2'), modern.replace('minos 14.0', 'sdk 26.0')):
        reject(lambda: macos_minimum(invalid))
    with tempfile.TemporaryDirectory(prefix='zhpp-resources-self-check-') as temporary:
        root = Path(temporary)
        manifest = dict(libraries=[], licenses={})
        def licensed(identifier, text, version='1'):
            manifest['libraries'].append(dict(uniqueId=identifier, licenses=[identifier], artifactVersion=version))
            manifest['licenses'][identifier] = dict(content=text)
        web = root / 'web'
        base = 'app-resources/1-1/'
        resources = web / base
        entry = 'zhihu.' + 'a' * 20 + '.js'
        for name in WEB_FILES + ('export/katex/fonts/example.woff2', entry, 'lazy-chunk.js'):
            (resources / name).parent.mkdir(parents=True, exist_ok=True)
            (resources / name).write_bytes(b'fixture')
        for name in ('voice-codec/ffmpeg-core.wasm', 'nlp/ort/ort-wasm-simd-threaded.wasm'):
            (resources / name).write_bytes(b'\0asm\x01\0\0\0')
        for directory, name in (('nlp/ort', 'onnxruntime-web'), ('nlp/hashes', '@noble/hashes')):
            (resources / directory / 'package.json').write_text(json.dumps(dict(name=name, version='1')))
            licensed('npm:' + name, 'fixture')
        (resources / 'voice-codec/licenses/NOTICE').write_text('fixture')
        (resources / 'voice-codec/licenses/sources.json').write_text(json.dumps(dict(components=[dict(id='runtime', licenseFiles=['NOTICE'])])))
        licensed('native:ffmpegwasm-runtime', 'fixture')
        (resources / 'aboutlibraries.json').write_text(json.dumps(manifest))
        (resources / 'index.html').write_text('<meta name="zhihu-cache-mode" content="production">' +
            f'<meta name="zhihu-resource-base" content="{base}"><meta name="zhihu-app-entry" content="{entry}">' +
            '<meta name="zhihu-version-name" content="1"><meta name="zhihu-version-code" content="1">' +
            '<script src="zhihu-app-bootstrap.js"></script>')
        for name in ('index.html', 'zhihu-app-bootstrap.js', 'zhihu-app-cache.js', 'zhihu-notifications-sw.js', 'LICENSE',
                     'manifest.webmanifest', 'icon-192.png', 'icon-512.png'):
            (web / name).write_bytes((resources / name).read_bytes())
        assets = [dict(path=path.relative_to(resources).as_posix(), bytes=path.stat().st_size,
                       sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                       group='lazy' if path.relative_to(resources).as_posix().startswith(('nlp/', 'voice-codec/', 'export/')) else 'core')
                  for path in sorted(resources.rglob('*')) if path.is_file()]
        inventory = dict(schema=1, versionName='1', versionCode=1, buildId='a' * 20, base=base, entry=entry, assets=assets)
        inventory_bytes = json.dumps(inventory).encode()
        (resources / 'app-assets.json').write_bytes(inventory_bytes)
        (web / 'web-version.json').write_text(json.dumps(dict(schema=1, versionName='1', versionCode=1, buildId='a' * 20,
            manifest=base + 'app-assets.json', sha256=hashlib.sha256(inventory_bytes).hexdigest())))
        verify_web(web)
        for name in ('nlp/offline-nlp-worker.mjs', 'nlp/hashes/_u64.js', 'nlp/ort/ThirdPartyNotices.txt', 'lazy-chunk.js'):
            saved = (resources / name).read_bytes()
            (resources / name).unlink()
            reject(lambda: verify_web(web))
            (resources / name).write_bytes(saved + b'tampered')
            reject(lambda: verify_web(web))
            (resources / name).write_bytes(saved)
        (resources / 'unexpected.js').write_text('unexpected')
        reject(lambda: verify_web(web))
        (resources / 'unexpected.js').unlink()
        (web / 'unversioned.js').write_text('unexpected')
        reject(lambda: verify_web(web))
        (web / 'unversioned.js').unlink()
        reject(lambda: check_license(manifest, 'npm:onnxruntime-web', ['missing notice']))
        for name in ('../escape', '/absolute', 'C:/escape', 'a\\b'):
            reject(lambda: relative_name(name))
        fonts = {name: b'\0\x01\0\0synthetic TrueType' for name in FONTS}
        sources = dict(components=[dict(id='font:noto-sans-sc', licenseFiles=['OFL.txt', 'NOTICE.txt'])],
                       generated=[dict(file=name, sha256=hashlib.sha256(data).hexdigest()) for name, data in fonts.items()])
        licensed('font:noto-sans-sc', 'fixture')
        licensed('native:codec', 'fixture')
        licensed('com.microsoft.onnxruntime:onnxruntime', 'ORT license\n\nORT notices', '1.23.2')
        codec = dict(components=[dict(id='native:codec', licenseFiles=['LICENSE'])])
        ort = dict(components=[dict(id='com.microsoft.onnxruntime:onnxruntime', licenseFiles=['LICENSE', 'ThirdPartyNotices.txt'])])
        for platform, directory in (('windows', 'app'), ('linux', 'lib/app'), ('macos', 'Contents/app')):
            target = root / platform / directory
            target.mkdir(parents=True)
            runtime = root / platform / RUNTIME_RELEASE[platform]
            runtime.parent.mkdir(parents=True)
            runtime.write_text('JAVA_VERSION="25.0.1"\nMODULES="java.base jdk.xml.dom jdk.jsobject"\n')
            if platform == 'macos':
                (root / platform / 'Contents/Info.plist').write_bytes(plistlib.dumps(dict(LSMinimumSystemVersion='13.4')))
            for variant in ('valid', 'bad-font', 'missing-ort-notices'):
                with ZipFile(target / 'actual-app-name.jar', 'w') as archive:
                    for name, data in fonts.items():
                        archive.writestr(name, b'broken' if variant == 'bad-font' else data)
                    archive.writestr('aboutlibraries.json', json.dumps(manifest))
                    archive.writestr('META-INF/licenses/noto-sans-sc/sources.json', json.dumps(sources))
                    for name in ('OFL.txt', 'NOTICE.txt'):
                        archive.writestr('META-INF/licenses/noto-sans-sc/' + name, 'fixture')
                    archive.writestr('META-INF/licenses/bytedeco/sources.json', json.dumps(codec))
                    archive.writestr('META-INF/licenses/bytedeco/LICENSE', 'fixture')
                    archive.writestr('META-INF/licenses/onnxruntime/sources.json', json.dumps(ort))
                    archive.writestr('META-INF/licenses/onnxruntime/LICENSE', 'ORT license')
                    if variant != 'missing-ort-notices':
                        archive.writestr('META-INF/licenses/onnxruntime/ThirdPartyNotices.txt', 'ORT notices')
                if variant == 'valid':
                    verify_desktop(root / platform, platform)
                    if platform == 'macos':
                        info = root / platform / 'Contents/Info.plist'
                        info.write_bytes(plistlib.dumps(dict(LSMinimumSystemVersion='12.0')))
                        reject(lambda: verify_desktop(root / platform, platform))
                        info.write_bytes(plistlib.dumps(dict(LSMinimumSystemVersion='13.4')))
                    runtime.write_text('JAVA_VERSION="21.0.9"\nMODULES="java.base jdk.xml.dom jdk.jsobject"\n')
                    reject(lambda: verify_desktop(root / platform, platform))
                    for modules in ('java.base jdk.jsobject', 'java.base jdk.xml.dom',
                                    'java.base jdk.xml.dom.extra jdk.jsobject', ''):
                        runtime.write_text(f'JAVA_VERSION="25.0.1"\nMODULES="{modules}"\n')
                        reject(lambda: verify_desktop(root / platform, platform))
                    runtime.write_text('JAVA_VERSION="25.0.1"\n')
                    reject(lambda: verify_desktop(root / platform, platform))
                    runtime.write_text('JAVA_VERSION="25.0.1"\nMODULES="java.base jdk.xml.dom jdk.jsobject"\n')
                else:
                    reject(lambda: verify_desktop(root / platform, platform))
    print('Package resource failure checks passed; no application or runtime executed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--web', type=Path)
    mode.add_argument('--desktop', type=Path)
    mode.add_argument('--native-macos', type=Path)
    mode.add_argument('--self-check', action='store_true')
    parser.add_argument('--platform', choices=('windows', 'linux', 'macos'))
    parser.add_argument('--ort-lock', type=Path)
    parser.add_argument('--evidence', type=Path)
    args = parser.parse_args()
    if args.self_check:
        self_check()
    elif args.web:
        verify_web(args.web.resolve())
    elif args.native_macos:
        if not args.ort_lock or not args.evidence:
            parser.error('--native-macos requires --ort-lock and --evidence')
        verify_native_macos(args.native_macos.resolve(), args.ort_lock.resolve(), args.evidence.resolve())
    elif args.platform:
        verify_desktop(args.desktop.resolve(), args.platform)
    else:
        parser.error('--desktop requires --platform')
