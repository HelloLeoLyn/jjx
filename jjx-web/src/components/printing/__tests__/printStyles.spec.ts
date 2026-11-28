import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { readSources, readStyles, selectPlateStyles } from '../printStyles'
import ProcessStylesPicker from '../ProcessStylesPicker.vue'
const sources = [{ filmId: 21, filmNo: 'FL-A', versionId: 11, styles: [{ styleNo: 'A', name: '正面', inkColor: 'RED' }] },
  { filmId: 22, filmNo: 'FL-B', versionId: 12, styles: [{ styleNo: 'C', name: '背面', inkColor: 'BLACK' }] }]
describe('工序样式', () => {
  it('同身份覆盖保留，新增身份带色号，旧身份移除且固定参数保留', () => {
    const raw = JSON.stringify({ meshMin: 100, styles: [{ versionId: 11, styleNo: 'A', inkColor: 'SPECIAL' }, { versionId: 99, styleNo: 'OLD', inkColor: 'OLD' }] })
    const result = JSON.parse(selectPlateStyles(raw, sources))
    expect(result.meshMin).toBe(100)
    expect(result.styles).toEqual([{ versionId: 11, styleNo: 'A', inkColor: 'SPECIAL' }, { versionId: 12, styleNo: 'C', inkColor: 'BLACK' }])
    expect(sources[0].styles[0].inkColor).toBe('RED')
  })
  it('没有历史事实时不推造样式，非法数据明确失败', () => {
    expect(readSources(null)).toEqual([]); expect(readStyles(null)).toEqual([])
    expect(() => readSources('{}')).toThrow(); expect(() => readStyles('bad')).toThrow()
    expect(JSON.parse(selectPlateStyles('{"meshMin":100,"styles":[{"versionId":11,"styleNo":"A"}]}', []))).toEqual({ meshMin: 100 })
  })
  it('部分选择与色号编辑发出完整印刷要求，保留固定参数', async () => {
    const Checkbox = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<button @click="$emit(\'update:modelValue\', !modelValue)"><slot /></button>' })
    const Input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' })
    const w = mount(ProcessStylesPicker, { props: { sources, modelValue: '{"meshMin":100,"styles":[{"versionId":11,"styleNo":"A","inkColor":"OVERRIDE"}]}' }, global: { stubs: { ElCheckbox: Checkbox, ElInput: Input } } })
    await w.find('input').setValue('SPECIAL')
    let result = JSON.parse(w.emitted('update:modelValue')![0][0] as string)
    expect(result.styles[0].inkColor).toBe('SPECIAL'); expect(result.meshMin).toBe(100)
    await w.findAll('button')[1].trigger('click')
    result = JSON.parse(w.emitted('update:modelValue')![1][0] as string)
    expect(result.styles).toHaveLength(2); expect(result.styles[1]).toEqual({ versionId: 12, styleNo: 'C', inkColor: 'BLACK' })
    w.unmount()
  })
})
