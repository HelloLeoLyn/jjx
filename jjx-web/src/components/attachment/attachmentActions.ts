/**
 * 公共附件组件的「动作清单」与「形态」（dev-20261016-002 第一阶段①）。
 *
 * 原则（见 jjx-docs/reference/attachment-refactor-decisions-dev-20261016-001.md §1）：
 * 组件只做增删改查；**显示哪些动作由调用方传参决定**，组件不按业务态自己判断。
 * **不传 `actions` = 保持现状**（各"行种类"用它默认支持的动作）。
 */

/** 操作列可用的动作名（第一阶段先接这几个；`save` 后续步骤再接）。 */
export type AttachmentAction = 'download' | 'detail' | 'remove' | 'retry' | 'save'

/** 各"行种类"默认支持的动作：调用方不传 actions 时按此渲染（= 现状）。 */
export const KIND_ACTIONS_DEFAULT: Record<'pending' | 'ref', readonly AttachmentAction[]> = {
  pending: ['retry', 'remove'],
  ref: ['download', 'detail', 'remove'],
}

/**
 * 组件形态（只管渲染形态，不含业务判断）——本步骤先只登记类型，
 * 与现有 `mode`（panel/dialog/upload-only/readonly）的收敛放到后续步骤，避免一次改太多。
 */
export type AttachmentVariant = 'panel' | 'dialog' | 'upload-only'
