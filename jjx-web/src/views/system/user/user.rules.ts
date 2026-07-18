import type { FormRules } from 'element-plus'

// 用户表单验证规则
export const formRules: FormRules = {
  userName: [
    { required: true, message: '用户名称不能为空', trigger: 'blur' },
    {
      min: 2,
      max: 20,
      message: '用户名称长度必须介于2到20之间',
      trigger: 'blur',
    },
  ],
  nickName: [{ required: true, message: '用户昵称不能为空', trigger: 'blur' }],
  password: [
    { required: true, message: '用户密码不能为空', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度必须介于5到20之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (value !== formDataPassword.value) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  email: [
    {
      type: 'email',
      message: '请输入正确的邮箱地址',
      trigger: ['blur', 'change'],
    },
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur',
    },
  ],
}

// 重置密码验证规则
export const resetPwdRules: FormRules = {
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度必须介于5到20之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (value !== resetPwdNewPassword.value) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// 用于在验证规则中引用密码值（运行时动态设置）
export const formDataPassword = { value: '' }
export const resetPwdNewPassword = { value: '' }
