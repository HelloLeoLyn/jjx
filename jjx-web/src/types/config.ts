/** 配置模块枚举：对应 sys_config.config_group */
export enum ConfigModule {
  PdfTemplate = 'pdf_template',
  ProductionConfig = 'production_config',
}

/** 后端返回的启用配置键值对：{ config_key: config_value } */
export type ConfigRecord = Record<string, string>

/** 模块加载状态 */
export type ConfigLoadState = 'idle' | 'loading' | 'loaded' | 'error'

/** pdf_template 分组 State（驼峰命名，show_* 转 boolean） */
export interface PdfTemplateState {
  /** 公司名称（PDF 抬头） */
  companyName: string
  /** 公司地址 */
  companyAddress: string
  /** 联系电话 */
  companyPhone: string
  /** 邮箱 */
  companyEmail: string
  /** 主题色（十六进制） */
  themeColor: string
  /** 是否显示公司抬头 */
  showHeader: boolean
  /** 是否显示页脚 */
  showFooter: boolean
  /** 签名栏 1 标题 */
  signatureLabel1: string
  /** 签名栏 2 标题 */
  signatureLabel2: string
  /** 签名栏 3 标题 */
  signatureLabel3: string
  /** 税号 */
  companyTaxNo: string
  /** 开户行 */
  companyBank: string
  /** 银行账号 */
  companyAccount: string
  /** 法人代表 */
  companyLegal: string
  /** 公司官网 */
  companyWebsite: string
  /** Logo 地址 */
  companyLogo: string
}

/** production_config 分组 State（数值字段统一 number） */
export interface ProductionConfigState {
  /** 默认生产交期（天） */
  defaultLeadDays: number
  /** 排产提前天数 */
  planAdvanceDays: number
  /** 默认班次时长（小时） */
  defaultShiftHours: number
  /** 交期超期预警天数 */
  overdueWarnDays: number
  /** 生产看板刷新间隔（秒） */
  boardRefreshSeconds: number
  /** 默认质检抽检比例（%） */
  qualitySamplingRate: number
  /** 合格率预警阈值（%） */
  qualityPassRateThreshold: number
  /** 设备稼动率目标（%） */
  deviceUtilizationTarget: number
}
