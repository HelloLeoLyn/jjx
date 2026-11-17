import { describe, expect, it } from 'vitest'
import { buildWorkSpecIssues, workSpecIssueMessage } from '../workSpecIssues'

const context = { productId: 42, productCode: 'P42', productName: '面板', bom: { bomName: '面板BOM', bomVersion: 'V2' }, routing: { routingName: '印刷路线', routingVersion: 'R3' } }
describe('作业规范问题归并与对象上下文', () => {
  it('只合并相同原因，保留不同对象ID及不同原因', () => {
    const issues = buildWorkSpecIssues(['附件失效 ID=1', ' 附件失效 ID=1 ', '附件失效 ID=2', '附件哈希不符 ID=1'], context)
    expect(issues).toHaveLength(3)
    expect(issues[0].count).toBe(2)
    expect(issues[0].object).toContain('P42')
  })
  it('保留BOM及路线名称与版本，不根据错误文字切换产品', () => {
    const issues = buildWorkSpecIssues(['BOM 不存在 id=999', '工艺路线不存在'], context)
    expect(issues[0].object).toContain('面板BOM · V2')
    expect(issues[1].object).toContain('印刷路线 · R3')
    expect(issues.every((issue) => issue.object.startsWith('P42'))).toBe(true)
  })
  it('负责人、草稿冲突、结构图分别给原流程处理指引', () => {
    const issues = buildWorkSpecIssues(['只有当前制定负责人可以定版', 'revision 冲突', '结构图失效'], context)
    expect(issues.map((issue) => issue.target)).toEqual(['owner', 'conflict', 'structure'])
    expect(issues[1].next).toContain('比较并重新应用')
  })
  it('附件ID精确匹配当前清单，显示文件名称和版本，不把其它文件误认成失效对象', () => {
    const files = [{ attachmentId: 8, fileName: '结构图.pdf', version: 'A2' }]
    const [known, missing] = buildWorkSpecIssues(['附件不存在（id=8）', '附件不存在（id=9）'], { ...context, files })
    expect(known.object).toContain('结构图.pdf · A2')
    expect(missing.object).not.toContain('结构图.pdf')
  })
  it('权限、网络与未知错误均保留明确失败信息', () => {
    expect(workSpecIssueMessage({ response: { status: 403 } })).toContain('HTTP 403')
    expect(workSpecIssueMessage(new Error('网络失败'))).toBe('网络失败')
    expect(workSpecIssueMessage(null)).toContain('检查失败')
    expect(buildWorkSpecIssues(['', '  '], context)).toEqual([])
  })
})
