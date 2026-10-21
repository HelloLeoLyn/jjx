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


const paginateNode = source.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'paginateMeasuredWorkSpec')
const paginate = load(paginateNode.getText(source)).paginateMeasuredWorkSpec

test('B材料默认14行，沿用A列宽与凹凸/加工要求布局', async () => {
  const { JSDOM } = require('jsdom')
  for (const count of [5, 18, 25, 65]) {
    const html = await render('B', count)
    const doc = new JSDOM(html).window.document
    const table = doc.querySelector('.materials')
    assert.equal(table.tBodies[0].rows.length, Math.max(14, count))
    assert.equal(table.querySelector('.engineering-notes').rowSpan, Math.max(14, count) - 6)
    assert.equal(table.querySelector('.engineering-notes').parentElement.sectionRowIndex, 6)
    assert.equal(table.querySelectorAll('.emboss-label').length, 6)
    assert.equal(html.includes('--extra-material-height'), false)
  }
  const a = await render('A', 5), b = await render('B', 5)
  assert.equal(a, b.replace('work-paper continuous', 'work-paper'))
})

function render(specTemplate, count, processCount = 0, changeCount = 1) {
  const rows = Array.from({ length: count }, (_, i) => ({
    itemId: i + 1, materialName: 'MAT_' + String(i + 1).padStart(3, '0'),
    specification: '110', moduleQty: 5,
  }))
  const routing = groups.flatMap(group => Array.from({ length: processCount }, (_, i) => ({
    processCategory: group.value, itemId: group.value + i, indexNumber: i + 1,
    remark: 'OP_' + group.value + '_' + String(i + 1).padStart(3, '0'),
  })))
  const data = { product: { productName: '连续材料验证', productCode: 'TEST-008' }, routing: { items: routing },
    workSpec: { emboss: { setupHeight: '0.25' }, issueUnit: 'ENGINEERING', approved: false } }
  return renderToString(vue.createSSRApp(paper.value, { data,
    page: { rows, engineeringNotes: 'NOTE-008', diePositionRows: [],
      changeLines: Array.from({ length: changeCount }, (_, i) => ({ text: 'CHANGE_' + String(i + 1).padStart(3, '0') })),
      groups: groups.map(group => ({ ...group, rows: routing.filter(row => row.processCategory === group.value) })) },
    structureCaption: 'STRUCTURE_008',
    structureImage: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="gray"/></svg>'),
    pageIndex: 0, pageCount: 1, specTemplate, fontSettings: fonts.defaultWorkSpecFontSettings() }))
}

