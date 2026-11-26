/** P06：把「产品-图案版本」扁平行按图案归组（纯函数，便于定向测试）。 */

export interface ArtworkVersionRow {
  versionId: number
  versionNo: string
  status?: string
  isCurrent?: number
  technicalSpec?: string | null
  fileId?: number | null
}

export interface ArtworkGroup {
  artworkId: number
  artworkNo: string
  name: string
  purpose?: string | null
  family?: string
  productId?: number
  productCode?: string
  productName?: string
  versions: ArtworkVersionRow[]
  currentVersionId?: number
  currentVersionNo?: string
  latestStatus?: string
}

/** 后端 /engineering/films/product/{id} 返回的是「图案 × 版本」行，这里按图案归并。 */
export function groupArtworkRows(rows: any[]): ArtworkGroup[] {
  const map = new Map<number, ArtworkGroup>()
  for (const row of rows || []) {
    const key = Number(row?.artworkId)
    if (!key) continue
    let group = map.get(key)
    if (!group) {
      group = {
        artworkId: key,
        artworkNo: row.filmCode,
        name: row.filmName,
        purpose: null,
        family: '面板',
        productId: row.productId,
        productCode: row.productCode,
        productName: row.productName,
        versions: [],
      }
      map.set(key, group)
    }
    group.versions.push({
      versionId: Number(row.versionId),
      versionNo: row.version,
      status: row.versionStatus,
      isCurrent: row.isCurrent,
      technicalSpec: row.technicalSpec,
      fileId: row.fileId,
    })
    if (row.isCurrent === 1) {
      group.currentVersionId = Number(row.versionId)
      group.currentVersionNo = row.version
    }
  }
  const groups = [...map.values()]
  for (const group of groups) {
    group.latestStatus = group.versions[group.versions.length - 1]?.status
  }
  return groups
}
