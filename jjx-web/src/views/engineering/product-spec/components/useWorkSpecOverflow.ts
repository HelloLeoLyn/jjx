import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import { detailTextLines, type PaperTextLine } from './docset'

/** 对最终字体、实际单元格尺寸进行测量；缩放不参与scroll/client尺寸比较。 */
export function useWorkSpecOverflow(root: Ref<HTMLElement | undefined>, contentKey: () => string,
  report: (lines: PaperTextLine[]) => void) {
  let disposed = false
  let generation = 0
  let observer: ResizeObserver | undefined
  const overflows = (element: HTMLElement) => {
    const style = getComputedStyle(element)
    const clips = (value: string) => value === 'hidden' || value === 'clip'
    return (clips(style.overflowX) && element.scrollWidth > element.clientWidth + 1)
      || (clips(style.overflowY) && element.scrollHeight > element.clientHeight + 1)
  }

  async function measure() {
    const current = ++generation
    await nextTick()
    await document.fonts.ready
    if (disposed || current !== generation || !root.value?.clientWidth) return
    const lines: PaperTextLine[] = []
    for (const element of root.value.querySelectorAll<HTMLElement>('[data-overflow-title]')) {
      const overflow = overflows(element)
        || [...element.querySelectorAll<HTMLElement>('[data-overflow-part]')].some(overflows)
      element.toggleAttribute('data-overflowing', overflow)
      if (!overflow || element.dataset.overflowRepeat === 'true') continue
      lines.push({ text: element.dataset.overflowTitle + '（完整内容）' })
      const records = element.querySelectorAll<HTMLElement>('[data-overflow-record]')
      if (records.length) records.forEach(record => lines.push(...detailTextLines(record.textContent || '', record.style.color)))
      else lines.push(...detailTextLines(element.dataset.overflowText || element.textContent || '', element.style.color))
    }
    report(lines)
  }
  const schedule = () => { void measure() }
  watch(contentKey, schedule)
  onMounted(() => {
    observer = new ResizeObserver(schedule)
    if (root.value) observer.observe(root.value)
    document.fonts.addEventListener('loadingdone', schedule)
    schedule()
  })
  onBeforeUnmount(() => {
    disposed = true
    generation++
    observer?.disconnect()
    document.fonts.removeEventListener('loadingdone', schedule)
  })
  return { measure }
}
