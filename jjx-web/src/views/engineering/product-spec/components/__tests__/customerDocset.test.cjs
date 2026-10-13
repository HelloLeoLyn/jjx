const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

const root = path.resolve(__dirname, '../../../../..')
function load(file, deps = {}) {
  const exports = {}
  const source = fs.readFileSync(path.join(root, file), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  vm.runInNewContext(code, { exports, require(name) {
    assert.ok(name in deps, `未声明依赖：${name}`)
    return deps[name]
  } })
  return exports
}
const directory = 'views/engineering/product-spec/components/'
const refs = load(directory + 'versionDocset.ts')
const api = load('api/product/workSpec.ts', { '@/utils/request': {} })
const forbidden = () => { throw new Error('不允许回查当前资料') }
function setup(items) {
  return load(directory + 'docset.ts', {
    './versionDocset': refs,
    '@/api/system/attachment': { attachmentApi: { customerDocs: forbidden, productFiles: forbidden } },
    '@/api/product/standardProcess': { standardProcessApi: { getById: forbidden } },
    '@/enums/product': { ProcessCategoryEnum: { items: [] } },
    '@/components/product/productFileCategories': load('components/product/productFileCategories.ts'),
    '@/enums/product/drawing': {},
    '@/api/product/workSpec': { ...api, productWorkSpecApi: { get: forbidden, getVersion: async () => ({ data: { version: { id: 3 }, items } }) } },
    '@/api/product/bom': { productBomApi: { getEngineeringBomInfo: forbidden } },
    '@/api/product/routing': { productRouteApi: { getProductRouteInfo: forbidden } },
    '@/components/product/productFilePreview': {}, '@/utils/bomQuantity': {},
  })
}
function snapshot() {
  const files = [
    { attachmentId: 36, section: 'customer', category: '客供稿', fileName: '客供稿.jpg', displayName: '客户确认稿', mime: 'image/jpeg' },
    ...['面板图', '线路图', '刀模参数图', '结构图', '结构图'].map((category, index) => ({
      attachmentId: index + 37, section: 'atlas', category, fileName: `工程图${index}.jpg`, fileRole: 'PRINT',
    })),
  ]
  return { schemaVersion: 1, productCode: 'YL1025PWOO', productName: '冻结名称', spec: { structureFileId: 40 }, bom: { items: [] }, routing: { items: [] }, files, counts: { files: files.length } }
}

test('完整版本只读快照：客供一页、工程图五页；客户页签与整套预览同源', async () => {
  const d = setup([{ resourceType: 'DOCSET_JSON', snapshotJson: JSON.stringify(snapshot()) }])
  const data = await d.loadDocsetForVersion(3, 3, '当前编码', '当前名称')
  assert.equal(data.product.productName, '冻结名称')
  const selected = d.defaultDocumentFiles(data.files)
  assert.equal(selected.length, 6)
  const customer = selected.find(f => f.id === 36)
  assert.equal(customer.name, '客户确认稿')
  assert.equal(customer.originalName, '客供稿.jpg')
  assert.equal(customer.kind, 'image')
  const images = Object.fromEntries(selected.map(f => [f.key, [{ url: `mock:${f.id}`, width: 100, height: 100 }]]))
  const ids = selected.map(f => f.key)
  const all = d.makePages(data, d.documentSections.map(s => s.key), ids, images)
  assert.equal(all.filter(p => p.section === 'customer' && p.kind === 'image').length, 1)
  assert.equal(all.filter(p => p.section === 'atlas' && p.kind === 'image').length, 5)
  const customerOnly = d.makePages(data, ['customer'], ids, images)
  assert.equal(customerOnly.length, 1)
  assert.equal(customerOnly[0].file.id, 36)
})

test('旧版客供未收录说明进入纸张；仍保留五图且不补取当前客供', async () => {
  const items = [{ resourceType: 'SPEC_JSON', snapshotJson: '{"structureFileId":40}' },
    ...snapshot().files.filter(f => f.section === 'atlas').map(f => ({ resourceType: 'DRAWING', operationRef: f.category, snapshotJson: JSON.stringify(f) }))]
  const d = setup(items)
  const data = await d.loadDocsetForVersion(3, 2)
  assert.equal(data.files.length, 5)
  assert.equal(d.defaultDocumentFiles(data.files).length, 5)
  const pages = d.makePages(data, ['customer'], [], {})
  assert.equal(pages[0].kind, 'empty')
  assert.match(pages[0].emptyMessage, /未收录客供资料/)
})

test('缺正文、缺清单、数量错误、无附件ID、重复文件和未知格式均禁止完整快照预览', async () => {
  const cases = [null, {}, { ...snapshot(), schemaVersion: 99 }, { ...snapshot(), spec: null },
    { ...snapshot(), files: undefined }, { ...snapshot(), counts: { files: 7 } },
    { ...snapshot(), files: [{ section: 'customer', fileName: 'a.jpg' }], counts: { files: 1 } },
    { ...snapshot(), files: [snapshot().files[0], snapshot().files[0]], counts: { files: 2 } }]
  for (const value of cases) {
    const d = setup([{ resourceType: 'DOCSET_JSON', snapshotJson: JSON.stringify(value) }])
    await assert.rejects(() => d.loadDocsetForVersion(3, 3), /无法预览/)
  }
})

test('完整快照确实无客供时允许空栏目，承认书和客供资料分类一致', async () => {
  const value = snapshot()
  value.files = value.files.filter(f => f.section !== 'customer')
  value.counts.files = value.files.length
  const d = setup([{ resourceType: 'DOCSET_JSON', snapshotJson: JSON.stringify(value) }])
  const data = await d.loadDocsetForVersion(3, 3)
  assert.equal(data.unavailableSections, undefined)
  assert.equal(d.makePages(data, ['customer'], [], {})[0].emptyMessage, undefined)
  for (const category of ['客供稿', '承认书', '客供资料']) assert.equal(refs.sectionForCategory(false, category), 'customer')
})
