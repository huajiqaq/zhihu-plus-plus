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

set -e # 失败立即退出，避免继续修改证书目录。
# 挂载后会遮住原系统证书；先在独立临时目录备份，避免丢失原有信任。
mkdir -m 700 /data/local/tmp/htk-ca-copy
# 备份系统已有证书。
cp /system/etc/security/cacerts/* /data/local/tmp/htk-ca-copy/
# 用内存文件系统覆盖系统证书目录。
mount -t tmpfs tmpfs /system/etc/security/cacerts
# 将原证书放回内存挂载，保留已有信任。
mv /data/local/tmp/htk-ca-copy/* /system/etc/security/cacerts/
# 加入待信任的新证书。
cp /data/local/tmp/c88f7ed0.0 /system/etc/security/cacerts/
# 恢复权限和 SELinux 上下文，确保系统仍可读取证书。
chown root:root /system/etc/security/cacerts/*
chmod 644 /system/etc/security/cacerts/*
chcon u:object_r:system_file:s0 /system/etc/security/cacerts/*
# 删除临时证书目录。
rm -r /data/local/tmp/htk-ca-copy
echo "System cert successfully injected"
