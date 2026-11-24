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
import json
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from queue import Queue
from threading import Thread

parser = argparse.ArgumentParser(description='确认测试报告包含实际执行且通过的完整测试')
parser.add_argument('directories', nargs='*', type=Path)
parser.add_argument('--class-name')
parser.add_argument('--generated-main', type=Path)
parser.add_argument('--generated-results', type=Path)
parser.add_argument('--smoke-binary', type=Path)
parser.add_argument('--smoke-output', type=Path)
parser.add_argument('--smoke-root', choices=('login', 'main'), default='login')
args = parser.parse_args()
if bool(args.generated_main) != bool(args.generated_results):
    parser.error('--generated-main 和 --generated-results 必须一起使用')
if bool(args.smoke_binary) != bool(args.smoke_output):
    parser.error('--smoke-binary 和 --smoke-output 必须一起使用')
if not args.directories and not args.generated_main and not args.smoke_binary:
    parser.error('至少指定一个 JUnit 报告目录、generated suite 或离屏 smoke')

if args.generated_main:
    main = args.generated_main.read_text(encoding='utf-8')
    classes = dict(re.findall(r'val (t\d+) = ([\w.]+)\(\)', main))
    expected = {
        classes[instance] + '.' + method
        for instance, method in re.findall(r'try \{ (t\d+)\.(\w+)\(\) \}', main)
        if method != 'flushTestTrace'
    }
    not_applicable = set(re.findall(r'\brecordNotApplicable\("([^"]+)"', main))
    expected.update(not_applicable)
    if not expected:
        raise SystemExit('generated suite 没有已注册用例')
    records = [json.loads(line) for line in args.generated_results.read_text(encoding='utf-8').splitlines() if line.strip()]
    observed = set()
    passed = 0
    for record in records:
        case_id = record.get('id')
        if not isinstance(case_id, str) or case_id in observed:
            raise SystemExit('generated suite 用例 ID 缺失或重复')
        observed.add(case_id)
        verdict = record.get('verdict')
        if verdict != 'pass' and not (verdict == 'not_applicable' and case_id in not_applicable):
            raise SystemExit(f'generated suite 测试失败或未声明跳过：{case_id}')
        passed += verdict == 'pass'
    if not passed or observed != expected:
        raise SystemExit(f'generated suite 未完整执行：{passed} 项通过，缺少 {sorted(expected - observed)}，额外 {sorted(observed - expected)}')
    print(f'{args.generated_results}: {passed} 项测试通过，{len(observed) - passed} 项明确不适用')

for directory in args.directories:
    executed = 0
    skipped = 0
    reports = sorted(directory.rglob('*.xml'))
    if not reports:
        raise SystemExit(f'缺少测试报告：{directory}')
    for report in reports:
        root = ET.parse(report).getroot()
        for suite in root.iter('testsuite'):
            if int(suite.get('tests', '0')) != len(list(suite.iter('testcase'))):
                raise SystemExit(f'测试报告用例数不完整：{report}')
            if int(suite.get('failures', '0')) or int(suite.get('errors', '0')):
                raise SystemExit(f'测试失败：{report}')
        for case in root.iter('testcase'):
            if args.class_name and case.get('classname') != args.class_name:
                continue
            if case.find('failure') is not None or case.find('error') is not None:
                raise SystemExit(f'测试失败：{report}')
            if case.find('skipped') is None:
                executed += 1
            else:
                skipped += 1
    if executed == 0:
        raise SystemExit(f'没有实际执行的目标测试：{directory}')
    print(f'{directory}: {executed} 项测试通过，{skipped} 项跳过')

