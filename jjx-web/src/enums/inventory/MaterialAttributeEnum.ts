import { createNamedEnum } from '../base'

/** 属性类型控制渲染和服务端校验，不是可任意新增的业务字典。 */
export const MaterialAttributeTypeEnum = createNamedEnum(
  {
    STRING: { value: 'string', label: '文本', tagProps: { type: 'info' } },
    INTEGER: { value: 'integer', label: '整数', tagProps: { type: 'info' } },
    DECIMAL: { value: 'decimal', label: '小数', tagProps: { type: 'info' } },
    BOOLEAN: { value: 'boolean', label: '是或否', tagProps: { type: 'info' } },
    ENUM: { value: 'enum', label: '单选', tagProps: { type: 'info' } },
    MULTIENUM: { value: 'multienum', label: '多选', tagProps: { type: 'info' } },
  },
  { type: 'info' }
)
