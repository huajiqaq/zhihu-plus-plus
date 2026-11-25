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

apk_dir, output = map(Path, sys.argv[1:3])
mode = sys.argv[3]
if mode == 'compare':
    output.mkdir()
    outcomes = {}
    for label in ('sync-1', 'system-1', 'system-2', 'sync-2'):
        command = [sys.executable, '-I', __file__, str(apk_dir), str(output / label), label.split('-')[0]]
        outcomes[label] = subprocess.run(command, check=False).returncode
    (output / 'comparison.json').write_text(json.dumps(outcomes, indent=2) + '\n', encoding='utf-8')
    print(outcomes, flush=True)
    sys.exit(0 if all(not code for code in outcomes.values()) else 1)

assert mode in {'sync', 'system'}
output.mkdir()
adb = ['adb', '-s', os.environ['ANDROID_SERIAL']]
package = 'com.github.zly2006.zhplus.lite'
runner = package + '.test/com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner'
targets = {
    'webLoginRequirementReturnsToMainLogin',
    'childCommentTargetAndScrollSurviveExternalNavigation',
    'rootAndChildEditorsAvoidRealIme',
}
window_field = re.compile(
    r'^(?:mCurrentFocus|mFocusedApp|mInputMethodWindow|mInputMethodTarget|mIme\w+Target|mObscuringWindow)='
    r'|^Window #\d+ Window\{|\btype=ime\b|OnBackInvokedCallback|ImeBack', re.IGNORECASE,
)


def query(*args):
    return subprocess.check_output(adb + list(args), stdin=subprocess.DEVNULL, timeout=4)


def save(method, code, trace, frames):
    folder = output / method
    folder.mkdir()
    for index, frame in enumerate(frames):
        (folder / f'{index:02d}-{frame[0]:.2f}.png').write_bytes(frame[1])
    (folder / 'windows.json').write_text(
        json.dumps({'statusCode': code, 'samples': trace}, indent=2) + '\n', encoding='utf-8',
    )
    print(f'{method}: status={code}, {len(trace)} window samples, {len(frames)} frames', flush=True)


assert query('shell', '-n', 'getprop', 'ro.build.version.sdk').strip() == b'36'
for name in ('app.apk', 'test.apk'):
    apk = apk_dir / name
    with apk.open('rb') as stream:
        print(name, file_digest(stream, 'sha256').hexdigest(), flush=True)
    subprocess.run(adb + ['install', '-r', '-t', str(apk)], check=True, timeout=60)
assert f'instrumentation:{runner} (target={package})'.encode() in query('shell', '-n', 'pm', 'list', 'instrumentation')

lines = Queue()
with subprocess.Popen(
    adb + ['shell', '-n', 'am', 'instrument', '-r', '-w', '-e', 'zhpp_data_mode', 'mock',
           '-e', 'focus_probe_system_back', str(mode == 'system').lower(),
           '-e', 'numShards', '5', '-e', 'shardIndex', '3', runner],
    stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    text=True, encoding='utf-8', errors='replace',
) as process:
    def read_lines():
        for line in process.stdout:
            lines.put((time.monotonic(), line))
        lines.put((time.monotonic(), None))

    Thread(target=read_lines, daemon=True).start()
    active = None
    fields = {}
    frames = deque(maxlen=8)
    trace = []
    next_frame = 0
    with (output / 'instrumentation-raw.txt').open('x', encoding='utf-8') as report:
        while True:
            try:
                received, line = lines.get(timeout=0.15)
            except Empty:
                line = ''
            if line is None:
                break
            if line:
                report.write(line)
            if line.startswith('INSTRUMENTATION_STATUS: '):
                key, _, value = line.removeprefix('INSTRUMENTATION_STATUS: ').strip().partition('=')
                if key in ('class', 'test'):
                    fields[key] = value
            elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
                code = int(line.split(': ', 1)[1])
                method = fields.get('test')
                if code == 1 and method in targets:
                    active = method
                    started = received
                    next_frame = started
                    trace.clear()
                    frames.clear()
                elif active is not None and method == active and code <= 0:
                    save(active, code, trace, frames)
                    active = None
                fields.clear()
            now = time.monotonic()
            if active is None or now < next_frame or not lines.empty():
                continue
            next_frame = now + 0.65
            try:
                window = query('shell', '-n', 'dumpsys', 'window').decode(errors='replace')
                parts = [line.strip() for line in window.splitlines() if window_field.search(line.strip())]
                png = query('exec-out', 'screencap', '-p')
                if png.startswith(b'\x89PNG\r\n\x1a\n'):
                    frames.append((now - started, png))
                trace.append({'seconds': round(now - started, 3), 'windows': parts})
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                trace.append({'seconds': round(now - started, 3), 'captureError': type(error).__name__})
    assert process.wait() == 0

text = (output / 'instrumentation-raw.txt').read_text(encoding='utf-8')
print('\n'.join(line for line in text.splitlines() if line.startswith(('Tests run:', 'OK ('))), flush=True)
# Preserve the original shard's nonzero-test success criterion.
sys.exit(0 if re.search(r'^OK \([1-9][0-9]* tests?\)$', text, re.MULTILINE) else 1)
