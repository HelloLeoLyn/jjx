import { test } from 'node:test'
import assert from 'node:assert/strict'
import { processRowsForGroup, workflowLabor, workflowNames, currentPlateForFrame, type SpecProcessRow } from '../workSpecProcessRows.ts'

test('JTT993MHML第6道面板+下线完整留在父级面板流程，不在下线流程重复', () => {
  const rows = [{ itemId: 58, processOrder: 6, workflowSeq: 1, majorCategory: 'ASSEMBLY', processCategory: 'PANEL',
    processName: '面板+下线', children: [
      { itemId: 59, parentId: 58, processName: '面板', processCategory: 'PANEL', icon: '面板', customLaborHours: 0.1 },
      { itemId: 60, parentId: 58, processName: '下线', processCategory: 'DOWN_LINE', icon: '下线', workInstruction: '11', customLaborHours: 0.2 },
    ] }]
  const before = JSON.stringify(rows)
  const panel = processRowsForGroup(rows, 'ASSEMBLY', 'PANEL')
  assert.equal(panel.length, 1)
  assert.deepEqual(panel[0].children?.map(row => row.itemId), [59, 60])
  assert.equal(workflowNames(panel[0]), '面板 ＋ 下线')
  assert.equal(workflowLabor(panel[0]), '0.3')
  assert.equal(panel[0].children?.[1].icon, '下线')
  assert.equal(panel[0].children?.[1].workInstruction, '11')
  assert.deepEqual(processRowsForGroup(rows, 'ASSEMBLY', 'DOWN_LINE'), [])
  assert.equal(JSON.stringify(rows), before)
})

test('各父级组合保留跨类子件，缺省类别和未分类组合仍遵循父级', () => {
  for (const parentCategory of ['PANEL', 'UP_LINE', 'DOWN_LINE', '']) {
    const rows = [{ itemId: 1, majorCategory: 'ASSEMBLY', processCategory: parentCategory, children: [
      { itemId: 11, processCategory: 'PANEL' },
      { itemId: 12, processCategory: 'UP_LINE' },
      { itemId: 13, processCategory: 'DOWN_LINE' },
      { itemId: 14, processCategory: '' },
      { itemId: 15 },
    ] }]
    assert.deepEqual(processRowsForGroup(rows, 'ASSEMBLY', parentCategory)[0].children?.map(row => row.itemId), [11,12,13,14,15])
    for (const other of ['PANEL', 'UP_LINE', 'DOWN_LINE', ''].filter(value => value !== parentCategory)) {
      assert.deepEqual(processRowsForGroup(rows, 'ASSEMBLY', other), [])
    }
  }
})

test('下线+下线隔片+垫片保持三子件顺序，独立面板工序照常归组', () => {
  const rows: SpecProcessRow[] = [{ itemId: 82, majorCategory: 'ASSEMBLY', processCategory: 'DOWN_LINE', children: [
    { itemId: 83, processName: '下线', processCategory: 'DOWN_LINE' },
    { itemId: 84, processName: '下线隔片', processCategory: 'DOWN_LINE' },
    { itemId: 85, processName: '垫片', processCategory: 'PANEL' },
  ] }, { itemId: 90, processName: '独立面板', processCategory: 'PANEL' }]
  assert.equal(workflowNames(processRowsForGroup(rows, 'ASSEMBLY', 'DOWN_LINE')[0]), '下线 ＋ 下线隔片 ＋ 垫片')
  assert.deepEqual(processRowsForGroup(rows, 'ASSEMBLY', 'PANEL').map(row => row.itemId), [90])
})

test('嵌套组合保留子件顺序，跨流程印刷按原分类独立展开且不污染输入', () => {
  const rows: SpecProcessRow[] = [{ itemId: 1, majorCategory: 'ASSEMBLY', processCategory: 'PANEL', children: [
    { itemId: 11, processCategory: 'DOWN_LINE', children: [
      { itemId: 12, processCategory: 'UP_LINE', workInstruction: '先贴合' },
      { itemId: 13, majorCategory: 'PRINT', processCategory: 'DOWN_LINE', processName: '印红' },
    ] },
    { itemId: 14, processCategory: 'DOWN_LINE' },
  ] }]
  const before = JSON.stringify(rows)
  const assembly = processRowsForGroup(rows, 'ASSEMBLY', 'PANEL')
  assert.deepEqual(assembly[0].children?.map(row => row.itemId), [11, 14])
  assert.deepEqual(assembly[0].children?.[0].children?.map(row => row.itemId), [12])
  assert.deepEqual(processRowsForGroup(rows, 'PRINT', 'PANEL'), [])
  assert.deepEqual(processRowsForGroup(rows, 'PRINT', 'DOWN_LINE').map(row => row.itemId), [13])
  assert.equal(JSON.stringify(rows), before)
})

test('仅有印刷子件的组合不产生空的冲型组装行', () => {
  const rows = [{ itemId: 1, majorCategory: 'ASSEMBLY', processCategory: 'PANEL', children: [
    { itemId: 11, majorCategory: 'PRINT', processCategory: 'DOWN_LINE' },
  ] }]
  assert.deepEqual(processRowsForGroup(rows, 'ASSEMBLY', 'PANEL'), [])
  assert.deepEqual(processRowsForGroup(rows, 'PRINT', 'DOWN_LINE').map(row => row.itemId), [11])
})

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