test('真实浏览器：5/18/25/65行按A4边界分页，流程和变更不丢失，PDF固定A4', { timeout: 120000 }, async t => {
  if (!process.env.CHROME_BIN) { t.skip('未设置CHROME_BIN，浏览器与PDF未验证'); return }
  const { JSDOM } = require('jsdom')
  const temporary = fs.mkdtempSync('/tmp/dev-008-print-')
  try {
    for (const [count, processCount, changeCount] of [[5, 0, 1], [18, 0, 1], [25, 0, 1], [65, 22, 90]]) {
      const fixture = path.join(temporary, 'fixture.html'), pdf = path.join(temporary, 'print.pdf')
      const sourceHtml = await render('B', count, processCount, changeCount)
      fs.writeFileSync(fixture, `<!doctype html><meta charset="utf-8"><style>
        body{margin:0}#source{position:absolute;left:-100000px;width:192mm}
        .sheet{width:210mm;height:297mm;padding:9mm;box-sizing:border-box;break-after:page;overflow:hidden}
        .sheet:last-child{break-after:auto}
        ${paper.css}${icon.css}
        @media print{@page{size:A4 portrait;margin:0}#source,#result{display:none}}
        </style><div id="source">${sourceHtml}</div><main id="output"></main><pre id="result"></pre>
        <script>
        onload=()=>{try{
          const pages=(${paginate.toString()})(document.querySelector('.work-paper'));
          document.querySelector('#output').innerHTML=pages.map(html=>'<div class="sheet">'+html+'</div>').join('');
          const result={pages:pages.length,heights:[...document.querySelectorAll('.sheet')].map(el=>el.getBoundingClientRect().height),
            fits:[...document.querySelectorAll('#output .work-paper')].map(el=>el.scrollHeight<=el.clientHeight+1),
            rows:[...document.querySelectorAll('#output .materials tbody tr')].map(el=>el.cells[0].textContent.trim())};
          document.querySelector('#result').textContent=JSON.stringify(result)
        }catch(e){document.querySelector('#result').textContent=JSON.stringify({error:e.message})}};
        </script>`)
      const run = extra => spawnSync(process.env.CHROME_BIN, ['--headless', '--no-sandbox',
        '--disable-dev-shm-usage', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--no-pdf-header-footer',
        ...extra, pathToFileURL(fixture).href], { encoding: 'utf8', timeout: 25000, maxBuffer: 10 * 1024 * 1024 })
      const dumped = run(['--dump-dom'])
      if (dumped.error) console.error(dumped.stderr); assert.ifError(dumped.error); assert.equal(dumped.status, 0, dumped.stderr)
      const doc = new JSDOM(dumped.stdout).window.document
      const actual = JSON.parse(doc.querySelector('#result').textContent)
      assert.equal(actual.error, undefined)
      assert.ok(actual.fits.every(Boolean), JSON.stringify(actual))
      assert.ok(actual.heights.every(height => Math.abs(height - 297 * 96 / 25.4) < 1))
      assert.deepEqual(actual.rows, Array.from({ length: Math.max(14, count) }, (_, i) => String(i + 1)))
      const names = [...doc.querySelectorAll('#output .material-name')].map(el => el.textContent.trim()).filter(Boolean)
      assert.deepEqual(names, Array.from({ length: count }, (_, i) => 'MAT_' + String(i + 1).padStart(3, '0')))
      // 相同材料列宽，续页没有重放材料；无14/20行截断。
      assert.equal(doc.querySelectorAll('#output .structure-art img').length, 1)
      assert.equal(doc.querySelector('#output').textContent.split('STRUCTURE_008').length - 1, 1)
      const tables = [...doc.querySelectorAll('#output .materials')]
      for (const table of tables) assert.equal(table.querySelector('colgroup').outerHTML, tables[0].querySelector('colgroup').outerHTML)
      if (count === 18 || count === 25) assert.equal(tables[0].tBodies[0].rows.length, count)
      if (count === 5) assert.equal(actual.pages, 1)
      if (count === 65) assert.ok(tables[0].tBodies[0].rows.length > 25)
      for (const group of groups) for (let i = 1; i <= processCount; i++)
        assert.equal(doc.querySelector('#output').textContent.split('OP_' + group.value + '_' + String(i).padStart(3, '0')).length - 1, 1)
      for (let i = 1; i <= changeCount; i++)
        assert.equal(doc.querySelector('#output').textContent.split('CHANGE_' + String(i).padStart(3, '0')).length - 1, 1)
      const printed = run(['--print-to-pdf=' + pdf])
      assert.ifError(printed.error); assert.equal(printed.status, 0, printed.stderr)
      const extract = spawnSync('python3', ['-c',
        'import pymupdf,json,sys; d=pymupdf.open(sys.argv[1]); print(json.dumps([dict(text=p.get_text(),width=p.rect.width,height=p.rect.height) for p in d]))',
        pdf], { encoding: 'utf8' })
      assert.ifError(extract.error); assert.equal(extract.status, 0, extract.stderr)
      const pdfPages = JSON.parse(extract.stdout)
      assert.equal(pdfPages.length, actual.pages)
      assert.ok(pdfPages.every(page => Math.abs(page.width - 595.28) < 1 && Math.abs(page.height - 841.89) < 1))
      const pdfText = pdfPages.map(page => page.text).join('\n')
      for (const name of names) assert.equal(pdfText.replace(/\s/g, '').split(name).length - 1, 1, name + ':' + pdfText)
      t.diagnostic(JSON.stringify({ count, processCount, changeCount, ...actual, pdfPages: pdfPages.length }))
    }
  } finally { fs.rmSync(temporary, { recursive: true, force: true }) }
})
