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
  assert.ok(value.startsWith('v3,'))
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
  for (const value of [undefined, '', 'v4,27,14,12,14,12,17,12,13',
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
  const deps = {
    vue,
    '@/components/SvgIcon/index.vue': { __esModule: true, default: icon },
    '@/api/product/workSpec': { embossFields: [] },
    './docset': { flowGroups: [], plain: v => String(v ?? ''), workSpecMaterialSpec: () => '',
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
  for (const continuation of [0, 1]) {
    const html = await renderToString(vue.createSSRApp(paper, {
      data: { product: { productName: 'DEMO', productCode: 'D' }, routing: { items: [] },
        workSpec: { approved: false, emboss: {}, issueUnit: '' } },
      page: { continuation, rows: [{ materialName: 'MATERIAL-A', icon: '/test.svg' }],
        groups: ['面板', '上线', '下线'].map((label, index) => ({ label,
          rows: [{ children: [{ icon: `flow-${index}-a` }, { icon: `flow-${index}-b` }] }] })), changeLines: [] },
      pageIndex: continuation, pageCount: 2, fontSettings,
    }))
    assert.match(html, /<th[^>]*style="font-size:21px;"[^>]*>序号<\/th>/)
    assert.match(html, /<th[^>]*style="font-size:22px;"[^>]*>材　料<\/th>/)
    assert.match(html, /<td style="font-size:19px;">[\s\S]*?MATERIAL-A/)
    assert.ok(html.includes(`style="font-size:23px;">${continuation * 14 + 1}</td>`))
    assert.ok(html.includes(`style="font-size:24px;">${continuation * 6 + 1}</td>`))
    assert.match(html, /<img[^>]*src="\/test.svg"[^>]*width="17"[^>]*height="17"/)
    for (const [index, size] of [[0, 11], [1, 15], [2, 18]]) {
      for (const child of ['a', 'b']) {
        assert.match(html, new RegExp(`<svg[^>]*width="${size}"[^>]*height="${size}"[^>]*><use href="#icon-flow-${index}-${child}"`))
      }
    }
  }
  }
})
