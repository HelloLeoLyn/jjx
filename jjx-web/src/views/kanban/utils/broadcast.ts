/**
 * 语音播报服务
 * 使用浏览器 Web Speech API，零依赖，纯前端
 */

export interface BroadcastMessage {
  text: string
  /** 播报优先级: urgent 会打断当前播报 */
  level?: 'info' | 'warning' | 'urgent'
}

class BroadcastService {
  private enabled = true
  private queue: BroadcastMessage[] = []
  private speaking = false
  private currentUtterance: SpeechSynthesisUtterance | null = null

  /** 初始化 */
  init() {
    if (!('speechSynthesis' in window)) {
      console.warn('当前浏览器不支持 SpeechSynthesis，语音播报不可用')
      this.enabled = false
      return
    }

    // 监听播报完成，播放下一条
    window.speechSynthesis.onend = () => {
      this.speaking = false
      this.processQueue()
    }
  }

  /** 开启/关闭 */
  setEnabled(on: boolean) {
    this.enabled = on
    if (!on) {
      this.cancel()
    }
  }

  /** 立即播报一条消息 */
  say(text: string, level: BroadcastMessage['level'] = 'info') {
    if (!this.enabled || !('speechSynthesis' in window)) return

    const msg: BroadcastMessage = { text, level }

    // urgent 级别：清空队列，立即播报
    if (level === 'urgent') {
      this.cancel()
      this.queue.unshift(msg)
    } else {
      this.queue.push(msg)
    }

    this.processQueue()
  }

  /** 播报卡片移动 */
  announceCardMove(cardTitle: string, fromCol: string, toCol: string, level?: BroadcastMessage['level']) {
    this.say(`${cardTitle}，已从 ${fromCol} 移到 ${toCol}`, level)
  }

  /** 播报逾期提醒 */
  announceOverdue(cardTitle: string) {
    this.say(`注意：${cardTitle} 已逾期，请尽快处理`, 'urgent')
  }

  /** 播报新建卡片 */
  announceNewCard(cardTitle: string, column: string) {
    this.say(`新任务：${cardTitle}，已添加到 ${column}`)
  }

  /** 播报紧急任务 */
  announceEmergency(cardTitle: string, reason: string) {
    this.say(`紧急任务：${cardTitle}，原因：${reason}`, 'urgent')
  }

  private cancel() {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
    this.speaking = false
    this.queue = []
  }

  private processQueue() {
    if (this.speaking || this.queue.length === 0 || !this.enabled) return

    this.speaking = true
    const msg = this.queue.shift()!
    const utterance = new SpeechSynthesisUtterance(msg.text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1.0
    utterance.pitch = 1.0
    utterance.volume = 1.0

    // 尝试使用中文语音
    const voices = window.speechSynthesis.getVoices()
    const zhVoice = voices.find(v => v.lang.startsWith('zh'))
    if (zhVoice) {
      utterance.voice = zhVoice
    }

    this.currentUtterance = utterance
    window.speechSynthesis.speak(utterance)
  }
}

/** 全局单例 */
export const broadcast = new BroadcastService()
