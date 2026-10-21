const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

const context = { exports: {} }
vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname, '../workSpecFont.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, context)
const { defaultWorkSpecFontSettings: defaults, parseWorkSpecFontSettings: parse,
  serializeWorkSpecFontSettings: serialize, workSpecFontAreas: areas } = context.exports
const plain = value => JSON.parse(JSON.stringify(value))

test('默认设置为实际px，标题和内容分别提供独立选项', () => {
  const settings = defaults()
  assert.equal(settings.title, 27)
  assert.equal(settings.materialNumberTitle, 14)
  assert.equal(settings.materialNameContent, 12)
  assert.equal(settings.flow0OperationTitle, 14)
  assert.equal(settings.notesTitle, 17)
  assert.ok(areas.every(area => settings[area.key] > 0))
  settings.materialNameContent = 16
  assert.equal(settings.materialNameTitle, 14)
  assert.equal(settings.materialNumberContent, 13)
  assert.equal(settings.flow0OperationContent, 12)
  assert.equal(defaults().materialNameContent, 12)
})

test('独立设置通过打印URL往返后保持实际字号', () => {
  const settings = { ...defaults(), title: 30, materialNumberTitle: 18, materialNumberContent: 13,
    materialNameTitle: 15, materialNameContent: 16, flow0OperationTitle: 16,
    flow1OperationContent: 15, notesTitle: 20, notes: 14, footer: 11 }
  const value = serialize(settings)
  assert.ok(value.startsWith('v4,'))
  const url = new URL('http://localhost/print')
  url.searchParams.set('fonts', value)
  assert.deepEqual(plain(parse(url.searchParams.get('fonts'))), settings)
})

test('旧打印链接五项相对增量转换为实际字号', () => {
  assert.deepEqual(plain(parse('0,0,0,0,0')), plain(defaults()))
  const legacy = parse('2,3,-1,1,0')
  assert.equal(legacy.title, 29)
  assert.equal(legacy.materialNameTitle, 17)
  assert.equal(legacy.materialNameContent, 15)
  assert.equal(legacy.flow0OperationTitle, 13)
  assert.equal(legacy.flow0OperationContent, 11)
  assert.equal(legacy.notesTitle, 18)
  assert.equal(legacy.notes, 13)
  assert.equal(legacy.footer, 13)
})

test('缺失、损坏、未知版本和越界参数安全恢复默认', () => {
  for (const value of [undefined, '', 'v5,27,14,12,14,12,17,12,13',
    'v2,0,14,12,14,12,17,12,13', 'v2,49,14,12,14,12,17,12,13',
    'v2,27.5,14,12,14,12,17,12,13', 'v2,27,14,12', '0,,0,0,0', '0,7,0,0,0']) {
    assert.deepEqual(plain(parse(value)), plain(defaults()))
  }
})

test('区域设置v2可以转换为逐列设置，再往返新打印链接', () => {
  const settings = parse('v2,30,18,13,16,15,20,14,11')
  assert.equal(settings.materialNumberTitle, 18)
  assert.equal(settings.materialNameTitle, 18)
  assert.equal(settings.materialNumberContent, 13)
  assert.equal(settings.flow0OperationTitle, 16)
  assert.equal(settings.flow2HoursContent, 15)
  assert.deepEqual(plain(parse(serialize(settings))), plain(settings))
  const smallestLegacy = parse('0,-3,-3,-3,0')
  assert.deepEqual(plain(parse(serialize(smallestLegacy))), plain(smallestLegacy))
})

test('所有列设置均可独立往返，不影响相邻列或其他流程分组', () => {
  for (const area of areas) {
    const settings = defaults()
    settings[area.key] = 23
    const result = parse(serialize(settings))
    for (const other of areas) {
      assert.equal(result[other.key], other.key === area.key ? 23 : other.defaultSize)
    }
  }
})

