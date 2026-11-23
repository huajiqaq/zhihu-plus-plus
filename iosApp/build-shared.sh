#!/bin/sh
# Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
# SPDX-License-Identifier: AGPL-3.0-only
set -eu
cd "$SRCROOT/.."

./gradlew -PzhihuUpstreamPatches=true :shared:embedAndSignAppleFrameworkForXcode :app:prepareLibraryDefinitionsLiteDebug

# Xcode processes this plist after the build phase; use the same version as the shared app.
version_name=$(awk -F= '$1 == "app.versionName" { sub(/\r$/, "", $2); print $2; exit }' gradle.properties)
version_code=$(awk -F= '$1 == "app.versionCode" { sub(/\r$/, "", $2); print $2; exit }' gradle.properties)
: "${version_name:?missing app.versionName}" "${version_code:?missing app.versionCode}"
mkdir -p "$DERIVED_FILE_DIR"
plist="$DERIVED_FILE_DIR/ZhihuPlusPlus-Info.plist"
cp "$SRCROOT/Info.plist" "$plist"
/usr/bin/plutil -insert CFBundleShortVersionString -string "$version_name" "$plist"
/usr/bin/plutil -insert CFBundleVersion -string "$version_code" "$plist"
