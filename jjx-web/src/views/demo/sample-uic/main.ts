import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'virtual:svg-icons-register'
import SampleUicPreview from './SampleUicPreview.vue'

createApp(SampleUicPreview).use(ElementPlus, { locale: zhCn }).mount('#app')
