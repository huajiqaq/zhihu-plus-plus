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

from collections import deque
from hashlib import file_digest
from pathlib import Path
from queue import Empty, Queue
from threading import Thread
import json
import os
import re
import subprocess
import sys
import time

apk_dir = Path(sys.argv[1])
output = Path(sys.argv[2])
if len(sys.argv) == 3:
    output.mkdir()
    codes = {}
    for shard in (3, 4):
        codes[str(shard)] = subprocess.run(
            [sys.executable, '-I', __file__, str(apk_dir), str(output / str(shard)), str(shard)],
            check=False,
        ).returncode
    (output / 'summary.json').write_text(json.dumps(codes, indent=2) + '\n', encoding='utf-8')
    print(codes, flush=True)
    sys.exit(0 if not any(codes.values()) else 1)

shard = int(sys.argv[3])
assert shard in (3, 4)
output.mkdir()
adb = ['adb', '-s', os.environ['ANDROID_SERIAL']]
package = 'com.github.zly2006.zhplus.lite'
runner = package + '.test/com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner'
methods = {
    'webLoginRequirementReturnsToMainLogin',
    'childCommentTargetAndScrollSurviveExternalNavigation',
    'emojiPickerInsertsPlaceholderAtCursor',
    'denseProfileBadgesKeepHeaderActionsVisibleOffline',
}


def query(*args):
    return subprocess.check_output(adb + list(args), stdin=subprocess.DEVNULL, timeout=5)


for path in [apk_dir / 'app.apk', apk_dir / 'test.apk']:
    with path.open('rb') as stream:
        print(path.name, file_digest(stream, 'sha256').hexdigest(), flush=True)
    subprocess.run(adb + ['install', '-r', '-t', str(path)], check=True, timeout=60)
assert f'instrumentation:{runner} (target={package})'.encode() in query('shell', '-n', 'pm', 'list', 'instrumentation')
queue = Queue()
with subprocess.Popen(
    adb + ['shell', '-n', 'am', 'instrument', '-r', '-w', '-e', 'zhpp_data_mode', 'mock',
           '-e', 'numShards', '5', '-e', 'shardIndex', str(shard), runner],
    stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    text=True, encoding='utf-8', errors='replace',
) as process:
    def collect():
        for line in process.stdout:
            queue.put((time.monotonic(), line))
        queue.put((time.monotonic(), None))
    Thread(target=collect, daemon=True).start()
    active = None
    fields = {}
    frames = deque(maxlen=8)
    samples = []
    next_frame = 0
    with (output / 'instrumentation.txt').open('x', encoding='utf-8') as report:
        while True:
            try:
                received, line = queue.get(timeout=0.15)
            except Empty:
                line = ''
            if line is None:
                break
            if line:
                report.write(line)
                report.flush()
            if line.startswith('INSTRUMENTATION_STATUS: '):
                key, _, value = line.removeprefix('INSTRUMENTATION_STATUS: ').strip().partition('=')
                if key == 'test': fields[key] = value
            elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
                code = int(line.split(': ', 1)[1])
                method = fields.get('test')
                if code == 1 and method in methods:
                    active = method
                    started = received
                    next_frame = started
                    frames.clear()
                    samples = []
                elif active is not None and code <= 0:
                    folder = output / active
                    folder.mkdir()
                    for i, (seconds, data) in enumerate(frames):
                        (folder / f'{i:02d}-{seconds:.2f}.png').write_bytes(data)
                    (folder / 'windows.json').write_text(json.dumps({'status': code, 'samples': samples}, indent=2), encoding='utf-8')
                    print(active, 'status', code, flush=True)
                    active = None
                fields.clear()
            now = time.monotonic()
            if active is None or now < next_frame or not queue.empty():
                continue
            next_frame = now + 0.75
            try:
                text = query('shell', '-n', 'dumpsys', 'window').decode(errors='replace')
                windows = [line.strip() for line in text.splitlines() if re.search(r'mCurrentFocus=|mFocusedApp=|\btype=ime\b|^\s*Window #', line)]
                data = query('exec-out', 'screencap', '-p')
                if data.startswith(b'\x89PNG\r\n\x1a\n'):
                    frames.append((now - started, data))
                samples.append({'seconds': now - started, 'windows': windows})
            except (subprocess.SubprocessError, OSError) as error:
                samples.append({'seconds': now - started, 'captureError': type(error).__name__})
    assert process.wait(timeout=20) == 0
text = (output / 'instrumentation.txt').read_text(encoding='utf-8')
print('\n'.join(line for line in text.splitlines() if line.startswith(('Tests run:', 'OK (', 'Error in'))), flush=True)
expected = 25 if shard == 3 else 23
sys.exit(0 if re.search(rf'^OK \({expected} tests\)$', text, re.MULTILINE) else 1)
