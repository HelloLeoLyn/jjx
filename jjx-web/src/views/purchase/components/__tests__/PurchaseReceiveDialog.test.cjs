const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { parse } = require('vue/compiler-sfc')
const tick = () => new Promise(resolve => setImmediate(resolve))

function harness({ fail = false, uploadPermission = true, disposalOnly = false } = {}) {
  const props = vue.reactive({ visible: false, orderId: 1, orderNo: 'PO1' })
  const calls = [], messages = [], events = []
  const api = {
    getReceiptsByOrder: async () => ({ data: [{ itemId: 10, quantity: 20, receivedQuantity: 0, ...(disposalOnly ? { processingPlan: '{"tree":{}}' } : {}) }] }),
    getReceiptDocDiskFiles: async () => ({ data: [{ fileName: 'old.png', fileUrl: '/old.png', fileSize: 1 }] }),
    getReceiptDocRefs: async () => ({ data: [{ refId: 2, attachmentId: 99, inboundNo: 'IN-old', fileName: 'old-ref.png' }] }),
    confirmBatchReceive: async (...args) => { calls.push(args); if (fail) throw Error('失败'); return { data: disposalOnly ? { disposalOnly: true, inboundNo: null } : { inboundNo: 'IN-new' } } },
  }
  const filename = path.resolve(__dirname, '../PurchaseReceiveDialog.vue')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'))
  const exports = {}
  let seq = 0
  const source = descriptor.scriptSetup.content + '\nexport { receiptAttachments, receiptDraftId, pendingFiles, form, submitting, busy, canSubmit, handleSubmit, handleClose, historyFiles, imageList, lastInboundNo, processingRefs }'
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const scope = vue.effectScope()
  scope.run(() => vm.runInNewContext(js, {
    exports, console: { error() {} }, crypto: { randomUUID: () => `receipt-draft-${++seq}` },
    defineProps: () => props, defineEmits: () => (...args) => events.push(args),
    require(name) {
      if (name === 'vue') return vue
      if (name === 'element-plus') return { ElMessage: { success: s => messages.push(s), error: s => messages.push(s), warning: s => messages.push(s) }, ElMessageBox: { confirm: async () => {} } }
      if (name === '@/store/modules/user') return { useUserStore: () => ({ hasPermission: p => p !== 'purchase:receipt:doc:add' || uploadPermission }) }
      if (name === '@/api/purchase/receipt') return api
      if (name === '@/api/system/attachment') return { attachmentApi: { downloadUrl: id => `/download/${id}` } }
      if (name.endsWith('AttachmentCenter.vue')) return {}
      throw Error(name)
    },
  }))
  exports.receiptAttachments.value = { pendingEntries: [], uploading: false, clearPending() { this.pendingEntries = [] } }
  return { props, state: exports, calls, messages, events, stop: () => scope.stop(), async open() { props.visible = true; await tick(); if(disposalOnly){exports.form.items[0].processingGroups=[{groupId:'root',outputs:[{outputId:'root-tail',quantity:1}]}];exports.processingRefs.set(10,{validate:async()=>({groups:exports.form.items[0].processingGroups,receivedQuantity:5,inboundItemCount:0})})} } }
}

test('仅提交本次草稿附件，成功换草稿且展示精确入库单号', async () => {
  const h=harness();await h.open();const first=h.state.receiptDraftId.value
  h.state.receiptAttachments.value.pendingEntries=[{ attachmentId:100,fileName:'new.png' }]
  await h.state.handleSubmit()
  assert.equal(h.calls.length,1)
  assert.deepEqual(JSON.parse(JSON.stringify(h.calls[0][2])),{ receiptRequestKey:first,attachmentIds:[100] })
  assert.notEqual(h.state.receiptDraftId.value,first)
  assert.equal(h.state.pendingFiles.value.length,0)
  assert.equal(h.state.lastInboundNo.value,'IN-new')
  assert.equal(h.state.historyFiles.value.length,1)
  assert.equal(h.state.imageList.value.length,1)
  h.stop()
})
test('失败保留草稿及附件供重试，关闭后新草稿不串附件', async () => {
  const h=harness({fail:true});await h.open();const first=h.state.receiptDraftId.value
  h.state.receiptAttachments.value.pendingEntries=[{attachmentId:100,fileName:'new.png'}]
  await h.state.handleSubmit();await h.state.handleSubmit()
  assert.equal(h.calls.length,2);assert.equal(h.state.receiptDraftId.value,first)
  assert.equal(h.state.pendingFiles.value.length,1)
  h.state.handleClose();h.props.visible=false;await tick();await h.open()
  assert.notEqual(h.state.receiptDraftId.value,first);assert.equal(h.state.pendingFiles.value.length,0)
  h.stop()
})
test('仅报废可以单独确认，成功提示没有入库单并换草稿键',async()=>{
 const h=harness({disposalOnly:true});await h.open();const key=h.state.receiptDraftId.value;await h.state.handleSubmit();assert.equal(h.calls.length,1);assert.notEqual(h.state.receiptDraftId.value,key);assert.equal(h.state.lastInboundNo.value,'');assert(h.messages.some(m=>m.includes('报废确认成功，本次未生成入库单')));h.stop()
})
test('仅报废不接收入库票据，失败不提交且保留草稿',async()=>{
 const h=harness({disposalOnly:true});await h.open();const key=h.state.receiptDraftId.value;h.state.receiptAttachments.value.pendingEntries=[{attachmentId:100}];await h.state.handleSubmit();assert.equal(h.calls.length,0);assert.equal(h.state.receiptDraftId.value,key);assert(h.messages.some(m=>m.includes('请移除收货票据')));h.stop()
})
test('上传中禁止确认和关闭；无上传权限可以无票据收货', async () => {
  const h=harness();await h.open();h.state.receiptAttachments.value.uploading=true
  assert.equal(h.state.canSubmit.value,false);await h.state.handleSubmit();h.state.handleClose()
  assert.equal(h.calls.length,0);assert.notEqual(h.state.receiptDraftId.value,'');h.stop()
  const r=harness({uploadPermission:false});await r.open();await r.state.handleSubmit()
  assert.equal(r.calls.length,1);assert.equal(r.calls[0][2].attachmentIds.length,0);r.stop()
})
