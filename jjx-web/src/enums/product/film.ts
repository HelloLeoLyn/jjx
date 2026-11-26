import { createNamedEnum } from '@/enums/base'

/**
 * 菲林审批状态枚举
 * 对应后端 com.jjx.common.enums.ApproveStatusEnum（engineering_film.approve_status）
 * 1=草稿 2=待审批 3=已批准 4=已驳回
 */
export const FilmApproveStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 1, label: '草稿', tagProps: { type: 'info' } },
    PENDING: { value: 2, label: '待审批', tagProps: { type: 'warning' } },
    APPROVED: { value: 3, label: '已批准', tagProps: { type: 'success' } },
    REJECTED: { value: 4, label: '已驳回', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)

/**
 * 菲林类型枚举（字符串码）
 * 对应后端 com.jjx.product.enums.FilmTypeEnum
 */
export const FilmTypeCodeEnum = createNamedEnum(
  {
    OVERLAY: { value: 'OVERLAY', label: '面板菲林', tagProps: { type: 'primary' } },
    UPPER_CIRCUIT: { value: 'UPPER_CIRCUIT', label: '上层线路菲林', tagProps: { type: 'success' } },
    SPACER: { value: 'SPACER', label: '间隔菲林', tagProps: { type: 'warning' } },
    LOWER_CIRCUIT: { value: 'LOWER_CIRCUIT', label: '下层线路菲林', tagProps: { type: 'info' } },
    BACK_ADHESIVE: { value: 'BACK_ADHESIVE', label: '背胶菲林', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)

/**
 * 菲林下发生产状态枚举
 * 0=未下发 1=已下发
 */
export const FilmReleaseStatusEnum = createNamedEnum(
  {
    NOT_RELEASED: { value: 0, label: '未下发', tagProps: { type: 'info' } },
    RELEASED: { value: 1, label: '已下发', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)

/** 菲林版本是否当前版本（0=否 1=是） */
export const FilmCurrentFlagEnum = createNamedEnum(
  {
    NOT_CURRENT: { value: 0, label: '历史版本', tagProps: { type: 'info' } },
    CURRENT: { value: 1, label: '当前版本', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)


/**
 * 图案版本状态（print_artwork_version.status，P03 后端既定状态）
 * 仅做展示映射，不新增状态。
 */
export const ArtworkVersionStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 'DRAFT', label: '草稿', tagProps: { type: 'info' } },
    REVIEW: { value: 'REVIEW', label: '待审核', tagProps: { type: 'warning' } },
    APPROVED: { value: 'APPROVED', label: '已批准', tagProps: { type: 'primary' } },
    RELEASED: { value: 'RELEASED', label: '已发布', tagProps: { type: 'success' } },
    DISABLED: { value: 'DISABLED', label: '已停用', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)

/** 实体底片状态（print_film_copy.status，P03 后端既定状态） */
export const FilmCopyStatusEnum = createNamedEnum(
  {
    AVAILABLE: { value: 'AVAILABLE', label: '可用', tagProps: { type: 'success' } },
    ARCHIVED: { value: 'ARCHIVED', label: '已封存', tagProps: { type: 'info' } },
    DESTROYED: { value: 'DESTROYED', label: '已销毁', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)
