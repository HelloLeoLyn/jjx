const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse } = require('vue/compiler-sfc')

function harness({ fail = false, historical = false } = {}) {
  const calls = [], messages = []
  const fixture = { documentId: 1, documentNo: 'INV202611170001', documentStatus: 0, orderId: 10,
    supplierName: '供应商', orderNo: 'PO-10', invoiceNo: historical ? null : 'REAL-1', documentDate: '2026-11-17', documentAmount: 0, currency: 'CNY', remark: '原登记备注' }
  const api = {
    listInvoice: async () => ({ data: { records: [], total: 0 } }),
    getInvoice: async () => ({ data: { ...fixture } }),
    getPendingInvoiceOrders: async () => ({ data: [] }),
    registerInvoice: async (...args) => { calls.push({ method: 'register', args }); if (fail) throw Error('保存失败') },
    verifyInvoice: async (...args) => calls.push({ method: 'verify', args }),
  }
  const { descriptor } = parse(fs.readFileSync(path.resolve(__dirname, '../index.vue'), 'utf8'))
  const source = descriptor.scriptSetup.content + '\nexport { createForm, createAttachments, registrationForm, editingId, createVisible, current, detailVisible, submitCreate, submitVerify, openEdit, detail, invoiceActions }'
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const exports = {}
  vm.runInNewContext(output, { exports, defineOptions() {}, crypto: { randomUUID: () => 'draft-1' }, console,
    require(name) {
      if (name === 'vue') return { ...vue, onMounted() {} }
      if (name === 'element-plus') return { ElMessage: { success: s => messages.push(s), warning: s => messages.push(s), error: s => messages.push(s) }, ElMessageBox: {} }
      if (name === '@/store/modules/user') return { useUserStore: () => ({ userInfo: { realName: '实际姓名' }, hasPermission: () => true }) }
      if (name === '@/api/purchase/invoice') return api
      if (name === '@/enums/purchase/invoice') return { InvoiceStatusEnum: { PENDING: { value: 0 }, VERIFIED: { value: 1 }, ARCHIVED: { value: 2 } } }
      if (name === '@/api/purchase/supplier' || name === '@/utils/format' || name.endsWith('.vue')) return {}
      throw Error(name)
    },
  })
  exports.registrationForm.value = { validate: async () => true }
  exports.createAttachments.value = { uploading: false, pendingEntries: [
    { attachmentId: 100, documentType: 'VAT_SPECIAL_INVOICE' }, { attachmentId: 101, documentType: 'VAT_SPECIAL_INVOICE' },
  ], getMissingTypeCount: () => 0, clearPending() { this.pendingEntries = [] } }
  Object.assign(exports.createForm, { orderId: 10, invoiceNo: 'REAL-NEW', documentDate: '2026-11-01', sellerName: '销售方', sellerTaxNo: 'SELLER', buyerName: '购买方', buyerTaxNo: 'BUYER', untaxedAmount: 100, taxAmount: 13, documentAmount: 113, currency: 'CNY' })
  exports.createVisible.value = true
  return { state: exports, calls, messages }
}

test('一张真实发票和两份文件只提交一次登记，保留资料类型及票面日期', async () => {
  const h = harness(); await h.state.submitCreate()
  assert.equal(h.calls.length, 1)
  const body = h.calls[0].args[0]
  assert.equal(body.invoiceNo, 'REAL-NEW'); assert.equal(body.documentDate, '2026-11-01')
  assert.equal(body.documentAmount, 113); assert.equal(body.attachments.length, 2)
  assert.equal(body.attachments[1].documentType, 'VAT_SPECIAL_INVOICE')
  assert.equal(h.state.createVisible.value, false)
})
test('保存失败保留表单、文件队列和编辑窗口供重试', async () => {
  const h = harness({ fail: true }); await h.state.submitCreate()
  assert.equal(h.state.createVisible.value, true)
  assert.equal(h.state.createAttachments.value.pendingEntries.length, 2)
  assert.equal(h.state.createForm.invoiceNo, 'REAL-NEW')
})
test('历史记录补录时不把当天日期、零金额和默认币种充当真实票面值', async () => {
  const h = harness({ historical: true }); await h.state.openEdit({ documentId: 1 })
  assert.equal(h.state.createForm.documentDate, '')
  assert.equal(h.state.createForm.documentAmount, null)
  assert.equal(h.state.createForm.currency, '')
  assert.equal(h.state.createForm.remark, '原登记备注')
  assert.equal(h.state.editingId.value, 1)
})
test('上传尚未完成时不保存登记', async () => {
  const h = harness(); h.state.createAttachments.value.uploading = true
  await h.state.submitCreate(); assert.equal(h.calls.length, 0)
})
test('仅待核验记录提供核验动作，已核验和已归档都隐藏', () => {
  const h = harness(); const action = h.state.invoiceActions.find(action => action.key === 'verify')
  assert.equal(action.visible({ row: { documentStatus: 0 } }), true)
  assert.equal(action.visible({ row: { documentStatus: 1 } }), false)
  assert.equal(action.visible({ row: { documentStatus: 2 } }), false)
})
test('核验提交不接受手填人员姓名，登记备注保持原值', async () => {
  const h = harness(); await h.state.detail({ documentId: 1 }, true); await h.state.submitVerify()
  assert.equal(h.calls[0].method, 'verify')
  assert.equal(h.calls[0].args[2], '')
  assert.equal(h.state.current.value.remark, '原登记备注')
})
