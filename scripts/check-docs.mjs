#!/usr/bin/env node
/**
 * jjx-docs 文档规则门禁（docs-as-code gate）
 *
 * 规则（AGENTS.md / jjx-docs/README.md 维护标准）：
 *   R1 analysis/*.md 必须登记 analysis/INDEX.md（零容忍，无基线）
 *   R2 analysis/*.md 必须 UTF-8 带 BOM（存量债务走基线，只管新增）
 *   R3 analysis/*.md 文件名必须带日期或 -dev-YYYYMMDD-NNN（存量债务走基线）
 *   R4 current/*.md 必须无日期/任务码后缀（一个模块一篇现行真相），且带 BOM（零容忍）
 *
 * 用法：
 *   node scripts/check-docs.mjs                 检查
 *   node scripts/check-docs.mjs --write-baseline 重写存量基线（只允许在"债务变少"时执行）
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..");
const docsRoot = path.join(repoRoot, "jjx-docs");
const analysisDir = path.join(docsRoot, "analysis");
const currentDir = path.join(docsRoot, "current");
const baselinePath = path.join(here, "docs-baseline.json");
const BOM = Buffer.from([0xef, 0xbb, 0xbf]);

const read = (p) => fs.readFileSync(p);
const hasBom = (p) => read(p).subarray(0, 3).equals(BOM);
const listMd = (dir) =>
  fs.existsSync(dir)
    ? fs.readdirSync(dir).filter((f) => f.endsWith(".md")).sort()
    : [];

// ── 采集 ──────────────────────────────────────────────────────────────
const registered = new Set(
  [...read(path.join(analysisDir, "INDEX.md")).toString("utf8").matchAll(
    /\|\s*([A-Za-z0-9._-]+\.md)\s*\|/g,
  )].map((m) => m[1]),
);

const analysisFiles = listMd(analysisDir).filter((f) => f !== "INDEX.md");
const unregistered = analysisFiles.filter((f) => !registered.has(f));
const noBom = analysisFiles.filter((f) => !hasBom(path.join(analysisDir, f)));
const badName = analysisFiles.filter(
  (f) => !/(-dev-\d{8}-\d{3}|-\d{8})/.test(f),
);

const currentFiles = listMd(currentDir);
const currentBadName = currentFiles.filter((f) => /(-\d{8}|-dev-)/.test(f));
const currentNoBom = currentFiles.filter((f) => !hasBom(path.join(currentDir, f)));

// ── 基线 ──────────────────────────────────────────────────────────────
const loadBaseline = () => {
  if (!fs.existsSync(baselinePath)) return { noBom: [], badName: [] };
  try {
    const b = JSON.parse(read(baselinePath).toString("utf8"));
    return { noBom: b.noBom || [], badName: b.badName || [] };
  } catch {
    console.error("缺少/损坏 scripts/docs-baseline.json，先审核后执行 --write-baseline");
    process.exit(1);
  }
};
const baseline = loadBaseline();

if (process.argv.includes("--write-baseline")) {
  fs.writeFileSync(
    baselinePath,
    JSON.stringify({ version: 1, noBom, badName }, null, 2) + "\n",
    "utf8",
  );
  console.log(
    `已写基线：缺 BOM ${noBom.length} 篇、命名不合规 ${badName.length} 篇 → scripts/docs-baseline.json`,
  );
  process.exit(0);
}

// ── 判定：基线条目只允许减少 ──────────────────────────────────────────
const newNoBom = noBom.filter((f) => !baseline.noBom.includes(f));
const newBadName = badName.filter((f) => !baseline.badName.includes(f));
const clearedNoBom = baseline.noBom.filter((f) => !noBom.includes(f));
const clearedBadName = baseline.badName.filter((f) => !badName.includes(f));

let failed = false;
const fail = (title, items, hint) => {
  if (!items.length) return;
  failed = true;
  console.error(`\n✘ ${title}`);
  items.slice(0, 20).forEach((i) => console.error(`    - ${i}`));
  if (items.length > 20) console.error(`    … 其余 ${items.length - 20} 条`);
  if (hint) console.error(`  → ${hint}`);
};

fail(
  `R1 新文档未登记 analysis/INDEX.md（${unregistered.length} 篇）`,
  unregistered,
  "登记后重跑，或跑 INDEX.md 头部注释里的再生命令",
);
fail(
  `R2 新文档缺 UTF-8 BOM（${newNoBom.length} 篇）`,
  newNoBom,
  'python3 -c "p=\'<file>\';d=open(p,\'rb\').read();open(p,\'wb\').write(b\'\\xef\\xbb\\xbf\'+d)"',
);
fail(
  `R3 新文档命名不合规（${newBadName.length} 篇）`,
  newBadName,
  "命名须带日期或任务码：<主题>-dev-YYYYMMDD-NNN.md / <主题>-YYYYMMDD.md",
);
fail(
  `R4 current/ 目录规范（${currentBadName.length + currentNoBom.length} 处）`,
  [...currentBadName.map((f) => `current/${f} 不该带日期/任务码（现行真相用 <模块>.md）`),
   ...currentNoBom.map((f) => `current/${f} 缺 UTF-8 BOM`)],
  "一个模块只允许一篇现行真相；快照请放 analysis/",
);

if (failed) {
  console.error("\n（判断为误报可复核后调整规则，不要直接放宽）\n");
  process.exit(1);
}

const debt =
  `存量基线：缺 BOM ${noBom.length} 篇、命名不合规 ${badName.length} 篇`;
const cleared = clearedNoBom.length + clearedBadName.length;
console.log(
  `文档门禁通过（analysis ${analysisFiles.length} 篇 / current ${currentFiles.length} 篇；` +
    `${debt}${cleared ? `，本次减少 ${cleared} 条，可执行 --write-baseline 收窄基线` : ""}）`,
);
