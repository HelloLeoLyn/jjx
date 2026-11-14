import { describe, it, expect } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'
import { parse, compileScript } from 'vue/compiler-sfc'
import { BomStatusEnum, RouteStatusEnum, ProductActions } from '@/enums/product'

// 执行两个真实列表 setup，隔离网络/编辑弹窗，不代替页面运行验收。
function setup(domain: 'bom' | 'route') {
  const filename = path.resolve(`src/views/product/${domain}/index.vue`)
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const script = compileScript(descriptor, { id: 'product-version-list' })
  const code = ts.transpileModule(script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const calls: Array<{ url: string; params: any }> = []
  const request = {
    get: async (url: string, config: any = { params: {} }) => {
      calls.push({ url, params: { ...config.params } })
      if (url.endsWith('next-version')) return { data: 'V1.1' }
      return url.endsWith('product-page')
        ? {
            data: {
              records: [
                {
                  outputType: 'PRODUCT',
                  outputId: config.params.pageNum,
                  latestId: 8,
                  versionCount: 3,
                },
              ],
              total: 2,
            },
          }
        : {
            data: [
              { bomId: 8, routingId: 8 },
              { bomId: 7, routingId: 7 },
              { bomId: 6, routingId: 6 },
            ],
          }
    },
    put: async (url: string, _data: unknown, config: any) => {
      calls.push({ url, params: { ...config?.params } })
      return { data: null }
    },
  }
  const api = { copyEngineeringBom: async () => {}, copyProductRoute: async () => {} }
  const mod = { exports: {} as any }
  const requireMock = (name: string): any => {
    if (name === 'vue') return { ...vue, onMounted() {} }
    if (name === 'vue-router') return { onBeforeRouteLeave() {} }
    if (name === 'element-plus')
      return {
        ElMessage: { success() {}, error() {} },
        ElMessageBox: { confirm: async () => {}, alert: async () => {} },
      }
    if (name === '@/utils/request') return { default: request }
    if (name === '@/enums/product') return { BomStatusEnum, RouteStatusEnum, ProductActions }
    if (name.startsWith('@/api/')) return { productBomApi: api, productRouteApi: api }
    if (name.endsWith('.vue') || name === '@/utils/format') return {}
    throw new Error(`Unexpected import ${name}`)
  }
  vm.runInNewContext(`(function(exports,require,module){${code}\n})`, { console })(
    mod.exports,
    requireMock,
    mod
  )
  const state = mod.exports.default.setup({}, { expose() {} })
  return { state, calls, request, api, template: descriptor.template!.content }
}

for (const domain of ['bom', 'route'] as const)
  describe(`${domain} 产品聚合列表`, () => {
    it('按产品分页，并以产出身份读取全部版本', async () => {
      const { state: s, calls, template } = setup(domain)
      await s.loadData()
      expect(s.rows.value).toHaveLength(1)
      expect(s.total.value).toBe(2)
      s.query.pageNum = 2
      await s.loadData()
      expect(s.rows.value[0].outputId).toBe(2)
      s.openVersions(s.rows.value[0])
      await flushPromises()
      expect(s.versions.value).toHaveLength(3)
      expect(calls.at(-1)?.params).toEqual({ outputType: 'PRODUCT', outputId: 2 })
      expect(template).not.toMatch(/tree-props|:lazy/)
      if (domain === 'bom') {
        // 产品BOM页签支持勾选多个产品做整套送审/审核；路由列表仍不允许勾选。
        expect(template).toContain('type="selection"')
        expect(template).toContain('批量送审')
        expect(template).toContain('批量审核')
        expect(template).toContain('材料BOM')
      } else {
        expect(template).not.toContain('type="selection"')
      }
      expect(template).toContain('当前版本（生效日）')
    })
    it('复制请求默认版本号；设为当前携带已观察到的当前版本，失败保留复制输入', async () => {
      const { state: s, calls, api } = setup(domain)
      const id = domain === 'bom' ? 'bomId' : 'routingId'
      const version = domain === 'bom' ? 'bomVersion' : 'routingVersion'
      s.selected.value = { outputType: 'PRODUCT', outputId: 1 }
      s.versions.value = [
        { [id]: 8, [version]: 'V1.0', isCurrent: false },
        { [id]: 7, [version]: 'V0.9', isCurrent: true },
      ]
      const row = {
        id: 8,
        version: 'V1.0',
        approveStatus: BomStatusEnum.APPROVED.value,
        isCurrent: false,
        canSetCurrent: true,
      }
      await s.handleVersionAction('copy', row)
      expect(calls.at(-1)?.url).toMatch(/8\/next-version$/)
      expect(s.suggestedVersion.value).toBe('V1.1')
      expect(s.copyVisible.value).toBe(true)
      api.copyEngineeringBom = api.copyProductRoute = async () => {
        throw new Error('同产品版本号已存在：V1.1')
      }
      await s.copyConfirm('V1.1')
      expect(s.copyVisible.value).toBe(true)
      expect(s.copyBusy.value).toBe(false)
      expect(s.copyError.value).toContain('版本号已存在')
      await s.handleVersionAction('setCurrent', row)
      expect(calls.find((x) => /setDefault|set-current/.test(x.url))?.params).toEqual({
        expectedCurrentId: 7,
      })
    })
    it('新搜索恢复第一页，旧请求不会覆盖新页', async () => {
      const { state: s, request } = setup(domain)
      let resolve: (x: any) => void = () => {}
      request.get = () =>
        new Promise((r) => {
          resolve = r
        })
      const old = s.loadData()
      request.get = async () => ({ data: { records: [], total: 0 } })
      s.query.pageNum = 3
      await s.loadData()
      resolve({ data: { records: [{ outputId: 999 }], total: 100 } })
      await old
      expect(s.rows.value).toHaveLength(0)
      s.search()
      expect(s.query.pageNum).toBe(1)
    })
  })
