/**
 * 去除路径的最后一项
 * @param {string} path - 路径字符串，如 'sys:user:add'
 * @param {string} separator - 分隔符，默认 ':'
 * @returns {string} - 去除最后一项后的路径
 */
export const removeLastSegment = (path: string, separator = ':') => {
  if (!path) return ''
  const lastIndex = path.lastIndexOf(separator)
  return lastIndex === -1 ? '' : path.substring(0, lastIndex)
}
