import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

/**
 * 公共附件 V1-A 引用接口封装（V1-B WP1）
 * 后端唯一真源：SysAttachmentRefController（/system/attachment/refs/**）
 * 说明：本模块只封装「已存在」的后端端点，不新增后端能力；
 *      历史兼容读取（按 biz_type/biz_id 列表）仍走 @/api/system/attachment 的 attachmentApi。
 */

/** 来源类型白名单（与后端 AttachmentSourcePolicy 一致） */
export type AttachmentSourceType =
  | 'product'
  | 'inquiry'
  | 'quotation'
  | 'sales_order'
  | 'sample'
  | 'purchase_order'
  | 'production_order'
  | 'material'

/** 引用行（GET /refs 返回；主键 refId）
 *  末尾三个文件元数据由后端 refs 查询链路回填（只读展示字段，不落库、不参与引用唯一性） */
export interface AttachmentRefItem {
  id: number
  attachmentId: number
  sourceType: string
  sourceId: number
  sourceNo?: string | null
  productId?: number | null
  /** 行级附件归属：产品编码（内部字段，S5 dev-20261013-043） */
  productCode?: string | null
  documentType?: string | null
  displayName?: string | null
  remark?: string | null
  sortOrder?: number
  deleted?: boolean
  createBy?: string | null
  createTime?: string | null
  updateBy?: string | null
  updateTime?: string | null
  deletedBy?: string | null
  deletedAt?: string | null
  /** 附件文件名（后端回填） */
  fileName?: string | null
  /** 附件字节数（后端回填） */
  fileSize?: number | null
  /** 附件 MIME（后端回填，用于预览/图片判定） */
  fileType?: string | null
}

/** 建立引用请求体（POST /refs） */
export interface AttachmentRefCreateBody {
  attachmentId: number
  sourceType: string
  sourceId: number
  productId?: number | null
  productCode?: string | null
  documentType?: string | null
  displayName?: string | null
  remark?: string | null
  sortOrder?: number | null
}

/** 修改引用元数据（PUT /refs/{refId}）：全量替换，null=清空 */
export interface AttachmentRefMetadata {
  documentType?: string | null
  displayName?: string | null
  remark?: string | null
  sortOrder?: number | null
}

/** 批量修改单条（PUT /refs/batch）：按行部分更新，null/不传 = 不改该字段 */
export interface AttachmentRefBatchItemBody {
  refId: number
  documentType?: string | null
  displayName?: string | null
  remark?: string | null
  sortOrder?: number | null
}

/** 同内容命中项（GET /refs/by-sha256） */
export interface AttachmentDuplicateItem {
  refId: number
  attachmentId: number
  file_name?: string
  file_size?: number
  sha256?: string
}

/** 资料类型字典项（GET /refs/document-types） */
export interface AttachmentDocumentTypeItem {
  dict_code: string
  item_key: string
  label: string
  is_active?: number
  /** 二级类型带 {"parent":"general","sources":["inquiry",...]} */
  ext_data?: string | Record<string, unknown> | null
  sort_order?: number
}

export const attachmentRefApi = {
  /** TEMP 上传：requestKey 必须 16~100 位 [A-Za-z0-9_-]，同键同内容重传幂等返回原 attachmentId */
  tempUpload(file: File, requestKey: string, onProgress?: (percent: number) => void): AxiosPromise<number> {
    const data = new FormData()
    data.append('file', file)
    data.append('requestKey', requestKey)
    return request({
      url: '/system/attachment/refs/temp',
      method: 'post',
      data,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
      onUploadProgress: (event) => {
        if (event.total) onProgress?.(Math.round((event.loaded * 100) / event.total))
      },
    })
  },

  /** 建立引用（同来源同附件同产品幂等：返回原 refId） */
  createRef(body: AttachmentRefCreateBody): AxiosPromise<number> {
    return request({ url: '/system/attachment/refs', method: 'post', data: body })
  },

  /** 按来源查询引用（主键 refId） */
  listRefs(sourceType: string, sourceId?: number, sourceNo?: string): AxiosPromise<AttachmentRefItem[]> {
    return request({ url: '/system/attachment/refs', method: 'get', params: { sourceType, sourceId, sourceNo } })
  },

  /** 产品维度引用（含逐来源权限过滤） */
  refsByProduct(productId: number): AxiosPromise<AttachmentRefItem[]> {
    return request({ url: `/system/attachment/refs/product/${productId}`, method: 'get' })
  },

  /** 修改引用元数据（分类/展示名/备注/排序） */
  updateMeta(refId: number, body: AttachmentRefMetadata): AxiosPromise<boolean> {
    return request({ url: `/system/attachment/refs/${refId}`, method: 'put', data: body })
  },

  /** 批量修改引用元数据（按行部分更新：每条只传要改的字段；一次事务） */
  batchUpdate(items: AttachmentRefBatchItemBody[]): AxiosPromise<number> {
    return request({ url: '/system/attachment/refs/batch', method: 'put', data: { items } })
  },

  /** 批量解除引用（逗号分隔 refId；一次事务；不动物理文件） */
  batchUnlink(refIds: number[]): AxiosPromise<number> {
    return request({ url: `/system/attachment/refs/batch/${refIds.join(',')}`, method: 'delete' })
  },

  /** 解除引用（幂等；不动物理文件） */
  unbind(refId: number): AxiosPromise<boolean> {
    return request({ url: `/system/attachment/refs/${refId}`, method: 'delete' })
  },

  /** 按引用下载 URL */
  downloadUrlByRef(refId: number): string {
    const base = (import.meta.env.VITE_APP_BASE_API || '/api') as string
    return `${base}/system/attachment/refs/${refId}/download`
  },

  /** 按引用预览 URL（后端白名单 inline：pdf/png/jpeg/gif/webp/bmp） */
  previewUrlByRef(refId: number): string {
    const base = (import.meta.env.VITE_APP_BASE_API || '/api') as string
    return `${base}/system/attachment/refs/${refId}/preview`
  },

  /** 范围内同内容检测（sha256 必须 64 位十六进制；需要业务范围） */
  refsBySha256(sha256: string, sourceType: string, sourceId: number): AxiosPromise<AttachmentDuplicateItem[]> {
    return request({
      url: '/system/attachment/refs/by-sha256',
      method: 'get',
      params: { sha256, sourceType, sourceId },
    })
  },

  /** 资料类型字典（一级 attachment_category + 二级 attachment_document_type） */
  documentTypes(): AxiosPromise<AttachmentDocumentTypeItem[]> {
    return request({ url: '/system/attachment/refs/document-types', method: 'get' })
  },

  /** 清理预演（admin 角色；仅后台页使用，业务页面不得调用） */
  cleanupPreview(afterId = 0, limit = 100): AxiosPromise<Record<string, unknown>[]> {
    return request({
      url: '/system/attachment/refs/cleanup-preview',
      method: 'post',
      params: { afterId, limit },
    })
  },

  /** 到期且无有效引用的附件标记删除（移入回收站，可恢复；不物理删除）。仅后台清理页使用。 */
  cleanupApply(afterId = 0, limit = 100): AxiosPromise<Record<string, unknown>> {
    return request({
      url: '/system/attachment/refs/cleanup-apply',
      method: 'post',
      params: { afterId, limit },
    })
  },
}

export default attachmentRefApi
