// src/main.ts
import { createApp } from 'vue'
import pinia from './store' // 从 index.ts 导入
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import Pagination from '@/components/Pagination/index.vue'
import App from './App.vue'
import router from './router'
import './permission' // 权限控制（路由守卫）
import { setupDirectives } from './directives'
import './styles/index.scss'
import SvgIcon from '@/components/SvgIcon/index.vue'

// 引入 SVG 插件生成的雪碧图
import 'virtual:svg-icons-register'

const app = createApp(App)

// 注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
// 全局注册分页组件
app.component('Pagination', Pagination)
// 注册自定义指令
setupDirectives(app)

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})
// 全局注册 SVG 图标组件
app.component('SvgIcon', SvgIcon)
app.mount('#app')
