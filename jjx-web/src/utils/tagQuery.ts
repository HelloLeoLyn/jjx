/**
 * 标签查询参数规范化（通用，dev-20260912-007）
 *
 * Spring 以逗号串绑定 List<Long>：把 tagIds 数组压成 "1,2,3"，
 * 避免 axios 默认序列化成 tagIds[]=1&tagIds[]=2 导致后端绑定失败。
 * 各模块按标签筛选的接口统一调用本函数。
 */
export function normalizeTagParams<T extends Record<string, any> | undefined>(params: T): T {
  if (params && Array.isArray((params as any).tagIds)) {
    const ids = (params as any).tagIds as number[]
    return { ...params, tagIds: ids.length ? ids.join(',') : undefined } as T
  }
  return params
}
