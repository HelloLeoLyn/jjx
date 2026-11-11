/* dev-20261111-020: isolated, fictional demo data and browser-local calculations. */
;(function (global) {
  'use strict'
  const copy = value => JSON.parse(JSON.stringify(value))
  let sequence = 100
  const uid = prefix => `${prefix}-${++sequence}-${Date.now().toString(36)}`
  const catalog = [
    { id: 'panel', code: 'DEMO-P100', name: '控制面板组件', spec: '演示产品 · 标准型', unit: 'PCS', kind: 'product' },
    { id: 'panel-plus', code: 'DEMO-P101', name: '加固型面板组件', spec: '演示产品 · 共用裁切基片', unit: 'PCS', kind: 'product' },
    { id: 'laminate', code: 'DEMO-S210', name: '面板基片组件', spec: '裁切基片＋胶膜片', unit: 'PCS', kind: 'assembly' },
    { id: 'cut', code: 'DEMO-S211', name: '裁切基片', spec: '70 × 180 mm · 假设排版', unit: 'PCS', kind: 'assembly' },
    { id: 'adhesive', code: 'DEMO-S212', name: '胶膜裁切片', spec: '卷宽 195 mm · 送料 80 mm', unit: 'PCS', kind: 'assembly' },
    { id: 'seal', code: 'DEMO-S220', name: '密封组件', spec: '密封条＋固定件', unit: 'PCS', kind: 'assembly' },
    { id: 'sheet', code: 'DEMO-R310', name: 'PC 片材', spec: '450 × 225 mm', unit: '张', kind: 'raw' },
    { id: 'roll', code: 'DEMO-R320', name: '胶膜卷材', spec: '宽 195 mm · 每卷 50 m', unit: 'M2', kind: 'roll' },
    { id: 'strip', code: 'DEMO-R330', name: '密封条', spec: '宽 8 mm · 连续材料', unit: 'M', kind: 'raw' },
    { id: 'screw', code: 'DEMO-R340', name: '固定螺钉', spec: 'M2 × 6', unit: 'PCS', kind: 'raw' },
    { id: 'pad', code: 'DEMO-R350', name: '缓冲垫', spec: '10 × 10 × 1 mm', unit: 'PCS', kind: 'raw' },
  ]
  const material = id => catalog.find(item => item.id === id)
  const fixed = (id, materialId, quantity, bomId = null) => ({ id, materialId, quantity, bomId, loss: 0, method: 'fixed' })
  function seed(blank = false) {
    return {
      schema: 1, root: 'bom-root', reviewRequested: false,
      boms: {
        'bom-root': { id: 'bom-root', materialId: 'panel', code: 'BOM-P100', version: 'R03', name: '控制面板标准装配', outputQty: 1, note: '', items: blank ? [] : [fixed('line-a', 'laminate', 1, 'bom-laminate'), fixed('line-b', 'seal', 1, 'bom-seal'), fixed('line-c', 'screw', 4)] },
        'bom-laminate': { id: 'bom-laminate', materialId: 'laminate', code: 'BOM-S210', version: 'R02', name: '标准贴合方案', outputQty: 1, note: '', items: [fixed('line-d', 'cut', 1, 'bom-cut'), fixed('line-e', 'adhesive', 1, 'bom-adhesive')] },
        'bom-cut': { id: 'bom-cut', materialId: 'cut', code: 'BOM-S211-A', version: 'R01', name: '六片排版', outputQty: 6, note: '示例排版比例用于体验；不代表真实工艺定额。', items: [fixed('line-f', 'sheet', 1)] },
        'bom-cut-alt': { id: 'bom-cut-alt', materialId: 'cut', code: 'BOM-S211-B', version: 'R01', name: '四片排版', outputQty: 4, note: '演示同一半成品的另一种制造方式。', items: [fixed('line-g', 'sheet', 1)] },
        'bom-adhesive': { id: 'bom-adhesive', materialId: 'adhesive', code: 'BOM-S212', version: 'R01', name: '卷材连续裁切', outputQty: 1, note: '假设整宽送料、每跳一片，跳距含该次送料长度；不自动当作成品净长度。', items: [{ ...fixed('line-h', 'roll', 0.0156), method: 'pitch', pitch: { width: 195, step: 80, pieces: 1, rollLength: 50, fixedLength: 0 } }] },
        'bom-seal': { id: 'bom-seal', materialId: 'seal', code: 'BOM-S220', version: 'R01', name: '密封组件标准方案', outputQty: 1, note: '', items: [fixed('line-i', 'strip', 0.65), fixed('line-j', 'screw', 2)] },
        'bom-shared-parent': { id: 'bom-shared-parent', materialId: 'panel-plus', code: 'BOM-P101', version: 'R01', name: '加固型面板装配', outputQty: 1, note: '另一演示产品复用同一裁切方案，用于体验共用方案的影响范围。', items: [fixed('line-k', 'cut', 1, 'bom-cut'), fixed('line-l', 'pad', 2)] },
      },
    }
  }
  function linePerOutput(bom, line) {
    const unit = line.method === 'pitch'
      ? Number(line.pitch.width) / 1000 * Number(line.pitch.step) / 1000 / Number(line.pitch.pieces)
      : Number(line.quantity) / Number(bom.outputQty)
    return unit * (1 + Number(line.loss || 0) / 100)
  }
  function options(state, materialId) { return Object.values(state.boms).filter(bom => bom.materialId === materialId) }
  function reaches(state, start, target, visited = new Set()) {
    if (start === target) return true
    if (visited.has(start) || !state.boms[start]) return false
    visited.add(start)
    return state.boms[start].items.some(line => line.bomId && reaches(state, line.bomId, target, visited))
  }
  function refs(state, id) {
    return Object.values(state.boms).flatMap(bom => bom.items.filter(line => line.bomId === id).map(line => ({ bom, line })))
  }
  function useBom(state, parentId, lineId, bomId) {
    const parent = state.boms[parentId]
    const line = parent?.items.find(item => item.id === lineId)
    const child = state.boms[bomId]
    if (!line || !child || child.materialId !== line.materialId) throw Error('方案产出与当前材料不一致')
    if (reaches(state, bomId, parentId)) throw Error('不能引用：这会形成循环结构')
    line.bomId = bomId
    return child
  }
  function createBom(state, materialId, name) {
    if (!material(materialId)) throw Error('请选择有效物料')
    const id = uid('bom')
    const bom = { id, materialId, code: `BOM-${material(materialId).code.replace('DEMO-', '')}-${Object.keys(state.boms).length + 1}`, version: 'R01', name: name || `${material(materialId).name}制造方案`, outputQty: 1, note: '', items: [] }
    state.boms[id] = bom
    return bom
  }
  function addLine(state, bomId, materialId, quantity = 1, childId = null) {
    if (!state.boms[bomId] || !material(materialId) || !(quantity > 0)) throw Error('材料和数量必须有效')
    const line = fixed(uid('line'), materialId, quantity)
    state.boms[bomId].items.push(line)
    try { if (childId) useBom(state, bomId, line.id, childId) }
    catch (error) { state.boms[bomId].items.pop(); throw error }
    return line
  }
  function cloneBom(state, id, name) {
    const source = state.boms[id]
    if (!source) throw Error('方案不存在')
    const bom = createBom(state, source.materialId, name || `${source.name} · 独立副本`)
    Object.assign(bom, copy(source), { id: bom.id, code: bom.code, version: 'R01', name: name || `${source.name} · 独立副本` })
    bom.items.forEach(line => { line.id = uid('line') })
    return bom
  }
  function tree(state) {
    const rows = []
    function walk(bomId, path, depth, line = null, parent = null, quantity = 1, ancestors = new Set()) {
      const bom = state.boms[bomId]
      if (!bom) return
      rows.push({ key: path, bomId, depth, line, parent, materialId: bom.materialId, quantity })
      if (ancestors.has(bomId)) return
      const next = new Set(ancestors).add(bomId)
      for (const item of bom.items) {
        const needed = quantity * linePerOutput(bom, item)
        if (item.bomId) walk(item.bomId, `${path}/${item.id}`, depth + 1, item, bomId, needed, next)
        else rows.push({ key: `${path}/${item.id}`, bomId: null, depth: depth + 1, line: item, parent: bomId, materialId: item.materialId, quantity: needed })
      }
    }
    walk(state.root, state.root, 0)
    return rows
  }
  function validate(state) {
    const errors = []
    const reachable = [...new Set(tree(state).filter(row => row.bomId).map(row => row.bomId))]
    for (const id of reachable) {
      const bom = state.boms[id]
      if (!String(bom.name).trim()) errors.push(`${bom.code}：请填写方案名称`)
      if (!(Number(bom.outputQty) > 0)) errors.push(`${bom.name}：产出数量须大于0`)
      if (!bom.items.length) errors.push(`${bom.name}：至少添加一项投入材料`)
      for (const line of bom.items) {
        if (!(Number(line.quantity) > 0) && line.method !== 'pitch') errors.push(`${bom.name}：材料数量须大于0`)
        if (!(Number(line.loss) >= 0 && Number(line.loss) <= 100)) errors.push(`${bom.name}：损耗率须在0至100之间`)
        if (line.bomId && (!state.boms[line.bomId] || state.boms[line.bomId].materialId !== line.materialId)) errors.push(`${bom.name}：方案产出物料不一致`)
        if (line.bomId && reaches(state, line.bomId, id)) errors.push(`${bom.name}：结构存在循环引用`)
        if (line.method === 'pitch' && (![line.pitch?.width, line.pitch?.step, line.pitch?.pieces, line.pitch?.rollLength].every(value => Number(value) > 0) || Number(line.pitch?.fixedLength || 0) < 0)) errors.push(`${bom.name}：卷宽、跳距、每跳产出和卷长须大于0，起尾料不能为负`)
      }
    }
    return errors
  }
  function calculate(state, demand, stock = false) {
    const errors = validate(state)
    if (errors.length || !(Number(demand) > 0)) throw Error(errors[0] || '需求数量须大于0')
    const nodes = new Map(), pending = [state.root]
    while (pending.length) {
      const id = pending.shift()
      if (nodes.has(id)) continue
      const bom = state.boms[id]
      nodes.set(id, bom)
      bom.items.forEach(line => { if (line.bomId) pending.push(line.bomId) })
    }
    const edges = new Map([...nodes.keys()].map(id => [id, 0]))
    for (const bom of nodes.values()) for (const line of bom.items) if (line.bomId) edges.set(line.bomId, edges.get(line.bomId) + 1)
    const queue = [...edges].filter(([, count]) => count === 0).map(([id]) => id)
    const required = new Map([[state.root, Number(demand)]]), raws = new Map(), production = [], available = { cut: 40, adhesive: 60, laminate: 20 }
    while (queue.length) {
      const id = queue.shift(), bom = nodes.get(id), gross = required.get(id) || 0
      const cover = stock && id !== state.root ? Math.min(gross, available[bom.materialId] || 0) : 0
      if (stock) available[bom.materialId] = Math.max(0, (available[bom.materialId] || 0) - cover)
      const net = gross - cover
      production.push({ bom, gross, cover, net })
      for (const line of bom.items) {
        let amount = net * linePerOutput(bom, line)
        if (line.method === 'pitch' && net > 0) amount += Number(line.pitch.fixedLength || 0) * Number(line.pitch.width) / 1000
        if (line.bomId) {
          required.set(line.bomId, (required.get(line.bomId) || 0) + amount)
          edges.set(line.bomId, edges.get(line.bomId) - 1)
          if (edges.get(line.bomId) === 0) queue.push(line.bomId)
        } else {
          const key = line.materialId
          const raw = raws.get(key) || { material: material(key), quantity: 0, roll: null }
          raw.quantity += amount
          if (line.method === 'pitch') {
            const area = Number(line.pitch.width) / 1000 * Number(line.pitch.rollLength)
            const roll = { area, length: Number(line.pitch.rollLength), width: Number(line.pitch.width) }
            if (raw.roll && (raw.roll.area !== area || raw.roll.width !== roll.width)) throw Error('同一卷材的示例包装尺寸不同，请先统一卷宽与卷长再汇总实领')
            raw.roll = roll
          }
          raws.set(key, raw)
        }
      }
    }
    return { raws: [...raws.values()].map(raw => ({ ...raw, rolls: raw.roll ? Math.ceil(Number((raw.quantity / raw.roll.area).toPrecision(12))) : null, issued: raw.roll ? Math.ceil(Number((raw.quantity / raw.roll.area).toPrecision(12))) * raw.roll.area : raw.quantity })), production }
  }
  function changes(base, state) {
    const rows = []
    for (const [id, bom] of Object.entries(state.boms)) {
      const before = base.boms[id]
      if (!before) { rows.push({ label: bom.name, detail: `新建制造方案 · ${bom.code}`, type: '新增' }); continue }
      for (const [key, label] of [['name', '名称'], ['outputQty', '产出数量'], ['note', '备注']]) if (before[key] !== bom[key]) rows.push({ label: bom.name, detail: `${label}：${before[key] || '空'} → ${bom[key] || '空'}`, type: '修改' })
      for (const line of bom.items) {
        const old = before.items.find(item => item.id === line.id), m = material(line.materialId)
        if (!old) rows.push({ label: bom.name, detail: `添加 ${m.name} × ${line.quantity} ${m.unit}`, type: '新增' })
        else if (JSON.stringify(old) !== JSON.stringify(line)) {
          const parts = []
          if (old.quantity !== line.quantity) parts.push(`投入 ${old.quantity} → ${line.quantity} ${m.unit}`)
          if (old.bomId !== line.bomId) parts.push(`方案 ${state.boms[old.bomId]?.name || '直接用料'} → ${state.boms[line.bomId]?.name || '直接用料'}`)
          if (old.loss !== line.loss) parts.push(`损耗 ${old.loss}% → ${line.loss}%`)
          if (JSON.stringify(old.pitch) !== JSON.stringify(line.pitch)) parts.push('卷材送料参数调整')
          rows.push({ label: bom.name, detail: `${m.name}：${parts.join('；') || '耗用方式调整'}`, type: '修改' })
        }
      }
      before.items.filter(old => !bom.items.some(line => line.id === old.id)).forEach(old => rows.push({ label: bom.name, detail: `移除 ${material(old.materialId).name}`, type: '移除' }))
    }
    return rows
  }
  const api = { seed, catalog, material, copy, uid, linePerOutput, options, refs, reaches, useBom, createBom, cloneBom, addLine, tree, validate, calculate, changes }
  if (typeof module !== 'undefined' && module.exports) module.exports = api
  else global.BomDemoModel = api
})(typeof window !== 'undefined' ? window : globalThis)
