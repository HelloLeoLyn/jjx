const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

// 隔离实际纯函数，避免为格式化测试加载浏览器与API模块。
const source = fs.readFileSync(path.join(__dirname, '../docset.ts'), 'utf8')
const parsed = ts.createSourceFile('docset.ts', source, ts.ScriptTarget.Latest, true)
const functions = parsed.statements.filter(node => ts.isFunctionDeclaration(node)
  && ['plain', 'workSpecMaterialSpec'].includes(node.name?.text)).map(node => node.getText(parsed)).join('\n')
const quantity = fs.readFileSync(path.join(__dirname, '../../../../../utils/bomQuantity.ts'), 'utf8')
const context = { exports: {} }
vm.runInNewContext(ts.transpileModule(quantity + '\n' + functions, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, context)
const { workSpecMaterialSpec } = context.exports

test('卷材跳距插在规格与模数之间，不影响用量', () => {
  const row = { specification: '110', split: '50mm', moduleQty: 5, quantity: 0.2 }
  assert.equal(workSpecMaterialSpec(row), '110跳50mm=5PCS')
  assert.equal(row.quantity, 0.2)
})

test('跳距为空沿用规格、模数、尺寸及计件用量规则', () => {
  for (const split of [undefined, null, '', '  ']) {
    assert.equal(workSpecMaterialSpec({ specification: '110', split, moduleQty: 5 }), '110=5PCS')
    assert.equal(workSpecMaterialSpec({ specification: '110', split, moduleQty: 1 }), '110=1PCS')
    assert.equal(workSpecMaterialSpec({ widthMm: 290, lengthMm: 135, split, moduleQty: 30 }), '290*135mm=30PCS')
    assert.equal(workSpecMaterialSpec({ split, unit: 'PCS', moduleQty: 1, baseQty: 2 }), '2个/PCS')
  }
})

test('无有效模数不编造PCS，清空跳距后恢复原格式', () => {
  assert.equal(workSpecMaterialSpec({ specification: '110', split: ' 50mm ' }), '110跳50mm')
  assert.equal(workSpecMaterialSpec({ specification: '110', split: '' }), '110')
  assert.equal(workSpecMaterialSpec(null), '')
})
