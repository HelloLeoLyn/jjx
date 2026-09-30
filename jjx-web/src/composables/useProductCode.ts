// composables/useProductCode.ts
// 产品编码生成统一逻辑（2026-08-12；2026-09-30 dev-20260930-011 加 4 位序号与反解）
// 格式：客户简称(1~3位) + 序号(默认3位，超过999给4位) + 面板结构(2位) + 线路结构(2位)，如 JST001MEOO / JST1000MEOO
// 统一校验（简称1-3位放行），输出可配置：'code' 只返回编码字符串 / 'object' 返回完整参数对象
import { ref } from 'vue'

/** 编码构成选项（三处页面统一，来自既有实现） */
export const PANEL_TYPE_OPTIONS = [
  { label: '有面板有线路', value: 'M' },
  { label: '仅有线路', value: 'S' },
  { label: '仅有面板', value: 'P' },
]
export const PANEL_FEATURE_OPTIONS = [
  { label: '面板有凹凸', value: 'E' },
  { label: '面板有窗口', value: 'W' },
  { label: '有窗口也有凹凸', value: 'H' },
  { label: '无', value: 'O' },
]
export const CIRCUIT_TYPE_OPTIONS = [
  { label: '无(印银平key)', value: 'O' },
  { label: '有金属弹片', value: 'M' },
  { label: '线路有凹凸', value: 'P' },
]
export const CIRCUIT_FEATURE_OPTIONS = [
  { label: '无', value: 'O' },
  { label: '有发光二极体', value: 'L' },
  { label: '有连接器', value: 'C' },
  { label: '有连接器及发光二极体', value: 'H' },
]

/** 结构位合法值（拼码与反解共用同一份口径） */
export const PANEL_TYPE_VALUES = 'MSP'
export const PANEL_FEATURE_VALUES = 'EWHO'
export const CIRCUIT_TYPE_VALUES = 'OMP'
export const CIRCUIT_FEATURE_VALUES = 'OLCH'

/** 编码构成状态（可 v-model 双向绑定，编辑回显时直接赋值） */
export interface ProductCodeState {
  serialNo: string
  panelType: string
  panelFeature: string
  circuitType: string
  circuitFeature: string
}

/** 完整编码结果对象（output='object' 时回调返回） */
export interface ProductCodeResult extends ProductCodeState {
  productCode: string
  customerShort: string
  panelPart: string
  circuitPart: string
}

/** 反解结果：构成要素 + 客户简称（编辑回显用） */
export interface ParsedProductCode extends ProductCodeState {
  productCode: string
  customerShort: string
}

export interface UseProductCodeOptions {
  /** 取客户简称（响应式 getter，页面提供） */
  customerShort: () => string
  /** 取序号（默认调统一接口 /product/code/next-serial） */
  fetchSerial?: (short: string) => Promise<string>
  /** 输出模式：code=只返回编码字符串（默认），object=返回完整对象（含面板/线路参数） */
  output?: 'code' | 'object'
  /** 生成成功回调（参数类型随 output 变化） */
  onResult?: (data: string | ProductCodeResult) => void
  /** 校验/生成失败回调（缺段提示、无客户等） */
  onError?: (msg: string) => void
}

/**
 * 序号规范化（2026-09-30 dev-20260930-011）
 * 只允许数字：1~3 位补零到 3 位（9 → 009），超过 999 给 4 位原样（1000 不补不回绕）；非法返回空串。
 */
export function normalizeSerial(raw?: string | number | null): string {
  const digits = String(raw ?? '').trim()
  if (!/^\d{1,4}$/.test(digits)) return ''
  return digits.length <= 3 ? digits.padStart(3, '0') : digits
}

/** 默认取序号：统一接口（兼容1-3位简称） */
export async function defaultFetchSerial(short: string): Promise<string> {
  const { default: request } = await import('@/utils/request')
  const res: any = await request.get('/product/code/next-serial', {
    params: { customerShort: short },
  })
  return res?.data || '001'
}

/** 拼接 + 校验（1-3位简称放行，序号 1~4 位自动补零），缺段返回 null */
export function composeProductCode(
  customerShort: string,
  state: ProductCodeState,
): ProductCodeResult | null {
  const short = (customerShort || '').trim()
  const serialNo = normalizeSerial(state.serialNo)
  const panelPart = `${state.panelType || ''}${state.panelFeature || ''}`
  const circuitPart = `${state.circuitType || ''}${state.circuitFeature || ''}`

  if (short.length < 1 || short.length > 3) return null
  if (!serialNo) return null
  if (panelPart.length !== 2) return null
  if (circuitPart.length !== 2) return null

  return {
    productCode: `${short}${serialNo}${panelPart}${circuitPart}`,
    customerShort: short,
    serialNo,
    panelType: state.panelType,
    panelFeature: state.panelFeature,
    circuitType: state.circuitType,
    circuitFeature: state.circuitFeature,
    panelPart,
    circuitPart,
  }
}

