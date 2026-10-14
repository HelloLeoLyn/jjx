#!/usr/bin/env node
// ============================================================================
// check-migration-collation.mjs —— 迁移 SQL 字符集/排序规则写法静态门禁（只读）
//   任务码：dev-20261014-006
//
// 规则：任一 SQL 语句里出现 CHARACTER SET / CHARSET，却整条语句没有 COLLATE → 报错。
//   原因：MySQL 8 在只写 DEFAULT CHARSET=utf8mb4 时会取字符集自带默认
//         utf8mb4_0900_ai_ci（不是库默认 utf8mb4_unicode_ci）→ 跨表字符串等值连接
//         报 ERROR 1267（事故与订正见迁移 278）。
//   例外：migrations/applied/ 是已执行历史，不可改，不参与本门禁。
//
// 用法：node scripts/check-migration-collation.mjs
// 环境变量：JJX_MIGRATIONS_DIR 覆盖迁移目录
// ============================================================================
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..');
const migDir = process.env.JJX_MIGRATIONS_DIR || join(repoRoot, 'jjx-docs/sql/migrations');

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) {
      if (name === 'applied') continue; // 已执行历史，不约束
      out.push(...walk(p));
    } else if (name.endsWith('.sql')) {
      out.push(p);
    }
  }
  return out;
}

// 去注释：-- 行注释、# 行注释、/* */ 块注释（迁移 SQL 内不含这些符号的字符串字面量场景）
function stripComments(sql) {
  return sql
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\n]*/g, ' ')
    .replace(/#[^\n]*/g, ' ');
}

const files = existsSync(migDir) ? walk(migDir).sort() : [];
const problems = [];

for (const f of files) {
  const sql = stripComments(readFileSync(f, 'utf8'));
  sql.split(';').forEach((stmt, i) => {
    if (!/\bcharset\b|\bcharacter\s+set\b/i.test(stmt)) return;
    if (/\bcollate\b/i.test(stmt)) return;
    const oneLine = stmt.replace(/\s+/g, ' ').trim().slice(0, 160);
    problems.push(`${relative(repoRoot, f)} [stmt ${i + 1}] ${oneLine}`);
  });
}

if (problems.length) {
  console.error('✘ 迁移 SQL 字符集写法门禁：出现 CHARACTER SET/CHARSET 但整条语句无 COLLATE');
  console.error('  规则：建表/加列/改列必须显式 COLLATE utf8mb4_unicode_ci；');
  console.error('        刻意例外列（ascii_bin 等）也须写明 COLLATE 并在迁移注释里给理由。');
  for (const p of problems) console.error('  - ' + p);
  process.exit(1);
}

console.log(`✅ 迁移 SQL 字符集写法门禁通过（扫描 ${files.length} 个待办迁移文件）`);
