// composables/useProductCode.ts
// 产品编码生成统一逻辑（2026-08-12）
// 格式：客户简称(1~3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)，如 JST001MEOL
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

export interface UseProductCodeOptions {
  /** 取客户简称（响应式 getter，页面提供） */
  customerShort: () => string
  /** 取流水号（默认调统一接口 /product/code/next-serial） */
  fetchSerial?: (short: string) => Promise<string>
  /** 输出模式：code=只返回编码字符串（默认），object=返回完整对象（含面板/线路参数） */
  output?: 'code' | 'object'
  /** 生成成功回调（参数类型随 output 变化） */
  onResult?: (data: string | ProductCodeResult) => void
  /** 校验/生成失败回调（缺段提示、无客户等） */
  onError?: (msg: string) => void
}

/** 默认取流水号：统一接口（兼容1-3位简称） */
export async function defaultFetchSerial(short: string): Promise<string> {
  const { default: request } = await import('@/utils/request')
  const res: any = await request.get('/product/code/next-serial', {
    params: { customerShort: short },
  })
  return res?.data || '001'
}

/** 拼接 + 校验（1-3位简称放行），缺段返回 null */
export function composeProductCode(
  customerShort: string,
  state: ProductCodeState,
): ProductCodeResult | null {
  const short = (customerShort || '').trim()
  const serialNo = (state.serialNo || '').trim()
  const panelPart = `${state.panelType || ''}${state.panelFeature || ''}`
  const circuitPart = `${state.circuitType || ''}${state.circuitFeature || ''}`

  if (short.length < 1 || short.length > 3) return null
  if (serialNo.length !== 3) return null
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

/** 生成失败/缺段时的提示文案 */
export function missingHint(customerShort: string, state: ProductCodeState): string {
  const short = (customerShort || '').trim()
  if (short.length < 1 || short.length > 3) return '请先选择客户（客户简称需1~3位）'
  if (!(state.serialNo || '').trim()) return '请点击生成编码获取流水号'
  if (!state.panelType || !state.panelFeature) return '请选择面板结构/特征'
  if (!state.circuitType || !state.circuitFeature) return '请选择线路类型/特征'
  return '编码格式：客户简称(1~3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)'
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
      state.value.serialNo = String(no || '001').padStart(3, '0').slice(0, 3)
      return emitResult()
    } catch (e: any) {
      options.onError?.(e?.message || '流水号获取失败')
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
    missingHint,
  }
}
