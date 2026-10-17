const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const dialog = fs.readFileSync(path.join(root, 'index.vue'), 'utf8')
const registry = fs.readFileSync(path.join(root, 'registry.ts'), 'utf8')
const center = fs.readFileSync(path.resolve(root, '../attachment/AttachmentCenter.vue'), 'utf8')

test('报价审核证据默认 SALES_QUOTATION_ATTACHMENT，仍可编辑', () => {
  assert.equal((registry.match(/evidenceDefaultDocumentType: 'SALES_QUOTATION_ATTACHMENT'/g) || []).length, 3)
  assert.match(dialog, /:default-document-type="operation\.evidenceDefaultDocumentType"/)
  assert.match(center, /uploadedEntry\.documentType = props\.defaultDocumentType/)
})

test('有待绑定证据时阻止确认；绑定成功后提交真实 refId；无证据仍走正常请求', () => {
  assert.match(dialog, /const pendingEvidence = evidenceCenter\.value\?\.getPendingEntries\?\.\(\) \|\| \[\]/)
  assert.match(dialog, /if \(pendingEvidence\.length\) \{[\s\S]*?return\n  \}/)
  assert.match(dialog, /attachmentRefIds: \[\.\.\.boundRefIds\]/)
  assert.match(registry, /attachmentRefIds\.length \? attachmentRefIds : undefined/)
  assert.match(center, /getPendingEntries: \(\) => pending\.value/)
})
