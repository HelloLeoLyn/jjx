import { sysConfigApi } from '@/api/system/sysConfig'

/**
 * 文档主题色（打印单据 / 移动端卡片）运行时入口 —— 2026-09-16 dev-20260916-003
 *
 * 链路：sys_config.theme_color（分组 pdf_template）
 *   → GET /config/module/pdf_template → applyDocTheme() → :root 的 --doc-theme
 * 样式侧兜底与派生色见 styles/doc-theme.scss（--doc-theme / --doc-theme-soft / --doc-theme-2）。
 */

/** 默认主色（与 styles/doc-theme.scss 兜底、store/config/pdfTemplate 默认值保持一致） */
export const DEFAULT_DOC_THEME = '#2B5AA7'

/** 仅接受十六进制颜色，非法值不落地（保留样式兜底，打印不会变成透明/黑） */
const HEX_COLOR = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/

/** 把主色写到 :root；传空/非法值时不动（沿用 CSS 兜底） */
export function applyDocTheme(color?: string | null): boolean {
  const value = (color ?? '').trim()
  if (!HEX_COLOR.test(value)) return false
  document.documentElement.style.setProperty('--doc-theme', value)
  return true
}

/** 从后端配置读取 theme_color 并应用；失败静默回退兜底色，不阻断页面 */
export async function loadDocTheme(): Promise<string | null> {
  try {
    const res = await sysConfigApi.module('pdf_template')
    const color = res.data?.theme_color
    if (color && applyDocTheme(color)) return color
  } catch (e) {
    console.warn('[docTheme] 主题色加载失败，使用默认色', e)
  }
  return null
}
