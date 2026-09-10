import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { HrEmployeeForm, HrEmployeeQuery, HrEmployeeVO, HrImportResult } from '@/types/hr/employee'

/** 人事管理 - 员工档案 API */
export const hrEmployeeApi = {
  /** 分页查询 */
  page(params: HrEmployeeQuery) {
    return request.get<R<PageResult<HrEmployeeVO>>>('/hrs/employees/page', { params })
  },

  /** 详情 */
  detail(empId: number) {
    return request.get<R<HrEmployeeVO>>(`/hrs/employees/${empId}`)
  },

  /** 预览下一个工号 */
  nextEmpNo() {
    return request.get<R<string>>('/hrs/employees/nextEmpNo')
  },

  /** 新增 */
  create(data: HrEmployeeForm) {
    return request.post<R<number>>('/hrs/employees', data)
  },

  /** 修改 */
  update(data: HrEmployeeForm) {
    return request.put<R<void>>('/hrs/employees', data)
  },

  /** 删除 */
  remove(empId: number) {
    return request.delete<R<void>>(`/hrs/employees/${empId}`)
  },

  /** 导入 */
  importFile(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<R<HrImportResult>>('/hrs/employees/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** 下载导入模板 */
  downloadTemplate() {
    return request.get('/hrs/employees/importTemplate', { responseType: 'blob' })
  },

  /** 导出 */
  exportList(params: HrEmployeeQuery) {
    return request.get('/hrs/employees/export', { params, responseType: 'blob' })
  },
}
