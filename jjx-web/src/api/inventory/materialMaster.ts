import request from '@/utils/request'
import type { R } from '@/types'
import type { InventoryMaterialQueryParams } from '@/types/inventory/material'
import type {
  MaterialMasterCategory,
  MaterialMasterConfiguration,
  MaterialMasterDetail,
  MaterialMasterForm,
  MaterialMasterRecord,
  MaterialDictionaries,
  MaterialImportResult,
} from '@/types/inventory/materialMaster'

const base = '/inventory/material/master'
export const materialMasterApi = {
  categories: (includeDisabled = false) =>
    request.get<R<MaterialMasterCategory[]>>(`${base}/categories`, { params: { includeDisabled } }),
  dictionaries: () => request.get<R<MaterialDictionaries>>(`${base}/dictionaries`),
  configuration: (categoryId: number) =>
    request.get<R<MaterialMasterConfiguration>>(`${base}/configuration/${categoryId}`),
  detail: (id: number) => request.get<R<MaterialMasterDetail>>(`${base}/${id}`),
  save: (data: MaterialMasterForm) =>
    data.materialId
      ? request.put<R<MaterialMasterRecord>>(base, data)
      : request.post<R<MaterialMasterRecord>>(base, data),
  downloadTemplate: (categoryId: number) =>
    request.get<Blob>(`${base}/import-template`, { params: { categoryId }, responseType: 'blob' }),
  importExcel: (file: File, categoryId: number, preview: boolean) => {
    const data = new FormData()
    data.append('file', file)
    return request.post<R<MaterialImportResult>>(`${base}/import`, data, {
      params: { categoryId, preview },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  exportExcel: (params?: InventoryMaterialQueryParams) =>
    request.get<Blob>(`${base}/export`, { params, responseType: 'blob' }),
  exportMigrationCandidates: () =>
    request.get<Blob>(`${base}/migration-candidates`, { responseType: 'blob' }),
  audit: () =>
    request.get<
      R<{
        total: number
        pendingMigration: number
        newModel: number
        categoryDistribution: Record<string, number>
        problems: { materialId: number; reason: string }[]
      }>
    >(`${base}/audit`),
  saveCategory: (data: unknown) => request.put<R<unknown>>(`${base}/category`, data),
  saveTemplate: (data: unknown) => request.put<R<unknown>>(`${base}/template`, data),
  template: (id: number) =>
    request.get<
      R<{
        template: MaterialMasterConfiguration['template']
        schema: MaterialMasterConfiguration['schema']
      } | null>
    >(`${base}/template/${id}`),
  removeUnusedTemplate: (id: number) => request.delete<R<void>>(`${base}/template/${id}`),
}