test('模板B独立列与字号、旧v3打印设置兼容，非法模板回退A', () => {
  const { normalizeWorkSpecTemplate, workSpecFontGroupsForTemplate, materialFontColumnsB } = context.exports
  assert.equal(normalizeWorkSpecTemplate('B'), 'B')
  for (const value of [undefined, 'A', '', 'C', ['B']]) assert.equal(normalizeWorkSpecTemplate(value), 'A')
  assert.deepEqual(plain(materialFontColumnsB.map(column => column.heading)), ['序号', '项目', '材　料', '规格', '模数', '基数', '刀模位置'])
  const controls = workSpecFontGroupsForTemplate('B').find(group => group.label === '材料表').areas
  assert.ok(controls.some(area => area.key === 'materialModuleContent'))
  assert.ok(controls.some(area => area.key === 'materialBaseContent'))
  assert.ok(controls.every(area => !area.key.startsWith('materialEmboss')))
  const overview = workSpecFontGroupsForTemplate('B').find(group => group.label === '顶部信息').areas
  assert.deepEqual(plain(overview.map(area => area.key)), ['materialEmbossTitle', 'materialEmbossContent', 'materialEmbossValueContent'])
  const oldAreas = areas.filter(area => !area.key.startsWith('materialModule') && !area.key.startsWith('materialBase'))
  const old = parse('v3,' + oldAreas.map(area => area.key === 'materialSpecContent' ? 18 : area.defaultSize).join(','))
  assert.equal(old.materialSpecContent, 18)
  assert.equal(old.materialModuleContent, 13)
  assert.equal(old.materialBaseContent, 13)
  assert.deepEqual(plain(parse(serialize(old))), plain(old))
})

test('实际打印按钮把A/B选择、版本和字号传入打印入口', async () => {
  const { parse: parseSfc } = require('vue/compiler-sfc')
  const filename = path.join(__dirname, '../ProductSpecPreview.vue')
  const { descriptor } = parseSfc(fs.readFileSync(filename, 'utf8'), { filename })
  const source = ts.createSourceFile('preview.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
  const method = source.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'printDocuments')
  assert.ok(method)
  for (const template of ['A', 'B']) {
    let opened
    const state = { canPrint: { value: true }, props: { standalone: false, productId: 41, versionId: 7 },
      selectedSections: { value: ['spec'] }, selectedFiles: { value: [] }, fontSettings: { value: defaults() },
      selectedTemplate: { value: template }, generateOverflowAppendix: { value: true }, serializeWorkSpecFontSettings: serialize, URLSearchParams,
      window: { open(url, target) { opened = { url, target } } } }
    vm.createContext(state)
    vm.runInContext(ts.transpileModule(method.getText(source), {
      compilerOptions: { target: ts.ScriptTarget.ES2022 },
    }).outputText, state)
    await state.printDocuments()
    const url = new URL(opened.url, 'http://localhost')
    assert.equal(opened.target, '_blank')
    assert.equal(url.pathname, '/print/product-spec/41')
    assert.equal(context.exports.normalizeWorkSpecTemplate(url.searchParams.get('template')), template)
    assert.equal(url.searchParams.get('versionId'), '7')
    assert.equal(url.searchParams.get('print'), '1')
    assert.equal(url.searchParams.get('generateOverflowAppendix'), '1')
    assert.deepEqual(plain(parse(url.searchParams.get('fonts'))), plain(defaults()))
  }
})

