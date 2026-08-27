// composables/useScanner.ts
import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 全局扫码枪识别 composable（scan-execution-spec.md §3.2）
 *
 * 扫码枪 = HID 键盘设备，扫描结果 = 字符串 + 回车。
 * 前端全局监听 keydown：
 *  - 缓冲单字符，用时间差区分扫码（字符间隔 <50ms）与手工输入（>100ms）
 *  - Enter 触发时校验工单号格式，命中才回调 onScan，避免干扰正常输入
 *
 * @example
 * const { isWorkOrderNo } = useScanner({
 *   onScan: (code) => handleScanWorkOrder(code),
 *   enabled: () => route.name === 'ProductionOrder',
 * })
 */
export interface UseScannerOptions {
  /** 命中工单号后的回调（扫码枪扫出工单号） */
  onScan?: (code: string) => void
  /** 是否启用：boolean 或动态函数（按页面/路由开关，防误触），默认 true */
  enabled?: boolean | (() => boolean)
  /** 扫码间隔阈值 ms：间隔超过则视为手工输入清缓冲，默认 100 */
  interval?: number
  /** 工单号匹配正则，默认匹配 WPO 生产订单 / WO- 生产工单 */
  match?: RegExp
}

/** 工单号格式：WPO2608120001（生产订单）/ WO-WPO2608120001-01（生产工单） */
export const WORK_ORDER_NO_REGEX = /^(WPO\d{8,}|WO-[\w-]+-\d{1,2})$/

/** 校验字符串是否为工单号（供页面/路由复用） */
export function isWorkOrderNo(code: string): boolean {
  return WORK_ORDER_NO_REGEX.test(code)
}

export function useScanner(options: UseScannerOptions = {}) {
  let scanBuffer = ''
  let lastKeyTime = 0

  const isEnabled = (): boolean => {
    const e = options.enabled ?? true
    return typeof e === 'function' ? e() : e
  }

  const handleKeydown = (e: KeyboardEvent) => {
    if (!isEnabled()) return

    if (e.key === 'Enter') {
      const code = scanBuffer.trim()
      scanBuffer = ''
      const regex = options.match ?? WORK_ORDER_NO_REGEX
      if (regex.test(code)) {
        options.onScan?.(code)
      }
      return
    }

    if (e.key.length === 1) {
      const now = Date.now()
      const interval = options.interval ?? 100
      // 间隔 > 阈值 视为手工输入，清缓冲重来
      if (now - lastKeyTime > interval) {
        scanBuffer = ''
      }
      lastKeyTime = now
      scanBuffer += e.key
    }
  }

  onMounted(() => window.addEventListener('keydown', handleKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))

  return {
    /** 校验字符串是否为工单号 */
    isWorkOrderNo,
    /** 手动触发一次扫码处理（供测试/按钮兜底） */
    handleScan: (code: string) => {
      if (isWorkOrderNo(code)) {
        options.onScan?.(code)
      }
    },
  }
}
