/**
 * 看板 API 接口定义
 * 当前使用 Mock 数据，后续对接 Spring Boot 后端时替换此文件
 *
 * 接口约定：
 *   GET    /api/board/{templateType}/views          → 获取可用视图
 *   GET    /api/board/{templateType}/data?viewId=xx → 获取看板数据
 *   PATCH  /api/board/cards/{cardId}/move           → 移动卡片
 *   GET    /api/board/cards/{cardId}                 → 卡片详情
 *   PATCH  /api/board/cards/{cardId}                 → 更新卡片
 */

import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 10000,
})

// 请求/响应拦截器
http.interceptors.response.use(
  (res) => res.data,
  (err) => Promise.reject(err),
)

export default http
