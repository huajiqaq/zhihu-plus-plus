#!/usr/bin/env bash
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

set -euo pipefail

if [[ $# -ne 2 || ! $1 =~ ^[1-9][0-9]*$ || ! $2 =~ ^[0-9]+$ || $2 -ge $1 ]]; then
    echo "usage: $0 <shard-count> <zero-based-shard-index>" >&2
    exit 2
fi

readonly shard_count=$1
readonly shard_index=$2
device_serial=${ANDROID_SERIAL:-}
if [[ -z "$device_serial" ]]; then
    device_serial=$(adb get-serialno)
fi
[[ -n "$device_serial" && "$device_serial" != unknown ]]
readonly device_serial
readonly results_dir=app/build/outputs/androidTest-results
readonly app_apk=${INSTRUMENT_APP_APK:?INSTRUMENT_APP_APK must point to the app APK}
readonly test_apk=${INSTRUMENT_TEST_APK:?INSTRUMENT_TEST_APK must point to the test APK}
readonly instrumentation_component=com.github.zly2006.zhplus.lite.test/com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner
readonly instrumentation_result="$results_dir/instrumentation-shard-$shard_index.txt"

mkdir -p "$results_dir"

adb -s "$device_serial" install -r -t "$app_apk"
adb -s "$device_serial" install -r -t "$test_apk"
frames=()
if [[ ${GITHUB_ACTIONS:-} == true && ${RUNNER_ENVIRONMENT:-} == github-hosted && ${GITHUB_EVENT_NAME:-} == workflow_dispatch && $device_serial == emulator-* ]]; then
    frames=(-e zhpp_mock_screenshots ci)
    collect_mock_frames() {
        local root=/sdcard/Android/data/com.github.zly2006.zhplus.lite/files/finishcheck/screenshots
        local suite method stage_name remote dir
        while read -r suite method; do
            dir="$root/$suite/$method"
            while IFS= read -r remote; do
                remote=${remote%$'\r'}
                stage_name=${remote##*/}
                [[ "$remote" == "$dir/$stage_name" && $stage_name =~ ^${suite}__${method}__[a-zA-Z0-9-]+__[0-9]{2}\.png$ ]] || continue
                mkdir -p "$results_dir/frames"
                adb -s "$device_serial" pull "$remote" "$results_dir/frames/$stage_name" </dev/null || printf 'Unable to collect %s\n' "$stage_name" >&2
            done < <(adb -s "$device_serial" shell -n "if test -d '$dir'; then find '$dir' -maxdepth 1 -type f -name '*.png'; fi")
        done <<'MOCK_CASES'
ArticleScreenInstrumentedTest highlightedParagraphSelectionHandleCanExtendToFollowingParagraph
ArticleScreenInstrumentedTest draggingSelectionHandleUsesSameCompleteTextLayer
CommentScreenInstrumentedTest childCommentMoreMenuTracksAnchorWithinWindowAndBackLayers
WriteAnswerScreenInstrumentedTest markdownShortcutsHideOnlyWhileTheSoftKeyboardIsVisible
MOCK_CASES
    }
    trap collect_mock_frames EXIT
fi
adb -s "$device_serial" shell am instrument -w \
    -e zhpp_data_mode mock \
    "${frames[@]}" \
    -e numShards "$shard_count" \
    -e shardIndex "$shard_index" \
    "$instrumentation_component" | tee "$instrumentation_result"
# OK (0 tests) 不能算通过。
grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$instrumentation_result"
