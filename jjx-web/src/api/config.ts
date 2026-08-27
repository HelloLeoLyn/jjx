import request from '@/utils/request'
import type { R } from '@/types'
import type { ConfigModule, ConfigRecord } from '@/types/config'

/**
 * 获取配置模块键值对（仅 is_active = 1）
 * GET /api/config/module/{group}
 * 返回示例：{ company_name: '...', show_header: '1', ... }
 */
export function getConfigModule(group: ConfigModule): Promise<R<ConfigRecord>> {
  return request({
    url: `/config/module/${group}`,
    method: 'get',
  })
}
