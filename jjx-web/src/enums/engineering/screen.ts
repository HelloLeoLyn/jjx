import { createNamedEnum } from '@/enums/base'

/**
 * 网版状态枚举（jjx_screen_master.status）
 * 1=在用 0=停用
 */
export const ScreenStatusEnum = createNamedEnum(
  {
    IN_USE: { value: 1, label: '在用', tagProps: { type: 'success' } },
    DISABLED: { value: 0, label: '停用', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)

/**
 * 网框型号枚举（jjx_screen_master.frame_type）
 * 历史台账按 A/B/C/F/G/H 分类，网版号 = 框型 + 4 位序号（如 A0001）
 */
export const ScreenFrameTypeEnum = createNamedEnum(
  {
    A: { value: 'A', label: 'A框', tagProps: { type: 'primary' } },
    B: { value: 'B', label: 'B框', tagProps: { type: 'success' } },
    C: { value: 'C', label: 'C框', tagProps: { type: 'warning' } },
    F: { value: 'F', label: 'F框', tagProps: { type: 'info' } },
    G: { value: 'G', label: 'G框', tagProps: { type: 'danger' } },
    H: { value: 'H', label: 'H框', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)
