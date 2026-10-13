/** 保持原组件既有格式化口径。 */
export function formatSize(size?: number | null): string {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}
export function formatTime(t?: string | null): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
