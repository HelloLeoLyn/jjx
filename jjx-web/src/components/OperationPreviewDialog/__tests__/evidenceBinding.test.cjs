const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const dialog = fs.readFileSync(path.join(root, 'index.vue'), 'utf8')
const registry = fs.readFileSync(path.join(root, 'registry.ts'), 'utf8')
const center = fs.readFileSync(path.resolve(root, '../attachment/AttachmentCenter.vue'), 'utf8')

test('报价审核证据默认 SALES_QUOTATION_REVIEW_EVIDENCE，仍可编辑', () => {
  assert.equal((registry.match(/evidenceDefaultDocumentType: 'SALES_QUOTATION_REVIEW_EVIDENCE'/g) || []).length, 3)
  assert.match(dialog, /:default-document-type="operation\.evidenceDefaultDocumentType"/)
  assert.match(center, /uploadedEntry\.documentType = props\.defaultDocumentType/)
})

test('送样登记默认送样凭证；客户确认/退回仍为客户反馈凭证（dev-20261020-003）', () => {
  assert.match(
    registry,
    /key: 'sample\.sendSample',[\s\S]*?evidenceDefaultDocumentType: 'SAMPLE_SEND_EVIDENCE'/
  )
  assert.equal((registry.match(/evidenceDefaultDocumentType: 'SAMPLE_SEND_EVIDENCE'/g) || []).length, 1)
  assert.equal((registry.match(/evidenceDefaultDocumentType: 'SAMPLE_CUSTOMER_EVIDENCE'/g) || []).length, 2)
})

test('证据区可改资料类型，其余 AttachmentCenter 调用方默认仍为只读类型（dev-20261020-003）', () => {
  assert.match(dialog, /editable-document-type/)
  assert.match(center, /editableDocumentType\?: boolean/)
  assert.match(center, /:readonly-type="compactMode && !editableDocumentType"/)
  assert.match(center, /editableDocumentType: false/)
})

test('有待绑定证据时阻止确认；绑定成功后提交真实 refId；无证据仍走正常请求', () => {
  assert.match(dialog, /const pendingEvidence = evidenceCenter\.value\?\.getPendingEntries\?\.\(\) \|\| \[\]/)
  assert.match(dialog, /if \(pendingEvidence\.length\) \{[\s\S]*?return\n  \}/)
  assert.match(dialog, /attachmentRefIds: \[\.\.\.boundRefIds\]/)
  assert.match(registry, /attachmentRefIds\.length \? attachmentRefIds : undefined/)
  assert.match(center, /getPendingEntries: \(\) => pending\.value/)
})
