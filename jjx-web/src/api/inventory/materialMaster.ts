import request from '@/utils/request'
import type { R } from '@/types'
import type { InventoryMaterialQueryParams } from '@/types/inventory/material'
import type {
  MaterialMasterCategory,
  MaterialMasterConfiguration,
  MaterialMasterDetail,
  MaterialMasterForm,
  MaterialMasterRecord,
  MaterialNamingPreview,
  MaterialRenameReport,
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
  updateNaming: (
    id: number,
    data: { materialName: string; reason?: string }
  ) =>
    request.put<R<{ material: MaterialMasterRecord; detail: string }>>(
      `${base}/${id}/naming`,
      data
    ),
  save: (data: MaterialMasterForm) =>
    data.materialId
      ? request.put<R<MaterialMasterRecord>>(base, data)
      : request.post<R<MaterialMasterRecord>>(base, data),
  downloadTemplate: (categoryId: number) =>
    request.get<Blob>(`${base}/import-template`, { params: { categoryId }, responseType: 'blob' }),
  importExcel: (file: File, categoryId: number | undefined, preview: boolean, autoClassify = false) => {
    const data = new FormData()
    data.append('file', file)
    return request.post<R<MaterialImportResult>>(`${base}/import`, data, {
      params: { categoryId, preview, autoClassify },
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
  /** 命名规则预览：只读试算，按品类取最近 5 条物料 */
  previewNaming: (categoryId: number, namePattern: unknown) =>
    request.post<R<MaterialNamingPreview>>(`${base}/naming/preview`, { categoryId, namePattern }),
  /** 规格模板渲染：用当前表单值按已保存模板生成规格文本（不写库） */
  renderSpec: (categoryId: number, material: unknown) =>
    request.post<R<string | null>>(`${base}/spec/render`, { categoryId, material }),
  /** 命名规则渲染：用当前表单值按该品类已保存规则生成名称（不写库） */
  renderNaming: (categoryId: number, material: unknown) =>
    request.post<R<string | null>>(`${base}/naming/render`, { categoryId, material }),
  /** 批量改名预演（按品类已保存规则） */
  renameChanges: (categoryId: number, limit = 50) =>
    request.get<R<MaterialRenameReport>>(`${base}/naming/changes`, { params: { categoryId, limit } }),
  /** 批量改名执行（只改差异行，逐条留痕） */
  applyRename: (categoryId: number) =>
    request.post<R<number>>(`${base}/naming/apply`, { categoryId }),
  template: (id: number) =>
    request.get<
      R<{
        template: MaterialMasterConfiguration['template']
        schema: MaterialMasterConfiguration['schema']
      } | null>
    >(`${base}/template/${id}`),
  removeUnusedTemplate: (id: number) => request.delete<R<void>>(`${base}/template/${id}`),
}
