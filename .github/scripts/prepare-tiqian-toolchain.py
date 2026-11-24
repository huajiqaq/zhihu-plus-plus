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

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
import zipfile


def digest_directory(directory):
    files = {}
    for path in sorted(directory.rglob('*')):
        name = path.relative_to(directory).as_posix()
        if path.is_symlink():
            target = path.resolve()
            if not target.is_relative_to(directory.resolve()) or not target.is_file():
                raise ValueError('工具链接必须指向源码树内的文件：' + name)
            path = target
        if path.is_file():
            files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return hashlib.sha256(json.dumps(files, sort_keys=True).encode('utf-8')).hexdigest()


def download(url, path, expected=None):
    with urllib.request.urlopen(url, timeout=60) as response, path.open('xb') as output:
        shutil.copyfileobj(response, output)
    if expected and hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError('工具下载摘要不符：' + path.name)


def unpack_zip(archive, destination):
    with zipfile.ZipFile(archive) as contents:
        for entry in contents.infolist():
            name = PurePosixPath(entry.filename)
            if name.is_absolute() or '..' in name.parts or '\\' in entry.filename or ':' in entry.filename:
                raise ValueError('压缩包路径越界：' + entry.filename)
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('工具 ZIP 不接受链接：' + entry.filename)
        contents.extractall(destination)


def prepare(lock, output):
    generation = json.loads(lock.read_text(encoding='utf-8'))['sources']['tiqian-core']['generation']
    tools = {}
    for name in ('haxe', 'node'):
        executable = shutil.which(name)
        if not executable:
            raise ValueError('工具未安装：' + name)
        tools[name] = str(Path(executable).resolve())
    version = subprocess.check_output([tools['haxe'], '--version'], text=True).strip()
    if version != generation['haxeVersion']:
        raise ValueError('Haxe 版本不符：' + version)
    if os.name == 'nt':
        neko = shutil.which('neko')
        if not neko:
            raise ValueError('Windows Haxe 缺少 Neko')
        tools['neko'] = str(Path(neko).resolve())
    output.mkdir(parents=True, exist_ok=False)
    for name, spec in generation['tools'].items():
        item = output / name
        item.mkdir()
        extracted = item / 'source'
        extracted.mkdir()
        if 'commit' in spec:
            repository = urllib.parse.urlparse(spec['repository'])
            if repository.scheme != 'https' or repository.netloc != 'github.com':
                raise ValueError('工具源码必须来自官方 GitHub 仓库')
            path = repository.path.removesuffix('.git').strip('/')
            if len(path.split('/')) != 2:
                raise ValueError('工具仓库路径无效')
            archive = item / 'source.tar.gz'
            download(f"https://codeload.github.com/{path}/tar.gz/{spec['commit']}", archive)
            with tarfile.open(archive) as contents:
                contents.extractall(extracted, filter='data')
            roots = list(extracted.iterdir())
            if len(roots) != 1 or not roots[0].is_dir():
                raise ValueError('工具源码根目录不唯一：' + name)
            directory = roots[0]
        else:
            archive = item / 'source.zip'
            download(spec['url'], archive, spec['archiveSha256'])
            unpack_zip(archive, extracted)
            directory = extracted / 'release' if name == 'format' else extracted
        if digest_directory(directory) != spec['sha256']:
            raise ValueError('工具源码摘要不符：' + name)
        tools[name] = str(directory.resolve())
    location = output / 'toolchain.json'
    location.write_text(json.dumps(tools, indent=2) + '\n', encoding='utf-8')
    return location.resolve()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lock', type=Path, default=Path('gradle/upstream-sources.lock.json'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--env', type=Path)
    args = parser.parse_args()
    location = prepare(args.lock.resolve(), args.output.resolve())
    if args.env:
        with args.env.open('a', encoding='utf-8') as output:
            output.write('ZHIHU_TIQIAN_TOOLCHAIN=' + str(location) + '\n')
    print(location)


if __name__ == '__main__':
    main()
