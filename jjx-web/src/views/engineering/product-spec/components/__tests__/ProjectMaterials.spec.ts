import { describe, expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: { post: vi.fn(), put: vi.fn(), get: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: vi.fn() } }))
vi.mock('../useWorkSpecOverflow', () => ({ useWorkSpecOverflow: () => ({ measure: vi.fn() }) }))
import { mount } from '@vue/test-utils'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import {
  normalizeWorkSpec,
  nestProjectMaterials,
  flattenProjectMaterials,
  checkProjectMaterialsBeforeSave,
  workSpecContent,
  acknowledgeProjectMaterials,
} from '@/api/product/workSpec'
import {
  groupProjectMaterials,
  workSpecFlowSource,
  makePages,
  projectMaterialFromBom,
  paginateMeasuredWorkSpec,
  type DocsetData,
} from '../docset'
import { defaultWorkSpecFontSettings } from '../workSpecFont'
import request from '@/utils/request'
import { ElMessageBox } from 'element-plus'
const material = (processName: string, materialName = '材料') => ({
  materialCode: materialName,
  materialName,
  processName,
  specification: '100*200mm',
  quantity: 1,
  unit: 'PCS',
  dieLocation: materialName + '位置',
  sortOrder: 0,
})
function data(): DocsetData {
  return {
    product: {},
    bom: { items: [material('BOM项目', 'BOM材料')] },
    routing: { items: [] },
    files: [],
    warnings: [],
    workSpec: normalizeWorkSpec({
      projectMaterials: [
        material('面板', '面材'),
        material('面板', '胶'),
        material('线路', '银浆'),
        material('面板', '覆膜'),
        material('', '空1'),
        material('', '空2'),
      ],
    }),
  }
}
describe('项目材料独立清单与B版打印', () => {
  it('连续项目合并，隔行同名为新组，空项目不合并', () => {
    const rows = groupProjectMaterials(flattenProjectMaterials(data().workSpec.projectMaterials!))
    expect(rows.map((row) => row.projectNo)).toEqual([1, 1, 2, 3, 4, 5])
    expect(rows.map((row) => row.projectSpan)).toEqual([2, 0, 1, 1, 1, 1])
  })
  it('旧平铺读取无损，新保存只写项目组，三个材料只打印一个项目图标', () => {
    const rows = [
      material('面板', '面1'),
      material('面板', '面2'),
      material('面板', '面3'),
      material('线路', '线1'),
      material('面板', '面4'),
      material('面板', '面5'),
    ]
    const before = JSON.stringify(rows)
    const spec = normalizeWorkSpec({ projectMaterials: rows })
    const groups = workSpecContent(spec).projectMaterials!
    expect(groups.map((group) => group.materials.length)).toEqual([3, 1, 2])
    expect(groups.map((group) => group.groupNo)).toEqual([1, 1, 2])
    expect(nestProjectMaterials([groups[0], groups[0]])[0].materials).toHaveLength(6)
    expect(nestProjectMaterials(groups)).toEqual(groups)
    expect(new Set(groups.map((group) => group.groupKey)).size).toBe(3)
    expect(groups[0].materials[0]).not.toHaveProperty('processName')
    expect(groups[0].materials[0]).not.toHaveProperty('processId')
    expect(flattenProjectMaterials(groups).map((row) => row.materialName)).toEqual(
      rows.map((row) => row.materialName)
    )
    expect(JSON.stringify(rows)).toBe(before)
    const source = data()
    source.workSpec = normalizeWorkSpec({ projectMaterials: [groups[0]] })
    source.workSpec.projectMaterials![0].projectIcon = 'panel'
    const wrapper = mount(WorkSpecPaper, {
      props: {
        data: source,
        page: workSpecFlowSource(source),
        specTemplate: 'B',
        pageIndex: 0,
        pageCount: 1,
        fontSettings: defaultWorkSpecFontSettings(),
      },
      global: { stubs: { SvgIcon: { template: '<i class="test-icon" />' } } },
    })
    expect(
      wrapper
        .findAll('.material-name')
        .map((node) => node.text())
        .filter(Boolean)
    ).toEqual(['面1', '面2', '面3'])
    expect(wrapper.findAll('.test-icon')).toHaveLength(1)
    expect(wrapper.get('td.material-number[rowspan="3"]').text()).toBe('1')
    wrapper.unmount()
  })
  it('B读独立材料/顺序/刀模，A仍读BOM；历史缺失时回退', () => {
    const source = data()
    expect(workSpecFlowSource(source).rows?.map((row) => row.materialName)).toEqual([
      '面材',
      '胶',
      '银浆',
      '覆膜',
      '空1',
      '空2',
    ])
    expect(workSpecFlowSource(source).diePositionRows?.[0]).toBe('面材位置')
    expect(makePages(source, ['spec'], [], {}, {}, 'A')[0].rows?.[0].materialName).toBe('BOM材料')
    source.workSpec.projectMaterials = undefined
    expect(workSpecFlowSource(source).rows?.[0].materialName).toBe('BOM材料')
    source.workSpec.projectMaterials = []
    expect(workSpecFlowSource(source).rows).toHaveLength(0)
  })
  it('保留旧表头，图标+每组单序号放序号列，无图标回退名称', () => {
    const source = data()
    source.workSpec.projectMaterials![0].projectIcon = 'panel'
    const wrapper = mount(WorkSpecPaper, {
      props: {
        data: source,
        page: workSpecFlowSource(source),
        specTemplate: 'B',
        pageIndex: 0,
        pageCount: 1,
        fontSettings: defaultWorkSpecFontSettings(),
      },
      global: {
        stubs: { SvgIcon: { props: ['name'], template: '<i class="test-icon">{{name}}</i>' } },
      },
    })
    const table = wrapper.get('.materials')
    expect(table.get('thead').text()).toContain('规格及模数')
    expect(table.get('thead').text()).not.toContain('用量')
    expect(table.get('thead').text()).not.toContain('单位')
    expect(table.findAll('td.material-number[rowspan="2"]')).toHaveLength(1)
    expect(table.findAll('td.material-project[rowspan="2"]')).toHaveLength(1)
    expect(table.findAll('.test-icon')).toHaveLength(1)
    expect(
      table
        .findAll('td.material-number')
        .map((node) => node.text())
        .filter(Boolean)
    ).toEqual(['1', '2', '3', '4', '5'])
    expect(table.findAll('sub')).toHaveLength(0)
    expect(table.text()).toContain('线路')
    wrapper.unmount()
  })
  it('从BOM复制一次，编辑不会改BOM', () => {
    const bom = {
      materialCode: 'M',
      materialName: '面材',
      specification: '20mm',
      quantity: 2,
      unit: 'PCS',
      processName: '面板',
    }
    const row = projectMaterialFromBom(bom, 0)
    row.quantity = 9
    row.specification = '30mm'
    expect(bom.quantity).toBe(2)
    expect(bom.specification).toBe('20mm')
    expect(
      normalizeWorkSpec({ projectMaterials: [row] }).projectMaterials![0].materials[0].sourceBom
        ?.quantity
    ).toBe('2')
  })
  it('保存缺项先确认，取消不通过；硬错误不能确认绕过', async () => {
    vi.mocked(request.post).mockResolvedValue({
      data: { errors: [], warnings: ['BOM 有 1 项未纳入：底胶'], token: 'checked' },
    } as any)
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as any)
    const value = data().workSpec
    await checkProjectMaterialsBeforeSave(1, value)
    expect(value.projectMaterialsAcknowledgement).toBe('checked')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await expect(checkProjectMaterialsBeforeSave(1, value)).rejects.toBe('cancel')
    vi.mocked(request.post).mockResolvedValueOnce({
      data: { errors: ['第 2 行BOM已移除'], warnings: [] },
    } as any)
    await expect(checkProjectMaterialsBeforeSave(1, value)).rejects.toThrow('第 2 行')
  })
  it('打印提醒确认走日志入口，取消不留确认日志', async () => {
    const value = normalizeWorkSpec({
      revision: 'r',
      projectMaterialsCheck: { errors: [], warnings: ['缺少底胶'], token: 't' },
    })
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm' as any)
    vi.mocked(request.post).mockResolvedValueOnce({ data: { revision: 'r2' } } as any)
    expect((await acknowledgeProjectMaterials(1, value, '打印')).revision).toBe('r2')
    expect(request.post).toHaveBeenLastCalledWith(
      '/product/1/work-spec/project-materials/acknowledge',
      { revision: 'r', token: 't', action: '打印' }
    )
    vi.mocked(request.post).mockClear()
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await expect(acknowledgeProjectMaterials(1, value, '打印')).rejects.toBe('cancel')
    expect(request.post).not.toHaveBeenCalled()
  })

  it('params人工文本按原文打印并保留换行，恢复自动后使用计算值；A不使用覆盖文本', () => {
    const source = data()
    const row = source.workSpec.projectMaterials![0].materials[0]
    row.params = '人工规格\n<样张> & 2模'
    const paper = () =>
      mount(WorkSpecPaper, {
        props: {
          data: source,
          page: workSpecFlowSource(source),
          specTemplate: 'B',
          pageIndex: 0,
          pageCount: 1,
          fontSettings: defaultWorkSpecFontSettings(),
        },
        global: { stubs: { SvgIcon: true } },
      })
    let wrapper = paper()
    expect(wrapper.find('.manual-params').text()).toBe(row.params)
    expect(wrapper.find('.manual-params').html()).toContain('&lt;样张&gt;')
    wrapper.unmount()
    row.params = ''
    row.moduleQty = 3
    wrapper = paper()
    expect(wrapper.text()).toContain('100*200mm=3PCS')
    expect(wrapper.find('.manual-params').exists()).toBe(false)
    wrapper.unmount()
    const a = mount(WorkSpecPaper, {
      props: {
        data: source,
        page: makePages(source, ['spec'], [], {}, {}, 'A')[0],
        specTemplate: 'A',
        pageIndex: 0,
        pageCount: 1,
        fontSettings: defaultWorkSpecFontSettings(),
      },
      global: { stubs: { SvgIcon: true } },
    })
    expect(a.text()).toContain('BOM材料')
    expect(a.text()).not.toContain('人工规格')
    a.unmount()
  })
  it('连续项目跨页后保持同组序号、材料和刀模列完整', () => {
    const source = data()
    source.workSpec.projectMaterials = nestProjectMaterials(
      Array.from({ length: 18 }, (_, i) => material(i < 10 ? '面板' : '线路', '材料' + i))
    )
    const wrapper = mount(WorkSpecPaper, {
      props: {
        data: source,
        page: workSpecFlowSource(source),
        specTemplate: 'B',
        pageIndex: 0,
        pageCount: 1,
        fontSettings: defaultWorkSpecFontSettings(),
      },
      global: { stubs: { SvgIcon: true } },
    })
    const height = vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(100)
    const scroll = vi
      .spyOn(HTMLElement.prototype, 'scrollHeight', 'get')
      .mockImplementation(function (this: HTMLElement) {
        return this.classList.contains('flow-page')
          ? this.querySelectorAll('table.materials tbody tr').length * 12
          : 0
      })
    try {
      const html = paginateMeasuredWorkSpec(wrapper.element as HTMLElement)
      const doc = document.createElement('div')
      doc.innerHTML = html.join('')
      const tables = Array.from(doc.querySelectorAll('table.materials'))
      expect(tables.length).toBeGreaterThan(1)
      expect(
        tables.map((table) => table.querySelector('td.material-number')?.textContent?.trim())
      ).toEqual(['1', '1', '2'])
      for (const table of tables) {
        const keys = new Set(
          Array.from(
            table.querySelectorAll('tbody tr[data-material-group]'),
            (row) => (row as HTMLElement).dataset.materialGroup
          )
        )
        const numbers = Array.from(
          table.querySelectorAll<HTMLTableCellElement>('td.material-number')
        ).filter((cell) => cell.textContent?.trim())
        expect(numbers.length).toBe(keys.size)
        expect(numbers.reduce((sum, cell) => sum + cell.rowSpan, 0)).toBe(
          table.querySelectorAll('tbody tr[data-material-group]').length
        )
      }
      expect(Array.from(doc.querySelectorAll('.material-name'), (el) => el.textContent)).toEqual(
        flattenProjectMaterials(source.workSpec.projectMaterials).map((row) => row.materialName)
      )
      expect(
        Array.from(doc.querySelectorAll('.die-position'), (el) => el.textContent?.trim())
      ).toEqual(
        flattenProjectMaterials(source.workSpec.projectMaterials).map((row) => row.dieLocation)
      )
      // 分页克隆保留材料表单格键：每行材料各一份，合并的项目/序号格沿用组首行键。
      const keys = Array.from(
        doc.querySelectorAll<HTMLElement>('td[data-work-spec-cell^="mat"]'),
        (el) => el.dataset.workSpecCell!
      )
      expect(new Set(keys.filter((key) => key.endsWith('-name'))).size).toBe(18)
      expect(new Set(keys.filter((key) => key.endsWith('-spec'))).size).toBe(18)
      expect(keys).toContain('mat0-number')
      expect(keys).toContain('mat0-project')
    } finally {
      height.mockRestore()
      scroll.mockRestore()
      wrapper.unmount()
    }
  })
})
