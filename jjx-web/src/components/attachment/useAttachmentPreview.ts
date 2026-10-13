import { ref } from 'vue'

const IMAGE_EXT = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp']

export interface PreviewRow {
  id: number
  fileName?: string | null
  fileType?: string | null
}

/**
 * 附件预览查看器（图片）+ 非图片回退打开 URL（dev-20261013-040）。
 * 唯一实现：AttachmentCenter 的「引用段」与「只读段（历史/同链路）」两条路共用，
 * 消除此前 previewRef / previewReadOnly 里重复的查看器状态与判断。
 */
export function useAttachmentPreview() {
  const viewerVisible = ref(false)
  const viewerList = ref<string[]>([])
  const viewerIndex = ref(0)

  function isImageName(name?: string | null, fileType?: string | null): boolean {
    if (fileType && fileType.startsWith('image/')) return true
    const lower = String(name || '').toLowerCase()
    return IMAGE_EXT.some((ext) => lower.endsWith(ext))
  }

  function openUrl(url: string) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  /** 预览：图片走查看器（同组图片可翻页），非图片回退打开 URL。 */
  function preview(rows: PreviewRow[], row: PreviewRow, urlOf: (row: PreviewRow) => string) {
    if (isImageName(row.fileName, row.fileType)) {
      const images = rows.filter((r) => isImageName(r.fileName, r.fileType))
      viewerList.value = images.map((r) => urlOf(r))
      const idx = images.findIndex((r) => r.id === row.id)
      viewerIndex.value = idx >= 0 ? idx : 0
      viewerVisible.value = true
      return
    }
    openUrl(urlOf(row))
  }

  function close() {
    viewerVisible.value = false
  }

  return { viewerVisible, viewerList, viewerIndex, isImageName, preview, openUrl, close }
}
