// 任务2960：仅用于前端评审的演示模型，不调用业务接口。
export interface Material {
  id: string
  name: string
  spec: string
  unit: string
  available: number
  yield: number
  step: number
}
export interface Allocation {
  id: number
  materialId: string
  coverage: number
  yield: number
  loss: number
  reason: string
}
export interface Demand {
  id: string
  process: string
  materialId: string
  originalSpec: string
  total: number
  issued: number
  returned: number
  pending: number
  allocations: Allocation[]
}
export interface Scenario {
  id: string
  title: string
  summary: string
  note: string
  materials: Material[]
  demands: Demand[]
  events: { title: string; detail: string }[]
}
export const materialCatalog: Material[] = [
  {
    id: 'A',
    name: 'A · PET原规格板材',
    spec: '透明PET / 0.5 × 500 × 1000 mm',
    unit: '张',
    available: 60,
    yield: 1,
    step: 1,
  },
  {
    id: 'B',
    name: 'B · PET大规格板材',
    spec: '透明PET / 0.5 × 1000 × 1000 mm',
    unit: '张',
    available: 30,
    yield: 2,
    step: 1,
  },
  {
    id: 'C',
    name: 'C · PET窄幅卷材',
    spec: '透明PET / 0.5 × 500 mm（卷）',
    unit: '米',
    available: 25,
    yield: 1,
    step: 0.1,
  },
  {
    id: 'D',
    name: 'D · PET加厚板材',
    spec: '透明PET / 0.6 × 500 × 1000 mm',
    unit: '张',
    available: 25,
    yield: 1,
    step: 1,
  },
]
let nextId = 100
export function allocation(materialId: string, coverage: number, reason = ''): Allocation {
  const material = materialCatalog.find((m) => m.id === materialId)!
  return { id: nextId++, materialId, coverage, yield: material.yield, loss: 0, reason }
}
function demand(allocations: Allocation[], extra: Partial<Demand> = {}): Demand {
  return {
    id: 'BOM-001',
    process: '01 · 面板裁切',
    materialId: 'A',
    originalSpec: materialCatalog[0].spec,
    total: 100,
    issued: 0,
    returned: 0,
    pending: 0,
    allocations,
    ...extra,
  }
}
function scenario(
  id: string,
  title: string,
  summary: string,
  note: string,
  demands: Demand[],
  stock: Partial<Record<string, number>> = {},
  events: Scenario['events'] = []
): Scenario {
  return {
    id,
    title,
    summary,
    note,
    demands,
    events,
    materials: materialCatalog.map((m) => ({ ...m, available: stock[m.id] ?? m.available })),
  }
}
export const scenarios: Scenario[] = [
  scenario(
    'partial',
    '原料不足，部分替换',
    'A 60 + B 抵扣40',
    '原需求100：先用A的60张，剩余40件由B承担；B每张可满足2件，需要20张。',
    [demand([allocation('A', 60), allocation('B', 40, '原规格库存不足，使用同材质大板裁切')])]
  ),
  scenario(
    'mixed',
    '原料缺货，多料拼凑',
    'B 40 + C 35 + D 25',
    'A没有库存：B、C、D分别承担40、35、25件。D厚度变化，需明确确认加工条件。',
    [
      demand([
        allocation('B', 40, '大板裁切'),
        allocation('C', 35, '卷材定长裁切'),
        allocation('D', 25, '厚度变化，演示加工条件确认'),
      ]),
    ],
    { A: 0, C: 35 }
  ),
  scenario(
    'shortage',
    '拼凑后仍有缺口',
    '已安排70，还缺30',
    '允许部分领料，但剩余30件继续保留为缺口，不能视为全部领足。',
    [demand([allocation('A', 30), allocation('B', 40, '原料不足，先安排现有库存')])],
    { A: 30, B: 20 }
  ),
  scenario(
    'append',
    '分批发料，追加换料',
    '已发60，本次补40',
    'A已经发出60，本次只安排剩余40件；原发料记录保持不变。',
    [demand([allocation('B', 40, '追加领料，改用大板')], { issued: 60 })],
    { A: 0 },
    [
      {
        title: 'V1 · 已发A 60张',
        detail: '演示领料单 PICK-DEMO-001 / 批次 LOT-A-01 / 抵扣原需求60件',
      },
    ]
  ),
  scenario(
    'pending',
    '待发占用，调整方案',
    '已发40，待发30，余30',
    '已发40和待发30已计入需求。本次仅安排30；演示取消待发后，会释放相应需求。',
    [demand([allocation('C', 30, '剩余需求使用卷材')], { issued: 40, pending: 30 })],
    { C: 40 },
    [
      { title: 'V1 · 已发A 40张', detail: 'PICK-DEMO-002 / LOT-A-02 / 抵扣40件' },
      { title: 'V1 · B 15张待发', detail: 'PICK-DEMO-003 / 占用需求30件，尚未实际发料' },
    ]
  ),
  scenario(
    'return',
    '退料后重新选料',
    '发60退10，本次补50',
    '原发60、退回10分别保留，净抵扣50；本次可重新分配50件。',
    [demand([allocation('B', 50, '原发A退回10，剩余需求改用B')], { issued: 60, returned: 10 })],
    {},
    [
      { title: 'V1 · 发出A 60张', detail: 'PICK-DEMO-004 / LOT-A-03 / 原发数量永久保留' },
      { title: '退回A 10张', detail: 'RETURN-DEMO-001 / 关联原发料批次与V1换算 / 恢复需求10件' },
    ]
  ),
  scenario(
    'shared',
    '同一材料跨项目分配',
    '两项需求，共用B库存',
    '两个BOM项目都使用B。库存校验按实际材料合计，不能每项各用一遍全部库存。当前B需要50张，可用45张。',
    [
      demand([allocation('B', 60, '面板使用B')], { total: 60 }),
      demand([allocation('B', 40, '背板使用B')], {
        id: 'BOM-002',
        process: '02 · 背板裁切',
        total: 40,
      }),
    ],
    { B: 45 }
  ),
  scenario(
    'rounding',
    '损耗与整张取料',
    '需求5，B需领3张',
    '5件需求按每张2件、损耗5%计算为2.625张，向上取整为3张；取整余量不再额外抵扣需求。',
    [demand([{ ...allocation('B', 5, '整张领料，余料另行管理'), loss: 5 }], { total: 5 })]
  ),
  scenario(
    'voluntary',
    '有库存，主动换料',
    'A有100，本次选择B',
    '即使A库存充足，也可按本次加工条件主动选择B，并保留原因。',
    [demand([allocation('B', 100, '本次大版排料，减少裁切次数')])],
    { A: 100, B: 60 }
  ),
  scenario(
    'changed',
    '历史复用与库存变化',
    '沿用组合，重新核算',
    '点击「引用历史组合」演示按本次剩余需求重算；B当前库存仅10张，复用后必须重新检查缺口。',
    [demand([allocation('A', 60), allocation('B', 40, '引用历史排料参数，待核对库存')])],
    { B: 10 }
  ),
]
export function remaining(d: Demand) {
  return Math.max(0, d.total - d.issued + d.returned - d.pending)
}
export function planned(d: Demand) {
  return d.allocations.reduce((sum, a) => sum + (Number(a.coverage) || 0), 0)
}
export function rawQuantity(a: Allocation) {
  if (!(a.yield > 0) || !(a.coverage > 0) || !(a.loss >= 0)) return 0
  return (a.coverage / a.yield) * (1 + a.loss / 100)
}
export function quantity(a: Allocation, material: Material) {
  return Number((Math.ceil(rawQuantity(a) / material.step - 1e-9) * material.step).toFixed(4))
}
export function materialTotals(s: Scenario) {
  const totals: Record<string, number> = {}
  for (const d of s.demands)
    for (const a of d.allocations) {
      const m = s.materials.find((item) => item.id === a.materialId)
      if (m) totals[m.id] = Number(((totals[m.id] || 0) + quantity(a, m)).toFixed(4))
    }
  return totals
}
export function validationIssues(s: Scenario): string[] {
  const issues: string[] = []
  for (const d of s.demands) {
    if (planned(d) > remaining(d) + 1e-8) issues.push(`${d.process}：本次分配超过剩余需求`)
    for (const a of d.allocations) {
      if (
        !(a.coverage > 0) ||
        !(a.yield > 0) ||
        !(a.loss >= 0) ||
        !Number.isFinite(a.coverage + a.yield + a.loss)
      )
        issues.push(`${d.process}：请填写有效需求量、模数和损耗`)
      if (!s.materials.some((m) => m.id === a.materialId))
        issues.push(`${d.process}：实际材料不存在`)
      if (a.materialId !== d.materialId && !a.reason.trim())
        issues.push(`${d.process}：请填写换料依据`)
    }
  }
  const totals = materialTotals(s)
  for (const m of s.materials)
    if ((totals[m.id] || 0) > m.available + 1e-8)
      issues.push(`${m.name}：需领${totals[m.id]}${m.unit}，可用${m.available}${m.unit}`)
  if (!s.demands.some((d) => d.allocations.length)) issues.push('请至少添加一条实际材料分配')
  return [...new Set(issues)]
}
