/** 使用实际DOM布局顺序装入A4；返回的HTML仅来自已安全渲染的Vue只读纸张。 */
export function paginateWorkSpecFlow(source: HTMLElement): string[] {
  const doc = source.ownerDocument
  const host = doc.createElement('div')
  host.style.cssText = `position:absolute;left:-100000px;top:0;width:${source.offsetWidth}px;visibility:hidden;pointer-events:none`
  doc.body.append(host)
  const pages: HTMLElement[] = []
  let page: HTMLElement
  let footer: HTMLElement
  const sourceHeader = source.querySelector(':scope > header')!
  const sourceFooter = source.querySelector(':scope > footer')!
  const clone = <T extends Node>(node: T) => node.cloneNode(true) as T
  function nextPage() {
    if (pages.length >= 1000) throw new Error('作业规范页数过多，请检查内容或字号')
    page = source.cloneNode(false) as HTMLElement
    page.classList.remove('continuous')
    page.classList.add('flow-page')
    const heading = clone(sourceHeader)
    if (pages.length && !heading.querySelector('span')) {
      const continuation = doc.createElement('span'); continuation.textContent = '（续）'; heading.append(continuation)
    }
    footer = clone(sourceFooter) as HTMLElement
    const number = footer.lastElementChild as HTMLElement
    number.textContent = ''; number.setAttribute('data-work-spec-page', '')
    page.append(heading, footer); host.append(page); pages.push(page)
  }
  const fits = () => {
    // 结构图首次出现的页保留结构图标题，后续纯流程页才使用上线列标题。
    for (const table of page.querySelectorAll<HTMLTableElement>('table.flows')) {
      const original = source.querySelector<HTMLTableElement>('table.flows')!
      const merged = table.tHead?.querySelector('th.structure-title')
      if (table.querySelector('.structure-art')) {
        if (!merged && original.tHead) table.tHead?.replaceWith(clone(original.tHead))
      } else if (merged) {
        const upperHeading = original.tBodies[0]?.rows[7]?.querySelectorAll('th')
        if (upperHeading?.length === 3) merged.replaceWith(...Array.from(upperHeading, cell => clone(cell)))
      }
    }
    return page.scrollHeight <= page.clientHeight + 1
  }
  const hasContent = () => page.children.length > 2
  const insert = (node: HTMLElement) => page.insertBefore(node, footer)

  function tableFragment(table: HTMLTableElement) {
    const fragment = table.cloneNode(false) as HTMLTableElement
    for (const child of table.children) if (child.tagName === 'COLGROUP' || child.tagName === 'THEAD') fragment.append(clone(child))
    const body = doc.createElement('tbody'); fragment.append(body); insert(fragment)
    return { fragment, body }
  }

  function rowGroups(rows: HTMLTableRowElement[]) {
    const groups: HTMLTableRowElement[][] = []
    for (let start = 0; start < rows.length;) {
      let end = start
      for (let i = start; i <= end; i++) {
        for (const cell of rows[i].cells) end = Math.min(rows.length - 1, Math.max(end, i + cell.rowSpan - 1))
        if (Array.from(rows[i].cells).every(cell => cell.tagName === 'TH')) end = Math.min(rows.length - 1, Math.max(end, i + 1))
      }
      groups.push(rows.slice(start, end + 1)); start = end + 1
    }
    return groups
  }

  // 超高的带跨行结构图组退化为逐行单元格，保留结构图和列对齐，不裁切正文。
  function expandSpans(rows: HTMLTableRowElement[]) {
    const active: { column: number; until: number; cell: HTMLTableCellElement }[] = []
    return rows.map((row, rowIndex) => {
      const result = clone(row)
      const original = Array.from(result.cells); result.replaceChildren()
      let column = 0, index = 0
      while (index < original.length || active.some(span => span.until >= rowIndex && span.column >= column)) {
        const span = active.find(item => item.column === column && item.until >= rowIndex)
        if (span) {
          const empty = span.cell.cloneNode(false) as HTMLTableCellElement
          empty.removeAttribute('rowspan'); result.append(empty); column += empty.colSpan
        } else {
          const cell = original[index++]
          if (!cell) break
          if (cell.rowSpan > 1) active.push({ column, until: rowIndex + cell.rowSpan - 1, cell: clone(cell) })
          cell.removeAttribute('rowspan'); result.append(cell); column += cell.colSpan
        }
      }
      return result
    })
  }

  function addTable(table: HTMLTableElement) {
    let current = tableFragment(table)
    function advance() {
      if (!current.body.children.length) current.fragment.remove()
      nextPage(); current = tableFragment(table)
    }
    function splitRow(row: HTMLTableRowElement) {
      const selector = '.material-remarks-content,.material-name,.cell-single,.operation-remark,.symbol sub,.die-position'
      let remaining = Array.from(row.querySelectorAll<HTMLElement>(selector), node => Array.from(node.textContent || ''))
      if (!remaining.some(text => text.length)) throw new Error('单行内容超过A4可用高度，请减小该列字号')
      let first = true
      while (remaining.some(text => text.length)) {
        const copy = clone(row)
        if (!first) copy.querySelectorAll('.structure').forEach(cell => cell.replaceChildren())
        const targets = Array.from(copy.querySelectorAll<HTMLElement>(selector))
        current.body.append(copy)
        const maximum = Math.max(...remaining.map(text => text.length))
        const prefixLength = (text: string[], amount: number) => Math.ceil(text.length * amount / maximum)
        function setPrefix(amount: number) {
          targets.forEach((target, i) => { target.textContent = remaining[i].slice(0, prefixLength(remaining[i], amount)).join('') })
        }
        let low = 0, high = maximum
        while (low < high) {
          const mid = Math.ceil((low + high) / 2); setPrefix(mid)
          if (fits()) low = mid; else high = mid - 1
        }
        if (!low) {
          copy.remove()
          const occupied = current.body.children.length > 0 || page.children.length > 3
          if (!occupied) throw new Error('该内容最小片段无法放入A4，请减小字号')
          advance(); continue
        }
        setPrefix(low)
        first = false
        remaining = remaining.map(text => text.slice(prefixLength(text, low)))
        if (remaining.some(text => text.length)) advance()
      }
    }
    function addRows(rows: HTMLTableRowElement[]) {
      const copies = rows.map(row => clone(row)); current.body.append(...copies)
      if (fits()) return
      copies.forEach(row => row.remove())
      if (rows.length === 1 && rows[0].classList.contains('material-remarks')
        && rows[0].querySelector('.material-remarks-content')?.textContent) { splitRow(rows[0]); return }
      if (current.body.children.length || page.children.length > 3) {
        advance(); current.body.append(...copies)
        if (fits()) return
        copies.forEach(row => row.remove())
      }
      if (rows.length > 1) { expandSpans(rows).forEach(row => addRows([row])); return }
      splitRow(rows[0])
    }
    rowGroups(Array.from(table.tBodies[0]?.rows || [])).forEach(addRows)
    if (table.tFoot) addRows(Array.from(table.tFoot.rows))
    if (!current.body.children.length) current.fragment.remove()
  }

  function splitTextBlock(block: HTMLElement, selector: string) {
    const sourceText = block.querySelector<HTMLElement>(selector)!
    const records = sourceText.children.length ? Array.from(sourceText.children) as HTMLElement[] : [sourceText]
    let remaining = records.map(node => ({ node, text: Array.from(node.textContent || '') }))
    while (remaining.some(record => record.text.length)) {
      const copy = clone(block), target = copy.querySelector<HTMLElement>(selector)!
      insert(copy)
      function setPrefix(amount: number) {
        target.replaceChildren()
        for (const record of remaining) {
          if (!amount) break
          const part = record.node.cloneNode(false) as HTMLElement
          const count = Math.min(amount, record.text.length)
          part.textContent = record.text.slice(0, count).join(''); target.append(part); amount -= count
        }
      }
      let low = 0, high = remaining.reduce((sum, record) => sum + record.text.length, 0)
      while (low < high) {
        const mid = Math.ceil((low + high) / 2); setPrefix(mid)
        if (fits()) low = mid; else high = mid - 1
      }
      if (!low) {
        copy.remove()
        if (!hasContent()) throw new Error('变更内容最小片段无法放入A4，请减小字号')
        nextPage(); continue
      }
      setPrefix(low)
      let consumed = low
      remaining = remaining.map(record => {
        const count = Math.min(consumed, record.text.length); consumed -= count
        return { ...record, text: record.text.slice(count) }
      })
      if (remaining.some(record => record.text.length)) nextPage()
    }
  }
  try {
    nextPage()
    for (const child of Array.from(source.children) as HTMLElement[]) {
      if (child === sourceHeader || child === sourceFooter) continue
      if (child.tagName === 'TABLE') { addTable(child as HTMLTableElement); continue }
      const copy = clone(child); insert(copy)
      if (fits()) continue
      copy.remove()
      if (hasContent()) nextPage()
      insert(copy)
      if (fits()) continue
      copy.remove()
      if (child.querySelector('.changes-content')) splitTextBlock(child, '.changes-content')
      else throw new Error('内容块无法放入A4，请减小字号')
    }
    return pages.map(item => item.outerHTML)
  } finally { host.remove() }
}

/** 只替换分页器自行生成的页码槽，正文HTML由Vue渲染后克隆，不拼接业务文本。 */
export function workSpecFlowPageHtml(html: string, index: number, count: number): string {
  return html.replace(/(<span\b[^>]*\bdata-work-spec-page=""[^>]*>)<\/span>/, `$1${index + 1}／${count}页</span>`)
}
