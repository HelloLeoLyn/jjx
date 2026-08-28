import dayjs from 'dayjs'
import {
  createQualityTemplatePrintLog,
  getQualityTemplatePage,
  type QualityTemplate,
} from '@/api/production/qualityTemplate'
import { QualityTemplateStatus } from '@/enums/production/QualityTemplateEnum'

export const display = (value: unknown) =>
  value === null || value === undefined || value === '' ? '-' : String(value)
export const dateTime = (value?: string | null) =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '-'
export const printDate = () => dayjs().format('YYYY-MM-DD')

export async function logTemplatePrint(recordNo: string): Promise<QualityTemplate> {
  const response: any = await getQualityTemplatePage({ pageNum: 1, pageSize: 10, recordNo })
  const data = response?.data
  const rows: QualityTemplate[] = data?.records || data?.list || (Array.isArray(data) ? data : [])
  const template = rows.find(
    (row) => row.recordNo === recordNo && row.status === QualityTemplateStatus.ACTIVE
  )
  if (!template?.id) throw new Error(`未找到已生效的模板 ${recordNo}`)
  await createQualityTemplatePrintLog(template.id)
  return template
}
