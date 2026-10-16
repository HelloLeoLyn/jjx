#!/usr/bin/env python3
"""清空全部附件测试数据（dev-20261016-044）；默认只读，执行经 db-migrate.sh。
危险等级：🟢预览；🔴执行清库及删除文件。前置：mysql、手工数据库/文件备份、暂停上传写入。
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tarfile

REPO = Path(__file__).resolve().parents[1]
# 只覆盖公共附件及旧业务附件专属目录；OCR、图标和 upload/backup 不属本工具范围。
DIRECTORIES = frozenset('attachment-temp product inquiry quotation order sales_order '
    'sales_order_confirmation sample sample_order task requirement bom routing quotation_flow '
    'production_order purchase_order purchase_payment purchase_receipt purchase_invoice '
    'sales_delivery quality_archive quality_template material inventory_material purchase_supplier'.split())


def mysql(sql, write=False):
    env = os.environ.copy()
    env['MYSQL_PWD'] = env.get('DB_PASS', '123456') if write else env.get('JJX_RO_PASS', 'jjx_ro_2026')
    args = ['mysql', '-h' + env.get('DB_HOST', '127.0.0.1'),
            '-P' + env.get('DB_PORT', '3306'),
            '-u' + (env.get('DB_USER', 'root') if write else env.get('JJX_RO_USER', 'jjx_ro')),
            '--connect-timeout=5', '--default-character-set=utf8mb4', '-N', '-B',
            env.get('DB_NAME', 'jjx_erp_db'), '-e', sql]
    return subprocess.run(args, env=env, text=True, capture_output=True, check=True).stdout.strip()


def digest(stream):
    value = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
        value.update(chunk)
    return value.hexdigest()


def scan(roots):
    files, retained = {}, []
    for root in roots:
        if root.is_symlink() or root.resolve() != root:
            raise ValueError(f'上传根目录及其父目录不能含软链接：{root}')
        if not root.exists():
            continue
        if not root.is_dir() or root == REPO or REPO.is_relative_to(root):
            raise ValueError(f'无效上传目录：{root}')
        for child in sorted(root.iterdir()):
            if child.name not in DIRECTORIES:
                retained.append(str(child))
                continue
            if not child.is_dir() or child.is_symlink():
                raise ValueError(f'附件专属路径不是普通目录：{child}')
            def fail(error):
                raise error
            for base, dirs, names in os.walk(child, followlinks=False, onerror=fail):
                for path in [Path(base)] + [Path(base) / n for n in dirs + names]:
                    info = path.lstat()
                    if stat.S_ISLNK(info.st_mode):
                        raise ValueError(f'待清目录含软链接，拒绝：{path}')
                    if stat.S_ISDIR(info.st_mode):
                        continue
                    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
                        raise ValueError(f'待清目录含特殊文件或硬链接，拒绝：{path}')
                    files[str(path)] = (info.st_ino, info.st_size, info.st_mtime_ns)
    return files, retained


def verify_archive(archive, files):
    """归档使用 tar -C / 创建的绝对路径去掉前导 /；逐文件校验，不解压。"""
    with tarfile.open(archive, 'r:*') as tar:
        members = {}
        for member in tar:
            name = member.name.removeprefix('./')
            if name in members:
                raise ValueError(f'文件备份含重复路径：{name}')
            members[name] = member
        for name in files:
            member = members.get(name.lstrip('/'))
            if member is None or not member.isfile():
                raise ValueError(f'手工文件备份缺少文件：{name}')
            with open(name, 'rb') as current, tar.extractfile(member) as saved:
                if digest(current) != digest(saved):
                    raise ValueError(f'手工文件备份与当前文件不一致：{name}')


def database_snapshot():
    # 包含已软删、草稿、受控和 LEGACY；本工具明确是全部测试附件重置。
    return mysql("SELECT JSON_OBJECT('files',(SELECT COUNT(*) FROM sys_attachment),"
                 "'refs',(SELECT COUNT(*) FROM sys_attachment_ref));")


def migration_sql():
    # TRUNCATE 不触发 DELETE trigger；不摘除触发器。仅本会话暂时关闭 FK。
    return """-- 全部附件测试数据清理；由 attachment-cleanup.py 经用户确认后生成。
