/** AttachmentManager detail dialog transaction boundary regression. */
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse, compileScript, compileTemplate } = require('vue/compiler-sfc')

function setupDialog(props) {
  const filename = path.resolve(__dirname, '../AttachmentManagerDetailDialog.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'attachment-detail-dialog-test' })
  const sandbox = {
    exports: {},
    module: { exports: {} },
    require(name) {
      if (name === 'vue') return vue
      if (name.endsWith('.vue')) return { render: () => null }
      return {}
    },
  }
  const mod = { exports: {} }
  const code = ts.transpileModule(compiled.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: 'AttachmentManagerDetailDialog.ts',
  }).outputText
  vm.runInNewContext(`(function (exports, require, module) {${code}\n})`, sandbox, { filename })(mod.exports, sandbox.require, mod)
  const emitted = []
  const bindings = mod.exports.default.setup(props, {
    emit: (...args) => emitted.push(args),
    expose() {},
    attrs: {},
    slots: {},
  })
  return { bindings, emitted }
}

const editableRow = { kind: 'ref', rowKey: 'ref:5', refId: 5, data: { id: 5 } }

test('取消只关闭详情弹窗，不发确认数据给会话', () => {
  const { bindings, emitted } = setupDialog({
    modelValue: true,
    row: editableRow,
    documentTypes: [],
    sourceType: 'quotation',
    editable: true,
    readonlyReason: '',
    remark: '原备注',
    sortOrder: 1,
  })
  bindings.form.remark = '未确认备注'
  bindings.form.sortOrder = 9
  bindings.onCancel()
  assert.deepEqual(emitted, [['update:modelValue', false]])
})

test('确认只发编辑值，受限行不能提交', () => {
  const editable = setupDialog({
    modelValue: true,
    row: editableRow,
    documentTypes: [],
    sourceType: 'quotation',
    editable: true,
    readonlyReason: '',
    remark: '当前备注',
    sortOrder: 2,
  })
  editable.bindings.form.remark = '更新备注'
  editable.bindings.form.sortOrder = 3
  editable.bindings.onConfirm()
  assert.deepEqual(JSON.parse(JSON.stringify(editable.emitted)), [['confirm', { remark: '更新备注', sortOrder: 3 }]])

  const readonly = setupDialog({
    modelValue: true,
    row: { kind: 'readonly', rowKey: 'trace:9' },
    documentTypes: [],
    sourceType: 'quotation',
    editable: false,
    readonlyReason: '同链路只读附件',
    remark: '',
    sortOrder: 0,
  })
  readonly.bindings.onConfirm()
  assert.deepEqual(Array.from(readonly.emitted), [])
})

test('Manager 与详情弹窗模板均可编译，Manager 将详情交给独立组件并复用 session.commit', () => {
  for (const file of ['../AttachmentManager.vue', '../AttachmentManagerDetailDialog.vue']) {
    const filename = path.resolve(__dirname, file)
    const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
    const script = compileScript(descriptor, { id: `attachment-template-${path.basename(file)}` })
    const template = compileTemplate({
      source: descriptor.template.content,
      filename,
      id: `attachment-template-${path.basename(file)}`,
      compilerOptions: { bindingMetadata: script.bindings },
    })
    assert.deepEqual(template.errors, [], file)
  }
  const manager = fs.readFileSync(path.resolve(__dirname, '../AttachmentManager.vue'), 'utf8')
  assert.match(manager, /<AttachmentManagerDetailDialog\b/)
  assert.match(manager, /session\.commit\(scope\)/)
  assert.doesNotMatch(manager, /session\.saveChanges\(/)
})
