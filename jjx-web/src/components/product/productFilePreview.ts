import request from '@/utils/request'
import type { ProductDrawingFile } from '@/api/system/attachment'

export interface PreviewableFile { id: number; kind: 'image' | 'pdf' | 'other' }
export interface FileImage { url: string; width: number; height: number }
export function fileKind(file: Pick<ProductDrawingFile, 'fileName' | 'fileType'>): PreviewableFile['kind'] {
  const ext = file.fileName?.split('.').pop()?.toLowerCase() || ''
  if (file.fileType?.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'svg'].includes(ext)) return 'image'
  if (file.fileType?.includes('pdf') || ext === 'pdf') return 'pdf'
  return 'other'
}
export async function downloadFile(file: Pick<ProductDrawingFile, 'id' | 'fileName'>) {
  const blob = await request.get<Blob>(`/system/attachment/download/${file.id}`, { responseType: 'blob', timeout: 120000 })
  if (!blob.size || blob.type.includes('json')) throw new Error('文件下载失败')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// 使用仓库已有 pdfjs；将 PDF 各页与图片统一成纸张内容，预览、打印共用。
export async function renderFile(file: PreviewableFile): Promise<FileImage[]> {
  const blob = await request.get<Blob>(`/system/attachment/download/${file.id}`, { responseType: 'blob', timeout: 120000 })
  if (!blob.size) throw new Error('文件内容为空')
  if (blob.type.includes('json')) throw new Error('文件下载失败，请重新加载')
  if (file.kind === 'image') {
    const url = URL.createObjectURL(blob)
    try {
      const img = new Image()
      img.src = url
      await img.decode()
      return [{ url, width: img.naturalWidth, height: img.naturalHeight }]
    } catch (error) { URL.revokeObjectURL(url); throw error }
  }
  const pdfjs = await import('pdfjs-dist')
  pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()
  const task = pdfjs.getDocument({ data: await blob.arrayBuffer() })
  const images: FileImage[] = []
  try {
    const pdf = await task.promise
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const viewport = page.getViewport({ scale: 2 })
      const canvas = document.createElement('canvas')
      canvas.width = Math.ceil(viewport.width)
      canvas.height = Math.ceil(viewport.height)
      const context = canvas.getContext('2d')
      if (!context) throw new Error('无法显示 PDF 页面')
      await page.render({ canvas, canvasContext: context, viewport }).promise
      const pageBlob = await new Promise<Blob>((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('PDF 页面转换失败')), 'image/png'))
      images.push({ url: URL.createObjectURL(pageBlob), width: canvas.width, height: canvas.height })
      canvas.width = canvas.height = 0
      page.cleanup()
    }
    return images
  } catch (error) {
    images.forEach((image) => URL.revokeObjectURL(image.url))
    throw error
  } finally { await task.destroy() }
}
