/**
 * 待绑定（未保存单据）附件点击预览的接线回归（dev-20261117-013）。
 * 运行：node --test src/components/attachment/__tests__/AttachmentPendingPreview.test.cjs
 * 做法：源码级静态守卫——两个行表都把「待绑定行文件名」渲染成可点击预览，
 * 且待绑定预览按 attachmentId 取文件（此时还没有 refId），不得退回纯文字或引用链。
 */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { test } = require('node:test')

const src = path.resolve(__dirname, '../../..')
const read = (relative) => fs.readFileSync(path.resolve(src, relative), 'utf8')

test('AttachmentRefList：待绑定文件名可点击，按 attachmentId 交给 previewPending', () => {
  const source = read('components/attachment/AttachmentRefList.vue')
  assert.doesNotMatch(source, /<span v-if="row\.kind === 'pending'">/, '待绑定的文件名不得退回纯文字')
  assert.match(source, /@click="previewRow\(row\)"/, '文件名必须绑定预览动作')
  assert.match(source, /emit\('previewPending'/, '待绑定行必须抛 previewPending')
})

test('AttachmentCenter：接收 previewPending 并按附件ID预览', () => {
  const source = read('components/attachment/AttachmentCenter.vue')
  assert.match(source, /@preview-pending="previewPending"/, '必须接住 AttachmentRefList 的 previewPending')
  assert.match(source, /attachmentApi\.downloadUrl\(r\.id\)/, '待绑定行按 attachmentId 取文件')
})

test('AttachmentManager：待绑定行参与自己那一组的预览', () => {
  const source = read('components/attachment/AttachmentManager.vue')
  assert.doesNotMatch(source, /<span v-if="row\.kind === 'pending'">/, '待绑定的文件名不得退回纯文字')
  assert.doesNotMatch(source, /if \(row\.kind === 'pending'\) return\s*\n\s*\/\/ 预览工具/, '待绑定行不得被预览入口直接拦掉')
  assert.match(source, /row\.kind === 'pending' \? item\.kind === 'pending' : item\.kind !== 'pending'/)
})
