import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

/** 文件上传和图标库登记由后端同一事务完成。 */
export function uploadProcessIcon(file: File, requestKey: string, label: string, category: string): AxiosPromise<number> {
  const data = new FormData()
  data.append('file', file)
  data.append('requestKey', requestKey)
  data.append('label', label)
  data.append('category', category)
  return request({
    url: '/engineering/process-icons',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}
