import { test } from 'node:test'
import assert from 'node:assert/strict'
import { processRowsForGroup, workflowLabor, workflowNames, currentPlateForFrame } from '../workSpecProcessRows.ts'

test('冲型组装保留组合图标行；印刷分流且继承父行分组，三组不串行', () => {
  const rows = [
    { itemId: 1, majorCategory: 'ASSEMBLY', processCategory: 'PANEL', children: [
      { itemId: 11, processName: '裁切', icon: 'cut', customLaborHours: 0, standardLaborHours: 9 },
      { itemId: 12, processName: '贴合', icon: 'paste', standardLaborHours: 0.12567 },
      { itemId: 13, majorCategory: 'PRINT', processName: '印红', customProcessParams: '{"colorNo":"R"}' },
    ] },
    { itemId: 2, majorCategory: 'PRINT', processCategory: 'UP_LINE', processName: '印黑' },
    { itemId: 3, processCategory: 'DOWN_LINE', processName: '组装' },
  ]
  const before = JSON.stringify(rows)
  const assembly = processRowsForGroup(rows, 'ASSEMBLY', 'PANEL')
  assert.equal(assembly.length, 1)
  assert.deepEqual(assembly[0].children?.map(row => row.itemId), [11, 12])
  assert.equal(workflowNames(assembly[0]), '裁切 ＋ 贴合')
  assert.equal(workflowLabor(assembly[0]), '0.1257')
  assert.deepEqual(processRowsForGroup(rows, 'PRINT', 'PANEL').map(row => row.itemId), [13])
  assert.deepEqual(processRowsForGroup(rows, 'PRINT', 'UP_LINE').map(row => row.itemId), [2])
  assert.deepEqual(processRowsForGroup(rows, 'ASSEMBLY', 'DOWN_LINE').map(row => row.itemId), [3])
  assert.equal(JSON.stringify(rows), before)
})

test('人工耗时空值、零值及标准工时回退符合纸张口径', () => {
  assert.equal(workflowLabor({ customLaborHours: 0, standardLaborHours: 10 }), '0')
  assert.equal(workflowLabor({ standardLaborHours: 0.2 }), '0.2')
  assert.equal(workflowLabor({}), '-')
  assert.equal(workflowLabor({ children: [{}, { customLaborHours: 0.1 }, { standardLaborHours: 0.2 }] }), '0.3')
})

test('网框模糊查询翻页后精确匹配，网框号和网板号不能混用', async () => {
  const calls: number[] = []
  assert.equal(await currentPlateForFrame('F1', async pageNum => {
    calls.push(pageNum)
    return pageNum === 1
      ? { records: [{ frame_no: 'F10', plate_no: '错误网板' }], total: 2 }
      : { records: [{ frame_no: 'F1', plate_no: 'P9' }], total: 2 }
  }), 'P9')
  assert.deepEqual(calls, [1, 2])
  assert.equal(await currentPlateForFrame('F1', async () => ({ records: [{ frame_no: 'F1', plate_no: null }], total: 1 })), '')
  assert.equal(await currentPlateForFrame('F1', async () => ({ records: [], total: 0 })), '')
  await assert.rejects(currentPlateForFrame('F1', async () => { throw new Error('读取失败') }), /读取失败/)
})
