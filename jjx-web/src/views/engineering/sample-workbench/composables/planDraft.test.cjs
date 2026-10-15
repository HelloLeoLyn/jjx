const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const source = fs.readFileSync(path.join(__dirname, 'planDraft.ts'), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const compiledModule = { exports: {} }
new Function('exports', compiled)(compiledModule.exports)
const { createCardEditSessions, planFingerprint, materialValues } = compiledModule.exports
const card = () => ({ items: [{ stdProcessId: 8 }], category: 'PANEL', operationRemark: '原备注', materials: '[{"name":"胶","qty":0.25}]', materialRows: [], editing: false, saveState: 'synced' })
test('取消恢复本次输入但不覆盖执行状态，也不影响其他卡片', () => {
  const a = card(), b = card(), sessions = createCardEditSessions()
  sessions.begin(a)
  a.editing = true; a.items.push({ stdProcessId: 9 }); a.operationRemark = '修改'; a.materialRows = [{ name: '新料', qty: 3 }]
  a.status = 1; b.operationRemark = '其他草稿'
  sessions.cancel(a)
  assert.equal(a.items.length, 1); assert.equal(a.operationRemark, '原备注'); assert.equal(a.editing, false)
  assert.equal(a.status, 1); assert.equal(b.operationRemark, '其他草稿')
  assert.equal(JSON.parse(a.materials)[0].qty, 0.25)
})
test('完成编辑保留材料草稿并剔除搜索临时状态', () => {
  const a = card(), sessions = createCardEditSessions(), before = planFingerprint([a], [])
  sessions.begin(a); a.editing = true
  a.materialRows = [{ name: '铜', qty: 2, options: [{ materialId: 1 }], loading: false, uid: 'ui' }]
  sessions.finish(a)
  assert.deepEqual(JSON.parse(a.materials), [{ name: '铜', qty: 2 }])
  assert.equal(a.editing, false); assert.notEqual(planFingerprint([a], []), before)
})
test('删除计入未保存变化；执行时间和界面展开不计入计划草稿', () => {
  const a = card(), baseline = planFingerprint([a], [])
  a.status = 1; a.startTime = 'now'; a.expanded = true
  assert.equal(planFingerprint([a], []), baseline)
  assert.notEqual(planFingerprint([], []), baseline)
})
test('兼容损坏或非数组材料JSON，不导致页面计算崩溃', () => {
  assert.deepEqual(materialValues({ materials: '{' }), [])
  assert.deepEqual(materialValues({ materials: '{}' }), [])
})
