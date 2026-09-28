import fs from 'node:fs'
import path from 'node:path'
import fg from 'fast-glob'

const webRoot = process.cwd()
const sourcePatterns = [
  'src/**/*.{java,vue,ts}',
  '../jjx-server/src/main/java/**/*.{java,vue,ts}',
]
const ignoredPatterns = [
  '**/node_modules/**',
  '**/dist/**',
  '**/InspectionEnum.ts',
  '**/QualityInspectionResultEnum.java',
  '**/migration/**',
  '**/migrations/**',
  '**/sql/**',
  '**/quality-print/**',
]

const equalsPattern = /\.equals\((['"])(PASS|FAIL|pass|fail)\1\)/
const comparisonPattern = /(?:===|!==)\s*(['"])(PASS|FAIL|pass|fail)\1|(['"])(PASS|FAIL|pass|fail)\3\s*(?:===|!==)/

function isAllowed(line) {
  return line.includes('case-gate-allow')
}

function hasCaseInsensitiveContext(lines, index) {
  return lines
    .slice(Math.max(0, index - 2), Math.min(lines.length, index + 3))
    .some((line) => /equalsIgnoreCase|getCode\(\)|isPass|isFail/.test(line))
}

function hasNormalizationContext(lines, index) {
  return lines
    .slice(Math.max(0, index - 2), Math.min(lines.length, index + 3))
    .some((line) => /toUpperCase|toLowerCase/.test(line))
}

async function scan() {
  const files = await fg(sourcePatterns, {
    cwd: webRoot,
    ignore: ignoredPatterns,
    onlyFiles: true,
  })
  const findings = []
  for (const file of files.sort()) {
    const absolute = path.resolve(webRoot, file)
    const lines = fs.readFileSync(absolute, 'utf8').split(/\r?\n/)
    lines.forEach((source, index) => {
      if (isAllowed(source)) return
      if ((equalsPattern.test(source) && !hasCaseInsensitiveContext(lines, index))
        || (comparisonPattern.test(source) && !hasNormalizationContext(lines, index))) {
        findings.push({ file, line: index + 1, source })
      }
    })
  }
  return findings
}

const findings = await scan()
console.log(`结果大小写敏感比较：${findings.length} 处（期望 0）`)
for (const finding of findings) {
  console.log(`${finding.file}:${finding.line}:${finding.source.trim()}`)
}
process.exitCode = findings.length > 0 ? 1 : 0
