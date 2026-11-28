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

ZHIHU_IOS_VARIANT=${ZHIHU_IOS_VARIANT:-full}
case "$ZHIHU_IOS_VARIANT" in
    full|lite) ;;
    *) printf '%s\n' 'ZHIHU_IOS_VARIANT must be full or lite' >&2; exit 1 ;;
esac
case "${PLATFORM_NAME:-}" in
    iphoneos) ios_target=iosArm64; license_task=prepareIosArm64LicenseManifest ;;
    iphonesimulator) ios_target=iosSimulatorArm64; license_task=prepareIosSimulatorArm64LicenseManifest ;;
    *) printf '%s\n' 'build-shared.sh requires an iOS device or simulator Xcode build' >&2; exit 1 ;;
esac
: "${DERIVED_FILE_DIR:?missing DERIVED_FILE_DIR}"

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
if [ "${ZHIHU_GRADLE_PROFILE:-false}" = true ]; then
    set -- "$@" --no-daemon --profile
fi
./gradlew "$@" -PzhihuUpstreamPatches=true "-PzhihuIosVariant=$ZHIHU_IOS_VARIANT" :shared:embedAndSignAppleFrameworkForXcode ":shared:$license_task"

if [ "$ZHIHU_IOS_VARIANT" = full ]; then
    : "${ZHIHU_IOS_ORT_BINARY:?missing ZHIHU_IOS_ORT_BINARY}"
    [ -s "$ZHIHU_IOS_ORT_BINARY" ] || { printf 'Missing generated ONNX Runtime link input: %s\n' "$ZHIHU_IOS_ORT_BINARY" >&2; exit 1; }
    printf 'Generated ONNX Runtime link input: %s\n' "$ZHIHU_IOS_ORT_BINARY"
fi

license_source="shared/build/generated/apple-licenses/$ZHIHU_IOS_VARIANT/$ios_target"
license_staging="$DERIVED_FILE_DIR/apple-licenses/$ZHIHU_IOS_VARIANT/$ios_target"
[ -s "$license_source/aboutlibraries.json" ] || { printf '%s\n' 'Missing iOS dependency license manifest' >&2; exit 1; }
if [ "$ZHIHU_IOS_VARIANT" = full ]; then
    [ -s "$license_source/licenses/onnxruntime/LICENSE" ] && [ -s "$license_source/licenses/onnxruntime/ThirdPartyNotices.txt" ] || {
        printf '%s\n' 'Full iOS licenses must include ONNX Runtime license and third-party notices' >&2
        exit 1
    }
else
    if [ -e "$license_source/licenses/onnxruntime" ] || grep -Eiq 'onnxruntime|onnx[ -]runtime' "$license_source/aboutlibraries.json"; then
        printf '%s\n' 'Lite iOS licenses must not contain ONNX Runtime' >&2
        exit 1
    fi
fi
# Only distributable notices are staged; never carry over another build's license files.
rm -rf "$license_staging"
mkdir -p "$license_staging/licenses"
cp "$license_source/aboutlibraries.json" "$license_staging/aboutlibraries.json"
if [ -d "$license_source/licenses" ]; then
    cp -R "$license_source/licenses/." "$license_staging/licenses/"
fi

# Xcode processes this plist after the build phase; use the same version as the shared app.
version_name=$(awk -F= '$1 == "app.versionName" { sub(/\r$/, "", $2); print $2; exit }' gradle.properties)
version_code=$(awk -F= '$1 == "app.versionCode" { sub(/\r$/, "", $2); print $2; exit }' gradle.properties)
: "${version_name:?missing app.versionName}" "${version_code:?missing app.versionCode}"
mkdir -p "$DERIVED_FILE_DIR"
plist="$DERIVED_FILE_DIR/ZhihuPlusPlus-Info.plist"
cp "$SRCROOT/Info.plist" "$plist"
/usr/bin/plutil -insert CFBundleShortVersionString -string "$version_name" "$plist"
/usr/bin/plutil -insert CFBundleVersion -string "$version_code" "$plist"

# Localized names override CFBundleDisplayName in the processed Info.plist.
for language in en zh-Hans; do
    localized_dir="$DERIVED_FILE_DIR/$language.lproj"
    mkdir -p "$localized_dir"
    localized_strings="$localized_dir/InfoPlist.strings"
    cp "$SRCROOT/$language.lproj/InfoPlist.strings" "$localized_strings"
    if [ "$ZHIHU_IOS_VARIANT" = lite ]; then
        # plutil can read OpenStep .strings, but mutations require a writable plist format.
        /usr/bin/plutil -convert binary1 "$localized_strings"
        for key in CFBundleDisplayName CFBundleName; do
            name=$(/usr/bin/plutil -extract "$key" raw -o - "$localized_strings")
            /usr/bin/plutil -replace "$key" -string "$name Lite" "$localized_strings"
        done
    fi
done
