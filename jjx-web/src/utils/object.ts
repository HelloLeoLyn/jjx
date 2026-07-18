/**
 * 只将源对象中存在于目标对象键名的属性复制到目标对象
 * @param target 目标对象（会直接被修改）
 * @param source 源对象
 * @returns 修改后的目标对象
 */
export function assignExisting<T extends object, S extends object>(target: T, source: S): T {
  ;(Object.keys(target) as Array<keyof T>).forEach((key) => {
    if (key in source && (source as any)[key] !== undefined) {
      ;(target as any)[key] = (source as any)[key]
    }
  })
  return target
}

/**
 * 创建一个新对象，只包含源对象中存在于键列表的属性
 * @param source 源对象
 * @param keys 允许的键列表
 * @returns 新对象
 */
export function pick<T extends object, K extends keyof T>(
  source: T,
  keys: readonly K[]
): Pick<T, K> {
  const result = {} as Pick<T, K>
  keys.forEach((key) => {
    if (key in source && source[key] !== undefined) {
      result[key] = source[key]
    }
  })
  return result
}

/**
 * 从源对象中排除指定的键
 * @param source 源对象
 * @param keys 要排除的键列表
 * @returns 新对象
 */
export function omit<T extends object, K extends keyof T>(
  source: T,
  keys: readonly K[]
): Omit<T, K> {
  const result = { ...source }
  keys.forEach((key) => {
    delete result[key]
  })
  return result
}
