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
import platform
import re
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor


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


def download(url, path):
    with urllib.request.urlopen(url, timeout=60) as response, path.open('xb') as output:
        shutil.copyfileobj(response, output)


def prepare_source(name, spec, output):
    if not re.fullmatch('[a-z][a-z0-9-]*', name):
        raise ValueError('Invalid tool name: ' + name)
    if not re.fullmatch('[0-9a-f]{40}', spec['commit']) or not re.fullmatch('[0-9a-f]{64}', spec['sha256']):
        raise ValueError('Tool sources require a pinned commit and SHA-256: ' + name)
    item = output / name
    item.mkdir()
    extracted = item / 'source'
    extracted.mkdir()
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
    if digest_directory(directory) != spec['sha256']:
        raise ValueError('工具源码摘要不符：' + name)
    return name, str(directory.resolve())


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
    # Each worker owns its archive and extraction tree; never publish a partial toolchain.
    with ThreadPoolExecutor(max_workers=2 if platform.system() == 'Darwin' else 1) as pool:
        prepared = list(pool.map(lambda item: prepare_source(*item, output), generation['tools'].items()))
    tools.update(prepared)
    location = output / 'toolchain.json'
    location.write_text(json.dumps(tools, indent=2) + '\n', encoding='utf-8')
    return location.resolve()


def self_check():
    import sys
    import tempfile
    import threading
    from unittest.mock import patch

    with tempfile.TemporaryDirectory(prefix='zhpp-tiqian-self-check-') as temporary:
        root = Path(temporary).resolve()
        specs, archives = {}, {}
        for name in ('boring', 'reflaxe', 'format', 'formatter'):
            fixture = root / ('fixture-' + name)
            fixture.mkdir()
            (fixture / 'source.txt').write_text(name)
            specs[name] = dict(repository='https://github.com/example/' + name, commit='a' * 40, sha256=digest_directory(fixture))
            archives[name] = root / (name + '.tar.gz')
            with tarfile.open(archives[name], 'w:gz') as archive:
                archive.add(fixture, arcname='checkout')
        lock = root / 'lock.json'
        for host, corrupt in [('Darwin', False), ('Linux', False), ('Darwin', True)]:
            selected = json.loads(json.dumps(specs))
            if corrupt:
                selected['formatter']['sha256'] = '0' * 64
            lock.write_text(json.dumps(dict(sources={'tiqian-core': dict(generation=dict(haxeVersion='4.3.7', tools=selected))})))
            output = root / f'{host}-{corrupt}'
            environment = root / f'{host}-{corrupt}.env'
            environment.write_text('existing=value\n')
            mutex, barrier = threading.Lock(), threading.Barrier(2)
            active = peak = 0
            visited = set()

            def fake_download(url, destination):
                nonlocal active, peak
                name = destination.parent.name
                assert url == f'https://codeload.github.com/example/{name}/tar.gz/' + 'a' * 40
                assert destination.parent == output / name and not destination.exists()
                with mutex:
                    visited.add(name)
                    active += 1
                    peak = max(active, peak)
                try:
                    if host == 'Darwin':
                        barrier.wait(timeout=10)
                    shutil.copyfile(archives[name], destination)
                    assert not (output / 'toolchain.json').exists()
                    assert environment.read_text() == 'existing=value\n'
                finally:
                    with mutex:
                        active -= 1

            with patch.dict(globals(), download=fake_download), patch.object(platform, 'system', return_value=host), \
                    patch.object(shutil, 'which', return_value=sys.executable), \
                    patch.object(subprocess, 'check_output', return_value='4.3.7\n'), \
                    patch.object(sys, 'argv', ['prepare', '--lock', str(lock), '--output', str(output), '--env', str(environment)]):
                try:
                    main()
                except ValueError:
                    assert corrupt
                else:
                    assert not corrupt
            assert active == 0 and peak == (2 if host == 'Darwin' else 1) and visited == specs.keys()
            location = output / 'toolchain.json'
            assert location.exists() != corrupt
            if corrupt:
                assert environment.read_text() == 'existing=value\n'
            else:
                result = json.loads(location.read_text())
                assert all(Path(result[name]) == output / name / 'source/checkout' for name in specs)
                assert environment.read_text() == 'existing=value\nZHIHU_TIQIAN_TOOLCHAIN=' + str(location) + '\n'
    print('Pinned source integrity, Apple two-worker limit and all-or-nothing environment checks passed (no downloads)')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lock', type=Path, default=Path('gradle/upstream-sources.lock.json'))
    parser.add_argument('--output', type=Path)
    parser.add_argument('--env', type=Path)
    parser.add_argument('--self-check', action='store_true')
    args = parser.parse_args()
    if args.self_check:
        self_check()
        return
    if not args.output:
        parser.error('--output is required outside --self-check')
    location = prepare(args.lock.resolve(), args.output.resolve())
    if args.env:
        with args.env.open('a', encoding='utf-8') as output:
            output.write('ZHIHU_TIQIAN_TOOLCHAIN=' + str(location) + '\n')
    print(location)


if __name__ == '__main__':
    main()
