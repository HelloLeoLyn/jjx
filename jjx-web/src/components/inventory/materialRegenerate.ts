/**
 * 单条「重算名称与规格」纯逻辑（dev-20261024-004）。
 * 只负责对比与最终取值；生成规则本身复用 materialMasterModel / materialMasterFields，避免第二套规则。
 */
export interface RegenerationSnapshot {
  materialName?: string | null
  specification?: string | null
}

export interface RegenerationResult {
  currentName: string
  currentSpec: string
  generatedName: string
  generatedSpec: string
  nameChanged: boolean
  specChanged: boolean
  evidence: string[]
}

export function buildRegeneration(
  current: RegenerationSnapshot,
  generated: { name: string; specification: string },
  evidence: string[]
): RegenerationResult {
  const currentName = (current.materialName || '').trim()
  const currentSpec = (current.specification || '').trim()
  const generatedName = (generated.name || '').trim()
  const generatedSpec = (generated.specification || '').trim()
  return {
    currentName,
    currentSpec,
    generatedName,
    generatedSpec,
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
  finalName: string,
  finalSpec: string
): boolean {
  return finalName.trim() !== result.currentName || finalSpec.trim() !== result.currentSpec
}
