import { ElMessageBox } from 'element-plus'

/** 只确认材料随工序删除的影响，不改变既有归属或保存规则。 */
export async function confirmSampleMaterialRemoval(count: number, impact: string): Promise<boolean> {
  if (!count) return true
  try {
    await ElMessageBox.confirm(
      `${impact}共涉及 ${count} 条材料；对应BOM行会一起移除，点击「保存打样方案」后生效。`,
      '确认删除及材料影响',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    return true
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return false
    throw reason
  }
}