if args.smoke_binary:
    if not args.smoke_binary.is_file():
        raise SystemExit(f'缺少离屏调试可执行文件：{args.smoke_binary}')
    output = args.smoke_output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    messages = Queue()
    with (output / 'stderr.txt').open('w', encoding='utf-8') as stderr, tempfile.TemporaryDirectory(prefix='zhpp-native-smoke-', dir='/tmp') as screenshots:
        process = subprocess.Popen(['/usr/bin/sandbox-exec', '-p', '(version 1)(allow default)(deny network*)', str(args.smoke_binary.resolve()), '--root=' + args.smoke_root], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=stderr, text=True, encoding='utf-8')
        def read_messages():
            with (output / 'protocol.jsonl').open('w', encoding='utf-8') as transcript:
                for line in process.stdout:
                    transcript.write(line)
                    transcript.flush()
                    messages.put(line)
                messages.put('')
        Thread(target=read_messages, daemon=True).start()
        def request(op, **fields):
            request_id = str(request.counter)
            request.counter += 1
            command = {'id': request_id, 'op': op, **fields}
            with (output / 'commands.jsonl').open('a', encoding='utf-8') as commands:
                commands.write(json.dumps(command, ensure_ascii=False) + '\n')
            process.stdin.write(json.dumps(command, ensure_ascii=False) + '\n')
            process.stdin.flush()
            response = json.loads(messages.get(timeout=30))
            if response.get('id') != request_id or response.get('ok') is not True:
                raise RuntimeError(f'离屏命令失败：{command}, {response}')
            return response.get('data')
        request.counter = 1
        try:
            ready = json.loads(messages.get(timeout=30))
            home = Path(ready['dataHome']).resolve()
            if not Path(ready['dataHome']).is_absolute() or ready.get('event') != 'ready' or ready.get('protocol') != 'ZHPP_BACKGROUND_UI_DEBUG_V1' or ready.get('windowHost') is not False or ready.get('dataMode') != 'isolated' or ready.get('root') != args.smoke_root or not home.is_relative_to(Path(tempfile.gettempdir()).resolve()) or not home.is_dir():
                raise RuntimeError('离屏宿主未确认临时数据隔离')
            state = request('state')
            if any(state.get(key) != ready.get(key) for key in ('protocol', 'root', 'windowHost', 'dataMode', 'dataHome')):
                raise RuntimeError('ready 与 state 的离屏隔离状态不一致')
            selector = {'tag': 'login_notice_step_1', 'useUnmergedTree': False}
            request('wait', selector=selector, timeoutMs=10000)
            before = request('dump', selector=selector)
            if '已收起' not in before:
                raise RuntimeError('登录说明未处于初始收起状态')
            request('click', selector=selector)
            request('wait', selector={'textContains': '我清楚，本应用'}, timeoutMs=10000)
            expanded = request('dump', selector=selector)
            if '已展开' not in expanded or before == expanded:
                raise RuntimeError('登录说明没有真正展开')
            request('click', selector=selector)
            collapsed = request('dump', selector=selector)
            if '已收起' not in collapsed or expanded == collapsed:
                raise RuntimeError('登录说明没有真正收起')
            if args.smoke_root == 'main':
                for index in range(1, 4):
                    notice = {'tag': f'login_notice_step_{index}', 'useUnmergedTree': False}
                    if '已收起' not in request('dump', selector=notice):
                        raise RuntimeError('游客说明未处于收起状态')
                    request('click', selector=notice)
                    if '已展开' not in request('dump', selector=notice):
                        raise RuntimeError('游客说明未展开')
                    request('click', selector=notice)
                request('wait', selector={'text': '已读 3 / 3 项'}, timeoutMs=10000)
                agreement = {'tag': 'login_notice_agreement', 'useUnmergedTree': False}
                if not re.search(r"ToggleableState = '?Off'?", request('dump', selector=agreement)):
                    raise RuntimeError('游客说明确认初始值错误')
                request('click', selector=agreement)
                if not re.search(r"ToggleableState = '?On'?", request('dump', selector=agreement)):
                    raise RuntimeError('游客说明未同意')
                request('dump', selector={'tag': 'login_notice_confirm', 'useUnmergedTree': False})
                request('click', selector={'tag': 'login_notice_confirm', 'useUnmergedTree': False})
                request('wait', selector={'tag': 'login_guest'}, timeoutMs=10000)
                request('dump', selector={'tag': 'login_guest', 'useUnmergedTree': False})
                request('click', selector={'tag': 'login_guest', 'useUnmergedTree': False})
                for tag in ('home_account_button', 'accountSettings.appearance'):
                    target = {'tag': tag, 'useUnmergedTree': False}
                    request('wait', selector=target, timeoutMs=10000)
                    request('dump', selector=target)
                    request('click', selector=target)
                style = {'tag': 'appearanceSettings.floatingBottomBarStyle', 'useUnmergedTree': False}
                request('wait', selector=style, timeoutMs=10000)
                request('dump', selector=style)
                request('click', selector={'text': '悬浮底栏样式', 'useUnmergedTree': False})
                apple = {'text': 'Apple', 'useUnmergedTree': False}
                request('wait', selector=apple, timeoutMs=10000)
                request('dump', selector=apple)
                request('click', selector=apple)
                request('advance', milliseconds=300)
                if 'Apple' not in request('dump', selector=style):
                    raise RuntimeError('Apple 底栏选择未写回设置')
                back = {'tag': 'appearance_settings_back_button', 'useUnmergedTree': False}
                request('dump', selector=back)
                request('click', selector=back)
                if 'accountSettings.appearance' in request('dump'):
                    request('back')
                home_tab = {'tag': 'nav_tab_home', 'useUnmergedTree': False}
                request('wait', selector=home_tab, timeoutMs=10000)
                if not re.search(r"Selected = '?true'?", request('dump', selector=home_tab)):
                    raise RuntimeError('Apple 底栏没有 tab 选中语义')
            request('dump')
            request('show_message', message='Native 离屏 CI smoke', duration='long')
            request('wait', selector={'text': 'Native 离屏 CI smoke'}, timeoutMs=10000)
            request('dump')
            image_name = args.smoke_root + '.png'
            image = request('screenshot', file=str(Path(screenshots) / image_name))
            if image.get('nonUniform') is not True or image.get('sampledColors', 0) <= 1 or image.get('bytes', 0) <= 0:
                raise RuntimeError('离屏画布没有真实绘制内容')
            shutil.copyfile(image['file'], output / image_name)
            request('quit')
            process.wait(timeout=15)
            if process.returncode != 0 or home.exists():
                raise RuntimeError('离屏宿主退出失败或临时数据未删除')
            print(f'macOS {args.smoke_root} 离屏 smoke：隔网、隔离、说明展开/收起、消息语义、真实绘制和退出清理通过')
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=15)
