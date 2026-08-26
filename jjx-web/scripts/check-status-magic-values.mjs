import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import fg from "fast-glob";

const webRoot = process.cwd();
const baselinePath = path.join(webRoot, "scripts/status-magic-baseline.json");
const sourcePattern = "src/**/*.{ts,tsx,vue}";
const ignoredPatterns = [
  "src/enums/**",
  "src/**/*.d.ts",
  "src/**/__tests__/**",
  "src/**/*.spec.ts",
  "src/**/*.test.ts",
];

const comparisonPattern =
  /(?:\b[\w$.?\[\]]*(?:Status|status)\b\s*(?:===|!==|==|!=|<=|>=|<|>)\s*-?\d+\b)|(?:\b-?\d+\s*(?:===|!==|==|!=|<=|>=|<|>)\s*[\w$.?\[\]]*(?:Status|status)\b)/;
const includesPattern =
  /\[[\d\s,-]+\]\.includes\([^)]*\b[\w$.?\[\]]*(?:Status|status)\b[^)]*\)/;
const localMapPattern =
  /\b(?:const|let|var)\s+(?:[A-Z0-9_]*STATUS[A-Z0-9_]*(?:_MAP|_NAMES|_LABELS)?|\w*[Ss]tatus(?:Map|Names|Labels))\b.*(?:Record\s*<\s*number|=\s*\{)/;

export function detectStatusMagicValue(sourceLine) {
  if (sourceLine.includes("status-magic-ignore")) return null;
  if (comparisonPattern.test(sourceLine)) return "status-comparison";
  if (includesPattern.test(sourceLine)) return "status-includes";
  if (localMapPattern.test(sourceLine)) return "local-status-map";
  return null;
}

function fingerprint(finding) {
  const normalizedSource = finding.source.trim().replace(/\s+/g, " ");
  return `${finding.file}|${finding.rule}|${normalizedSource}`;
}

async function scan() {
  const files = await fg(sourcePattern, {
    cwd: webRoot,
    ignore: ignoredPatterns,
    onlyFiles: true,
  });
  const findings = [];
  for (const file of files.sort()) {
    const lines = fs
      .readFileSync(path.join(webRoot, file), "utf8")
      .split(/\r?\n/);
    lines.forEach((source, index) => {
      const rule = detectStatusMagicValue(source);
      if (rule) findings.push({ file, line: index + 1, rule, source });
    });
  }
  return findings;
}

function countFingerprints(findings) {
  const counts = {};
  for (const finding of findings) {
    const key = fingerprint(finding);
    counts[key] = (counts[key] || 0) + 1;
  }
  return Object.fromEntries(
    Object.entries(counts).sort(([a], [b]) => a.localeCompare(b)),
  );
}

function selfTest() {
  assert.equal(
    detectStatusMagicValue("row.sampleStatus === 6"),
    "status-comparison",
  );
  assert.equal(
    detectStatusMagicValue("7 !== row.sampleStatus"),
    "status-comparison",
  );
  assert.equal(
    detectStatusMagicValue("[2, 6].includes(row.orderStatus)"),
    "status-includes",
  );
  assert.equal(
    detectStatusMagicValue(
      "const SAMPLE_STATUS_MAP: Record<number, string> = {",
    ),
    "local-status-map",
  );
  assert.equal(
    detectStatusMagicValue(
      "row.sampleStatus === SampleOrderStatus.CONFIRMED.value",
    ),
    null,
  );
  assert.equal(detectStatusMagicValue("const pageSize = 10"), null);
  console.log("状态魔法值检查器自测通过");
}

if (process.argv.includes("--self-test")) {
  selfTest();
  process.exit(0);
}

const findings = await scan();
const current = countFingerprints(findings);

if (process.argv.includes("--write-baseline")) {
  fs.writeFileSync(
    baselinePath,
    `${JSON.stringify({ version: 1, findings: current }, null, 2)}\n`,
    "utf8",
  );
  console.log(
    `已写入状态魔法值基线：${Object.values(current).reduce((a, b) => a + b, 0)} 处`,
  );
  process.exit(0);
}

if (!fs.existsSync(baselinePath)) {
  console.error(
    "缺少 scripts/status-magic-baseline.json，请先审核后执行 --write-baseline",
  );
  process.exit(1);
}

const baseline =
  JSON.parse(fs.readFileSync(baselinePath, "utf8")).findings || {};
const newFindings = findings.filter((finding) => {
  const key = fingerprint(finding);
  return !baseline[key] || current[key] > baseline[key];
});
const staleBaseline = Object.keys(baseline).filter((key) => !current[key]);

if (newFindings.length > 0 || staleBaseline.length > 0) {
  if (newFindings.length > 0) {
    console.error("\n检测到新增状态魔法值：");
    for (const finding of newFindings) {
      console.error(
        `- ${finding.file}:${finding.line} [${finding.rule}] ${finding.source.trim()}`,
      );
    }
  }
  if (staleBaseline.length > 0) {
    console.error(
      `\n有 ${staleBaseline.length} 条存量违规已消失，请审核后更新基线，防止其重新进入。`,
    );
  }
  process.exit(1);
}

console.log(`状态魔法值门禁通过（存量基线 ${findings.length} 处，新增 0 处）`);
