const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const { test } = require('node:test')
const ts = require('typescript')
const source = fs.readFileSync(path.join(__dirname, '../src/views/sales/delivery/components/mergeDeliveryQuantity.ts'), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const exportsObject = {}
vm.runInNewContext(compiled, { exports: exportsObject, Map, Math, Number })
const { defaultDeliveryQuantities, deliveryQuantityError } = exportsObject
const line = (id, orderId, owned, shared, total = owned + shared) => ({
  id, orderId, productId: 2, orderRemainingQuantity: 1000, availableQuantity: owned + shared,
  ownStockAvailable: owned, sharedStockAvailable: shared, productStockAvailable: total,
})
const values = rows => Array.from(defaultDeliveryQuantities(rows))

test('1000件订单只有700件自有成品时默认建700', () => {
  assert.deepEqual(values([line(4, 5, 700, 0)]), [700])
})
test('同订单同产品两行不能重复使用700件预留', () => {
  assert.deepEqual(values([line(4, 5, 700, 0), line(6, 5, 700, 0)]), [700, 0])
})
test('跨订单共享700件只分配一次', () => {
  assert.deepEqual(values([line(4, 5, 0, 700), line(6, 7, 0, 700)]), [700, 0])
})
test('先分配各订单自有成品，再分配共享成品', () => {
  assert.deepEqual(values([line(4, 5, 500, 200, 900), line(6, 7, 200, 200, 900)]), [700, 200])
})
test('共享库存可以拆成400和300，400和400不能提交', () => {
  const rows = [{ ...line(4, 5, 0, 700), sendQuantity: 400 }, { ...line(6, 7, 0, 700), sendQuantity: 400 }]
  assert.ok(deliveryQuantityError(rows))
  rows[1].sendQuantity = 300
  assert.equal(deliveryQuantityError(rows), '')
})
test('不能使用另一个订单的自有预留填补缺口', () => {
  const rows = [{ ...line(4, 5, 500, 200, 900), sendQuantity: 750 }, { ...line(6, 7, 200, 200, 900), sendQuantity: 150 }]
  assert.ok(deliveryQuantityError(rows))
})
