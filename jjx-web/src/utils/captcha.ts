/**
 * 验证码生成工具
 * 生成图形验证码，支持数字和字母组合
 */

interface CaptchaOptions {
  width?: number
  height?: number
  length?: number
  type?: 'number' | 'letter' | 'mixed'
  fontSize?: number
  backgroundColor?: string
  fontColor?: string
  lineColor?: string
  dotColor?: string
}

interface CaptchaResult {
  code: string
  dataURL: string
}

/**
 * 生成随机验证码
 */
function generateCode(length: number = 4, type: 'number' | 'letter' | 'mixed' = 'mixed'): string {
  const numbers = '0123456789'
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
  const mixed = numbers + letters

  let chars = ''
  switch (type) {
    case 'number':
      chars = numbers
      break
    case 'letter':
      chars = letters
      break
    case 'mixed':
    default:
      chars = mixed
      break
  }

  let code = ''
  for (let i = 0; i < length; i++) {
    const randomIndex = Math.floor(Math.random() * chars.length)
    code += chars[randomIndex]
  }

  return code
}

/**
 * 生成随机颜色
 */
function generateRandomColor(min: number = 0, max: number = 255): string {
  const r = Math.floor(Math.random() * (max - min + 1)) + min
  const g = Math.floor(Math.random() * (max - min + 1)) + min
  const b = Math.floor(Math.random() * (max - min + 1)) + min
  return `rgb(${r}, ${g}, ${b})`
}

/**
 * 生成验证码图片
 */
export function generateCaptcha(options: CaptchaOptions = {}): CaptchaResult {
  const {
    width = 120,
    height = 40,
    length = 4,
    type = 'mixed',
    fontSize = 24,
    backgroundColor = '#f5f7fa',
    fontColor = '#333',
    lineColor = '#ccc',
    dotColor = '#999'
  } = options

  // 生成验证码
  const code = generateCode(length, type)

  // 创建canvas
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')

  if (!ctx) {
    throw new Error('Canvas context not supported')
  }

  // 绘制背景
  ctx.fillStyle = backgroundColor
  ctx.fillRect(0, 0, width, height)

  // 绘制干扰线
  for (let i = 0; i < 5; i++) {
    ctx.beginPath()
    ctx.moveTo(Math.random() * width, Math.random() * height)
    ctx.lineTo(Math.random() * width, Math.random() * height)
    ctx.strokeStyle = generateRandomColor(100, 200)
    ctx.lineWidth = 1
    ctx.stroke()
  }

  // 绘制干扰点
  for (let i = 0; i < 30; i++) {
    ctx.beginPath()
    ctx.arc(Math.random() * width, Math.random() * height, 1, 0, 2 * Math.PI)
    ctx.fillStyle = generateRandomColor(100, 200)
    ctx.fill()
  }

  // 绘制验证码文字
  const charWidth = width / (length + 1)
  for (let i = 0; i < code.length; i++) {
    const char = code[i]
    const x = charWidth * (i + 0.5)
    const y = height / 2

    // 随机旋转角度
    const rotate = (Math.random() - 0.5) * 0.5

    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(rotate)

    // 随机字体颜色
    ctx.fillStyle = generateRandomColor(50, 150)
    ctx.font = `bold ${fontSize}px Arial`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(char, 0, 0)

    ctx.restore()
  }

  // 返回base64图片
  const dataURL = canvas.toDataURL('image/png')

  return {
    code,
    dataURL
  }
}

/**
 * 验证验证码
 */
export function validateCaptcha(input: string, captchaCode: string, caseSensitive: boolean = false): boolean {
  if (!input || !captchaCode) {
    return false
  }

  if (caseSensitive) {
    return input === captchaCode
  } else {
    return input.toLowerCase() === captchaCode.toLowerCase()
  }
}

/**
 * 验证码管理器
 */
export class CaptchaManager {
  private currentCode: string = ''
  private currentImage: string = ''

  /**
   * 生成新的验证码
   */
  generate(options?: CaptchaOptions): { code: string; image: string } {
    const result = generateCaptcha(options)
    this.currentCode = result.code
    this.currentImage = result.dataURL
    return {
      code: this.currentCode,
      image: this.currentImage
    }
  }

  /**
   * 获取当前验证码
   */
  getCurrent(): { code: string; image: string } {
    return {
      code: this.currentCode,
      image: this.currentImage
    }
  }

  /**
   * 验证输入
   */
  validate(input: string, caseSensitive: boolean = false): boolean {
    return validateCaptcha(input, this.currentCode, caseSensitive)
  }

  /**
   * 清除验证码
   */
  clear(): void {
    this.currentCode = ''
    this.currentImage = ''
  }
}

// 导出默认实例
export const captchaManager = new CaptchaManager()

export default {
  generateCaptcha,
  validateCaptcha,
  CaptchaManager,
  captchaManager
}
