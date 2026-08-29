import { createApp } from 'vue'
import pinia from './store'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './permission'
import { setupDirectives } from './directives'
import './styles/index.scss'
import 'virtual:svg-icons-register'

// 组件导入
import Pagination from '@/components/Pagination/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

// 插件导入
import { setupSkeleton } from '@/plugins/skeleton'

const app = createApp(App)

// ========== 1. 注册插件 ==========
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// ========== 2. 注册全局组件 ==========
// Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
// 业务组件
app.component('Pagination', Pagination)
app.component('SvgIcon', SvgIcon)

// ========== 3. 注册自定义指令 ==========
setupDirectives(app)

// ========== 4. 注册骨架占位组件 ==========
setupSkeleton(app)

// ========== 5. 挂载应用 ==========
app.mount('#app')
