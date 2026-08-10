import { createPinia } from 'pinia'

// 创建 pinia 实例
const pinia = createPinia()

export default pinia

// 导出所有 store
export * from './modules/user'
export * from './modules/permission'
export * from './modules/sampleTransfer'
