/* A local-only interaction prototype. No requests, production APIs, or real records. */
;(function () {
  'use strict'
  const M = window.BomDemoModel
  const STORAGE = 'bom-studio-demo-v1'
  const $ = selector => document.querySelector(selector)
  const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
  const num = (value, digits = 4) => Number(value).toLocaleString('zh-CN', { maximumFractionDigits: digits })
  let workspace = M.seed(), published = M.copy(workspace), saved = M.copy(workspace), savedAt = '', saveAvailable = true
  try {
    const loaded = JSON.parse(localStorage.getItem(STORAGE) || 'null')
    if (loaded?.workspace?.schema === 1 && loaded.workspace.boms?.[loaded.workspace.root]
      && Object.values(loaded.workspace.boms).every(bom => Array.isArray(bom.items) && M.material(bom.materialId))) {
      workspace = loaded.workspace
      saved = M.copy(workspace)
      published = loaded.published?.boms ? loaded.published : M.seed()
      savedAt = loaded.savedAt || ''
    }
  } catch (error) { console.warn('本地样机数据不可读取，已使用初始示例。', error); saveAvailable = false }
  let selection = { key: workspace.root, bomId: workspace.root, depth: 0, parent: null, line: null, materialId: 'panel' }
  let view = 'structure', readingPublished = false, query = '', collapsed = new Set(), history = [], demand = 300, useStock = false, toastTimer, previousFocus
  const state = () => readingPublished ? published : workspace
  const readOnly = () => readingPublished || workspace.reviewRequested
  const current = () => state().boms[selection.bomId]
  const pendingChanges = () => M.changes(published, workspace)
  const isDirty = () => JSON.stringify(workspace) !== JSON.stringify(saved)
  function toast(message, error = false) {
    const el = $('#toast')
    el.textContent = message
    el.className = `toast visible${error ? ' error' : ''}`
    clearTimeout(toastTimer)
    toastTimer = setTimeout(() => { el.className = 'toast' }, 3700)
  }
  function mutate(action, message) {
    if (readOnly()) { toast('当前为只读预览，请返回编辑草稿后操作', true); return false }
    const before = M.copy(workspace)
    try {
      action()
      history.push(before)
      if (history.length > 30) history.shift()
      render()
      if (message) toast(message)
      return true
    } catch (error) { workspace = before; toast(error.message, true); return false }
  }
  function setView(next) { view = next; render() }
  function selectRow(row) { selection = row; view = 'structure'; render() }
  function materialIcon(material) { return material.kind === 'roll' ? '◉' : material.kind === 'raw' ? '◇' : '▧' }
  function icon(material) { return `<span class="node-icon ${material.kind === 'roll' ? 'roll' : material.kind === 'raw' ? 'raw' : ''}" aria-hidden="true">${materialIcon(material)}</span>` }
  function isBomDirty(id) { return JSON.stringify(workspace.boms[id]) !== JSON.stringify(saved.boms[id]) }
  function renderTree() {
    const s = state(), rows = M.tree(s), hidden = []
    const html = rows.filter(row => {
      if (query) return `${M.material(row.materialId).name} ${M.material(row.materialId).code} ${s.boms[row.bomId]?.name || ''}`.toLowerCase().includes(query.toLowerCase())
      if (hidden.some(key => row.key.startsWith(key + '/'))) return false
      if (collapsed.has(row.key)) hidden.push(row.key)
      return true
    }).map(row => {
      const m = M.material(row.materialId), bom = s.boms[row.bomId]
      const toggle = bom?.items.length ? (collapsed.has(row.key) ? '▸' : '▾') : ''
      return `<button class="tree-row depth-${Math.min(row.depth, 3)} ${bom ? '' : 'raw'} ${row.key === selection.key ? 'selected' : ''}" role="treeitem" aria-level="${row.depth + 1}" ${bom ? `aria-expanded="${!collapsed.has(row.key)}"` : ''} aria-selected="${row.key === selection.key}" data-tree-key="${esc(row.key)}" title="${esc(m.name)}${bom ? ' · ' + esc(bom.name) : ''}">${row.depth ? '<i class="branch"></i>' : ''}<span class="tree-toggle" data-toggle="${esc(row.key)}">${toggle}</span>${icon(m)}<span class="tree-text"><strong>${esc(m.name)}</strong><small>${bom ? `${esc(bom.code)} · ${esc(bom.version)}` : `${num(row.quantity)} ${m.unit} / 成品`}</small></span>${bom && !readingPublished && isBomDirty(row.bomId) ? '<span class="node-dirty" title="未保存修改"></span>' : ''}</button>`
    }).join('')
    $('#tree').innerHTML = html || '<p class="editor-description" style="padding:20px">未找到匹配的物料或方案</p>'
    $('#structure-count').textContent = [...new Set(rows.filter(row => row.bomId).map(row => row.bomId))].length
  }
  function breadcrumbs() {
    const rows = M.tree(state()), ancestors = rows.filter(row => row.bomId && (selection.key === row.key || selection.key.startsWith(row.key + '/')))
    if (!ancestors.length) return '<span>方案库</span><span>›</span><span class="current">独立方案维护</span>'
    return ancestors.map((row, index) => `<button data-action="breadcrumb" data-key="${esc(row.key)}" class="${index === ancestors.length - 1 ? 'current' : ''}">${esc(M.material(row.materialId).name)}</button>`).join('<span>›</span>')
  }
  function renderEditor() {
    const s = state(), bom = current(), m = M.material(selection.materialId), disabled = readOnly() ? 'disabled' : ''
    const reading = readingPublished ? '<div class="reading-banner">正在查看本机发布基线，内容只读。<button class="text-button" data-action="back-edit">返回工作草稿 →</button></div>' : workspace.reviewRequested ? '<div class="reading-banner">已模拟提交审核，工作草稿暂时只读。<button class="text-button" data-action="review">查看模拟审核 →</button></div>' : ''
    if (!bom) {
      $('#editor').innerHTML = `${reading}<div class="breadcrumb">${breadcrumbs()}<span>›</span><span class="current">${esc(m.name)}</span></div><div class="editor-heading"><div><h2>${esc(m.name)}<span class="pill neutral">直接用料</span></h2><div class="heading-code">${esc(m.code)}</div></div></div><div class="raw-info"><div><span>档案规格</span><strong>${esc(m.spec)}</strong></div><div><span>库存计量单位</span><strong>${esc(m.unit)}</strong></div></div><div class="callout">这是上层直接投入的物料。结构中的分组与显示缩进，不会自动把它变成另一层制造。</div><div class="definition"><span>每个上层产出投入</span><input aria-label="直接用料投入数量" class="field-input" data-field="raw-quantity" type="number" min="0.0001" step="0.0001" value="${selection.line?.quantity || 1}" ${disabled || (selection.line?.method === 'pitch' ? 'disabled' : '')}><span class="unit-label">${m.unit}</span></div><div class="editor-bottom"><span>物料档案只读，数量保存在所属上层方案中。</span><button class="button preview-button" data-action="parent">返回所属制造层 ↗</button></div>`
      return
    }
    const references = M.refs(s, bom.id), occurrence = M.tree(s).filter(row => row.bomId === bom.id).length
    const scope = references.length > 1 ? `<div class="scope-strip warning"><span>↗</span><strong>共用方案 · 被 ${references.length} 个上层引用</strong><span>修改会影响这些引用。</span><button class="text-button" data-action="references">查看引用</button></div>` : `<div class="scope-strip"><span>◈</span><strong>${selection.parent ? '当前层独立维护' : bom.id === s.root ? '顶层产品 BOM' : '方案库独立维护'}</strong><span>${selection.parent ? '修改投入清单，关联已保留在上层草稿。' : '下层使用已有制造方案；直接用料无需建立下层。'}</span></div>`
    const rows = bom.items.map((line, index) => {
      const mat = M.material(line.materialId), child = s.boms[line.bomId]
      return `<tr><td>${String(index + 1).padStart(2, '0')}</td><td><div class="material-cell">${icon(mat)}<div><strong>${esc(mat.name)}</strong><small>${esc(mat.code)}</small>${line.method === 'pitch' ? `<span class="pitch-chip">↦ 跳距 ${num(line.pitch.step)} mm</span>` : ''}</div></div></td><td><div class="quantity-cell">${line.method === 'pitch' ? `<button class="text-button" data-action="pitch" data-line="${line.id}" ${readingPublished ? '' : ''}>${num(M.linePerOutput(bom, { ...line, loss: 0 }) * Number(bom.outputQty), 6)} <small>↗</small></button>` : `<input class="field-input small" aria-label="${esc(mat.name)}投入数量" data-field="quantity" data-line="${line.id}" type="number" step="0.0001" min="0.0001" value="${line.quantity}" ${disabled}>`}<span>${mat.unit}</span></div></td><td>${child ? `<button class="scheme-link" data-action="open-line" data-line="${line.id}"><strong>${esc(child.name)} ↗</strong><small>${esc(child.code)} · ${esc(child.version)}</small></button>` : `<span class="direct-label">直接用料</span>`}</td><td><div class="quantity-cell"><input class="field-input small" aria-label="${esc(mat.name)}损耗率" data-field="loss" data-line="${line.id}" type="number" step="0.1" min="0" max="100" value="${line.loss || 0}" ${disabled}><span>%</span></div></td><td><div class="row-actions">${mat.kind === 'roll' ? `<button class="icon-button" title="卷材送料参数" aria-label="${esc(mat.name)}卷材送料参数" data-action="pitch" data-line="${line.id}">↦</button>` : ''}<button class="icon-button" title="选择或建立制造方案" aria-label="${esc(mat.name)}选择制造方案" data-action="scheme" data-line="${line.id}" ${disabled}>⋯</button><button class="icon-button" title="移除此投入行" aria-label="移除${esc(mat.name)}" data-action="remove" data-line="${line.id}" ${disabled}>×</button></div></td></tr>`
    }).join('')
    const rootWelcome = bom.id === s.root && !readingPublished ? '<div class="welcome-ribbon"><span>从左侧展开结构，点击“胶膜裁切片”体验卷材送料；点击方案名称进入下层。</span><button class="text-button" data-action="tour">体验指引 ↗</button></div>' : ''
    $('#editor').innerHTML = `${reading}<div class="breadcrumb">${breadcrumbs()}</div>${rootWelcome}<div class="editor-heading"><div><h2>${esc(m.name)}<span class="pill ${readOnly() ? 'blue' : 'teal'}">${readingPublished ? '发布基线' : workspace.reviewRequested ? '模拟审核中' : '工作草稿'}</span></h2><div class="heading-code">${esc(m.code)} <span class="dot-separator">/</span> ${esc(bom.code)} · ${esc(bom.version)} ${occurrence > 1 ? `<span class="reference-tag">结构中复用 ${occurrence} 次</span>` : ''}</div></div><div class="editor-heading-actions"><button class="button small" data-action="undo" ${!history.length || readOnly() ? 'disabled' : ''}>↶ 撤销</button><button class="button small" data-action="clone" ${disabled}>另存为方案</button></div></div>${scope}<div class="definition"><strong>每生产</strong><input class="field-input" aria-label="本层产出数量" data-field="outputQty" type="number" min="0.0001" step="0.0001" value="${bom.outputQty}" ${disabled}><span class="unit-label">${esc(m.unit)}</span><span>的投入清单</span><span class="annotation">数量均以本层产出为基准</span></div><div class="section-toolbar"><h3>投入材料 <small>${bom.items.length} 项</small></h3><div><button class="button soft small" data-action="add" ${disabled}>＋ 添加材料</button></div></div>${rows ? `<div class="table-scroll"><table class="materials-table"><thead><tr><th>#</th><th>物料 / 半成品</th><th>投入数量</th><th>制造方案</th><th>损耗率</th><th></th></tr></thead><tbody>${rows}</tbody></table></div><p class="editor-description">半成品点击方案名称进入下层；“⋯”选择其他方案或建立下层。已有引用无需逐层重复采用。</p>` : '<div class="empty-state"><div class="empty-symbol">⊞</div><h3>从第一项投入开始</h3><p>选原料直接使用，选半成品可复用或建立它的制造方案。</p><button class="button soft" data-action="add">＋ 添加投入材料</button></div>'}<div class="note-area"><label for="bom-note">本层工艺说明 / 备注</label><textarea id="bom-note" data-field="note" placeholder="可选，例如排版依据或特殊要求" ${disabled}>${esc(bom.note)}</textarea></div><div class="editor-bottom"><span>草稿跨层保留 · ${references.length} 处上层引用 · 所有数据均为演示</span><button class="button preview-button" data-action="preview"><span>⌁</span> 用量试算 / 查看展开</button></div>`
  }
  function renderLibrary() {
    const s = state()
    $('#library-view').innerHTML = `<div class="library-heading"><div><h2>制造方案库</h2><p>一个产出物料可以有多套制造方式；上层引用明确的方案，不复制下层材料。</p></div><button class="button soft" data-action="new-library" ${readOnly() ? 'disabled' : ''}>＋ 新建制造方案</button></div><div class="library-grid">${Object.values(s.boms).filter(bom => bom.id !== s.root).map(bom => {
      const m = M.material(bom.materialId), references = M.refs(s, bom.id).length
      return `<article class="scheme-card"><div class="card-head">${icon(m)}<span class="pill ${references ? 'teal' : 'neutral'}">${references ? `${references} 处引用` : '可选方案'}</span></div><h3>${esc(bom.name)}</h3><p>${esc(m.name)} · ${esc(m.code)}</p><div class="card-footer"><span>${esc(bom.version)} <span class="dot-separator">·</span> ${bom.items.length} 项投入</span><button class="text-button" data-action="library-open" data-bom="${bom.id}">查看 / 编辑 ↗</button></div></article>`
    }).join('')}</div>`
  }
  function render() {
    const s = state(), found = M.tree(s).find(row => row.key === selection.key)
    if (found) selection = found
    else if (selection.bomId && s.boms[selection.bomId]) selection.materialId = s.boms[selection.bomId].materialId
    else selection = M.tree(s)[0]
    document.body.classList.toggle('read-only', readOnly())
    $('#structure-view').hidden = view !== 'structure'
    $('#library-view').hidden = view !== 'library'
    document.querySelectorAll('[data-view]').forEach(button => button.classList.toggle('active', button.dataset.view === view))
    $('#version-label').textContent = `${s.boms[s.root].version} · ${readingPublished ? '发布基线' : workspace.reviewRequested ? '模拟审核中' : '工作草稿'}`
    $('#product-title').textContent = s.boms[s.root].name
    $('#dirty-label').textContent = isDirty() ? '● 有未保存修改' : '● 无未保存修改'
    $('#dirty-label').classList.toggle('dirty', isDirty())
    $('#change-count').textContent = pendingChanges().length
    $('#save-time').textContent = saveAvailable ? (savedAt ? `本机保存于 ${savedAt}` : '尚未保存到本机') : '浏览器存储不可用'
    $('#save-time').classList.toggle('storage-error', !saveAvailable)
    $('#save').disabled = readingPublished
    $('#view-published').textContent = readingPublished ? '返回工作草稿 →' : '查看发布基线 ↗'
    renderTree()
    renderEditor()
    if (view === 'library') renderLibrary()
    if (!$('#drawer').hidden) renderPreview()
  }
  function modal(title, kicker, body, actions = []) {
    previousFocus = document.activeElement
    $('#modal-title').textContent = title
    $('#modal-kicker').textContent = kicker
    $('#modal-body').innerHTML = body
    $('#modal-footer').innerHTML = ''
    for (const action of actions) {
      const button = document.createElement('button')
      button.className = `button ${action.primary ? 'primary' : action.danger ? 'danger' : ''}`
      button.textContent = action.label
      button.disabled = !!action.disabled
      button.addEventListener('click', action.click)
      $('#modal-footer').append(button)
    }
    if (!$('#modal').open) $('#modal').showModal()
  }
  function closeModal() { $('#modal').close(); previousFocus?.isConnected && previousFocus.focus() }
  function persist() {
    try {
      const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      localStorage.setItem(STORAGE, JSON.stringify({ workspace, published, savedAt: time }))
      saved = M.copy(workspace); savedAt = time; saveAvailable = true
      return true
    } catch (error) { saveAvailable = false; toast('浏览器不允许本地存储，草稿仍在本页；可先导出样机数据。', true); return false }
  }
  function save() {
    if (persist()) toast('所有层的草稿已保存到当前浏览器；没有修改真实 BOM')
    render()
  }
  function showErrors(errors) { modal('请先检查这些内容', 'WORKSPACE CHECK', `<ul class="validation-list">${errors.map(error => `<li>${esc(error)}</li>`).join('')}</ul>`, [{ label: '返回修改', primary: true, click: closeModal }]) }
  function confirmReset(blank = false) {
    modal(blank ? '从空白 BOM 开始？' : '重置为初始演示数据？', 'DEMO WORKSPACE', `<p class="dialog-intro">${blank ? '顶层投入清单将清空，示例方案库仍可复用。' : '恢复初始多层结构与卷材参数。'}当前浏览器里的样机草稿会被替换，真实系统数据不受影响。</p>`, [{ label: '保留当前草稿', click: () => { $('#scenario').value = 'multi'; closeModal() } }, { label: blank ? '新建空白 BOM' : '重置样机', primary: true, click: () => {
      workspace = M.seed(blank); published = M.copy(workspace); saved = M.copy(workspace); savedAt = ''; history = []; readingPublished = false; collapsed = new Set(); query = ''; $('#tree-search').value = ''; selection = M.tree(workspace)[0]; view = 'structure'
      persist(); closeModal(); render(); toast(blank ? '已创建空白工作草稿，可添加材料或复用方案' : '演示数据已重置')
    } }])
  }
  function materialPicker() {
    const bomId = current().id
    modal('添加投入材料', 'ADD COMPONENT', '<p class="dialog-intro">选择一个物料。半成品可直接复用已有制造方案，也可先加入后建立下层。</p><input class="dialog-search" id="material-search" placeholder="搜索物料名称或编码" aria-label="搜索可添加物料"><div id="material-results"></div>', [{ label: '完成', click: closeModal }])
    function list(filter = '') {
      $('#material-results').innerHTML = M.catalog.filter(m => m.id !== workspace.boms[bomId].materialId && `${m.name} ${m.code}`.toLowerCase().includes(filter.toLowerCase())).map(m => {
        const opts = M.options(workspace, m.id).filter(bom => !M.reaches(workspace, bom.id, bomId))
        return `<div class="material-choice">${icon(m)}<div class="choice-text"><strong>${esc(m.name)}</strong><small>${esc(m.code)} · ${esc(m.spec)} · ${esc(m.unit)}</small></div>${opts.length ? `<select aria-label="${esc(m.name)}采用方案" data-material-plan="${m.id}"><option value="">直接用料</option>${opts.map((bom, i) => `<option value="${bom.id}" ${i === 0 ? 'selected' : ''}>${esc(bom.name)}</option>`).join('')}</select>` : ''}<button class="button small" data-pick-material="${m.id}">添加</button></div>`
      }).join('') || '<p class="dialog-intro">没有匹配的模拟物料</p>'
      document.querySelectorAll('[data-pick-material]').forEach(button => button.addEventListener('click', () => {
        const id = button.dataset.pickMaterial, plan = document.querySelector(`[data-material-plan="${id}"]`)?.value || null
        if (mutate(() => M.addLine(workspace, bomId, id, 1, plan), `已添加 ${M.material(id).name}${plan ? '，已有制造方案一并关联' : ''}`)) { button.textContent = '已添加'; button.disabled = true }
      }))
    }
    list()
    $('#material-search').addEventListener('input', event => list(event.target.value))
  }
  function openChild(lineId) {
    const row = M.tree(state()).find(row => row.key === `${selection.key}/${lineId}`)
    if (row) selectRow(row)
  }
  function schemePicker(lineId) {
    const parentId = current().id, line = workspace.boms[parentId].items.find(item => item.id === lineId), mat = M.material(line.materialId)
    const opts = M.options(workspace, line.materialId).filter(bom => !M.reaches(workspace, bom.id, parentId))
    modal(`为“${mat.name}”选择方案`, 'REUSE OR CREATE', `<p class="dialog-intro">方案选择只改变当前投入行的引用，不会改其他产品的选择。修改被共用的方案时，可先另存为独立方案。</p>${opts.map(bom => `<button class="scheme-option ${line.bomId === bom.id ? 'selected' : ''}" data-use-bom="${bom.id}">${icon(mat)}<div><h3>${esc(bom.name)}</h3><p>${esc(bom.code)} · ${esc(bom.version)} · ${bom.items.length} 项投入</p></div><div class="option-end">${line.bomId === bom.id ? '当前采用' : '使用此方案 →'}</div></button>`).join('') || '<div class="callout">尚无可用的制造方案，可以从这里建立下层。</div>'}`, [{ label: '直接用料，不展开下层', click: () => { if (mutate(() => { line.bomId = null }, '当前行已改为直接用料')) closeModal() } }, { label: '新建方案并使用', primary: true, click: () => createChild(parentId, lineId) }])
    document.querySelectorAll('[data-use-bom]').forEach(button => button.addEventListener('click', () => {
      if (mutate(() => M.useBom(workspace, parentId, lineId, button.dataset.useBom), '当前行已采用该方案；其他引用不变')) closeModal()
    }))
  }
  function createChild(parentId, lineId) {
    const line = workspace.boms[parentId].items.find(item => item.id === lineId)
    const parentKey = selection.key
    const mat = M.material(line.materialId)
    modal('建立下层制造方案', 'CREATE ASSEMBLY', `<p class="dialog-intro">产出物料为 <strong>${esc(mat.name)} · ${esc(mat.unit)}</strong>。建立后会关联当前上层投入行，并直接进入下层填写材料；一起保存到本机工作区。</p><div class="form-field"><label for="new-scheme-name">方案名称</label><input id="new-scheme-name" class="field-input" value="${esc(mat.name)}制造方案" maxlength="60"></div>`, [{ label: '取消', click: closeModal }, { label: '建立并进入下层', primary: true, click: () => {
      const name = $('#new-scheme-name').value.trim()
      if (!name) return toast('请填写方案名称', true)
      let child
      if (mutate(() => { child = M.createBom(workspace, line.materialId, name); M.useBom(workspace, parentId, lineId, child.id) })) {
        selection = M.tree(workspace).find(row => row.key === `${parentKey}/${lineId}`) || { key: `library:${child.id}`, bomId: child.id, materialId: child.materialId }
        closeModal(); render(); toast('下层草稿已建立，上层关联已记录；继续添加投入材料')
      }
    } }])
  }
  function cloneCurrent() {
    const bom = current(), original = M.copy(selection)
    modal('另存为独立制造方案', 'CREATE ALTERNATIVE', `<p class="dialog-intro">新方案复制当前层的投入清单，下层仍引用原有方案。${original.parent ? '本次可只替换当前上层投入行，其他引用保持原方案。' : '新方案保存在方案库，原方案和引用不改变。'}</p><div class="form-field"><label for="clone-name">新方案名称</label><input id="clone-name" class="field-input" value="${esc(bom.name)} · 方案二" maxlength="60"></div>${original.parent ? '<label class="check-label"><input type="checkbox" id="clone-replace" checked>当前这一处引用改用新方案</label>' : ''}`, [{ label: '取消', click: closeModal }, { label: '建立独立方案', primary: true, click: () => {
      const name = $('#clone-name').value.trim(), replace = $('#clone-replace')?.checked
      if (!name) return toast('请填写方案名称', true)
      let next
      if (mutate(() => { next = M.cloneBom(workspace, bom.id, name); if (replace) M.useBom(workspace, original.parent, original.line.id, next.id) })) {
        selection = replace ? M.tree(workspace).find(row => row.key === original.key) : { key: `library:${next.id}`, bomId: next.id, materialId: next.materialId }
        closeModal(); render(); toast('独立方案已建立，原方案内容保留')
      }
    } }])
  }
  function pitchEditor(lineId) {
    const bomId = current().id, line = state().boms[bomId].items.find(item => item.id === lineId)
    const p = M.copy(line.pitch || { width: 195, step: 80, pieces: 1, rollLength: 50, fixedLength: 0 })
    const disabled = readOnly() ? 'disabled' : ''
    modal('卷材送料与耗用', 'ROLL MATERIAL · SIMULATION', `<p class="dialog-intro">以<strong>送料长度</strong>和每跳产出确认理论投入。跳距可含间隙，不等同于成品净长度；本例假设整宽连续送料，不自动排版。</p><div class="form-grid">${[['width', '卷宽', 'mm'], ['step', '送料跳距', 'mm'], ['pieces', '每跳产出', '片'], ['rollLength', '一卷原始长度', 'm'], ['fixedLength', '整批起尾料', 'm']].map(([key, label, unit]) => `<div class="form-field"><label for="pitch-${key}">${label}（${unit}）</label><input id="pitch-${key}" data-pitch="${key}" class="field-input" type="number" min="${key === 'fixedLength' ? 0 : 0.0001}" step="${key === 'pieces' ? 1 : 0.1}" value="${p[key]}" ${disabled}></div>`).join('')}</div><div id="pitch-result" class="calculation-card"></div><div class="callout warning">此处是用于评价交互的模拟计算，不代表已批准工艺。确认后更新本行定额；整卷实领和余料在“用量试算”中分别展示。起尾料每次整批展开只加一次。</div>`, [{ label: readOnly() ? '关闭' : '取消', click: closeModal }, ...(!readOnly() ? [{ label: '确认送料参数', primary: true, click: () => {
      const values = readValues()
      if (![values.width, values.step, values.pieces, values.rollLength].every(value => value > 0) || values.fixedLength < 0) return toast('送料参数须为正数，起尾料不能为负', true)
      if (mutate(() => { Object.assign(workspace.boms[bomId].items.find(item => item.id === lineId), { method: 'pitch', pitch: values }); }, '本行送料参数已更新，用量试算随草稿更新')) closeModal()
    } }] : [])])
    function readValues() { return Object.fromEntries(Object.keys(p).map(key => [key, Number($(`#pitch-${key}`).value)])) }
    function recalc() {
      const values = readValues(), amount = values.width / 1000 * values.step / 1000 / values.pieces
      $('#pitch-result').innerHTML = `<div class="overline">每片分摊的理论送料面积 · 不含损耗与整批起尾料</div><strong>${Number.isFinite(amount) && amount > 0 ? num(amount, 6) : '—'} <span style="font-size:13px;font-weight:400">㎡ / PCS</span></strong><p>${num(values.width)} ÷ 1000 × ${num(values.step)} ÷ 1000 ÷ ${num(values.pieces)}<br>每卷面积 ${num(values.width / 1000 * values.rollLength)} ㎡，损耗沿用本行设置。</p>`
    }
    document.querySelectorAll('[data-pitch]').forEach(input => input.addEventListener('input', recalc)); recalc()
  }
  function references() {
    const bom = current(), references = M.refs(state(), bom.id)
    modal('方案引用范围', 'WHERE USED', `<p class="dialog-intro">正在维护 <strong>${esc(bom.name)}</strong>。修改这个共用方案会影响以下引用；只想改当前这一处时，请另存为独立方案。</p>${references.map(ref => `<div class="change-item"><span class="pill teal">上层引用</span><div class="change-content"><strong>${esc(ref.bom.name)}</strong><p>${esc(ref.bom.code)} · 投入 ${num(ref.line.quantity)} ${M.material(bom.materialId).unit}</p></div></div>`).join('') || '<div class="callout">当前没有其他上层方案引用。</div>'}`, [{ label: '知道了', primary: true, click: closeModal }])
  }
  function changeReview() {
    const changes = pendingChanges(), errors = M.validate(workspace)
    modal('检查本次工作区变更', 'CHANGE REVIEW · DEMO', `<p class="dialog-intro">对比上一次本机发布基线。未完成的内容可以先保存草稿；提交审核前需补齐材料。提交审核和发布均为演示，真实系统不会发生任何变更。</p>${changes.length ? changes.map(change => `<div class="change-item"><span class="pill ${change.type === '移除' ? 'amber' : 'teal'}">${change.type}</span><div class="change-content"><strong>${esc(change.label)}</strong><p>${esc(change.detail)}</p></div></div>`).join('') : '<div class="empty-state"><div class="empty-symbol">✓</div><h3>暂时没有内容变更</h3><p>先调整数量、替换方案或添加材料，再查看变更。</p></div>'}${errors.length ? `<div class="callout error">${errors.map(esc).join('<br>')}</div>` : ''}`, [{ label: '继续编辑', click: closeModal }, { label: '保存草稿', click: () => { save(); closeModal() } }, { label: workspace.reviewRequested ? '查看模拟审核' : '模拟提交审核', primary: true, disabled: !changes.length || errors.length > 0, click: () => {
      if (!workspace.reviewRequested) { workspace.reviewRequested = true; persist(); render() }
      reviewDialog()
    } }])
  }
  function reviewDialog() {
    modal('模拟审核与发布', 'WORKFLOW PREVIEW', '<p class="dialog-intro">这里仅体验“编辑草稿 → 检查变更 → 审核 → 发布基线”的边界。真实权限、审核记录和生效控制需要正式系统实现，样机不会调用审批接口。</p><div class="callout">审核期间编辑只读。退回可继续修改；模拟发布后，将本机草稿复制为新的只读基线。</div>', [{ label: '退回继续修改', click: () => { workspace.reviewRequested = false; persist(); closeModal(); render(); toast('演示草稿已退回，可继续编辑') } }, { label: '模拟审核通过并发布', primary: true, click: () => {
      const errors = M.validate(workspace)
      if (errors.length) return showErrors(errors)
      const changedIds = Object.values(workspace.boms).filter(bom => JSON.stringify(bom) !== JSON.stringify(published.boms[bom.id])).map(bom => bom.id)
      for (const id of changedIds) { const bom = workspace.boms[id]; bom.version = `R${String((Number(bom.version.replace(/^R/, '')) || 0) + 1).padStart(2, '0')}` }
      workspace.reviewRequested = false; published = M.copy(workspace); persist(); history = []; closeModal(); render(); toast('已生成新的本机发布基线；没有发布到业务系统')
    } }])
  }
  function renderPreview() {
    let result, error
    try { result = M.calculate(state(), demand, useStock) } catch (err) { error = err.message }
    const originalFocused = document.activeElement?.id
    $('#drawer-content').innerHTML = `<p class="dialog-intro">从顶层展开当前草稿，不需要先保存。计算仅用于体验，不创建采购、生产或领料单。</p><div class="demand-controls"><strong>成品需求</strong><input id="preview-demand" class="field-input" type="number" min="1" step="1" value="${demand}" aria-label="试算成品需求"><span class="unit-label">PCS</span><button class="button soft small" id="recalculate">重新试算</button></div><label class="check-label"><input type="checkbox" id="use-stock" ${useStock ? 'checked' : ''}>抵扣演示半成品库存（组件20、基片40、胶膜片60 PCS）</label>${error ? `<div class="callout error">${esc(error)}</div>` : `<div class="summary-stats"><div><span>成品需求</span><strong>${num(demand)}</strong><small>PCS</small></div><div><span>原料种类</span><strong>${result.raws.length}</strong><small>按物料汇总</small></div><div><span>制造层级</span><strong>${Math.max(...M.tree(state()).map(row => row.depth))}</strong><small>含最底层原料</small></div></div><h3 class="section-label">原料理论耗用</h3><table class="result-table"><thead><tr><th>物料</th><th style="text-align:right">理论投入</th><th>库存单位</th></tr></thead><tbody>${result.raws.map(raw => `<tr><td><strong>${esc(raw.material.name)}</strong><small>${esc(raw.material.code)}</small></td><td class="numeric">${num(raw.quantity, 6)}</td><td>${raw.material.unit}</td></tr>`).join('')}</tbody></table>${result.raws.filter(raw => raw.roll).map(raw => `<div class="roll-card"><h3>◉ ${esc(raw.material.name)} · 包装与耗用分开</h3><div class="roll-card-grid"><div><span>理论耗用</span><strong>${num(raw.quantity, 6)} ㎡</strong></div><div><span>整卷实领示意</span><strong>${raw.rolls} 卷 / ${num(raw.issued)} ㎡</strong></div><div><span>理论余料</span><strong>${num(raw.issued - raw.quantity, 6)} ㎡</strong></div></div><p>包装假设：宽 ${num(raw.roll.width)} mm、长 ${num(raw.roll.length)} m，每卷 ${num(raw.roll.area)} ㎡。理论余料不是自动退料或实耗记录；样机不做库存过账。</p></div>`).join('')}<h3 class="section-label">逐层制造需求</h3><table class="result-table"><thead><tr><th>产出 / 所用方案</th><th>总需求</th><th>库存抵扣</th><th>需制造</th></tr></thead><tbody>${result.production.map(row => `<tr><td><strong>${esc(M.material(row.bom.materialId).name)}</strong><small>${esc(row.bom.name)}</small></td><td>${num(row.gross)}</td><td>${num(row.cover)}</td><td>${num(row.net)}</td></tr>`).join('')}</tbody></table><div class="callout">试算使用示例定额和显式制造引用，原料保留理论小数，不自动把理论值变成实领。未模拟采购在途、需求日期、并发占用和批次分配，不能作为生产计划结果。</div>`}`
    $('#recalculate').addEventListener('click', () => { demand = Number($('#preview-demand').value); renderPreview() })
    $('#preview-demand').addEventListener('change', event => { demand = Number(event.target.value); renderPreview() })
    $('#use-stock').addEventListener('change', event => { useStock = event.target.checked; renderPreview() })
    if (originalFocused === 'preview-demand') $('#preview-demand').focus()
  }
  function preview() { $('#drawer').hidden = false; $('#drawer-backdrop').hidden = false; renderPreview(); $('#drawer-close').focus() }
  function closeDrawer() { $('#drawer').hidden = true; $('#drawer-backdrop').hidden = true }
  function tour() {
    modal('先用这几个动作试一遍', 'A HANDS-ON WALKTHROUGH', '<p class="dialog-intro">这是一套可操作的流程候选，用来确认你希望怎样维护 BOM。全部物料、方案、库存均为虚构示例。</p><div class="tour-step"><span class="step-number">1</span><div><h3>连续维护三层结构</h3><p>左侧依次选择面板基片组件、裁切基片。修改投入数量后切回顶层，草稿仍保留；无需逐层关闭窗口。</p></div></div><div class="tour-step"><span class="step-number">2</span><div><h3>复用或更换制造方案</h3><p>在面板基片组件中，点裁切基片行的“⋯”，切换六片排版或四片排版。查看用量试算中的片材耗用变化。</p></div></div><div class="tour-step"><span class="step-number">3</span><div><h3>体验卷材跳距与实际领料的区别</h3><p>进入胶膜裁切片，点“↦”调整跳距和每跳产出。试算分别展示理论面积、整卷实领和理论余料。</p></div></div><div class="tour-step"><span class="step-number">4</span><div><h3>保存、新增和变更预览</h3><p>从空白场景新增投入并复用方案；或另存方案后只改当前引用。顶部保存整个本机工作区，变更列表可模拟审核发布。</p></div></div><div class="callout">这次先评价操作路径、信息和保存边界。送料计算只是显式标记的交互假设，正式算法与业务规则尚未实施。</div>', [{ label: '开始体验', primary: true, click: closeModal }])
  }
  document.addEventListener('click', event => {
    const target = event.target.closest('[data-action]')
    if (!target || target.disabled) return
    const action = target.dataset.action, lineId = target.dataset.line
    if (action === 'tour') tour()
    if (action === 'breadcrumb') { const row = M.tree(state()).find(row => row.key === target.dataset.key); if (row) selectRow(row) }
    if (action === 'add') materialPicker()
    if (action === 'scheme') schemePicker(lineId)
    if (action === 'open-line') openChild(lineId)
    if (action === 'pitch') pitchEditor(lineId)
    if (action === 'preview') preview()
    if (action === 'clone') cloneCurrent()
    if (action === 'references') references()
    if (action === 'undo' && history.length && !readOnly()) { workspace = history.pop(); render(); toast('已撤销上一步工作区修改') }
    if (action === 'remove') {
      const bom = current(), line = bom.items.find(item => item.id === lineId)
      modal('移除这项投入？', 'EDIT COMPONENT', `<p class="dialog-intro">从 <strong>${esc(bom.name)}</strong> 移除 ${esc(M.material(line.materialId).name)}。原物料档案和下层制造方案均保留。</p>`, [{ label: '保留', click: closeModal }, { label: '移除投入行', danger: true, click: () => { if (mutate(() => { bom.items = bom.items.filter(item => item.id !== lineId) }, '投入行已移除，下层方案仍在方案库')) closeModal() } }])
    }
    if (action === 'parent') { const row = M.tree(state()).find(row => row.key === selection.key.slice(0, selection.key.lastIndexOf('/'))); if (row) selectRow(row) }
    if (action === 'library-open') { const bom = state().boms[target.dataset.bom]; selectRow(M.tree(state()).find(row => row.bomId === bom.id) || { key: `library:${bom.id}`, bomId: bom.id, materialId: bom.materialId }) }
    if (action === 'new-library') {
      modal('新建独立制造方案', 'SCHEME LIBRARY', `<p class="dialog-intro">方案归属于产出物料，建立后可供上层引用。此操作不会自动改动产品结构。</p><div class="form-field"><label for="library-output">产出物料</label><select id="library-output">${M.catalog.filter(m => m.kind === 'assembly').map(m => `<option value="${m.id}">${esc(m.name)} · ${m.code}</option>`).join('')}</select></div>`, [{ label: '取消', click: closeModal }, { label: '建立并编辑', primary: true, click: () => { let bom; if (mutate(() => { bom = M.createBom(workspace, $('#library-output').value) })) { closeModal(); selectRow({ key: `library:${bom.id}`, bomId: bom.id, materialId: bom.materialId }) } } }])
    }
    if (action === 'back-edit') { readingPublished = false; render() }
    if (action === 'review') reviewDialog()
  })
  $('#editor').addEventListener('change', event => {
    const field = event.target.dataset.field
    if (!field) return
    const value = event.target.value, bom = current(), lineId = event.target.dataset.line
    if (field === 'note') mutate(() => { bom.note = value })
    else if (field === 'raw-quantity') {
      if (!(Number(value) > 0)) { toast('投入数量须大于0', true); render(); return }
      mutate(() => { workspace.boms[selection.parent].items.find(line => line.id === selection.line.id).quantity = Number(value) })
    } else {
      if (field === 'loss' ? !(Number(value) >= 0 && Number(value) <= 100) : !(Number(value) > 0)) { toast(field === 'loss' ? '损耗率须在0至100之间' : '数量须大于0', true); render(); return }
      mutate(() => { if (field === 'outputQty') bom.outputQty = Number(value); else bom.items.find(line => line.id === lineId)[field] = Number(value) })
    }
  })
  $('#tree').addEventListener('click', event => {
    const toggle = event.target.closest('[data-toggle]')
    if (toggle?.textContent) { const key = toggle.dataset.toggle; collapsed.has(key) ? collapsed.delete(key) : collapsed.add(key); renderTree(); return }
    const button = event.target.closest('[data-tree-key]')
    if (button) { const row = M.tree(state()).find(row => row.key === button.dataset.treeKey); if (row) selectRow(row) }
  })
  $('#tree-search').addEventListener('input', event => { query = event.target.value; renderTree() })
  $('#expand-all').addEventListener('click', () => { collapsed = collapsed.size ? new Set() : new Set(M.tree(state()).filter(row => row.depth > 0 && row.bomId).map(row => row.key)); renderTree() })
  document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => setView(button.dataset.view)))
  $('#rail-library').addEventListener('click', () => setView('library'))
  $('.rail-button.active').addEventListener('click', () => setView('structure'))
  $('#rail-preview').addEventListener('click', preview)
  $('#walkthrough').addEventListener('click', tour)
  $('#modal-close').addEventListener('click', closeModal)
  $('#save').addEventListener('click', save)
  $('#changes').addEventListener('click', changeReview)
  $('#reset').addEventListener('click', () => confirmReset(false))
  $('#scenario').addEventListener('change', event => confirmReset(event.target.value === 'blank'))
  $('#view-published').addEventListener('click', () => { readingPublished = !readingPublished; selection = M.tree(state())[0]; render() })
  $('#drawer-close').addEventListener('click', closeDrawer)
  $('#drawer-backdrop').addEventListener('click', closeDrawer)
  document.addEventListener('keydown', event => { if (event.key === 'Escape') closeDrawer() })
  window.addEventListener('beforeunload', event => { if (isDirty()) { event.preventDefault(); event.returnValue = '' } })
  $('#export').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify({ demoOnly: true, workspace, published }, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob), anchor = document.createElement('a')
    anchor.href = url; anchor.download = 'bom-studio-demo.json'; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast('已导出模拟工作区，不包含真实业务数据')
  })
  const startup = new URLSearchParams(window.location.search)
  const requestedFocus = startup.get('focus')
  const requested = requestedFocus ? M.tree(workspace).find(row => row.bomId === requestedFocus) : null
  if (requested) selection = requested
  render()
  if (startup.get('preview') === '1') preview()
  window.BomDemo = { getState: () => M.copy(workspace), getPublished: () => M.copy(published), getSelection: () => M.copy(selection), getDirty: isDirty }
})()
