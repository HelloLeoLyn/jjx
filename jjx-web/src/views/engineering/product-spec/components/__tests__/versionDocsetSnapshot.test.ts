import { test } from 'node:test'
import assert from 'node:assert/strict'
import { snapshotFileToDocFile, snapshotWarnings, buildVersionFiles } from '../versionDocset.ts'

test('快照文件映射：冻结元数据 + published 标记', () => {
  const structure = snapshotFileToDocFile({
    attachmentId: 40,
    section: 'atlas',
    category: '结构图',
    fileName: '工程图4.jpg',
    mime: 'image/jpeg',
    version: 'A',
    fileRole: 'PRINT',
  })
  assert.equal(structure.id, 40)
  assert.equal(structure.published, true)
  assert.equal(structure.section, 'atlas')
  assert.equal(structure.kind, 'image')
  assert.equal(structure.productFile, true)
  assert.equal(structure.version, 'A')
  assert.equal(structure.fileRole, 'PRINT')

  const customer = snapshotFileToDocFile({
    attachmentId: 36,
    section: 'customer',
    category: '客供稿',
    fileName: '客供.jpg',
  })
  assert.equal(customer.productFile, false)
  const pdf = snapshotFileToDocFile({
    attachmentId: 70,
    section: 'print',
    category: '印刷指导图',
    fileName: 'a.pdf',
    mime: 'application/pdf',
  })
  assert.equal(pdf.kind, 'pdf')
})

test('完整性：缺正文告警、计数不一致告警；空 BOM/路线不算缺项', () => {
  assert.deepEqual(snapshotWarnings({ schemaVersion: 1, spec: {}, counts: { files: 0 } }, 0), [])
  assert.match(
    snapshotWarnings({ schemaVersion: 1, counts: { files: 2 } }, 0).join('；'),
    /未固化规范正文/
  )
  assert.match(
    snapshotWarnings({ schemaVersion: 1, spec: {}, counts: { files: 3 } }, 0).join('；'),
    /声明 3 个文件.*实际固化 0/
  )
})

test('旧版引用路径不受影响（回归：DRAWING 全部入集、published 标记）', () => {
  const items = [
    { resourceType: 'SPEC_JSON', snapshotJson: '{"issueUnit":"工程部"}' },
    {
      resourceType: 'DRAWING',
      operationRef: '结构图',
      snapshotJson: '{"attachmentId":40,"fileName":"a.jpg","version":"A","fileRole":"PRINT"}',
    },
    {
      resourceType: 'DRAWING',
      operationRef: '面板图',
      snapshotJson: '{"attachmentId":37,"fileName":"b.jpg","version":"A","fileRole":"PRINT"}',
    },
  ]
  const legacy = buildVersionFiles(items)
  assert.deepEqual(
    legacy.files.map((f) => f.id),
    [40, 37]
  )
  assert.ok(legacy.files.every((f) => f.published === true))
  assert.deepEqual(
    legacy.files.map((f) => f.section),
    ['atlas', 'atlas']
  )
})

test('完整快照优先固化section，未知/停用类型不重算且保留打印选择', () => {
  for (const documentType of ['WORK_INSTRUCTION', 'FUTURE_ENGINEERING_TYPE']) {
    const file = snapshotFileToDocFile({
      attachmentId: 80,
      fileName: 'work.pdf',
      section: 'atlas',
      documentType,
      printSelected: false,
    })
    assert.equal(file.section, 'atlas')
    assert.equal(file.printSelected, false)
    assert.equal(file.documentType, documentType)
  }
})
