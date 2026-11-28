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

"""在实际 Apple target 依赖清单上补充随包分发的 ORT C 运行库原许可。"""

import argparse
import json
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--ort-licenses', type=Path)
    args = parser.parse_args()
    manifest = json.loads(args.input.read_text(encoding='utf-8'))
    if not manifest.get('libraries') or not isinstance(manifest.get('licenses'), dict):
        raise ValueError('The Apple target dependency license manifest is empty or invalid')
    bundled_notices = args.output.parent / 'licenses/onnxruntime'
    if bundled_notices.exists():
        shutil.rmtree(bundled_notices)
    bundled_notices.parent.mkdir(parents=True, exist_ok=True)
    if args.ort_licenses:
        sources = json.loads((args.ort_licenses / 'sources.json').read_text(encoding='utf-8'))
        license_id = 'onnxruntime-apple-MIT-and-third-party-notices'
        content = (args.ort_licenses / 'LICENSE').read_text(encoding='utf-8') + '\n\n' + (args.ort_licenses / 'ThirdPartyNotices.txt').read_text(encoding='utf-8')
        manifest['licenses'][license_id] = {
            'name': 'MIT and bundled third-party notices', 'content': content,
            'url': sources['source'], 'hash': license_id,
        }
        manifest['libraries'] = [item for item in manifest['libraries'] if item['uniqueId'] != 'native:onnxruntime']
        manifest['libraries'].append({
            'uniqueId': 'native:onnxruntime', 'name': 'ONNX Runtime C API',
            'artifactVersion': sources['version'], 'website': sources['source'],
            'licenses': [license_id],
        })
        bundled_notices.mkdir(parents=True, exist_ok=True)
        for name in ('LICENSE', 'ThirdPartyNotices.txt', 'sources.json'):
            shutil.copyfile(args.ort_licenses / name, bundled_notices / name)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
