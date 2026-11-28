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

"""Wrap the project's Linux x64 jpackage image; never launch the application."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile


APPIMAGETOOL_VERSION = '1.9.1'
APPIMAGETOOL_SHA256 = 'ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0'
# glibc and its loader must come from the host, not from the private helper directory.
HOST_LIBRARIES = frozenset({
    'ld-linux-x86-64.so.2', 'libc.so.6', 'libm.so.6', 'libpthread.so.0',
    'libdl.so.2', 'librt.so.1', 'libresolv.so.2', 'libutil.so.1', 'libanl.so.1',
})


def output(*args):
    return subprocess.check_output([str(arg) for arg in args], text=True).strip()


def elf_dependencies(executable):
    # setup-python must not hide the distro's python3-pyelftools used by pax-utils.
    paths = output('/usr/bin/python3', '-I', '/usr/bin/lddtree', '-l', executable).splitlines()
    if not paths or any(not Path(path).is_absolute() or not Path(path).is_file() for path in paths):
        raise RuntimeError(f'Unresolved ELF dependency for {executable}: {paths}')
    return [Path(path) for path in paths if Path(path).resolve() != executable.resolve()]


def package_metadata(library):
    # Account for merged-/usr aliases in dpkg's recorded file paths.
    candidates = [library, library.resolve()]
    for path in tuple(candidates):
        if str(path).startswith('/usr/lib/'):
            candidates.append(Path(str(path)[4:]))
        elif str(path).startswith('/lib/'):
            candidates.append(Path('/usr' + str(path)))
    for path in dict.fromkeys(candidates):
        lookup = subprocess.run(['dpkg-query', '-S', str(path)], capture_output=True, text=True)
        if lookup.returncode == 0:
            owners = {line.rsplit(': ', 1)[0] for line in lookup.stdout.splitlines() if ': ' in line}
            if len(owners) != 1:
                raise RuntimeError(f'Ambiguous library owner: {path}: {owners}')
            package = owners.pop()
            fields = output('dpkg-query', '-W', '-f=${binary:Package}\t${Version}\t${source:Package}\t${source:Version}\t${Homepage}', package).split('\t')
            if len(fields) < 4:
                raise RuntimeError(f'Missing package/source metadata: {package}')
            return dict(zip(('package', 'version', 'sourcePackage', 'sourceVersion', 'homepage'), fields))
    raise RuntimeError(f'No Debian package/source metadata for {library}')


def bundle_reading_libraries(appdir):
    # Compose flattens appResourcesRootDir/common before jpackage copies it into lib/app/resources.
    helper = appdir / 'lib/app/resources/reading/zhihu-reading-host'
    if not helper.is_file() or helper.is_symlink():
        candidates = [
            f'{path.relative_to(appdir)} (mode={oct(path.lstat().st_mode & 0o7777)}, executable={os.access(path, os.X_OK)})'
            for path in sorted(appdir.rglob('zhihu-reading-host'))
        ]
        raise RuntimeError(
            f'Expected a regular reading helper at {helper.relative_to(appdir)}; '
            f'file_exists={helper.is_file()}; packaged helper paths={candidates}'
        )
    with helper.open('rb') as stream:
        header = stream.read(20)
    if (len(header) != 20 or header[:7] != b'\x7fELF\x02\x01\x01'
            or header[16:18] not in (b'\x02\x00', b'\x03\x00') or header[18:20] != b'\x3e\x00'):
        raise RuntimeError(f'Expected an x86_64 ELF executable reading helper: {helper.relative_to(appdir)}')
    # Compose's File.copyTo copies resource bytes, not POSIX modes; normalize only this validated app-owned executable.
    original_mode = helper.stat().st_mode & 0o7777
    if original_mode != 0o755:
        helper.chmod(0o755)
        print(f'Normalized {helper.relative_to(appdir)} mode {oct(original_mode)} -> 0o755', flush=True)
    if not os.access(helper, os.X_OK):
        raise RuntimeError(f'Reading helper is still not executable: {helper.relative_to(appdir)}')
    libraries = helper.parent / 'lib'
    licenses = helper.parent / 'licenses'
    libraries.mkdir()
    licenses.mkdir()
    bundled = {}
    sources = []
    for library in elf_dependencies(helper):
        if library.name in HOST_LIBRARIES:
            continue
        soname = output('patchelf', '--print-soname', library) or library.name
        if Path(soname).name != soname or not soname.startswith('lib'):
            raise RuntimeError(f'Unsupported library SONAME: {soname}')
        if soname in bundled:
            if bundled[soname] != library.resolve():
                raise RuntimeError(f'Conflicting library SONAME: {soname}')
            continue
        bundled[soname] = library.resolve()
        destination = libraries / soname
        shutil.copy2(library, destination)
        subprocess.run(['patchelf', '--set-rpath', '$ORIGIN', str(destination)], check=True)
        metadata = package_metadata(library)
        package = metadata['package'].split(':', 1)[0]
        copyright_file = Path('/usr/share/doc') / package / 'copyright'
        if not copyright_file.is_file():
            raise RuntimeError(f'Missing license for {package}')
        license_directory = licenses / package
        license_directory.mkdir(exist_ok=True)
        shutil.copy2(copyright_file, license_directory / 'copyright')
        metadata.update(library=soname, originalSha256=hashlib.sha256(library.read_bytes()).hexdigest())
        sources.append(metadata)
    for prefix in ('libspeechd.so.', 'libjson-glib-1.0.so.', 'libgio-2.0.so.'):
        if not any(name.startswith(prefix) for name in bundled):
            raise RuntimeError(f'Missing required reading library: {prefix}')
    shutil.copytree('/usr/share/common-licenses', licenses / 'common-licenses')
    (licenses / 'sources.json').write_text(json.dumps(sources, indent=2) + '\n', encoding='utf-8')
    subprocess.run(['patchelf', '--set-rpath', '$ORIGIN/lib', str(helper)], check=True)
    # Check DT_NEEDED and resolution without executing the helper or starting speech-dispatcher.
    for binary in [helper, *libraries.iterdir()]:
        needed = output('patchelf', '--print-needed', binary).splitlines()
        missing = set(needed) - bundled.keys() - HOST_LIBRARIES
        if missing:
            raise RuntimeError(f'Unbundled dependency for {binary.name}: {missing}')
    for library in elf_dependencies(helper):
        if library.name not in HOST_LIBRARIES and library.resolve().parent != libraries.resolve():
            raise RuntimeError(f'Reading library resolves outside its private directory: {library}')
    (helper.parent / 'RUNTIME.txt').write_text(
        'The reading helper bundles its client libraries, not the Speech Dispatcher service.\n'
        'Install speech-dispatcher and a configured speech backend (for example speech-dispatcher-espeak-ng).\n'
        'A user-session D-Bus and working audio service are required. No service is started by packaging.\n'
        f'This package was built against {output("getconf", "GNU_LIBC_VERSION")}; glibc and its loader remain host dependencies.\n'
        'licenses/sources.json records Debian source-package names and versions; copyright files retain upstream source locations.\n'
        'Private libraries use $ORIGIN only; the JVM LD_LIBRARY_PATH is not changed.\n', encoding='utf-8')


def package(app_image, icon, destination):
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise RuntimeError('AppImage packaging supports only the existing Linux x64 target')
    app_image, icon, destination = app_image.resolve(strict=True), icon.resolve(strict=True), destination.resolve()
    if destination.exists() or destination.is_relative_to(app_image):
        raise RuntimeError('Output must be new and outside the input app-image')
    if not (app_image / 'bin/Zhihu++').is_file() or not (app_image / 'lib/app/Zhihu++.cfg').is_file():
        raise RuntimeError('Expected the Zhihu++ Linux jpackage app-image')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='zhihu-appimage-', dir=os.environ.get('RUNNER_TEMP')) as temporary:
        work = Path(temporary)
        appdir = work / 'AppDir'
        shutil.copytree(app_image, appdir, symlinks=True)
        bundle_reading_libraries(appdir)
        icon_name = 'com.github.zly2006.zhihu'
        icons = appdir / 'usr/share/icons/hicolor/512x512/apps'
        icons.mkdir(parents=True)
        shutil.copy2(icon, appdir / f'{icon_name}.png')
        shutil.copy2(icon, icons / f'{icon_name}.png')
        (appdir / '.DirIcon').symlink_to(f'{icon_name}.png')
        (appdir / 'zhihu-plus-plus.desktop').write_text(
            '[Desktop Entry]\nType=Application\nName=Zhihu++\n'
            f'Comment=自由、无广告的知乎第三方客户端\nExec=AppRun\nIcon={icon_name}\n'
            'Terminal=false\nCategories=Network;\n', encoding='utf-8')
        launcher = appdir / 'AppRun'
        launcher.write_text('#!/bin/sh\nHERE="$(dirname "$(readlink -f "$0")")"\nexec "$HERE/bin/Zhihu++" "$@"\n', encoding='utf-8')
        launcher.chmod(0o755)
        downloads = work / 'download'
        downloads.mkdir()
        tool = downloads / 'appimagetool-x86_64.AppImage'
        subprocess.run(['curl', '-fsSL', '-o', str(tool),
                        f'https://github.com/AppImage/appimagetool/releases/download/{APPIMAGETOOL_VERSION}/{tool.name}'], check=True)
        if hashlib.sha256(tool.read_bytes()).hexdigest() != APPIMAGETOOL_SHA256:
            raise RuntimeError('appimagetool checksum mismatch')
        tool.chmod(0o755)
        tool_work = work / 'tool-work'
        tool_work.mkdir()
        subprocess.run([str(tool), '--appimage-extract-and-run', str(appdir), str(destination)],
                       cwd=tool_work, env={**os.environ, 'ARCH': 'x86_64'}, check=True)
        extracted = work / 'archive-check'
        extracted.mkdir()
        subprocess.run([str(destination), '--appimage-extract'], cwd=extracted, stdout=subprocess.DEVNULL, check=True)
        relative_helper = Path('lib/app/resources/reading/zhihu-reading-host')
        packaged_helper = extracted / 'squashfs-root' / relative_helper
        if not os.access(packaged_helper, os.X_OK) or packaged_helper.read_bytes() != (appdir / relative_helper).read_bytes():
            raise RuntimeError('AppImage did not preserve its reading helper')
        for library in (appdir / relative_helper).parent.joinpath('lib').iterdir():
            if library.read_bytes() != (packaged_helper.parent / 'lib' / library.name).read_bytes():
                raise RuntimeError(f'AppImage library mismatch: {library.name}')
    print(destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app-image', type=Path, required=True)
    parser.add_argument('--icon', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    package(arguments.app_image, arguments.icon, arguments.output)
