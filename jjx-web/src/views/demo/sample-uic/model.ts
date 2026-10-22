export interface UicProcess {
  key: string
  name: string
  icon: string
  instruction: string
  remark: string
}
export interface UicMaterial {
  key: string
  ownerKey: string
  name: string
  spec: string
  moduleQty: number
  baseQty: number
  split: number | null
  registered: boolean
}
export interface UicGroup {
  key: string
  processes: UicProcess[]
  materials: UicMaterial[]
  remark: string
}
export const processLibrary = [
  { name: '面板冲型', icon: '面板冲型', category: '面板' },
  { name: '面板', icon: '面板', category: '面板' },
  { name: '下线', icon: '下线', category: '线路' },
  { name: '下线冲型', icon: '下线冲型', category: '线路' },
  { name: '上线冲型', icon: '上线冲型', category: '线路' },
  { name: 'LED装配', icon: '', category: '组装' },
]
let sequence = 100
export function nextKey() {
  return `preview-${++sequence}`
}
export function createGroup(names: string[]): UicGroup {
  return {
    key: nextKey(),
    processes: names.map((name) => ({
      key: nextKey(),
      name,
      icon: processLibrary.find((p) => p.name === name)?.icon || '',
      instruction: '',
      remark: '',
    })),
    materials: [],
    remark: '',
  }
}
export function demoGroups(): UicGroup[] {
  return [
    {
      key: 'single',
      processes: [
        { key: 'p1', name: '面板冲型', icon: '面板冲型', instruction: '外观', remark: '一车二膜' },
      ],
      remark: '',
      materials: [
        {
          key: 'm1',
          ownerKey: 'p1',
          name: 'pet125',
          spec: '30×12',
          moduleQty: 3,
          baseQty: 1,
          split: null,
          registered: true,
        },
        {
          key: 'm2',
          ownerKey: 'p1',
          name: '离型膜',
          spec: '透明',
          moduleQty: 3,
          baseQty: 1,
          split: null,
          registered: true,
        },
      ],
    },
    {
      key: 'composite',
      processes: [
        {
          key: 'p2',
          name: '面板',
          icon: '面板',
          instruction: '冲窗口灯孔',
          remark: '按图纸定位窗口',
        },
        { key: 'p3', name: '下线', icon: '下线', instruction: '线路外形', remark: '注意出线方向' },
      ],
      remark: '面板与下线配套加工',
      materials: [
        {
          key: 'm3',
          ownerKey: 'p2',
          name: 'pet125',
          spec: '30×12',
          moduleQty: 3,
          baseQty: 1,
          split: null,
          registered: true,
        },
        {
          key: 'm4',
          ownerKey: 'p2',
          name: '离型膜',
          spec: '透明',
          moduleQty: 3,
          baseQty: 1,
          split: null,
          registered: true,
        },
        {
          key: 'm5',
          ownerKey: 'p3',
          name: 'sdt124',
          spec: '300',
          moduleQty: 1,
          baseQty: 1,
          split: 30,
          registered: true,
        },
      ],
    },
    {
      key: 'led',
      processes: [
        {
          key: 'p4',
          name: 'LED装配',
          icon: '',
          instruction: '正负极方向',
          remark: '红灯2颗，蓝灯1颗',
        },
      ],
      remark: '',
      materials: [
        {
          key: 'm6',
          ownerKey: 'p4',
          name: 'led.02.26s10',
          spec: 'red',
          moduleQty: 1,
          baseQty: 2,
          split: null,
          registered: true,
        },
        {
          key: 'm7',
          ownerKey: 'p4',
          name: 'led.02.26s10',
          spec: 'blue',
          moduleQty: 1,
          baseQty: 1,
          split: null,
          registered: false,
        },
      ],
    },
  ]
}
export function materialLabel(material: UicMaterial) {
  return material.name + (['red', 'blue'].includes(material.spec) ? ` · ${material.spec}` : '')
}
export function processMaterials(group: UicGroup, key: string) {
  return group.materials.filter((m) => m.ownerKey === key)
}
