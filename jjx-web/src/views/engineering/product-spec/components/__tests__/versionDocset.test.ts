import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildVersionFiles, parseSnapshotStrict, sectionForCategory } from '../versionDocset.ts'

/** 真实 V1.0 的发布条目形态（product_work_spec_item，YL1025PWOO / productId=3）。 */
const v1Items = [
  { resourceType: 'SPEC_JSON', snapshotJson: '{"issueUnit":"工程部","structureFileId":40}' },
  { resourceType: 'BOM', snapshotJson: '{"bomId":1,"bomVersion":"V1.0"}' },
  { resourceType: 'ROUTING', snapshotJson: '{"routingId":1,"routingVersion":"V1.0"}' },
  { resourceType: 'DRAWING', operationRef: '结构图', snapshotJson: '{"attachmentId":40,"fileName":"工程图4.jpg","category":"结构图","version":"A","fileRole":"PRINT","sha256":"1b49"}' },
  { resourceType: 'DRAWING', operationRef: '面板图', snapshotJson: '{"attachmentId":37,"fileName":"工程图.jpg","category":"面板图","version":"A","fileRole":"PRINT","sha256":"7781"}' },
  { resourceType: 'DRAWING', operationRef: '线路图', snapshotJson: '{"attachmentId":38,"fileName":"工程图2.jpg","category":"线路图","version":"A","fileRole":"PRINT","sha256":"fd91"}' },
  { resourceType: 'DRAWING', operationRef: '刀模参数图', snapshotJson: '{"attachmentId":39,"fileName":"工程图3.jpg","category":"刀模参数图","version":"A","fileRole":"PRINT","sha256":"c359"}' },
  { resourceType: 'DRAWING', operationRef: '结构图', snapshotJson: '{"attachmentId":41,"fileName":"工程图5 (1).jpg","category":"结构图","version":"A","fileRole":"PRINT","sha256":"398d"}' },
]

test('版本入口读取全部 DRAWING 条目：五图全部入集且按真实图种分类', () => {
  const { files, warnings } = buildVersionFiles(v1Items)
  assert.equal(files.length, 5)
  assert.deepEqual(files.map((file) => file.id), [40, 37, 38, 39, 41])
  assert.deepEqual(files.map((file) => file.category), ['结构图', '面板图', '线路图', '刀模参数图', '结构图'])
  assert.ok(files.every((file) => file.section === 'atlas'))
  assert.ok(files.every((file) => file.productFile === true && file.published === true))
  assert.equal(files[0].version, 'A')
  assert.equal(files[0].fileRole, 'PRINT')
  // 发布清单未记录现行/下发，不得伪造这两个当前态标记。
  assert.equal(files[0].isCurrent, undefined)
  assert.equal(files[0].released, undefined)
  assert.deepEqual(warnings, [])
})

test('解析失败 / 缺附件的引用显式告警，不静默冒充空白，其余照常入集', () => {
  const { files, warnings } = buildVersionFiles([
    { resourceType: 'DRAWING', operationRef: '结构图', snapshotJson: '{不是合法 JSON' },
    { resourceType: 'DRAWING', operationRef: '面板图', snapshotJson: '' },
    { resourceType: 'DRAWING', operationRef: '线路图', snapshotJson: '{"attachmentId":7,"fileName":"a.pdf"}' },
  ])
  assert.deepEqual(files.map((file) => file.id), [7])
  assert.equal(files[0].kind, 'pdf')
  assert.equal(warnings.length, 2)
  assert.match(warnings[0], /解析失败/)
  assert.match(warnings[1], /缺少附件标识/)
})

test('重复附件合并告警；空清单明确告警', () => {
  const { files, warnings } = buildVersionFiles([
    { resourceType: 'DRAWING', operationRef: '结构图', snapshotJson: '{"attachmentId":40,"fileName":"a.jpg"}' },
    { resourceType: 'DRAWING', operationRef: '面板图', snapshotJson: '{"attachmentId":40,"fileName":"a.jpg"}' },
  ])
  assert.equal(files.length, 1)
  assert.match(warnings.join('；'), /附件重复/)

  const empty = buildVersionFiles([{ resourceType: 'BOM', snapshotJson: '{}' }])
  assert.equal(empty.files.length, 0)
  assert.match(empty.warnings.join('；'), /未包含工程图引用/)
})

test('印刷指导图归 print、样品照片归 sample、客供归 customer，其余归 atlas', () => {
  assert.equal(sectionForCategory(false, '印刷指导图'), 'print')
  assert.equal(sectionForCategory(false, '样品照片'), 'sample')
  assert.equal(sectionForCategory(false, '分色检查表'), 'color')
  assert.equal(sectionForCategory(false, '结构图'), 'atlas')
  assert.equal(sectionForCategory(true, '结构图'), 'customer')
  assert.equal(sectionForCategory(false, '客供稿'), 'customer')
})

test('parseSnapshotStrict 区分空值与损坏 JSON', () => {
  assert.deepEqual(parseSnapshotStrict(null), { ok: true, value: {} })
  assert.deepEqual(parseSnapshotStrict(''), { ok: true, value: {} })
  assert.equal(parseSnapshotStrict('{"a":1}').value.a, 1)
  assert.equal(parseSnapshotStrict('{bad').ok, false)
  assert.equal(parseSnapshotStrict('[1]').ok, false)
})
