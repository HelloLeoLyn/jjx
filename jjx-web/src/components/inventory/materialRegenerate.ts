/**
 * 单条「重算名称与规格」纯逻辑（dev-20261024-004）。
 * 只负责对比与最终取值；生成规则本身复用 materialMasterModel / materialMasterFields，避免第二套规则。
 */
export interface RegenerationSnapshot {
  materialName?: string | null
  specification?: string | null
}

export interface RegenerationResult {
  remark?: string
  currentName: string
  currentSpec: string
  generatedName: string
  generatedSpec: string
  specGenerated: boolean
  nameChanged: boolean
  specChanged: boolean
  evidence: string[]
}

export function buildRegeneration(
  current: RegenerationSnapshot,
  generated: { name: string; specification: string; specGenerated?: boolean },
  evidence: string[],
  remark?: string
): RegenerationResult {
  const currentName = (current.materialName || '').trim()
  const currentSpec = (current.specification || '').trim()
  const generatedName = (generated.name || '').trim()
  const specGenerated = generated.specGenerated !== false
  const generatedSpec = specGenerated ? (generated.specification || '').trim() : currentSpec
  return {
    remark,
    currentName,
    currentSpec,
    generatedName,
    generatedSpec,
    specGenerated,
    nameChanged: currentName !== generatedName,
    specChanged: currentSpec !== generatedSpec,
    evidence,
  }
}

/** 名称默认保留当前值；只有显式勾选「采用生成名称」才替换（人工确认不自动覆盖）。 */
export function resolveFinalName(result: RegenerationResult, adoptGenerated: boolean): string {
  if (adoptGenerated) return result.generatedName
  return result.currentName || result.generatedName
}

/** 完全无变化时不重复保存。 */
export function hasRegenerationChanges(
  result: RegenerationResult,
  finalName: string
): boolean {
  return finalName.trim() !== result.currentName || result.specChanged
}
