<template>
  <div class="spec-print-page">
    <div class="toolbar no-print">
      <span class="tb-title">产品作业规范 · 文档集预览</span>
      <el-button type="primary" @click="doPrint">打印</el-button>
      <el-button @click="closeWin">关闭</el-button>
    </div>

    <div v-loading="loading" class="canvas-wrap">
      <A4Canvas :padding-mm="15" v-if="product">
        <div class="doc-title">产品作业规范文档集</div>
        <div class="doc-sub">
          产品编码：{{ product.productCode || '-' }}　产品名称：{{ product.productName || '-' }}　客户：{{ product.customerName || '-' }}
        </div>
        <div class="doc-sub">生成时间：{{ nowText }}</div>

        <!-- ① 客供资料 -->
        <section v-if="has('customer')">
          <h2>① 客供资料</h2>
          <table class="grid" v-if="customerDocs.length">
            <thead><tr><th style="width:80px">来源</th><th style="width:150px">单据号</th><th>文件名</th><th style="width:110px">上传人</th></tr></thead>
            <tbody>
              <tr v-for="d in customerDocs" :key="d.id">
                <td>{{ d.sourceType || d.sourcetype }}</td>
                <td>{{ d.sourceNo || d.sourceno }}</td>
                <td>{{ d.file_name || d.fileName }}</td>
                <td>{{ d.create_by || d.createBy }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">暂无询价/报价附件</div>
        </section>

        <!-- ② 产品作业规范 -->
        <section v-if="has('spec')">
          <h2>② 产品作业规范</h2>
          <h3>BOM（{{ bomTitle }}）</h3>
          <table class="grid" v-if="bomItems.length">
            <thead><tr><th style="width:140px">物料编码</th><th>物料名称</th><th style="width:80px">用量</th><th style="width:60px">单位</th></tr></thead>
            <tbody>
              <tr v-for="(it, i) in bomItems" :key="i">
                <td>{{ it.materialCode }}</td><td>{{ it.materialName }}</td>
                <td class="num">{{ it.quantity }}</td><td class="num">{{ it.unit }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">未配置 BOM</div>

          <h3>工艺路线（{{ routingTitle }}）</h3>
          <table class="grid" v-if="routingItems.length">
            <thead><tr><th style="width:70px">序号</th><th>工序名称</th><th style="width:90px">类别</th><th>说明</th></tr></thead>
            <tbody>
              <tr v-for="(it, i) in routingItems" :key="i">
                <td class="num">{{ it.processOrder }}</td><td>{{ it.processName }}</td>
                <td>{{ it.processCategory }}</td><td>{{ it.description }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">未配置工艺路线</div>

          <h3>刀模（库位）</h3>
          <table class="grid" v-if="dies.length">
            <thead><tr><th style="width:140px">刀模编号</th><th>名称</th><th style="width:120px">规格</th><th style="width:100px">库位</th><th style="width:100px">状态</th></tr></thead>
            <tbody>
              <tr v-for="(d, i) in dies" :key="i">
                <td>{{ d.resourceNo || d.die_no }}</td><td>{{ d.resourceName || d.die_name }}</td>
                <td>{{ d.specification }}</td><td>{{ d.location }}</td><td>{{ d.status }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">未关联刀模</div>

          <h3>凹凸条件</h3>
          <div class="empty">待工程字段确定后补充</div>
        </section>

        <!-- ③ 印刷规范 -->
        <section v-if="has('print')">
          <h2>③ 印刷规范</h2>
          <div class="empty">印刷工序/油墨/网板/调配记录：待接入</div>
          <template v-if="printGuides.length">
            <h3>产品印刷指导图</h3>
            <ul class="files">
              <li v-for="f in printGuides" :key="f.id">{{ f.fileName }}</li>
            </ul>
          </template>
        </section>

        <!-- ④ 产品图集 -->
        <section v-if="has('atlas')">
          <h2>④ 产品图集</h2>
          <ul class="files" v-if="atlasFiles.length">
            <li v-for="f in atlasFiles" :key="f.id">{{ f.category }} · {{ f.fileName }}<span v-if="f.isCurrent === 1">（现行）</span></li>
          </ul>
          <div v-else class="empty">暂无产品图集文件</div>
        </section>

        <!-- ⑤ 分色检查表 -->
        <section v-if="has('color')">
          <h2>⑤ 规范分色检查表</h2>
          <div class="empty">待样张字段确定后补充</div>
        </section>

        <!-- ⑥ 样品 -->
        <section v-if="has('sample')">
          <h2>⑥ 样品</h2>
          <h3>样品实物照片 / 客户确认样品</h3>
          <ul class="files" v-if="sampleFiles.length">
            <li v-for="f in sampleFiles" :key="f.id">{{ f.category }} · {{ f.fileName }}</li>
          </ul>
          <div v-else class="empty">暂无样品照片</div>
        </section>

        <!-- ⑦ 打样领料单 -->
        <section v-if="has('pick')">
          <h2>⑦ 打样领料单</h2>
          <table class="grid" v-if="picks.length">
            <thead><tr><th style="width:180px">领料单号</th><th style="width:90px">数量</th><th style="width:110px">状态</th><th>创建时间</th></tr></thead>
            <tbody>
              <tr v-for="p in picks" :key="p.outboundId">
                <td>{{ p.outboundNo }}</td><td class="num">{{ p.totalQuantity }}</td>
                <td>{{ p.orderStatus }}</td><td>{{ p.createTime }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">暂无打样领料单</div>
        </section>
      </A4Canvas>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import A4Canvas from '@/components/A4Canvas/index.vue'
import { getFullProduct } from '@/api/product'
import { attachmentApi } from '@/api/system/attachment'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { outboundApi } from '@/api/inventory/outbound'

const route = useRoute()
const loading = ref(false)
const product = ref<any>(null)
const bomItems = ref<any[]>([])
const routingItems = ref<any[]>([])
const dies = ref<any[]>([])
const customerDocs = ref<any[]>([])
const productFiles = ref<any[]>([])
const picks = ref<any[]>([])

const sections = computed(() => {
  const q = String(route.query.sections || '')
  return q ? q.split(',').filter(Boolean) : ['customer', 'spec', 'print', 'atlas', 'color', 'sample', 'pick']
})
const has = (k: string) => sections.value.includes(k)

const nowText = computed(() => new Date().toLocaleString('zh-CN'))
const bomTitle = computed(() => (bomItems.value.length ? `${(bomItems.value as any).code || ''}` : '未配置'))
const routingTitle = computed(() => (routingItems.value.length ? `${(routingItems.value as any).code || ''}` : '未配置'))

const byCategory = (cats: string[]) => productFiles.value.filter((f: any) => cats.includes(f.category))
const printGuides = computed(() => byCategory(['印刷指导图']))
const atlasFiles = computed(() => byCategory(['结构图', '印刷指导图', '产品图集', '确认图', '菲林', '模具']))
const sampleFiles = computed(() => byCategory(['样品照片', '客户确认样品']))

async function load() {
  const productId = Number(route.params.productId)
  if (!productId) return
  loading.value = true
  try {
    const res: any = await getFullProduct(productId)
    const full: any = res?.data || {}
    product.value = full.product || null
    bomItems.value = full.bom?.items || []
    routingItems.value = full.routing?.items || []
    const code = full.product?.productCode
    // 并行加载引用数据
    const jobs: Promise<any>[] = [
      engineeringResourceApi.byProduct('DIE', productId).then((r: any) => (dies.value = r?.data || [])).catch(() => {}),
      attachmentApi.customerDocs(productId).then((r: any) => (customerDocs.value = r?.data || [])).catch(() => {}),
    ]
    if (code) {
      jobs.push(attachmentApi.productFiles(code).then((r: any) => (productFiles.value = r?.data || [])).catch(() => {}))
      jobs.push(
        sampleOrderApi.page({ productCode: code, pageNum: 1, pageSize: 100 } as any).then(async (r: any) => {
          const orders: any[] = r?.data?.records || []
          const ids = new Set(orders.map((o) => o.sampleOrderId))
          if (!ids.size) return
          const ob: any = await outboundApi.list({ sourceType: 'sample', pageNum: 1, pageSize: 200 } as any)
          picks.value = ((ob?.data?.records || []) as any[]).filter((x) => ids.has(Number(x.sourceId)))
        }).catch(() => {})
      )
    }
    await Promise.all(jobs)
  } finally {
    loading.value = false
  }
}

function doPrint() {
  window.print()
}
function closeWin() {
  window.close()
}

onMounted(load)
</script>

<style scoped>
.spec-print-page {
  background: #f0f2f5;
  min-height: 100vh;
  padding: 16px 0;
}
.toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.tb-title {
  font-weight: bold;
  margin-right: auto;
}
.canvas-wrap {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}
.doc-title {
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 6px;
}
.doc-sub {
  text-align: center;
  font-size: 12px;
  color: #666;
}
section {
  margin-top: 14px;
}
h2 {
  font-size: 15px;
  border-bottom: 1.5px solid #333;
  padding-bottom: 2px;
  margin: 14px 0 6px;
}
h3 {
  font-size: 13px;
  margin: 10px 0 4px;
}
table.grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
table.grid th,
table.grid td {
  border: 1px solid #999;
  padding: 3px 6px;
}
table.grid th {
  background: #f5f5f5;
}
.num {
  text-align: right;
}
.empty {
  font-size: 12px;
  color: #999;
  padding: 6px 0;
}
ul.files {
  font-size: 12px;
  margin: 4px 0 0;
  padding-left: 18px;
}
@media print {
  .no-print {
    display: none !important;
  }
  .spec-print-page {
    background: #fff;
    padding: 0;
  }
  .canvas-wrap {
    padding: 0;
  }
}
</style>
