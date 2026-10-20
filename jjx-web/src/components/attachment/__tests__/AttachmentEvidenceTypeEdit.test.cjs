/**
 * 证据区资料类型编辑回归（dev-20261020-003）
 *
 * 运行：node --test src/components/attachment/__tests__/AttachmentEvidenceTypeEdit.test.cjs
 *
 * 边界：AttachmentRefList 只认 readonlyType；「紧凑证据区是否可改」由 AttachmentCenter
 * 计算成 readonlyType=compactMode && !editableDocumentType 后透传。本用例渲染真实组件，
 * 证明 readonlyType=false 时出现类型选择器、true 时仍是只读文本；并核对候选类型按来源过滤。
 */
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileScript } = require('vue/compiler-sfc')
const { renderToString } = require('vue/server-renderer')

const src = path.resolve(__dirname, '../../..')
const filename = path.join(src, 'components/attachment/AttachmentRefList.vue')
const cache = new Map()

const documentTypes = [
  { dict_code: 'attachment_category', item_key: 'BUSINESS_DOC', label: '商务资料', is_active: 1 },
  { dict_code: 'attachment_category', item_key: 'CUSTOMER_DOC', label: '客户资料', is_active: 1 },
  {
    dict_code: 'attachment_document_type', item_key: 'SAMPLE_SEND_EVIDENCE', label: '送样凭证', is_active: 1,
    ext_data: { parent: 'BUSINESS_DOC', sources: ['sample'] },
  },
  {
    dict_code: 'attachment_document_type', item_key: 'SAMPLE_CUSTOMER_EVIDENCE', label: '客户反馈凭证', is_active: 1,
    ext_data: { parent: 'CUSTOMER_DOC', sources: ['sample'] },
  },
  {
    dict_code: 'attachment_document_type', item_key: 'SALES_QUOTATION_REVIEW_EVIDENCE', label: '报价审核凭证', is_active: 1,
    ext_data: { parent: 'BUSINESS_DOC', sources: ['quotation'] },
  },
]

const pending = [{
  attachmentId: 1, requestKey: 'p1', fileName: '快递单.jpg', fileSize: 1024, sha256: null,
  state: 'pending', createdAt: '2026-10-20 10:00:00', documentType: 'SAMPLE_SEND_EVIDENCE',
}]
const refRow = {
  id: 11, attachmentId: 21, sourceType: 'sample', sourceId: 7, sourceNo: 'SP20261020001',
  fileName: '样品照片.jpg', fileSize: 2048, documentType: 'SAMPLE_SEND_EVIDENCE',
  createBy: 'dahuang', createTime: '2026-10-20 10:00:00',
}

function evaluate(source, file) {
  const exports = {}
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  vm.runInNewContext(code, {
    exports,
    console,
    defineProps: () => ({}),
    withDefaults: (value) => value,
    defineExpose() {},
    require(name) {
      if (name === 'vue') return vue
      if (name === 'element-plus') return { ElMessage: { success() {}, error() {} }, ElMessageBox: {} }
      if (name === '@/api/system/attachmentRef') {
        return {
          attachmentRefApi: {
            batchUpdate: () => Promise.resolve({ data: 1 }),
            previewUrlByRef: (id) => `/refs/${id}/preview`,
            downloadUrlByRef: (id) => `/refs/${id}/download`,
          },
        }
      }
      if (name === '@/api/system/attachment') return { attachmentApi: {} }
      const target = name.startsWith('@/')
        ? path.join(src, name.slice(2))
        : path.resolve(path.dirname(file), name)
      const full = /\.(vue|ts)$/.test(target) ? target : `${target}.ts`
      if (!cache.has(full)) {
        const raw = fs.readFileSync(full, 'utf8')
        const next = full.endsWith('.vue')
          ? compileScript(parse(raw, { filename: full }).descriptor, { id: 'ref-list-test', inlineTemplate: true }).content
          : raw
        cache.set(full, evaluate(next, full))
      }
      return cache.get(full)
    },
  })
  return exports
}

async function render(props) {
  const descriptor = parse(fs.readFileSync(filename, 'utf8')).descriptor
  const component = evaluate(
    compileScript(descriptor, { id: 'ref-list-test', inlineTemplate: true }).content,
    filename
  ).default
  const app = vue.createSSRApp(component, {
    rows: [refRow], pending, canEdit: true, documentTypes, sourceType: 'sample', ...props,
  })
  app.component('el-table', {
    props: ['data', 'emptyText'],
    setup(p, { slots }) {
      vue.provide('rows', vue.computed(() => p.data))
      return () => vue.h('div', [!p.data.length ? p.emptyText : '', slots.default?.()])
    },
  })
  app.component('el-table-column', {
    props: ['label'],
    setup(p, { slots }) {
      const rows = vue.inject('rows')
      return () => vue.h('div', [p.label, ...rows.value.map((row) => slots.default?.({ row }))])
    },
  })
  for (const name of ['el-button', 'el-tag', 'el-tooltip', 'el-option', 'el-option-group']) {
    app.component(name, { setup: (_, { slots }) => () => vue.h('span', slots.default?.()) })
  }
  app.component('el-select', {
    props: ['modelValue'],
    setup: (p) => () => vue.h('select', { value: p.modelValue }),
  })
  app.component('el-dialog', { render: () => null })
  app.directive('loading', { getSSRProps: () => ({}) })
  return (await renderToString(app)).replace(/<!--[\s\S]*?-->/g, '')
}

test('readonlyType=false：待绑定行出现类型选择器，可改类型', async () => {
  const html = await render({ readonlyType: false })
  assert.match(html, /at-select/)
  assert.match(html, /批量改类型/)
})

test('readonlyType=true：仍是只读文本，不出现类型选择器', async () => {
  const html = await render({ readonlyType: true })
  assert.doesNotMatch(html, /at-select/)
  assert.doesNotMatch(html, /批量改类型/)
  assert.match(html, /送样凭证/)
})

test('候选类型按 source 过滤，送样凭证只出现在样品来源', () => {
  const file = path.join(src, 'components/attachment/useAttachmentDocTypes.ts')
  const { useAttachmentDocTypes } = evaluate(fs.readFileSync(file, 'utf8'), file)
  const sample = useAttachmentDocTypes(vue.ref(documentTypes), () => 'sample')
  const sampleKeys = sample.selectableTypes.value.map((t) => t.item_key)
  assert.ok(sampleKeys.includes('SAMPLE_SEND_EVIDENCE'))
  assert.ok(sampleKeys.includes('SAMPLE_CUSTOMER_EVIDENCE'))
  assert.equal(sampleKeys.includes('SALES_QUOTATION_REVIEW_EVIDENCE'), false)

  const quotation = useAttachmentDocTypes(vue.ref(documentTypes), () => 'quotation')
  assert.equal(quotation.selectableTypes.value.some((t) => t.item_key === 'SAMPLE_SEND_EVIDENCE'), false)
})

test('送样凭证归入商务资料一级，一级过滤只给本组类型', () => {
  const file = path.join(src, 'components/attachment/useAttachmentDocTypes.ts')
  const { useAttachmentDocTypes } = evaluate(fs.readFileSync(file, 'utf8'), file)
  const sample = useAttachmentDocTypes(vue.ref(documentTypes), () => 'sample')
  assert.equal(sample.categoryKeyOfType('SAMPLE_SEND_EVIDENCE'), 'BUSINESS_DOC')
  const business = sample.secondaryGroupsFor('BUSINESS_DOC').flatMap((g) => g.options.map((o) => o.item_key))
  assert.deepEqual(Array.from(business), ['SAMPLE_SEND_EVIDENCE'])
})
