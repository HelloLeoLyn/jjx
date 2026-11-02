/**
 * 作业规范「空块」显示开关（dev-20261102-002）。
 * 凹凸条件 / 备注 / 变更内容 三块默认都渲染；材料或流程过多需要分页时，可取消勾选把空块收掉、让出高度。
 * 只在打印会话内生效（走打印链接参数），不落库。
 */
export const workSpecEmptyBlocks = [
  { key: 'emboss', label: '凹凸条件' },
  { key: 'notes', label: '备注' },
  { key: 'change', label: '变更内容' },
] as const

export type WorkSpecEmptyBlockKey = (typeof workSpecEmptyBlocks)[number]['key']

export const WORK_SPEC_EMPTY_BLOCK_KEYS: WorkSpecEmptyBlockKey[] = workSpecEmptyBlocks.map(
  (block) => block.key
)

/** 解析打印链接里的 hideBlocks=a,b（非法值忽略；重复去重）。 */
export function parseHiddenBlocks(value?: string | null): WorkSpecEmptyBlockKey[] {
  if (!value) return []
  const picked = new Set<string>(String(value).split(',').map((item) => item.trim()))
  return WORK_SPEC_EMPTY_BLOCK_KEYS.filter((key) => picked.has(key))
}

/** 序列化隐藏项；全显示时返回空串，不写进链接。 */
export function serializeHiddenBlocks(hidden?: string[] | null): string {
  if (!hidden?.length) return ''
  return WORK_SPEC_EMPTY_BLOCK_KEYS.filter((key) => hidden.includes(key)).join(',')
}
