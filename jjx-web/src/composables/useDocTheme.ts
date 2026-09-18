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

/** 本次会话是否已成功取到主题色（避免每次路由切换重复请求） */
let themeLoaded = false
/** 是否已有一次请求在途（首屏 onMounted 与路由 watch 可能同时触发） */
let themeLoading = false

/** 登录页判定：PC /login、移动端 /m/login —— 登录前不得请求需登录态的接口 */
export function isLoginPath(path: string): boolean {
  return path === '/login' || path === '/m/login'
}

/**
 * 按登录态 / 当前路由决定是否拉取主题色（App 启动 + 每次路由切换时调用）。
 *
 * 2026-09-18 修复：此前 App.vue 在 onMounted 无条件调 loadDocTheme()，未登录访问
 * /m/login 时该请求返回 code 401，被 utils/request.ts 的 redirectToLogin() 按
 * “当前路由不以 /m/ 开头”判定成 PC 而 replace 到 /login，移动端登录页被踢走。
 * 现在：未登录或停留在登录页一律不发请求；回到登录页时重置标记，换账号后可重新取色。
 */
export function ensureDocTheme(path: string): void {
  if (isLoginPath(path)) {
    themeLoaded = false
    return
  }
  if (themeLoaded || themeLoading) return
  if (!localStorage.getItem('token')) return
  themeLoading = true
  void loadDocTheme().then((color) => {
    themeLoading = false
    if (color) themeLoaded = true
  })
}
