import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import router from '@/router'
import { useUserStore } from '@/store/modules/user'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8',
  },
})

// 扩展 axios 类型声明
declare module 'axios' {
  export interface AxiosInstance {
    request<T = any>(config: AxiosRequestConfig): Promise<T>
    get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
    delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
    head<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
    post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
    put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
    patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  }
}

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers = config.headers || {}
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // 如果是 blob 类型响应，直接返回原始响应数据
    if (response.config.responseType === 'blob') {
      return res
    }

    // 如果返回的状态码不是200，则判断为错误
    if (res.code !== 200) {
      ElMessage({
        message: res.msg || 'Error',
        type: 'error',
        duration: 5 * 1000,
      })

      // 401: 未登录
      if (res.code === 401) {
        // 使用 userStore 的 resetToken 方法，确保状态一致性
        const userStore = useUserStore()
        userStore.resetToken()
        router.push('/login')
      }

      return Promise.reject(new Error(res.msg || 'Error'))
    } else {
      return res
    }
  },
  (error) => {
    let message = error.message
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求错误'
          break
        case 401:
          message = '未授权，请重新登录'
          // 使用 userStore 的 resetToken 方法，确保状态一致性
          const userStore = useUserStore()
          userStore.resetToken()
          router.push('/login')
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求地址出错'
          break
        case 408:
          message = '请求超时'
          break
        case 500:
          message = '服务器内部错误'
          break
        case 501:
          message = '服务未实现'
          break
        case 502:
          message = '网关错误'
          break
        case 503:
          message = '服务不可用'
          break
        case 504:
          message = '网关超时'
          break
        case 505:
          message = 'HTTP版本不受支持'
          break
        default:
          message = '网络错误'
      }
    }

    ElMessage({
      message,
      type: 'error',
      duration: 5 * 1000,
    })

    return Promise.reject(error)
  }
)

export default service
