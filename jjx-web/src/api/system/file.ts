import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 文件管理（统计/备份/预警）
export const fileApi = {
  // 文件统计
  stats(): AxiosPromise<any> {
    return request({
      url: '/system/file/stats',
      method: 'get',
    })
  },

  // 手动每日增量备份
  backupDaily(): AxiosPromise<any> {
    return request({
      url: '/system/file/backup/daily',
      method: 'post',
    })
  },

  // 手动每周全量备份
  backupWeekly(): AxiosPromise<any> {
    return request({
      url: '/system/file/backup/weekly',
      method: 'post',
    })
  },

  // 手动触发容量预警检查
  alertCheck(): AxiosPromise<any> {
    return request({
      url: '/system/file/alert/check',
      method: 'post',
    })
  },

  // 产品文件迁移（扫描源目录→upload/product，产品须已建档）
  migrateProduct(sourcePath: string): AxiosPromise<any> {
    return request({
      url: '/system/file/migrate-product',
      method: 'post',
      params: { sourcePath },
    })
  },
}
