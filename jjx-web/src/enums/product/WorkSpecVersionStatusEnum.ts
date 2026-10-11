import { createNamedEnum } from '@/enums/base'

/**
 * 产品作业规范发布版本状态（dev-20261011-013）
 * 与后端 product_work_spec_version.status 同步：PUBLISHED 已发布 / RETIRED 已停用。
 */
export const WorkSpecVersionStatusEnum = createNamedEnum(
  {
    PUBLISHED: { value: 'PUBLISHED', label: '已发布', tagProps: { type: 'success' } },
    RETIRED: { value: 'RETIRED', label: '已停用', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)
