import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description='确认 JUnit 报告包含实际执行且通过的测试')
parser.add_argument('directories', nargs='+', type=Path)
parser.add_argument('--class-name')
args = parser.parse_args()

for directory in args.directories:
    executed = 0
    reports = sorted(directory.rglob('*.xml'))
    if not reports:
        raise SystemExit(f'缺少测试报告：{directory}')
    for report in reports:
        root = ET.parse(report).getroot()
        for suite in root.iter('testsuite'):
            if int(suite.get('failures', '0')) or int(suite.get('errors', '0')):
                raise SystemExit(f'测试失败：{report}')
        for case in root.iter('testcase'):
            if args.class_name and case.get('classname') != args.class_name:
                continue
            if case.find('failure') is not None or case.find('error') is not None:
                raise SystemExit(f'测试失败：{report}')
            if case.find('skipped') is None:
                executed += 1
    if executed == 0:
        raise SystemExit(f'没有实际执行的目标测试：{directory}')
    print(f'{directory}: {executed} 项测试通过')
