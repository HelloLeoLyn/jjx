// src/vite-env.d.ts
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 声明 SVG 图标插件虚拟模块（解决 import 'virtual:svg-icons-register' 报错）
declare module 'virtual:svg-icons-register' {
  const content: string
  export default content
}
