// src/enums/product/ProductEnum.ts
import { createEnum } from '../actionsEnum'
import { ProcessTypeEnum, ProcessCategoryEnum } from './process'

export { ProcessTypeEnum, ProcessCategoryEnum }

/**
 * 产品操作类型常量
 */
export const ProductActions = {
  EDIT: 'edit', // 编辑
  DELETE: 'delete', // 删除
  SUBMIT: 'submit', // 提交审核
  APPROVE: 'approve', // 审核通过
  REJECT: 'reject', // 审核驳回
  START: 'start', // 开始
  UPDATE: 'update', // 更新
  PAUSE: 'pause', // 暂停
  RESUME: 'resume', // 恢复
  COMPLETE: 'complete', // 完成
  CANCEL: 'cancel', // 取消
  PUBLISH: 'publish', // 发布
  OBSOLETE: 'obsolete', // 停产
} as const

/**
 * 产品状态枚举
 * 对应 Java ProductEnums.Status
 */
export const ProductStatusEnum = createEnum({
  items: [
    {
      value: 1,
      label: '开发中',
      tagProps: { type: 'warning' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT],
    },
    {
      value: 2,
      label: '待审核',
      tagProps: { type: 'warning' },
      actions: [ProductActions.APPROVE, ProductActions.REJECT, ProductActions.CANCEL],
    },
    { value: 3, label: '审核中', tagProps: { type: 'info' }, actions: [] },
    {
      value: 4,
      label: '已通过',
      tagProps: { type: 'success' },
      actions: [ProductActions.PUBLISH, ProductActions.CANCEL],
    },
    {
      value: 5,
      label: '已驳回',
      tagProps: { type: 'danger' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT, ProductActions.CANCEL],
    },
    {
      value: 6,
      label: '已发布',
      tagProps: { type: 'success' },
      actions: [ProductActions.OBSOLETE],
    },
    { value: 7, label: '停产', tagProps: { type: 'danger' }, actions: [ProductActions.OBSOLETE] },
    {
      value: 8,
      label: '取消',
      tagProps: { type: 'danger' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT],
    },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 产品类型枚举
 * 对应 Java ProductEnums.Type
 */
export const ProductTypeEnum = createEnum({
  items: [
    { value: 1, label: '标准产品', tagProps: { type: 'primary' } },
    { value: 2, label: '定制产品', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * BOM类型枚举
 * 对应 Java ProductEnums.BomType
 */
export const BomTypeEnum = createEnum({
  items: [
    { value: 1, label: '工程BOM', tagProps: { type: 'primary' } },
    { value: 2, label: '制造BOM', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * BOM状态枚举
 * 对应 Java ProductEnums.BomStatus
 */
export const BomStatusEnum = createEnum({
  items: [
    {
      value: 1,
      label: '草稿',
      tagProps: { type: 'info' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT, ProductActions.DELETE],
    },
    {
      value: 2,
      label: '审核中',
      tagProps: { type: 'warning' },
      actions: [ProductActions.APPROVE, ProductActions.REJECT],
    },
    {
      value: 3,
      label: '已批准',
      tagProps: { type: 'success' },
      actions: [ProductActions.COMPLETE],
    },
    {
      value: 4,
      label: '已驳回',
      tagProps: { type: 'danger' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT],
    },
    { value: 5, label: '已作废', tagProps: { type: 'danger' }, actions: [] },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 物料来源类型枚举
 * 对应 Java ProductEnums.SourceType
 */
export const SourceTypeEnum = createEnum({
  items: [
    { value: 1, label: '外购', tagProps: { type: 'primary' } },
    { value: 2, label: '自制', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * BOM层枚举（薄膜开关专用）
 * 对应 Java ProductEnums.BomLayer
 */
export const BomLayerEnum = createEnum({
  items: [
    { value: 1, label: '面板层', tagProps: { type: 'primary' } },
    { value: 2, label: '上层线路', tagProps: { type: 'success' } },
    { value: 3, label: '间隔层', tagProps: { type: 'warning' } },
    { value: 4, label: '下层线路', tagProps: { type: 'info' } },
    { value: 5, label: '背胶层', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 工艺路线状态枚举
 * 对应 Java ApproveStatusEnum
 * 1=草稿 2=待审批 3=已批准 4=已驳回
 */
export const RouteStatusEnum = createEnum({
  items: [
    {
      value: 1,
      label: '草稿',
      tagProps: { type: 'info' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT, ProductActions.DELETE],
    },
    {
      value: 2,
      label: '审核中',
      tagProps: { type: 'warning' },
      actions: [ProductActions.APPROVE, ProductActions.REJECT],
    },
    {
      value: 3,
      label: '已批准',
      tagProps: { type: 'success' },
      actions: [],
    },
    {
      value: 4,
      label: '已驳回',
      tagProps: { type: 'danger' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT, ProductActions.DELETE],
    },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 工序类型枚举
 * 对应 Java ProductEnums.StepType
 */
export const StepTypeEnum = createEnum({
  items: [
    { value: 1, label: '丝印', tagProps: { type: 'primary' } },
    { value: 2, label: '冲切', tagProps: { type: 'success' } },
    { value: 3, label: '贴合', tagProps: { type: 'warning' } },
    { value: 4, label: '测试', tagProps: { type: 'info' } },
    { value: 5, label: '包装', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 产品实例生命周期状态枚举
 * 对应 Java ProductEnums.LifecycleStatus
 */
export const LifecycleStatusEnum = createEnum({
  items: [
    {
      value: 1,
      label: '设计阶段',
      tagProps: { type: 'info' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT],
    },
    {
      value: 2,
      label: '客户确认',
      tagProps: { type: 'warning' },
      actions: [ProductActions.APPROVE, ProductActions.REJECT],
    },
    {
      value: 3,
      label: '备料阶段',
      tagProps: { type: 'primary' },
      actions: [ProductActions.UPDATE, ProductActions.PAUSE],
    },
    {
      value: 4,
      label: '生产阶段',
      tagProps: { type: 'success' },
      actions: [ProductActions.UPDATE, ProductActions.PAUSE],
    },
    {
      value: 5,
      label: '质检阶段',
      tagProps: { type: 'warning' },
      actions: [ProductActions.UPDATE, ProductActions.PAUSE],
    },
    { value: 6, label: '发货阶段', tagProps: { type: 'info' }, actions: [ProductActions.COMPLETE] },
    { value: 7, label: '完成阶段', tagProps: { type: 'success' }, actions: [] },
    {
      value: 8,
      label: '暂停',
      tagProps: { type: 'danger' },
      actions: [ProductActions.RESUME, ProductActions.CANCEL],
    },
    { value: 9, label: '返工', tagProps: { type: 'danger' }, actions: [ProductActions.UPDATE] },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 产品实例状态枚举
 * 对应 Java ProductEnums.InstanceStatus
 */
export const InstanceStatusEnum = createEnum({
  items: [
    { value: 1, label: '正常', tagProps: { type: 'success' }, actions: [ProductActions.EDIT] },
    { value: 2, label: '异常', tagProps: { type: 'danger' }, actions: [ProductActions.UPDATE] },
    { value: 3, label: '暂停', tagProps: { type: 'warning' }, actions: [ProductActions.RESUME] },
    { value: 4, label: '完成', tagProps: { type: 'info' }, actions: [] },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 设计任务状态枚举
 * 对应 Java ProductEnums.TaskStatus
 */
export const TaskStatusEnum = createEnum({
  items: [
    {
      value: 1,
      label: '待处理',
      tagProps: { type: 'warning' },
      actions: [ProductActions.START, ProductActions.EDIT, ProductActions.DELETE],
    },
    { value: 2, label: '处理中', tagProps: { type: 'info' }, actions: [ProductActions.COMPLETE] },
    { value: 3, label: '已完成', tagProps: { type: 'success' }, actions: [] },
    {
      value: 4,
      label: '已驳回',
      tagProps: { type: 'danger' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT],
    },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 设计任务类型枚举
 * 对应 Java ProductEnums.TaskType
 */
export const TaskTypeEnum = createEnum({
  items: [
    { value: 1, label: '设计', tagProps: { type: 'primary' } },
    { value: 2, label: '审核', tagProps: { type: 'warning' } },
    { value: 3, label: '修改', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 菲林状态枚举
 * 对应 Java ProductEnums.FilmStatus
 */
export const FilmStatusEnum = createEnum({
  items: [
    {
      value: 1,
      label: '草稿',
      tagProps: { type: 'info' },
      actions: [ProductActions.EDIT, ProductActions.SUBMIT, ProductActions.DELETE],
    },
    { value: 2, label: '审核中', tagProps: { type: 'warning' }, actions: [] },
    {
      value: 3,
      label: '已批准',
      tagProps: { type: 'success' },
      actions: [ProductActions.COMPLETE],
    },
    { value: 4, label: '已作废', tagProps: { type: 'danger' }, actions: [] },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 菲林类型枚举
 * 对应 Java ProductEnums.FilmType
 */
export const FilmTypeEnum = createEnum({
  items: [
    { value: 1, label: '面板菲林', tagProps: { type: 'primary' } },
    { value: 2, label: '上层线路菲林', tagProps: { type: 'success' } },
    { value: 3, label: '间隔菲林', tagProps: { type: 'warning' } },
    { value: 4, label: '下层线路菲林', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 配置选项类型枚举
 * 对应 Java ProductEnums.ConfigOptionType
 */
export const ConfigOptionTypeEnum = createEnum({
  items: [
    { value: 1, label: '材料', tagProps: { type: 'primary' } },
    { value: 2, label: '颜色', tagProps: { type: 'success' } },
    { value: 3, label: '电路', tagProps: { type: 'warning' } },
    { value: 4, label: '尺寸', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 配置模型状态枚举
 * 对应 Java ProductEnums.ConfigModelStatus
 */
export const ConfigModelStatusEnum = createEnum({
  items: [
    { value: 1, label: '激活', tagProps: { type: 'success' }, actions: [ProductActions.EDIT] },
    { value: 0, label: '未激活', tagProps: { type: 'danger' }, actions: [ProductActions.START] },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 产品分类状态枚举
 * 对应 Java ProductEnums.CategoryStatus
 */
export const CategoryStatusEnum = createEnum({
  items: [
    { value: 0, label: '正常', tagProps: { type: 'success' }, actions: [ProductActions.EDIT] },
    { value: 1, label: '停用', tagProps: { type: 'danger' }, actions: [ProductActions.START] },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 产品相关枚举统一导出
 */
export const ProductEnum = {
  status: ProductStatusEnum,
  type: ProductTypeEnum,
  bomType: BomTypeEnum,
  bomStatus: BomStatusEnum,
  bomLayer: BomLayerEnum,
  sourceType: SourceTypeEnum,
  routeStatus: RouteStatusEnum,
  stepType: StepTypeEnum,
  lifecycleStatus: LifecycleStatusEnum,
  instanceStatus: InstanceStatusEnum,
  taskStatus: TaskStatusEnum,
  taskType: TaskTypeEnum,
  filmStatus: FilmStatusEnum,
  filmType: FilmTypeEnum,
  configOptionType: ConfigOptionTypeEnum,
  configModelStatus: ConfigModelStatusEnum,
  categoryStatus: CategoryStatusEnum,
  actions: ProductActions,
}
