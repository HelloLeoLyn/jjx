const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { spawnSync } = require('node:child_process')
const { pathToFileURL } = require('node:url')
const ts = require('typescript')
const vue = require('vue')
const { renderToString } = require('vue/server-renderer')
const { parse, compileScript, compileStyle } = require('vue/compiler-sfc')
const directory = path.resolve(__dirname, '..')
const compile = text => ts.transpileModule(text, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
} }).outputText
function load(text, deps = {}) {
  const state = { exports: {}, require: name => {
    assert.ok(name in deps, `Unexpected dependency: ${name}`)
    return deps[name]
  } }
  vm.runInNewContext(compile(text), state)
  return state.exports
}
function component(filename, deps, scope) {
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const script = compileScript(descriptor, { id: scope, inlineTemplate: true })
  const value = load(script.content, deps).default
  value.__scopeId = 'data-v-' + scope
  const css = descriptor.styles.map(style => compileStyle({ source: style.content,
    filename, id: 'data-v-' + scope, scoped: style.scoped }).code).join('\n')
  return { value, css }
}
const fonts = load(fs.readFileSync(path.join(directory, 'workSpecFont.ts'), 'utf8'))
const source = ts.createSourceFile('docset.ts', fs.readFileSync(path.join(directory, 'docset.ts'), 'utf8'), ts.ScriptTarget.Latest, true)
const functions = source.statements.filter(node => ts.isFunctionDeclaration(node) &&
  ['plain', 'workSpecMaterialSpec', 'workSpecMaterialDimensions', 'workSpecOperationRemark', 'workSpecStepSubscript'].includes(node.name?.text))
  .map(node => node.getText(source)).join('\n')
const formatters = load(fs.readFileSync(path.join(directory, '../../../../utils/bomQuantity.ts'), 'utf8') + '\n' + functions)
const groups = ['PANEL', 'UP_LINE', 'DOWN_LINE'].map((value, i) => ({ value, label: ['面板', '上线', '下线'][i] }))
const icon = component(path.join(directory, '../../../../components/SvgIcon/index.vue'), { vue }, 'flow-icon')
const { embossFields } = load(fs.readFileSync(path.join(directory, '../../../../api/product/workSpec.ts'), 'utf8'), { '@/utils/request': {} })
const paper = component(path.join(directory, 'WorkSpecPaper.vue'), {
  vue, '@/components/SvgIcon/index.vue': { __esModule: true, default: icon.value },
  '@/api/product/workSpec': { embossFields },
  './docset': { ...formatters, flowGroups: groups }, './workSpecFont': fonts,
  './useWorkSpecOverflow': { useWorkSpecOverflow: () => ({ measure() {} }) },
}, 'flow-paper')

test('B沿用A排版，18行材料连续且打印只分页后续区块', { timeout: 90000 }, async t => {
  const { JSDOM } = require('jsdom')
  const rows = Array.from({ length: 18 }, (_, i) => ({ itemId: i + 1, materialName: `MATERIAL-${i + 1}`, specification: '110', moduleQty: 5 }))
  const data = { product: { productName: '连续材料验证', productCode: 'TEST-007' }, routing: { items: [] },
    workSpec: { emboss: { setupHeight: '0.25' }, issueUnit: '工程部', approved: false } }
  const render = (specTemplate, count) => renderToString(vue.createSSRApp(paper.value, { data,
    page: { rows: rows.slice(0, count), engineeringNotes: 'NOTE-007', diePositionRows: [], changeLines: [{ text: 'CHANGE-007' }],
      groups: groups.map(group => ({ ...group, rows: [] })) },
    pageIndex: 0, pageCount: 1, specTemplate, fontSettings: fonts.defaultWorkSpecFontSettings() }))
  const a = await render('A', 5), b = await render('B', 5), extended = await render('B', 18)
  assert.equal(a, b, '5行材料时A/B必须使用相同DOM、列宽和字体')
  const doc = new JSDOM(extended).window.document, table = doc.querySelector('.materials')
  assert.equal(new JSDOM(b).window.document.querySelector('.materials').tBodies[0].rows.length, 14)
  assert.equal(table.tBodies[0].rows.length, 18)
  assert.equal(table.querySelector('.engineering-notes').rowSpan, 12)
  assert.equal(table.querySelector('.engineering-notes').parentElement.sectionRowIndex, 6)
  assert.equal(table.querySelectorAll('.emboss-label').length, 6)
  assert.deepEqual([...table.querySelectorAll('.material-name')].map(el => el.textContent.trim()), rows.map(row => row.materialName))
  assert.equal(doc.querySelector('.spec-overview'), null)
  if (!process.env.CHROME_BIN) { t.diagnostic('未设置CHROME_BIN，未验证浏览器与PDF'); return }
  const temporary = fs.mkdtempSync('/tmp/dev-007-print-')
  try {
    const fixture = path.join(temporary, 'fixture.html'), pdf = path.join(temporary, 'print.pdf')
    fs.writeFileSync(fixture, `<!doctype html><meta charset="utf-8"><style>body{margin:0}#paper{width:192mm}${paper.css}${icon.css}
      @media print{@page{size:A4 portrait;margin:9mm}#result{display:none}}</style>
      <div id="paper">${extended}</div><pre id="result"></pre><script>
      onload=()=>{const q=s=>document.querySelector(s),r=s=>q(s).getBoundingClientRect();
      q('#result').textContent=JSON.stringify({materialHeight:r('.materials').height,notesHeight:r('.engineering-notes').height,
        materialsBottom:r('.materials').bottom,flowsTop:r('.flows').top,flowsBottom:r('.flows').bottom,hoursTop:r('.hours').top,
        issueTop:r('.issuance').top,hoursBottom:r('.hours').bottom,paperHeight:r('.work-paper').height});};</script>`)
    const { chromium } = require(process.env.PLAYWRIGHT_CORE_PATH || 'playwright-core')
    const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN, headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage'], timeout: 15000 })
    let actual
    try {
      const page = await browser.newPage({ viewport: { width: 840, height: 1300 } })
      await page.goto(pathToFileURL(fixture).href, { waitUntil: 'load', timeout: 15000 })
      actual = JSON.parse(await page.locator('#result').textContent())
      await page.pdf({ path: pdf, preferCSSPageSize: true, printBackground: true })
    } finally { await browser.close() }
    assert.ok(actual.materialHeight >= 427)
    assert.ok(actual.notesHeight >= 264)
    assert.ok(actual.flowsTop >= actual.materialsBottom - 1)
    assert.ok(actual.hoursTop >= actual.flowsBottom - 1)
    assert.ok(actual.issueTop >= actual.hoursBottom - 1)
    assert.ok(actual.paperHeight > 278 * 96 / 25.4)
    const extract = spawnSync('python3', ['-c', 'import pymupdf,json,sys; d=pymupdf.open(sys.argv[1]); print(json.dumps([p.get_text() for p in d]))', pdf], { encoding: 'utf8' })
    assert.ifError(extract.error)
    assert.equal(extract.status, 0, extract.stderr)
    const printed = JSON.parse(extract.stdout)
    assert.ok(printed.length >= 2)
    for (const row of rows) assert.ok(printed[0].includes(row.materialName), row.materialName + '应在打印首页')
    assert.equal(printed.filter(page => page.includes('MATERIAL-18')).length, 1)
    assert.ok(printed.slice(1).some(page => page.includes('CHANGE-007')))
    t.diagnostic(JSON.stringify({ ...actual, pdfPages: printed.length, materialRowsOnFirstPrintedPage: 18 }))
  } finally { fs.rmSync(temporary, { recursive: true, force: true }) }
})
