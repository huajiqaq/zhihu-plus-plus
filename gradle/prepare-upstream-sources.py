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

"""下载构建输入并重放打包补丁。"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import urllib.parse
import urllib.request
from pathlib import Path, PurePosixPath


def relative_path(value):
    path = PurePosixPath(value)
    if not value or value != path.as_posix() or path.is_absolute() or any(part in ('', '.', '..') for part in path.parts) or '\\' in value or ':' in value:
        raise SystemExit('输入路径无效：' + value)
    return value


def file_digest(path):
    if path.is_symlink() or any(parent.is_symlink() for parent in path.parents) or not path.is_file():
        raise ValueError('文件缺失或路径包含链接：' + str(path))
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_file(path, expected):
    if file_digest(path) != expected:
        raise ValueError('SHA-256 不符：' + str(path))


def provenance_bytes(record):
    return (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')


def prepare_math_resources(source, math, wheel, gradle=None):
    compiler = math['compiler']
    fonts = math['fonts']
    resources = math['resources']
    check_file(source / relative_path(compiler['path']), compiler['sha256'])
    check_file(wheel, math['wheel']['sha256'])
    for font in fonts:
        check_file(source / relative_path(font['path']), font['sha256'])
    for relative, expected in resources.items():
        relative_path(relative)
        if relative.endswith('.txt'):
            check_file(source / relative, expected)

    output = source / relative_path(math['output'])
    if output.is_symlink() or any(parent.is_symlink() for parent in output.parents):
        raise ValueError('字体输出路径包含链接')
    record = output / 'layout-sha256.json'
    layout_names = {font['name'] + '-layout.otf' for font in fonts}
    runtime_names = {font['name'] + '-runtime.otf' for font in fonts}
    if output.exists() and any(output.iterdir()):
        file_digest(record)
        layouts = json.loads(record.read_text(encoding='utf-8'))
        if not isinstance(layouts, dict) or set(layouts) != layout_names or {path.name for path in output.iterdir()} != layout_names | runtime_names | {record.name}:
            raise ValueError('字体输出文件集合不符')
        for name, expected in layouts.items():
            check_file(output / name, expected)
        for name in runtime_names:
            check_file(output / name, resources[math['output'] + '/' + name])
    else:
        if gradle is None:
            raise ValueError('字体编译输出缺失')
        output.mkdir(parents=True, exist_ok=True)
        # -I 隔离下载源码；fonttools 只从已校验的 wheel 导入。
        bootstrap = 'import runpy,sys; sys.path.insert(0,sys.argv[1]); sys.argv=sys.argv[2:]; runpy.run_path(sys.argv[0],run_name="__main__")'
        for font in fonts:
            subprocess.run(
                [sys.executable, '-I', '-c', bootstrap, str(wheel), str(source / compiler['path']),
                 str(source / font['path']), str(output / (font['name'] + '-layout.otf')), str(output / (font['name'] + '-runtime.otf'))],
                cwd=root, check=True, stdout=sys.stderr,
            )
            name = font['name'] + '-runtime.otf'
            check_file(output / name, resources[math['output'] + '/' + name])
        with record.open('x', encoding='utf-8') as destination:
            json.dump({name: file_digest(output / name) for name in sorted(layout_names)}, destination, indent=2)
            destination.write('\n')

    if any(not (source / relative).is_file() for relative in resources):
        if gradle is None or not gradle.is_file():
            raise ValueError('生成字体元数据需要 --gradle')
        properties = [f'-P{font["property"]}{kind}Font={output / (font["name"] + "-" + kind.lower() + ".otf")}'
                      for font in fonts for kind in ('Layout', 'Runtime')]
        # 父构建传最终覆盖值，不读取上游的默认大堆。
        # shortcut: 独立准备仅读本项目的单行 key=value 预算；多行属性需升级解析。
        budget = {}
        for line in (root / 'gradle.properties').read_text(encoding='utf-8').splitlines():
            key, separator, value = line.strip().partition('=')
            if separator and key in ('org.gradle.jvmargs', 'kotlin.daemon.jvmargs', 'org.gradle.workers.max'):
                budget[key] = value.strip()
        gradle_jvmargs = os.environ.get('ZHIHU_UPSTREAM_GRADLE_JVMARGS', budget.get('org.gradle.jvmargs', ''))
        kotlin_jvmargs = os.environ.get('ZHIHU_UPSTREAM_KOTLIN_JVMARGS', budget.get('kotlin.daemon.jvmargs', ''))
        workers = int(os.environ.get('ZHIHU_UPSTREAM_GRADLE_WORKERS', budget.get('org.gradle.workers.max', '0')))
        if not gradle_jvmargs.strip() or not kotlin_jvmargs.strip() or workers < 1:
            raise ValueError('字体自举缺少有效的父构建预算')
        # 元数据生成器依赖 engine，先用仓库资源自举以避免构建循环。
        command = [str(gradle), '--project-dir', str(source), ':font:metadata-generator:bakeLeteRuntimeMetadata',
                   ':font:metadata-generator:bakeStixRuntimeMetadata', '-PtiqianCompiledMathResources=false',
                   *properties, '--no-daemon', f'--max-workers={workers}',
                   f'-Dorg.gradle.jvmargs={gradle_jvmargs}', f'-Pkotlin.daemon.jvmargs={kotlin_jvmargs}']
        subprocess.run(gradle_subprocess_command(command), cwd=root, check=True, stdout=sys.stderr)
    for relative, expected in resources.items():
        check_file(source / relative, expected)


def directory_digest(directory):
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError('工具输入目录缺失或是链接：' + str(directory))
    files = {}
    for path in sorted(directory.rglob('*')):
        relative = relative_path(path.relative_to(directory).as_posix())
        if path.is_symlink():
            target = path.resolve()
            if not target.is_relative_to(directory.resolve()) or not target.is_file():
                raise ValueError('工具链接必须指向源码树内的文件：' + relative)
            path = target
        if path.is_file():
            files[relative] = file_digest(path)
    return hashlib.sha256(json.dumps(files, sort_keys=True).encode('utf-8')).hexdigest()


def prepare_tiqian_engine(source, mirror, tree, commit, generation):
    if not args.tiqian_toolchain:
        raise SystemExit('当前提椠引擎需官方 Haxe/boring 生成；用 --tiqian-toolchain 或 ZHIHU_TIQIAN_TOOLCHAIN 提供隔离工具位置')
    toolchain = json.loads(args.tiqian_toolchain.read_text(encoding='utf-8'))
    tools = {name: Path(toolchain[name]).resolve() for name in ('haxe', 'node', *generation['tools'])}
    for name, spec in generation['tools'].items():
        if directory_digest(tools[name]) != spec['sha256']:
            raise SystemExit('提椠生成工具源码摘要不符：' + name)
    pins = json.loads((source / 'flake.lock').read_text(encoding='utf-8'))['nodes']
    if any(pins[name]['locked']['rev'] != generation['tools'][name]['commit'] for name in ('boring', 'reflaxe')):
        raise SystemExit('所选提椠的官方编译器 pin 已变化，需要审查生成工具')
    environment = {key: value for key, value in os.environ.items() if not key.startswith(('HAXE', 'NEKO', 'NODE_'))}
    environment['PATH'] = os.pathsep.join([str(tools['haxe'].parent), *([str(Path(toolchain['neko']).resolve().parent)] if toolchain.get('neko') else []), environment['PATH']])
    environment['HAXE_STD_PATH'] = str(tools['haxe'].parent / 'std')
    if toolchain.get('neko'):
        environment['NEKOPATH'] = str(Path(toolchain['neko']).resolve().parent)
    version = subprocess.run([str(tools['haxe']), '--version'], cwd=root, env=environment, check=True, capture_output=True, text=True).stdout.strip()
    if version != generation['haxeVersion']:
        raise SystemExit('提椠生成需 Haxe ' + generation['haxeVersion'] + '，实际为 ' + version)
    with tempfile.TemporaryDirectory(prefix='tiqian-generation-', dir=parent) as temporary:
        work = Path(temporary)
        # 仅明确校验过的源码进入执行 staging；下载树不作为解释器或构建 cwd。
        shutil.copytree(tools['boring'], work, dirs_exist_ok=True, symlinks=True)
        run_git(work, ['init', '--quiet'])
        project = work / 'tiqian-input'
        project.mkdir()
        shutil.copyfile(source / 'boring.json', project / 'boring.json')
        shutil.copytree(source / 'engine-haxe', project / 'engine-haxe', symlinks=True)
        compiler = project / '.haxelib/boring/git'
        shutil.copytree(tools['boring'], compiler, symlinks=True)
        for filename, patch in generation['patches'].items():
            check_file(root / 'gradle' / relative_path(filename), patch['sha256'])
            if patch['base'] != generation['tools']['boring']['commit']:
                raise SystemExit('生成工具补丁基线不符')
            command = ['git', 'apply', '--directory=' + compiler.relative_to(work).as_posix()]
            subprocess.run([*command, '--check', str(root / 'gradle' / filename)], cwd=work, check=True, stdout=sys.stderr)
            subprocess.run([*command, str(root / 'gradle' / filename)], cwd=work, check=True, stdout=sys.stderr)
        libraries = {'boring': compiler, **{name: tools[name] for name in ('reflaxe', 'format', 'formatter')}}
        for location in (work, project):
            for name, directory in libraries.items():
                registration = location / '.haxelib' / name
                registration.mkdir(parents=True, exist_ok=True)
                (registration / '.dev').write_bytes(directory.as_posix().encode('utf-8'))
        environment['HAXELIB_PATH'] = str(project / '.haxelib')
        shutil.copytree(tools['boring'] / 'tools/unicode-data', project / 'tools/unicode-data')
        subprocess.run([str(tools['haxe']), str(work / 'packages/driver/driver.hxml')], cwd=work, env=environment, check=True, stdout=sys.stderr)
        subprocess.run([str(tools['node']), str(work / 'out/driver/driver.js'), 'gen', 'kotlin-f32', '--project', str(project / 'boring.json')], cwd=work, env=environment, check=True, stdout=sys.stderr)
        for relative in generation['outputs']:
            path = project / relative_path(relative)
            if not path.is_dir() or not any(path.rglob('*.kt' if relative.endswith(('gen', 'gen-tests')) else 'build.gradle.kts')):
                raise SystemExit('官方生成输出缺失或为空：' + relative)
            generated_files = path / '_GeneratedFiles.txt'
            if generated_files.is_file():
                # reflaxe 的清理索引记录绝对路径；只搬迁索引，不改生成的 Kotlin 源码。
                names = []
                for line in generated_files.read_text(encoding='utf-8').splitlines():
                    item = Path(line).resolve().relative_to(project.resolve())
                    if not (project / item).is_file():
                        raise SystemExit('官方生成索引包含缺失文件')
                    names.append((target / 'sources' / source.name / item).as_posix())
                generated_files.write_bytes(('\n'.join(names) + '\n').encode('utf-8'))
            shutil.copytree(path, source / relative, symlinks=True)
    with tempfile.TemporaryDirectory(prefix='generated-index-', dir=parent) as temporary:
        index_environment = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'), GIT_OPTIONAL_LOCKS='0')
        run_git(mirror, ['read-tree', tree], environment=index_environment)
        entries = bytearray()
        for relative in generation['outputs']:
            for path in sorted((source / relative).rglob('*')):
                if not path.is_file():
                    continue
                file_digest(path)
                name = relative_path(path.relative_to(source).as_posix())
                oid = run_git(mirror, ['hash-object', '-w', '--stdin'], data=path.read_bytes()).stdout.decode().strip()
                mode = '100755' if path.name == 'gradlew' else '100644'
                entries.extend((mode + ' ' + oid + '\t' + name).encode('utf-8') + b'\0')
        run_git(mirror, ['update-index', '-z', '--index-info'], data=bytes(entries), environment=index_environment)
        generated_tree = run_git(mirror, ['write-tree'], environment=index_environment).stdout.decode().strip()
    base = tree_commit(mirror, tree, commit)
    generated = tree_commit(mirror, generated_tree, base)
    run_git(mirror, ['update-ref', 'refs/build-generated/' + fingerprint, generated])
    return generated_tree


def gradle_subprocess_command(arguments):
    if os.name != 'nt':
        return arguments
    # /s 只移除最外层引号；整串交给 CMD，避免 argv 转义拆开 JVM 参数。
    command = subprocess.list2cmdline(arguments)
    comspec = subprocess.list2cmdline([os.environ.get('COMSPEC', 'cmd.exe')])
    return comspec + ' /d /s /c "' + command + '"'


def run_git(directory, arguments, *, data=None, environment=None, check=True):
    if directory.is_symlink() or not directory.resolve().is_relative_to(parent.resolve()):
        raise SystemExit('Git 操作只能发生在本任务源码缓存内')
    git_environment = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
    if environment is not None:
        git_environment.update({key: environment[key] for key in ('GIT_INDEX_FILE', 'GIT_OPTIONAL_LOCKS') if key in environment})
    result = subprocess.run(
        ['git', '--no-replace-objects', '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', '-c', 'core.longpaths=true', '-c', 'http.version=HTTP/1.1', '-C', str(directory), *arguments],
        input=data, env=git_environment, capture_output=True, timeout=240,
    )
    if check and result.returncode:
        raise SystemExit('源码 Git 操作失败：\n' + (result.stderr + result.stdout).decode('utf-8', errors='replace'))
    return result


def tree_commit(directory, tree, parent_commit):
    data = (
        f'tree {tree}\nparent {parent_commit}\n'
        'author Build patches <build@example.invalid> 0 +0000\n'
        'committer Build patches <build@example.invalid> 0 +0000\n\n'
        'Reviewed build patch\n'
    ).encode('utf-8')
    return run_git(directory, ['hash-object', '-t', 'commit', '-w', '--stdin'], data=data).stdout.decode().strip()


def replay_patches(directory, commit, spec):
    current = commit
    records = []
    for filename, patch in spec.get('patches', {}).items():
        base = patch['base']
        if run_git(directory, ['cat-file', '-e', base + '^{commit}'], check=False).returncode:
            if args.offline:
                raise SystemExit('离线缺少补丁基线：' + base)
            run_git(directory, ['fetch', '--quiet', '--depth=1', spec['repository'], base])
        run_git(directory, ['update-ref', 'refs/build-inputs/' + base, base])
        with tempfile.TemporaryDirectory(prefix='patch-index-', dir=parent) as temporary:
            environment = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'), GIT_OPTIONAL_LOCKS='0')
            run_git(directory, ['read-tree', base], environment=environment)
            path = root / 'gradle' / filename
            run_git(directory, ['apply', '--cached', '--check', str(path)], environment=environment)
            run_git(directory, ['apply', '--cached', str(path)], environment=environment)
            tree = run_git(directory, ['write-tree'], environment=environment).stdout.decode().strip()
        before = run_git(directory, ['rev-parse', current + '^{tree}']).stdout.decode().strip()
        patched = tree_commit(directory, tree, base)
        merged = run_git(directory, ['merge-tree', '--write-tree', '--merge-base=' + base, current, patched], check=False)
        if merged.returncode:
            raise SystemExit('补丁与所选上游版本冲突，未修改原仓库：' + filename + '\n' + merged.stdout.decode('utf-8', errors='replace') + merged.stderr.decode('utf-8', errors='replace'))
        tree = merged.stdout.decode().splitlines()[0]
        status = 'already-present' if tree == before else 'applied'
        current = tree_commit(directory, tree, commit)
        records.append({'patch': filename, 'sha256': patch['sha256'], 'base': base, 'status': status, 'tree': tree})
    tree = run_git(directory, ['rev-parse', current + '^{tree}']).stdout.decode().strip()
    run_git(directory, ['update-ref', 'refs/build-overlays/' + fingerprint, current])
    return tree, records


def align_source_build(directory, tree, spec, host_version):
    filename = spec.get('androidPluginCatalog') or spec.get('androidPluginScript')
    if not filename:
        return tree, None
    relative_path(filename)
    data = run_git(directory, ['show', tree + ':' + filename]).stdout.decode('utf-8')
    if not re.fullmatch(r'[A-Za-z0-9_.+-]+', host_version):
        raise SystemExit('宿主 AGP 版本无效')
    if spec.get('androidPluginCatalog'):
        before = tomllib.loads(data)['versions']['agp']
        pattern = re.compile(r'^(\s*agp\s*=\s*")([^"]+)(".*)$', re.MULTILINE)
        expected_count = 1
    else:
        pattern = re.compile(r'^(\s*id\("com\.android\.[^"]+"\)\s+version\s+")([^"]+)(".*)$', re.MULTILINE)
        versions = {match.group(2) for match in pattern.finditer(data)}
        if len(versions) != 1:
            raise SystemExit('上游 AGP 插件声明缺失或版本不一致')
        before = versions.pop()
        expected_count = sum(1 for _ in pattern.finditer(data))
    build_info = {'androidPlugin': {'version': host_version}}
    changed = before != host_version
    if changed:
        data, count = pattern.subn(lambda match: match.group(1) + host_version + match.group(3), data)
        if count != expected_count:
            raise SystemExit('上游 AGP 声明结构已变，拒绝猜测修改')
        build_info['androidPlugin'].update({'from': before, 'file': filename})
    group = spec.get('sourceGroup')
    if group:
        if filename != 'build.gradle.kts' or not re.fullmatch(r'[A-Za-z0-9_.-]+', group):
            raise SystemExit('源码构建身份配置无效')
        # 多个上游都含 engine/compose，group 必须区分。
        data += '\nallprojects { group = "' + group + '" }\n'
        build_info['sourceGroup'] = group
        changed = True
    if not changed:
        return tree, build_info
    oid = run_git(directory, ['hash-object', '-w', '--stdin'], data=data.encode('utf-8')).stdout.decode().strip()
    with tempfile.TemporaryDirectory(prefix='android-plugin-index-', dir=parent) as temporary:
        environment = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'), GIT_OPTIONAL_LOCKS='0')
        run_git(directory, ['read-tree', tree], environment=environment)
        run_git(directory, ['update-index', '--cacheinfo', '100644,' + oid + ',' + filename], environment=environment)
        aligned = run_git(directory, ['write-tree'], environment=environment).stdout.decode().strip()
    return aligned, build_info


def configure_math_inputs(directory, tree, math, final_source):
    output = final_source.resolve() / relative_path(math['output'])
    properties = {'tiqianCompiledMathResources': 'true'}
    for font in math['fonts']:
        if not re.fullmatch(r'tiqian[A-Za-z]+', font['property']):
            raise SystemExit('字体属性名称无效')
        for kind in ('Layout', 'Runtime'):
            filename = relative_path(font['name'] + '-' + kind.lower() + '.otf')
            properties[font['property'] + kind + 'Font'] = (output / filename).as_posix()
    data = run_git(directory, ['show', tree + ':gradle.properties']).stdout.decode('utf-8')
    for key in properties:
        if re.search(r'^\s*' + re.escape(key) + r'\s*[=:]', data, re.MULTILINE):
            raise SystemExit('上游已声明字体输入，需重新核对宿主接线：' + key)
    # staging 自举用 -P 覆盖；最终字体绝对路径只写入本地构建树。
    data += '\n' + ''.join(key + '=' + json.dumps(value, ensure_ascii=True)[1:-1] + '\n' for key, value in properties.items())
    oid = run_git(directory, ['hash-object', '-w', '--stdin'], data=data.encode('utf-8')).stdout.decode().strip()
    with tempfile.TemporaryDirectory(prefix='math-input-index-', dir=parent) as temporary:
        environment = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'), GIT_OPTIONAL_LOCKS='0')
        run_git(directory, ['read-tree', tree], environment=environment)
        run_git(directory, ['update-index', '--cacheinfo', '100644,' + oid + ',gradle.properties'], environment=environment)
        configured = run_git(directory, ['write-tree'], environment=environment).stdout.decode().strip()
    return configured


def validate_source(directory, commit, tree, spec):
    if directory.is_symlink() or not directory.is_dir():
        raise SystemExit('源码仓库不能是链接或缺失目录：' + str(directory))
    if run_git(directory, ['rev-parse', 'HEAD']).stdout.decode().strip() != commit:
        raise SystemExit('源码上游提交身份不符')
    run_git(directory, ['fsck', '--no-reflogs', '--no-dangling'])
    entries = run_git(directory, ['ls-tree', '-r', '-z', '--full-tree', tree]).stdout
    original = {}
    for entry in entries.split(b'\0'):
        if not entry:
            continue
        metadata, name = entry.split(b'\t', 1)
        mode, kind, oid = metadata.decode('ascii').split()
        name = relative_path(name.decode('utf-8'))
        if kind != 'blob' or mode not in ('100644', '100755', '120000'):
            raise SystemExit('源码包含未声明子模块或文件类型：' + name)
        original[name] = (mode, oid)
    expected = original.keys()
    generated = {'.git', '.gradle', '.kotlin', 'build', 'local.properties'}
    for name in expected:
        if name.endswith(('build.gradle.kts', 'build.gradle')):
            module = PurePosixPath(name).parent
            for child in ('build', '.gradle', '.kotlin'):
                generated.add((module / child).as_posix())
    actual = set()
    for current, directories, files in os.walk(directory):
        current = Path(current)
        for child in tuple(directories):
            path = current / child
            name = path.relative_to(directory).as_posix()
            if name in generated:
                directories.remove(child)
            elif path.is_symlink():
                actual.add(name)
                directories.remove(child)
        for child in files:
            name = (current / child).relative_to(directory).as_posix()
            if name not in generated:
                actual.add(name)
    if actual != expected:
        raise SystemExit('源码文件集合不符：' + '\n'.join(sorted(actual ^ expected)))
    for name in expected:
        path = directory / name
        if path.is_symlink():
            if original[name][0] != '120000' or not path.resolve().is_relative_to(directory.resolve()):
                raise SystemExit('源码链接类型或范围无效：' + name)
            data = path.readlink().as_posix().encode('utf-8')
        else:
            data = path.read_bytes()
        valid = hashlib.sha1(b'blob ' + str(len(data)).encode('ascii') + b'\0' + data).hexdigest() == original[name][1]
        if not valid:
            raise SystemExit('源码被修改，拒绝覆盖或回落：' + name)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--package', action='store_true', help='对版本目录所选上游源码应用打包补丁')
parser.add_argument('--offline', action='store_true', help='使用上一次成功记录的所选版本提交，不查询远端')
parser.add_argument('--gradle', type=Path, help='打包字体自举使用的当前 Gradle 可执行文件')
parser.add_argument('--tiqian-toolchain', type=Path, default=os.environ.get('ZHIHU_TIQIAN_TOOLCHAIN'), help='官方 Haxe/Node 和固定 boring/reflaxe/format/formatter 的隔离位置 JSON')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
content = (root / 'gradle/upstream-sources.lock.json').read_bytes()
recipe = json.loads(content)
if recipe.get('schema') != 3 or not recipe.get('sources'):
    raise SystemExit('源码补丁清单为空或格式不支持')
catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text(encoding='utf-8'))['versions']
versions = {name: catalog[spec['versionKey']] for name, spec in recipe['sources'].items()}
parent = root / '.gradle/upstream-inputs'
if (root / '.gradle').is_symlink() or parent.is_symlink():
    raise SystemExit('输入缓存不能是符号链接')
parent.mkdir(parents=True, exist_ok=True)
for child in ('references', 'git'):
    directory = parent / child
    if directory.is_symlink() or not directory.resolve().is_relative_to(parent.resolve()):
        raise SystemExit('输入缓存子目录不能链接到外部：' + child)
input_identity = hashlib.sha256(json.dumps({'recipe': hashlib.sha256(content).hexdigest(), 'implementation': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'versions': versions, 'androidPlugin': catalog.get('agp'), 'package': args.package}, sort_keys=True).encode()).hexdigest()
reference = parent / 'references' / (input_identity + '.json')
resolved = {}
mirrors = {}
if args.package:
    if args.offline:
        if not reference.is_file() or reference.is_symlink():
            raise SystemExit('离线缺少所选版本的成功打包记录')
        resolved = json.loads(reference.read_text(encoding='utf-8'))
        if not isinstance(resolved, dict) or resolved.keys() != recipe['sources'].keys():
            raise SystemExit('离线版本记录不匹配')
    for name, spec in recipe['sources'].items():
        if not re.fullmatch(r'[a-z0-9][a-z0-9-]*', name):
            raise SystemExit('源码名称无效：' + name)
        repository = spec['repository']
        parsed = urllib.parse.urlparse(repository)
        if not (re.fullmatch(r'https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?', repository) or parsed.scheme == 'file' and parsed.netloc in ('', 'localhost') and parsed.path):
            raise SystemExit('源码只接受 GitHub 或本地 Git 镜像：' + name)
        for filename, patch in spec.get('patches', {}).items():
            data = (root / 'gradle' / relative_path(filename)).read_bytes()
            if not re.fullmatch(r'[0-9a-f]{40}', patch['base']) or not re.fullmatch(r'[0-9a-f]{64}', patch['sha256']) or hashlib.sha256(data).hexdigest() != patch['sha256']:
                raise SystemExit('补丁基线或摘要不符：' + filename)
        for module, project in spec.get('modules', {}).items():
            if not re.fullmatch(r'[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+', module) or not re.fullmatch(r':(?:[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)*)?', project):
                raise SystemExit('模块替换无效：' + module)
        mirror = parent / 'git' / (name + '-' + hashlib.sha256(repository.encode()).hexdigest()[:12])
        mirror.mkdir(parents=True, exist_ok=True)
        if not (mirror / 'HEAD').exists():
            run_git(mirror, ['init', '--bare', '--quiet'])
        mirrors[name] = mirror
        version = versions[name]
        if not re.fullmatch(r'[A-Za-z0-9_.+-]+', version):
            raise SystemExit('上游版本无效：' + version)
        ref = spec['snapshotRef'] if version.endswith('SNAPSHOT') else spec['releaseRef'].replace('{version}', version)
        if not re.fullmatch(r'refs/(?:heads|tags)/[A-Za-z0-9_./+-]+', ref) or '..' in ref:
            raise SystemExit('上游引用无效：' + ref)
        pinned_commit = spec.get('snapshotCommit') if version.endswith('SNAPSHOT') else None
        if pinned_commit is not None and (not isinstance(pinned_commit, str) or not re.fullmatch(r'[0-9a-f]{40}', pinned_commit)):
            raise SystemExit('快照提交必须为完整的 Git SHA：' + name)
        if args.offline:
            record = resolved[name]
            commit = record.get('commit') if isinstance(record, dict) else None
            if record != {'version': version, 'ref': ref, 'commit': commit} or pinned_commit not in (None, commit):
                raise SystemExit('离线版本记录不匹配：' + name)
        elif pinned_commit is not None:
            commit = pinned_commit
        else:
            rows = run_git(mirror, ['ls-remote', '--exit-code', repository, ref, ref + '^{}']).stdout.decode().splitlines()
            candidates = {row.split('\t', 1)[1]: row.split('\t', 1)[0] for row in rows}
            commit = candidates.get(ref + '^{}', candidates.get(ref))
        if not isinstance(commit, str) or not re.fullmatch(r'[0-9a-f]{40}', commit):
            raise SystemExit('未取得版本对应提交：' + name)
        resolved[name] = {'version': version, 'ref': ref, 'commit': commit}

fingerprint = hashlib.sha256(json.dumps({'input': input_identity, 'resolved': resolved}, sort_keys=True).encode()).hexdigest()
target = parent / fingerprint[:20]
if target.is_symlink():
    raise SystemExit('生成输入不能是符号链接')
source_records = {}
if args.package:
    for name, spec in recipe['sources'].items():
        commit = resolved[name]['commit']
        mirror = mirrors[name]
        if run_git(mirror, ['cat-file', '-e', commit + '^{commit}'], check=False).returncode:
            if args.offline:
                raise SystemExit('离线缺少上游提交：' + name)
            run_git(mirror, ['fetch', '--quiet', '--depth=1', spec['repository'], commit])
        run_git(mirror, ['update-ref', 'refs/build-inputs/' + commit, commit])
        run_git(mirror, ['update-ref', '--no-deref', 'HEAD', commit])
        run_git(mirror, ['fsck', '--no-reflogs', '--no-dangling'])
        tree, patches = replay_patches(mirror, commit, spec)
        tree, build_info = align_source_build(mirror, tree, spec, catalog.get('agp', ''))
        math = recipe.get('math')
        if math and math['source'] == name:
            tree = configure_math_inputs(mirror, tree, math, target / 'sources' / name)
            build_info = {**(build_info or {}), 'resources': math['resources']}
        run_git(mirror, ['update-ref', 'refs/build-overlays/' + fingerprint, tree_commit(mirror, tree, commit)])
        source_records[name] = {**resolved[name], 'tree': tree, 'patches': patches, **(build_info or {})}
        if spec.get('generation'):
            source_records[name]['generation'] = {'sourceTree': tree, **spec['generation']}
            generated_ref = 'refs/build-generated/' + fingerprint
            generated = run_git(mirror, ['rev-parse', '--verify', generated_ref], check=False)
            if generated.returncode == 0:
                base = tree_commit(mirror, tree, commit)
                if run_git(mirror, ['rev-parse', generated_ref + '^']).stdout.decode().strip() != base:
                    raise SystemExit('已有生成树不属于当前源码与工具输入')
                source_records[name]['tree'] = run_git(mirror, ['rev-parse', generated_ref + '^{tree}']).stdout.decode().strip()
            elif target.exists():
                raise SystemExit('已有输入缺少当前源码的生成记录；拒绝补写或回落')

provenance = {'mode': 'package' if args.package else 'upstream', 'versions': versions, 'sources': source_records, 'recipeSha256': hashlib.sha256(content).hexdigest(), 'inputSha256': fingerprint}
if not target.exists():
    if args.offline:
        raise SystemExit('离线缺少完整的同版本打包输入')
    with tempfile.TemporaryDirectory(prefix=fingerprint[:20] + '-', dir=parent) as temporary:
        staging = Path(temporary)
        for name, record in source_records.items():
            directory = staging / 'sources' / name
            directory.mkdir(parents=True, exist_ok=True)
            run_git(directory, ['init', '--quiet'])
            (directory / '.git/info/attributes').write_bytes(b'* -text -filter\n')
            (directory / '.git/objects/info/alternates').write_bytes(((mirrors[name] / 'objects').resolve().as_posix() + '\n').encode('utf-8'))
            shallow = mirrors[name] / 'shallow'
            if shallow.exists():
                (directory / '.git/shallow').write_bytes(shallow.read_bytes())
            run_git(directory, ['update-ref', '--no-deref', 'HEAD', record['commit']])
            run_git(directory, ['read-tree', '--reset', '-u', record['tree']])
            validate_source(directory, record['commit'], record['tree'], recipe['sources'][name])
            generation = recipe['sources'][name].get('generation')
            if generation and record['tree'] == record['generation']['sourceTree']:
                record['tree'] = prepare_tiqian_engine(directory, mirrors[name], record['tree'], record['commit'], generation)
                validate_source(directory, record['commit'], record['tree'], recipe['sources'][name])
            local = root / 'local.properties'
            if local.exists():
                sdk = ''.join(line + '\n' for line in local.read_text(encoding='utf-8').splitlines() if line.startswith('sdk.dir='))
                if sdk:
                    (directory / 'local.properties').write_text(sdk, encoding='utf-8')
        if args.package and recipe.get('math'):
            math = recipe['math']
            if not args.gradle:
                raise SystemExit('数学资源自举需要 --gradle 指定当前 Gradle')
            tool = staging / 'tools/fonttools.whl'
            tool.parent.mkdir()
            with urllib.request.urlopen(math['wheel']['url'], timeout=60) as response, tool.open('xb') as output:
                for chunk in iter(lambda: response.read(1024 * 1024), b''):
                    output.write(chunk)
            if hashlib.sha256(tool.read_bytes()).hexdigest() != math['wheel']['sha256']:
                raise SystemExit('字体工具摘要不符')
            prepare_math_resources(staging / 'sources' / math['source'], math, tool, args.gradle.absolute())
        metadata = provenance_bytes(provenance)
        (staging / 'provenance.json').write_bytes(metadata)
        (staging / 'assets').mkdir()
        (staging / 'assets/upstream-patches.json').write_bytes(metadata)
        if target.exists():
            raise SystemExit('已有同指纹输入；拒绝替换，请重新执行以校验')
        try:
            staging.rename(target)
        except OSError:
            # Windows 对刚创建目录的 rename 偶发拒绝访问；目录内容已逐项校验，重试一次安全。
            import time
            time.sleep(0.2)
            staging.rename(target)

expected_roots = {'provenance.json', 'assets'} | ({'sources'} if args.package else set()) | ({'tools'} if args.package and recipe.get('math') else set())
if {path.name for path in target.iterdir()} != expected_roots:
    raise SystemExit('输入缓存包含清单外内容')
expected_metadata = provenance_bytes(provenance)
record = target / 'provenance.json'
if record.is_symlink() or record.read_bytes() != expected_metadata:
    raise SystemExit('输入来源记录不匹配当前版本和补丁')
assets = target / 'assets'
if assets.is_symlink() or {path.name for path in assets.iterdir()} != {'upstream-patches.json'} or (assets / 'upstream-patches.json').is_symlink() or (assets / 'upstream-patches.json').read_bytes() != expected_metadata:
    raise SystemExit('产物来源记录不匹配当前输入')
if args.package:
    if (target / 'sources').is_symlink() or {path.name for path in (target / 'sources').iterdir()} != source_records.keys():
        raise SystemExit('源码仓库集合不符')
    for name, record in source_records.items():
        validate_source(target / 'sources' / name, record['commit'], record['tree'], recipe['sources'][name])
    if recipe.get('math'):
        math = recipe['math']
        tool = target / 'tools/fonttools.whl'
        if tool.is_symlink() or hashlib.sha256(tool.read_bytes()).hexdigest() != math['wheel']['sha256']:
            raise SystemExit('已有字体工具内容不符')
        # 已有缓存只校验；字体编译和元数据自举只能修改上方的新 staging。
        prepare_math_resources(target / 'sources' / math['source'], math, tool)
if args.package:
    if reference.parent.is_symlink() or not reference.parent.resolve().is_relative_to(parent.resolve()):
        raise SystemExit('输入记录路径不能链接到外部')
    reference.parent.mkdir(exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=input_identity + '-', dir=reference.parent, delete=False) as output:
        output.write((json.dumps(resolved, sort_keys=True) + '\n').encode('utf-8'))
        temporary = Path(output.name)
    try:
        temporary.replace(reference)
    finally:
        temporary.unlink(missing_ok=True)
print(target.as_posix())