-- risk: high
-- 非事务操作；必须停写、先手工全库/文件备份；失败后人工检查，不自动重跑。
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE sys_attachment_ref;
TRUNCATE TABLE sys_attachment;
SET FOREIGN_KEY_CHECKS=1;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=(
        '不能区分正式与测试附件！清除全部附件记录/引用（含已删/受控）及专属目录文件。'
        '保留 OCR、图标、upload/backup 和未知目录。不会启停服务或自动备份。'
        '连接：DB_HOST/DB_PORT/DB_NAME；预览 JJX_RO_USER/JJX_RO_PASS；执行 DB_USER/DB_PASS。'
        '文件归档示例：tar -cf /手工备份.tar -C / home/administrator/jjx/upload '
        'home/administrator/jjx/jjx-server/upload'))
    parser.add_argument('--execute', action='store_true', help='真正清库并删除文件；默认只读预览')
    parser.add_argument('--task', help='真实任务码 dev-YYYYMMDD-NNN')
    parser.add_argument('--backup', type=Path, help='手工全库 SQL 备份（排除 hr_employee），由迁移入口校验')
    parser.add_argument('--files-backup', type=Path, help='手工 tar 归档，按 tar -C / 路径建立；逐文件验证')
    parser.add_argument('--writes-paused', action='store_true', help='操作人确认已阻止上传及其他附件写入')
    parser.add_argument('--upload-root', action='append', type=Path,
                        help='上传根目录，可重复；默认当前与旧上传目录；覆盖默认目录')
    args = parser.parse_args()
    roots = args.upload_root or [Path(os.environ.get('JJX_UPLOAD_ROOT', str(REPO / 'upload'))),
                                  REPO / 'jjx-server/upload']
    roots = sorted(set(p.absolute() for p in roots))
    for i, root in enumerate(roots):
        if any(root.is_relative_to(other) or other.is_relative_to(root) for other in roots[:i]):
            raise ValueError('上传根目录不能嵌套')
    db = os.environ.get('DB_NAME', 'jjx_erp_db')
    if not re.fullmatch(r'[A-Za-z0-9_]+', db):
        raise ValueError('数据库名格式错误')
    before = database_snapshot()
    files, retained = scan(roots)
    print(f'目标：{db}@{os.environ.get("DB_HOST", "127.0.0.1")}:{os.environ.get("DB_PORT", "3306")}')
    print(f'全部附件记录/引用：{before}')
    print(f'待删磁盘文件：{len(files)} 个 / {sum(x[1] for x in files.values())} 字节')
    for path in files:
        print('删除文件：' + path)
    for path in retained:
        print('保留范围外目录/文件：' + path)
    if not args.execute:
        print('仅预览；执行须 --execute --task <任务码> --backup <全库.sql> '
              '--files-backup <手工.tar> --writes-paused，并在终端输入确认。')
        return
    if not sys.stdin.isatty() or not args.writes_paused:
        raise ValueError('执行必须在终端，并以 --writes-paused 确认已暂停附件写入；脚本不操作服务')
    if not args.task or not re.fullmatch(r'dev-\d{8}-\d{3}', args.task):
        raise ValueError('必须提供真实任务码')
    if not args.backup or not args.backup.is_file():
        raise ValueError('必须提供手工全库 SQL 备份；脚本不会自动备份')
    for backup in (args.backup, args.files_backup):
        if backup and any(backup.resolve().is_relative_to(root / name)
                          for root in roots for name in DIRECTORIES):
            raise ValueError('手工备份不能位于待删除目录内')
    if files:
        if not args.files_backup or not args.files_backup.is_file():
            raise ValueError('必须提供手工文件 tar 备份')
        verify_archive(args.files_backup, files)
    # 拒绝未知外键，保持 FOREIGN_KEY_CHECKS=0 的范围可证明。
    foreign = mysql("SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE "
                    "WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IN "
                    "('sys_attachment','sys_attachment_ref') AND NOT "
                    "(TABLE_NAME='sys_attachment_ref' AND COLUMN_NAME='attachment_id' "
                    "AND REFERENCED_TABLE_NAME='sys_attachment');")
    if foreign:
        raise ValueError('发现额外附件外键，需人工处理：' + foreign)
    # 保留业务记录时不抹除其附件证据，按真实结构发现引用列并拒绝非空值。
    columns = mysql("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS "
                    "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME NOT IN ('sys_attachment','sys_attachment_ref') "
                    "AND COLUMN_NAME IN ('attachment_id','attachment_ids','attachment_ref_ids');")
    for row in columns.splitlines():
        table, column = row.split('\t')
        if not re.fullmatch(r'[A-Za-z0-9_]+', table + column):
            raise ValueError('非法表或列名')
        count = mysql(f"SELECT COUNT(*) FROM `{table}` WHERE `{column}` IS NOT NULL "
                      f"AND CAST(`{column}` AS CHAR) NOT IN ('','[]','null','0');")
        if count != '0':
            raise ValueError(f'{table}.{column} 仍有 {count} 条附件证据；先清理关联测试业务')
    token = f'{db} DELETE ALL ATTACHMENTS'
    if input(f'不可恢复地清空全部附件（不能区分正式/测试），请输入 {token}：') != token:
        raise ValueError('确认不匹配，未清理')
    if database_snapshot() != before or scan(roots)[0] != files:
        raise ValueError('预览后数据/文件已变化，拒绝清理')
    applied = mysql("SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';")
    numbers = [int(n) for n in applied.split(',') if n]
    directory = REPO / 'jjx-docs/sql/migrations'
    numbers += [int(p.name.split('_')[0]) for p in directory.rglob('*.sql')
                if re.match(r'^\d+_', p.name)]
    sql = directory / f'{max(numbers, default=0) + 1}_attachment_test_cleanup.sql'
    with sql.open('x', encoding='utf-8') as out:
        out.write('-- 任务码：' + args.task + '\n' + migration_sql())
    print('生成迁移：' + str(sql), flush=True)
    # 显式传入连接和迁移目录，避免环境变量把入口指向其他目录。
    env = os.environ.copy()
    env['JJX_MIGRATIONS_DIR'] = str(directory)
    subprocess.run(['bash', str(REPO / 'scripts/db-migrate.sh'), str(sql), '--yes',
                    '--task', args.task, '--backup', str(args.backup.absolute())], env=env, check=True)
    if json.loads(database_snapshot()) != {'files': 0, 'refs': 0}:
        raise ValueError('附件表清理后非空，不删除磁盘文件；检查并发写入')
    if scan(roots)[0] != files:
        raise ValueError('磁盘文件发生变化，未删除文件；数据库已清理，请人工核查')
    for name in files:
        Path(name).unlink()
    print(f'完成：附件两表已清空，删除 {len(files)} 个文件；上传空目录保留。')
    print('请提交迁移入口归档的 SQL；非事务清理中断后请核查账本和文件，勿盲目重跑。')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as exc:
        print(f'执行失败（不自动重跑）：{exc.stderr or exc}', file=sys.stderr)
        sys.exit(1)
    except (ValueError, OSError, tarfile.TarError) as exc:
        print(f'拒绝/中止清理：{exc}', file=sys.stderr)
        sys.exit(1)
