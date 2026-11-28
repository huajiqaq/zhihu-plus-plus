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

"""校验官方 NuGet 制品并只提取 Apple C API、所需 slice 和原许可。"""

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import tempfile
import urllib.parse
import urllib.request
import zipfile


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def safe_entries(package):
    names = set()
    for entry in package.infolist():
        path = PurePosixPath(entry.filename)
        if path.is_absolute() or '..' in path.parts or '\\' in entry.filename or ':' in entry.filename or entry.filename in names:
            raise ValueError(f'Unsafe/duplicate archive path: {entry.filename}')
        names.add(entry.filename)
        yield entry


def copy_entry(package, entry, destination):
    if entry.is_dir():
        return
    if stat.S_ISLNK(entry.external_attr >> 16):
        raise ValueError(f'Unexpected symlink in selected framework slice: {entry.filename}')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with package.open(entry) as source, destination.open('wb') as target:
        shutil.copyfileobj(source, target)


def prepare(lock, cache, output):
    archive = cache / lock['sha256'] / Path(urllib.parse.urlparse(lock['url']).path).name
    if not archive.is_file() or archive.stat().st_size != lock['size'] or digest(archive) != lock['sha256']:
        cache.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='download-', dir=cache) as temporary:
            downloaded = Path(temporary) / archive.name
            with urllib.request.urlopen(lock['url'], timeout=180) as response, downloaded.open('wb') as destination:
                target = urllib.parse.urlparse(response.url)
                if target.scheme != 'https' or target.hostname not in ('api.nuget.org', 'nuget.azure.cn', 'globalcdn.nuget.org'):
                    raise ValueError('Unexpected NuGet redirect origin')
                shutil.copyfileobj(response, destination)
            if downloaded.stat().st_size != lock['size'] or digest(downloaded) != lock['sha256']:
                raise ValueError('ONNX Runtime NuGet checksum/size mismatch')
            archive.parent.mkdir(parents=True, exist_ok=True)
            downloaded.replace(archive)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='apple-ort-', dir=output.parent) as temporary:
        staged = Path(temporary)
        with zipfile.ZipFile(archive) as package:
            for entry in safe_entries(package):
                if entry.filename == lock['macosArm64']['entry']:
                    copy_entry(package, entry, staged / 'macosArm64' / lock['macosArm64']['fileName'])
                elif entry.filename in ('LICENSE', 'ThirdPartyNotices.txt'):
                    copy_entry(package, entry, staged / 'licenses' / entry.filename)
            ios_bytes = package.read(lock['iosArchive'])
            if hashlib.sha256(ios_bytes).hexdigest() != lock['iosArchiveSha256']:
                raise ValueError('Nested official XCFramework checksum mismatch')
        with zipfile.ZipFile(io.BytesIO(ios_bytes)) as frameworks:
            header_prefix = str(PurePosixPath(lock['header']).parent) + '/'
            for entry in safe_entries(frameworks):
                # 未选择的 Mac Catalyst slice 自带 symlink；不提取、不跟随它们。
                for slice_name in lock['iosSlices']:
                    prefix = f'onnxruntime.xcframework/{slice_name}/'
                    if entry.filename.startswith(prefix):
                        copy_entry(frameworks, entry, staged / slice_name / entry.filename.removeprefix(prefix))
                        break
                if entry.filename.startswith(header_prefix):
                    copy_entry(frameworks, entry, staged / 'include' / entry.filename.removeprefix(header_prefix))
        header = staged / 'include/onnxruntime_c_api.h'
        if digest(header) != lock['headerSha256'] or f'#define ORT_API_VERSION {lock["apiVersion"]}' not in header.read_text():
            raise ValueError('Expected the exact official C API header from the iOS slice')
        for name, expected in lock['additionalHeaderSha256'].items():
            if digest(staged / 'include' / name) != expected:
                raise ValueError(f'Unexpected transitive C API header: {name}')
        dylib = staged / 'macosArm64' / lock['macosArm64']['fileName']
        if digest(dylib) != lock['macosArm64']['sha256'] or dylib.stat().st_size != lock['macosArm64']['size']:
            raise ValueError('Unexpected macOS arm64 ONNX Runtime binary')
        dylib.chmod(0o755)
        for slice_name in lock['iosSlices']:
            framework = staged / slice_name / 'onnxruntime.framework'
            if not (framework / 'onnxruntime').is_file() or digest(framework / 'Headers/onnxruntime_c_api.h') != lock['headerSha256']:
                raise ValueError(f'Missing or mismatched official iOS framework slice: {slice_name}')
        for name in ('LICENSE', 'ThirdPartyNotices.txt'):
            if not (staged / 'licenses' / name).is_file():
                raise ValueError(f'Missing upstream notice: {name}')
        (staged / 'licenses/sources.json').write_text(json.dumps(lock, indent=2) + '\n', encoding='utf-8')
        if output.exists():
            shutil.rmtree(output)
        shutil.copytree(staged, output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', required=True, type=Path)
    parser.add_argument('--cache', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    prepare(json.loads(args.lock.read_text(encoding='utf-8')), args.cache.resolve(), args.output.resolve())


if __name__ == '__main__':
    main()
