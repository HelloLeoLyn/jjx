import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import FilmPrototype from './FilmPrototype.vue'

createApp(FilmPrototype).use(ElementPlus, { locale: zhCn }).mount('#app')
