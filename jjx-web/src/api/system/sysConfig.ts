import request from '@/utils/request'
import type { R } from '@/types'

// 系统配置（sys_config）
export interface SysConfigItem {
  configId: number
  configKey: string
  configValue: string
  configName: string
  configGroup: string
  remark?: string
  sortOrder?: number
  isActive?: number
}

export const sysConfigApi = {
  // 配置列表
  list() {
    return request.get<R<SysConfigItem[]>>('/system/config/list')
  },

  // 按分组查询
  listByGroup(group: string) {
    return request.get<R<SysConfigItem[]>>(`/system/config/group/${group}`)
  },

  /** 运行态配置模块（登录即可，敏感键已过滤；打印页公司抬头用，2026-09-04） */
  module(group: string) {
    return request.get<R<Record<string, string>>>(`/config/module/${group}`)
  },

  // 获取配置值
  getValue(key: string) {
    return request.get<R<string>>(`/system/config/value/${key}`)
  },

  // 更新配置值（后端 PUT /{id}?value=xxx）
  update(id: number, value: string) {
    return request.put<R<void>>(`/system/config/${id}`, null, {
      params: { value },
    })
  },

  uploadLogo(file: File, configId: number) {
    const data = new FormData()
    data.append('file', file)
    data.append('bizType', 'system_config')
    data.append('bizId', String(configId))
    data.append('category', 'company_logo')
    return request.post<R<number>>('/system/attachment/upload', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