test('B完整材料与流程送入DOM测量，A仍为14项', () => {
  const source = fs.readFileSync(path.join(__dirname, '../docset.ts'), 'utf8')
  const parsed = ts.createSourceFile('docset.ts', source, ts.ScriptTarget.Latest, true)
  const functions = parsed.statements.filter(node => ts.isFunctionDeclaration(node)
    && ['makePages', 'workSpecFlowSource', 'flatten', 'specText', 'dieLocationRows', 'flattenedBom', 'dieLocationKey'].includes(node.name?.text))
    .map(node => node.getText(parsed)).join('\n')
  const runtime = { exports: {}, documentSections: [{ key: 'spec' }], orderedPrintFiles: () => [],
    flowGroups: ['PANEL', 'UP_LINE', 'DOWN_LINE'].map(value => ({ value, label: value })) }
  vm.runInNewContext(ts.transpileModule(functions, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, runtime)
  const data = { bom: { items: Array.from({ length: 25 }, (_, i) => ({ itemId: i + 1, materialCode: `M${i + 1}` })) },
    routing: { items: Array.from({ length: 18 }, () => ({ processCategory: 'PANEL' })) }, files: [],
    workSpec: { engineeringRequirements: 'NOTE', changes: [], dieLocationText: Array.from({ length: 25 }, (_, i) => `D${i + 1}`).join('\n') } }
  const a = runtime.exports.makePages(data, ['spec'], [], {}, {}, 'A').filter(page => page.kind === 'spec')
  const pending = runtime.exports.makePages(data, ['spec'], [], {}, {}, 'B')
  assert.equal(pending[0].flowPending, true)
  const html = ['<article>首张A4</article>', '<article>续张A4</article>']
  const b = runtime.exports.makePages(data, ['spec'], [], {}, {}, 'B', html)
  const flow = runtime.exports.workSpecFlowSource(data)
  assert.deepEqual(plain(a.map(page => page.rows.length)), [14, 11])
  assert.equal(a[1].diePositionRows[0], 'D15')
  assert.deepEqual(plain(b.map(page => page.flowHtml)), html)
  assert.deepEqual(plain(flow.rows.map(row => row.itemId)), Array.from({ length: 25 }, (_, i) => i + 1))
  assert.equal(flow.diePositionRows[24], 'D25')
  assert.equal(flow.groups[0].rows.length, 18)
  const aOverflow = runtime.exports.makePages(data, ['spec'], [], {}, { 'spec-0': [{ text: '备注长内容' }] }, 'A')
  assert.equal(aOverflow.some(page => page.kind === 'spec-details'), false)
  const enabled = runtime.exports.makePages(data, ['spec'], [], {}, { 'spec-0': [{ text: '备注长内容' }] }, 'A', [], true)
  assert.equal(enabled.some(page => page.kind === 'spec-details'), true)

})

test('实际纸张组件将各列标题与内容字号写入对应单元格，续页同样生效', async () => {
  const vue = require('vue')
  const { renderToString } = require('vue/server-renderer')
  const { parse: parseSfc, compileScript } = require('vue/compiler-sfc')
  function compileComponent(filename, deps) {
    const { descriptor } = parseSfc(fs.readFileSync(filename, 'utf8'), { filename })
    const script = compileScript(descriptor, { id: 'font-render-check', inlineTemplate: true })
    const component = { exports: {}, require(name) {
      assert.ok(name in deps, `Unexpected dependency: ${name}`)
      return deps[name]
    } }
    vm.runInNewContext(ts.transpileModule(script.content, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText, component)
    return component.exports.default
  }
  const icon = compileComponent(path.join(__dirname, '../../../../../components/SvgIcon/index.vue'), { vue })
  const source = fs.readFileSync(path.join(__dirname, '../docset.ts'), 'utf8')
  const parsed = ts.createSourceFile('docset.ts', source, ts.ScriptTarget.Latest, true)
  const functions = parsed.statements.filter(node => ts.isFunctionDeclaration(node)
    && ['plain', 'workSpecMaterialSpec', 'workSpecMaterialDimensions'].includes(node.name?.text))
    .map(node => node.getText(parsed)).join('\n')
  const quantity = fs.readFileSync(path.join(__dirname, '../../../../../utils/bomQuantity.ts'), 'utf8')
  const formatters = { exports: {} }
  vm.runInNewContext(ts.transpileModule(quantity + '\n' + functions, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, formatters)
  const deps = {
    vue,
    '@/components/SvgIcon/index.vue': { __esModule: true, default: icon },
    '@/api/product/workSpec': { embossFields: [{ key: 'test', label: 'EMBOSS-ONLY-A', unit: 'mm' }, { key: 'blank', label: 'EMPTY-EMBOSS', unit: 'mm' }] },
    './docset': { ...formatters.exports, flowGroups: [],
      workSpecStepSubscript: () => '', workSpecOperationRemark: () => '' },
    './useWorkSpecOverflow': { useWorkSpecOverflow: () => ({ measure() {} }) },
    './workSpecFont': context.exports,
  }
  const paper = compileComponent(path.join(__dirname, '../WorkSpecPaper.vue'), deps)
  const fonts = { ...defaults(), materialNumberTitle: 21, materialNameTitle: 22,
    materialNameContent: 19, materialProjectContent: 17, flow0NumberContent: 23, flow1NumberContent: 24,
    flow0OperationContent: 11, flow1OperationContent: 15, flow2OperationContent: 18 }
  // 直接预览和打印URL还原使用相同纸张组件，两者都须把尺寸传到真实SvgIcon。
  for (const fontSettings of [fonts, parse(serialize(fonts))]) {
  for (const { specTemplate, testValue, hasRemarks } of ['A', 'B'].flatMap(specTemplate =>
    [undefined, 0, '0.2mm', '常温', '<80℃>', '按实物调整并确认无白痕后再连续生产'].map(testValue => ({ specTemplate, testValue, hasRemarks: testValue !== undefined })))) {
  for (const continuation of [0, 1]) {
    const rendered = await renderToString(vue.createSSRApp(paper, {
      data: { product: { productName: 'DEMO', productCode: 'D' }, routing: { items: [] },
        workSpec: { approved: false, emboss: hasRemarks ? { test: testValue, blank: '' } : {}, issueUnit: '' } },
      page: { continuation, engineeringNotes: hasRemarks ? 'NOTE-ONLY-A' : '', rows: [{ materialName: 'MATERIAL-A', icon: '/test.svg', specification: '110', split: '50mm', moduleQty: 5, baseQty: 2 }],
        groups: ['面板', '上线', '下线'].map((label, index) => ({ label,
          rows: [{ children: [{ icon: `flow-${index}-a` }, { icon: `flow-${index}-b` }] }] })), changeLines: [] },
      pageIndex: continuation, pageCount: 2, fontSettings, specTemplate,
    }))
    const html = rendered.replace(/<!--[\s\S]*?-->/g, '')
    assert.match(html, /<th[^>]*style="font-size:21px;"[^>]*>序号<\/th>/)
    assert.match(html, /<th[^>]*style="font-size:22px;"[^>]*>材　料<\/th>/)
    assert.match(html, /<td style="font-size:19px;">[\s\S]*?MATERIAL-A/)
    assert.ok(html.includes(`style="font-size:23px;">${continuation * 14 + 1}</td>`))
    assert.ok(html.includes(`style="font-size:24px;">${continuation * 6 + 1}</td>`))
    assert.match(html, /<img[^>]*src="\/test.svg"[^>]*width="17"[^>]*height="17"/)
    const materialHtml = html.match(/<table\b[^>]*class="grid materials"[^>]*>[\s\S]*?<\/table>/)[0]
    assert.ok(!html.includes('spec-overview'))
    assert.ok(html.includes('class="structure-art"'))
    assert.ok(html.includes('class="structure-title"'))
    assert.ok(materialHtml.includes('规格及模数'))
    assert.ok(materialHtml.includes('110跳50mm=5PCS'))
    assert.ok(materialHtml.includes('EMBOSS-ONLY-A'))
    assert.equal(materialHtml.includes('NOTE-ONLY-A'), hasRemarks)
    assert.equal([...materialHtml.split('<tbody>')[1].matchAll(/<tr\b/g)].length, 14)
    for (const [index, size] of [[0, 11], [1, 15], [2, 18]]) {
      for (const child of ['a', 'b']) {
        assert.match(html, new RegExp(`<svg[^>]*width="${size}"[^>]*height="${size}"[^>]*><use href="#icon-flow-${index}-${child}"`))
      }
    }
  }
  }
  }
})
