#!/bin/sh
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

set -eu
cd "$SRCROOT/.."

# Xcode puts its own Python ahead of the host tools in a Run Script phase.
if [ -n "${ZHIHU_PYTHON_BIN_DIR:-}" ]; then
    export PATH="$ZHIHU_PYTHON_BIN_DIR:$PATH"
    [ -x "$ZHIHU_PYTHON_BIN_DIR/python3" ] || { printf '%s\n' 'ZHIHU_PYTHON_BIN_DIR must contain an executable python3' >&2; exit 1; }
    python3 -I -c 'import tomllib'
fi

set --
if [ -n "${ZHIHU_GRADLE_JVMARGS:-}" ]; then
    set -- "-Dorg.gradle.jvmargs=$ZHIHU_GRADLE_JVMARGS"
fi
if [ -n "${ZHIHU_GRADLE_MAX_WORKERS:-}" ]; then
    set -- "$@" "--max-workers=$ZHIHU_GRADLE_MAX_WORKERS"
fi
./gradlew "$@" -PzhihuUpstreamPatches=true :shared:embedAndSignAppleFrameworkForXcode :app:prepareLibraryDefinitionsLiteDebug

# Xcode processes this plist after the build phase; use the same version as the shared app.
version_name=$(awk -F= '$1 == "app.versionName" { sub(/\r$/, "", $2); print $2; exit }' gradle.properties)
version_code=$(awk -F= '$1 == "app.versionCode" { sub(/\r$/, "", $2); print $2; exit }' gradle.properties)
: "${version_name:?missing app.versionName}" "${version_code:?missing app.versionCode}"
mkdir -p "$DERIVED_FILE_DIR"
plist="$DERIVED_FILE_DIR/ZhihuPlusPlus-Info.plist"
cp "$SRCROOT/Info.plist" "$plist"
/usr/bin/plutil -insert CFBundleShortVersionString -string "$version_name" "$plist"
/usr/bin/plutil -insert CFBundleVersion -string "$version_code" "$plist"
