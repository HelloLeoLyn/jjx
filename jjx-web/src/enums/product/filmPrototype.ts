import { createNamedEnum } from '@/enums/base'

/** 仅用于前端原型：出片申请进度按关联回片数量派生，不代表最终后端状态机。 */
export const FilmPrototypeRequestProgressEnum = createNamedEnum(
  {
    PENDING: { value: 'PENDING', label: '待回片', tagProps: { type: 'warning' } },
    PARTIAL: { value: 'PARTIAL', label: '部分回片', tagProps: { type: 'primary' } },
    RECEIVED: { value: 'RECEIVED', label: '已回齐', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)
