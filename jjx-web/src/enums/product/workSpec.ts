import { createNamedEnum } from '@/enums/base'

/**
 * 产品作业规范发布版本状态（对应后端 product_work_spec_version.status；dev-20261011-033）
 * 停用 = RETIRED（不再被「最新已发布版本」选中）；恢复 = 回到 PUBLISHED。
 */
export const WorkSpecVersionStatus = createNamedEnum(
  {
    PUBLISHED: { value: 'PUBLISHED', label: '已发布', tagProps: { type: 'success' } },
    RETIRED: { value: 'RETIRED', label: '已停用', tagProps: { type: 'info' } },
  },
  { type: 'info' }
)
