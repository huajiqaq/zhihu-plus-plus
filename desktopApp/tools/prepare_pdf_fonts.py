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

"""用固定官方字体和隔离 FontTools 生成 PDFBox 可嵌入的 SC 静态 TrueType。"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verified_download(spec, cache):
    url = urllib.parse.urlparse(spec['url'])
    if url.scheme != 'https' or url.hostname not in ('raw.githubusercontent.com', 'files.pythonhosted.org'):
        raise ValueError(f'Unexpected font-build download origin: {spec["url"]}')
    destination = cache / spec['sha256'] / Path(url.path).name
    valid = lambda path: path.is_file() and digest(path) == spec['sha256'] and ('size' not in spec or path.stat().st_size == spec['size'])
    if valid(destination):
        return destination
    cache.mkdir(parents=True, exist_ok=True)
    # 下载数据始终进入新的空目录，绝不在该目录运行 Python 或上游脚本。
    with tempfile.TemporaryDirectory(prefix='download-', dir=cache) as temporary:
        downloaded = Path(temporary) / destination.name
        request = urllib.request.Request(spec['url'], headers={'User-Agent': 'ZhihuPlusPlus-PdfFontBuild'})
        with urllib.request.urlopen(request, timeout=120) as response, downloaded.open('wb') as output:
            if urllib.parse.urlparse(response.url).hostname != url.hostname:
                raise ValueError('Font-build download redirected to a different origin')
            shutil.copyfileobj(response, output)
        if not valid(downloaded):
            raise ValueError(f'Font-build input failed SHA-256/size verification: {destination.name}')
        destination.parent.mkdir(parents=True, exist_ok=True)
        downloaded.replace(destination)
    return destination


def prepare_toolchain(lock, cache):
    wheel = verified_download(lock['fonttools'], cache / 'downloads')
    environment = cache / 'toolchain' / lock['fonttools']['sha256']
    python = environment / ('Scripts/python.exe' if os.name == 'nt' else 'bin/python')
    ready = environment / 'verified-wheel.sha256'
    if not python.is_file() or not ready.is_file() or ready.read_text() != lock['fonttools']['sha256']:
        subprocess.run([sys.executable, '-I', '-m', 'venv', '--clear', str(environment)], check=True)
        subprocess.run([
            str(python), '-I', '-m', 'pip', '--isolated', 'install', '--no-index', '--no-deps',
            '--no-cache-dir', '--disable-pip-version-check', str(wheel),
        ], check=True, cwd=Path(__file__).resolve().parent)
        ready.write_text(lock['fonttools']['sha256'])
    return python


def generate(lock, font_path, license_path, destination):
    from importlib.metadata import version
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont

    if version('fonttools') != lock['fonttools']['version']:
        raise ValueError('Unexpected FontTools version in isolated toolchain')
    if digest(font_path) != lock['font']['sha256'] or digest(license_path) != lock['license']['sha256']:
        raise ValueError('Font source or license changed before instantiation')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='static-fonts-', dir=destination.parent) as temporary:
        staged = Path(temporary)
        fonts = staged / 'pdf-fonts'
        notices = staged / 'META-INF/licenses/noto-sans-sc'
        fonts.mkdir()
        notices.mkdir(parents=True)
        generated = []
        for weight, style in ((400, 'Regular'), (700, 'Bold')):
            output = fonts / f'NotoSansSC-{style}.ttf'
            with TTFont(font_path, recalcTimestamp=False) as variable:
                if 'glyf' not in variable or 'loca' not in variable or 'CFF ' in variable or 'CFF2' in variable:
                    raise ValueError('Expected the locked SC TrueType outlines, not CFF/OTF')
                if {axis.axisTag for axis in variable['fvar'].axes} != {'wght'}:
                    raise ValueError('Unexpected variable-font axes')
                source_characters = set(variable.getBestCmap())
                copyright_notice = variable['name'].getDebugName(0)
                if not copyright_notice:
                    raise ValueError('The source font has no copyright notice')
                instance = instantiateVariableFont(variable, {'wght': weight}, inplace=True, updateFontNames=True, static=True)
                instance.recalcTimestamp = False
                instance.save(output)
            with TTFont(output, recalcTimestamp=False) as static:
                if any(table in static for table in ('fvar', 'gvar', 'CFF ', 'CFF2')) or 'glyf' not in static or 'loca' not in static:
                    raise ValueError(f'{style} is not a static TrueType font')
                if static['OS/2'].usWeightClass != weight:
                    raise ValueError(f'Incorrect {style} font weight')
                if set(static.getBestCmap()) != source_characters:
                    raise ValueError(f'{style} did not preserve the full upstream character set')
                if not set(map(ord, '中国知乎汉语测试')).issubset(static.getBestCmap()):
                    raise ValueError(f'{style} is missing required Chinese glyphs')
            generated.append({'file': f'pdf-fonts/{output.name}', 'weight': weight, 'sha256': digest(output)})
        shutil.copyfile(license_path, notices / 'OFL.txt')
        (notices / 'NOTICE.txt').write_text(
            copyright_notice + '\nNoto Sans SC is distributed under the SIL Open Font License 1.1.\n'
            'Zhihu++ generates static TrueType instances at weights 400 and 700 from the locked official SC variable font.\n'
            'The original character set is retained; no Japanese font or CFF/OTF replacement is used.\n'
            'The generated fonts retain the upstream copyright notices and are distributed under the same OFL.\n', encoding='utf-8')
        sources = dict(lock, generated=generated, components=[{
            'id': 'font:noto-sans-sc', 'name': 'Noto Sans SC (static Regular and Bold)',
            'version': 'f8d157532fbfaeda587e826d4cd5b21a49186f7c; wght=400,700',
            'license': 'SIL Open Font License 1.1', 'source': lock['source'],
            'licenseFiles': ['OFL.txt', 'NOTICE.txt'],
        }])
        (notices / 'sources.json').write_text(json.dumps(sources, indent=2) + '\n', encoding='utf-8')
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copytree(staged, destination, dirs_exist_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--license', type=Path)
    args = parser.parse_args()
    lock = json.loads(args.lock.read_text(encoding='utf-8'))
    if args.source is not None:
        if args.license is None:
            parser.error('--source requires --license')
        generate(lock, args.source, args.license, args.output)
        return
    if sys.version_info < (3, 11):
        raise RuntimeError('PDF font preparation requires Python 3.11 or newer (CI uses Python 3.12)')
    cache = args.cache.resolve()
    font = verified_download(lock['font'], cache / 'downloads')
    license_file = verified_download(lock['license'], cache / 'downloads')
    python = prepare_toolchain(lock, cache)
    subprocess.run([
        str(python), '-I', str(Path(__file__).resolve()), '--lock', str(args.lock.resolve()),
        '--cache', str(cache), '--output', str(args.output.resolve()), '--source', str(font), '--license', str(license_file),
    ], check=True, cwd=Path(__file__).resolve().parent)


if __name__ == '__main__':
    main()
