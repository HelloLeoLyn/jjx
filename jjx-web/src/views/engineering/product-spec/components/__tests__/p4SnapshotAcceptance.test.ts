import { test } from 'node:test'
import assert from 'node:assert/strict'
import { snapshotFileToDocFile, buildVersionFiles } from '../versionDocset.ts'

// P4（dev-20261013-012）验收：发布图纸不得被伪造「现行/下发/待归集」状态。
test('验收：快照文件带 published 标记，且不携带可被误判的现行/下发/图号', () => {
  const file = snapshotFileToDocFile({
    attachmentId: 40, section: 'atlas', category: '结构图', fileName: '工程图4.jpg',
    fileRole: 'PRINT', version: 'A',
  })
  assert.equal(file.published, true)
  assert.equal(file.isCurrent, undefined)
  assert.equal(file.released, undefined)
  // 快照未记录图号：documentFileCaption 依据 published 跳过「待归集/非现行/未下发」标签。
  assert.equal(file.drawingNo, undefined)
})

// P4 验收：旧版引用路径（V1/V2 这类无完整快照的版本）五图全部入集。
test('验收：旧版引用读取五图全部入集并按图种分类', () => {
  const refs = ['结构图', '面板图', '线路图', '刀模参数图', '结构图']
  const items = refs.map((ref, i) => ({
    resourceType: 'DRAWING',
    operationRef: ref,
    snapshotJson: JSON.stringify({ attachmentId: 40 + i, fileName: `工程图${i}.jpg`, version: 'A', fileRole: 'PRINT' }),
  }))
  const legacy = buildVersionFiles(items)
  assert.equal(legacy.files.length, 5)
  assert.ok(legacy.files.every((file) => file.published === true && file.section === 'atlas'))
  assert.ok(legacy.files.every((file) => file.isCurrent === undefined && file.released === undefined))
})