/**
 * 从产品编码反解构成要素（编辑回显，2026-09-30 dev-20260930-011）
 * 右起 4 位 = 面板结构/面板特征/线路类型/线路特征；其左侧连续数字段 = 序号（3~4 位）；
 * 再左侧 = 客户简称（1~3 位字母，可带一个 “-”，兼容历史档案编码 JTT-092MHMO）。
 * 4 位结构位只要有一位不在合法集合里就返回 null —— 调用方留空让人重选，不猜。
 */
export function parseProductCode(code?: string | null): ParsedProductCode | null {
  const c = (code || '').trim()
  const matched = /^([A-Za-z]{1,3})-?(\d{3,4})([A-Za-z]{4})$/.exec(c)
  if (!matched) return null
  const [, short, serial, tail] = matched
  const [panelType, panelFeature, circuitType, circuitFeature] = tail.split('')
  const pick = (value: string, allowed: string) => (allowed.includes(value) ? value : '')
  const state: ParsedProductCode = {
    customerShort: short,
    productCode: c,
    serialNo: normalizeSerial(serial),
    panelType: pick(panelType, PANEL_TYPE_VALUES),
    panelFeature: pick(panelFeature, PANEL_FEATURE_VALUES),
    circuitType: pick(circuitType, CIRCUIT_TYPE_VALUES),
    circuitFeature: pick(circuitFeature, CIRCUIT_FEATURE_VALUES),
  }
  if (!state.serialNo || !state.panelType || !state.panelFeature || !state.circuitType || !state.circuitFeature) {
    return null
  }
  return state
}

/** 生成失败/缺段时的提示文案 */
export function missingHint(customerShort: string, state: ProductCodeState): string {
  const short = (customerShort || '').trim()
  if (short.length < 1 || short.length > 3) return '请先选择客户（客户简称需1~3位）'
  if (!normalizeSerial(state.serialNo)) return '请点击「取号」获取序号，或手填 1~4 位数字'
  if (!state.panelType || !state.panelFeature) return '请选择面板结构/特征'
  if (!state.circuitType || !state.circuitFeature) return '请选择线路类型/特征'
  return '编码格式：客户简称(1~3位) + 序号(3~4位) + 面板结构(2位) + 线路结构(2位)'
}

/**
 * 统一产品编码生成器（composable 版）
 * 适合需要自定义布局/嵌入既有表单的场景；标准布局直接用 ProductCodeGenerator 组件
 */
export function useProductCode(options: UseProductCodeOptions) {
  const generating = ref(false)
  const state = ref<ProductCodeState>({
    serialNo: '',
    panelType: '',
    panelFeature: '',
    circuitType: '',
    circuitFeature: '',
  })

  /** 触发一次取号 + 拼接（选客户/切类型时调用） */
  async function generate(): Promise<ProductCodeResult | null> {
    const short = (options.customerShort() || '').trim()
    if (short.length < 1 || short.length > 3) {
      options.onError?.(`客户简称需为1~3位（当前：${short || '未选择客户'}）`)
      return null
    }
    generating.value = true
    try {
      const fetchSerial = options.fetchSerial ?? defaultFetchSerial
      const no = await fetchSerial(short)
      state.value.serialNo = normalizeSerial(no) || '001'
      return emitResult()
    } catch (e: any) {
      options.onError?.(e?.message || '序号获取失败')
      return null
    } finally {
      generating.value = false
    }
  }

  /** 按当前状态拼接并回调（下拉变化时调用） */
  function emitResult(): ProductCodeResult | null {
    const result = composeProductCode(options.customerShort(), state.value)
    if (!result) {
      options.onError?.(missingHint(options.customerShort(), state.value))
      return null
    }
    if (options.output === 'object') {
      options.onResult?.(result)
    } else {
      options.onResult?.(result.productCode)
    }
    return result
  }

  return {
    state,
    generating,
    generate,
    emitResult,
    compose: composeProductCode,
    parse: parseProductCode,
    missingHint,
  }
}
