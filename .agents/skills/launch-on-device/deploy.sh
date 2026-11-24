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

serial=${ANDROID_SERIAL:-}
if [[ -z "$serial" ]]; then
    devices=$(adb devices)
    serials=()
    while read -r candidate state _; do
        case "$state" in
            device|offline|unauthorized) serials+=("$candidate") ;;
        esac
    done <<< "$devices"
    if [[ ${#serials[@]} -ne 1 ]]; then
        printf '需要唯一连接设备，或设置 ANDROID_SERIAL。\n%s\n' "$devices" >&2
        exit 1
    fi
    serial=${serials[0]}
fi
if [[ $(adb -s "$serial" get-state | tr -d '\r') != device ]]; then
    printf '设备不可用：%s\n' "$serial" >&2
    exit 1
fi

printf '构建 Lite debug APK。\n'
./gradlew :app:assembleLiteDebug --quiet
apk=app/build/outputs/apk/lite/debug/app-lite-debug.apk
package=com.github.zly2006.zhplus.lite
if [[ ! -f "$apk" ]]; then
    printf '找不到 APK：%s\n' "$apk" >&2
    exit 1
fi

if ! adb -s "$serial" install -r "$apk"; then
    printf '安装失败，已保留原应用数据；请检查签名、版本和存储空间。\n' >&2
    exit 1
fi

launcher=$(adb -s "$serial" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$package" | tr -d '\r')
launcher=${launcher##*$'\n'}
if [[ "$launcher" != "$package/"* || "$launcher" == "$package/" || "$launcher" == *[![:alnum:]_./\$]* ]]; then
    printf '无法解析 Launcher：%s\n' "$launcher" >&2
    exit 1
fi
adb -s "$serial" shell am start -W \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$launcher"
