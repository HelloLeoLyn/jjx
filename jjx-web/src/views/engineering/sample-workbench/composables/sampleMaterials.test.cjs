const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const source = fs.readFileSync(path.join(__dirname, 'sampleMaterials.ts'), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const compiledModule = { exports: {} }
new Function('exports', 'require', compiled)(compiledModule.exports, require)
const { normalizeMaterial, validateMaterials, projectIdsOf } = compiledModule.exports

test('历史小数用量约分为整数基数/模数且单位用量=基数÷模数', () => {
  const row = normalizeMaterial({ name: '胶', qty: 0.25 })
  assert.equal(row.baseQty, 1)
  assert.equal(row.moduleQty, 4)
  assert.equal(row.qty, 0.25)
})

test('整数用量归一到基数/模数，已给基数模数优先', () => {
  assert.deepEqual(
    (({ baseQty, moduleQty, qty }) => ({ baseQty, moduleQty, qty }))(normalizeMaterial({ name: '铜', qty: 3 })),
    { baseQty: 3, moduleQty: 1, qty: 3 }
  )
  const explicit = normalizeMaterial({ name: '铜', baseQty: 3, moduleQty: 4, qty: 9 })
  assert.equal(explicit.baseQty, 3)
  assert.equal(explicit.moduleQty, 4)
  assert.equal(explicit.qty, 0.75)
})

test('项目继承：无项目时带入来源默认；用户已选不被覆盖', () => {
  const inherited = normalizeMaterial({ name: 'x', qty: 1 }, [8, 9])
  assert.deepEqual(inherited.projectIds, [8, 9])
  assert.equal(inherited.projectInherited, true)
  const chosen = normalizeMaterial({ name: 'x', qty: 1, projectIds: [7], projectInherited: false }, [8])
  assert.deepEqual(chosen.projectIds, [7])
})

test('模数/基数校验拦截非整数', () => {
  assert.equal(validateMaterials([{ name: 'a', baseQty: 1, moduleQty: 4 }]), null)
  assert.ok(validateMaterials([{ name: 'b', baseQty: 1.5, moduleQty: 1 }]))
  assert.ok(validateMaterials([{ name: 'c', baseQty: 0, moduleQty: 1 }]))
  assert.ok(validateMaterials([{ name: 'd', baseQty: undefined, moduleQty: 1 }]))
})

test('projectIdsOf 去重有效标准工序ID', () => {
  const pc = { items: [{ stdProcessId: 8 }, { stdProcessId: 9 }, { stdProcessId: 8 }, { stdProcessId: null }] }
  assert.deepEqual(projectIdsOf(pc), [8, 9])
})
