<template>
  <div class="a4-demo">
    <div class="demo-toolbar">
      <span>演示：A4 画布（示例单据内容）</span>
      <el-button size="small" type="primary" icon="Printer" @click="handlePrint">打印</el-button>
      <el-button size="small" @click="scale = scale === 0.7 ? 1 : 0.7">缩放 {{ scale }}</el-button>
    </div>

    <A4Canvas :padding-mm="15" :scale="scale">
      <!-- 公司抬头 -->
      <div class="doc-header">
        <div class="company-name">江苏某某薄膜开关有限公司</div>
        <div class="company-contact">地址：苏州工业园区××路 1 号 ｜ 电话：0512-88888888 ｜ 邮箱：sales@jjx.com</div>
      </div>

      <!-- 单据标题 -->
      <div class="doc-title">报 价 单</div>

      <!-- 信息区：两列 -->
      <div class="doc-info">
        <div class="info-item"><span class="info-label">报价单号</span>QT2608070001</div>
        <div class="info-item"><span class="info-label">报价日期</span>2026-08-07</div>
        <div class="info-item"><span class="info-label">客户名称</span>江苏盛泰科技有限公司</div>
        <div class="info-item"><span class="info-label">有效期至</span>2026-09-07</div>
        <div class="info-item"><span class="info-label">联系人</span>王经理 13800000000</div>
        <div class="info-item"><span class="info-label">币种</span>CNY</div>
        <div class="info-item"><span class="info-label">来源询价</span>INQ20260807001</div>
        <div class="info-item"><span class="info-label">销售负责人</span>张销售</div>
      </div>

      <!-- 明细表格 -->
      <table class="doc-items">
        <thead>
          <tr>
            <th style="width: 6%">序号</th>
            <th style="width: 14%">产品编码</th>
            <th>产品名称 / 规格</th>
            <th style="width: 8%">数量</th>
            <th style="width: 7%">单位</th>
            <th style="width: 13%">单价</th>
            <th style="width: 14%">金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in demoItems" :key="idx">
            <td class="col-center">{{ idx + 1 }}</td>
            <td>{{ item.code }}</td>
            <td class="col-spec">{{ item.spec }}</td>
            <td class="col-right">{{ item.qty }}</td>
            <td class="col-center">{{ item.unit }}</td>
            <td class="col-right">{{ item.price }}</td>
            <td class="col-right">{{ item.amount }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 金额汇总 -->
      <div class="doc-amounts">
        <div class="amount-row"><span>小计</span><span>10,000.00</span></div>
        <div class="amount-row"><span>税率 (%)</span><span>13</span></div>
        <div class="amount-row"><span>税额</span><span>1,300.00</span></div>
        <div class="amount-row"><span>折扣</span><span>500.00</span></div>
        <div class="amount-row amount-total"><span>合计</span><span>10,800.00</span></div>
      </div>

      <!-- 备注 -->
      <div class="doc-remark">备注：含税价格，交货期 15 天，付款方式月结 30 天。</div>

      <!-- 签名区 -->
      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">销售负责人：张销售</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">客户确认：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">日期：</div>
          <div class="sign-underline"></div>
        </div>
      </div>
    </A4Canvas>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import A4Canvas from '@/components/A4Canvas/index.vue'

const scale = ref(1)

const demoItems = [
  { code: 'FLK-001', spec: '薄膜开关 A 型（尺寸 50×30mm，材质 PC，线路银浆，连接器 FPC 8P）', qty: 2000, unit: 'PCS', price: '5.00', amount: '10,000.00' },
  { code: 'FLK-002', spec: 'FPC 柔性连接器（8P 间距 0.5mm）', qty: 500, unit: 'PCS', price: '2.50', amount: '1,250.00' },
  { code: 'FLK-003', spec: '导电薄膜（厚 0.125mm）', qty: 300, unit: 'PCS', price: '3.20', amount: '960.00' },
]

function handlePrint() {
  window.print()
}
</script>

<style scoped>
.a4-demo {
  padding: 20px;
  background: #eef0f3;
  min-height: 100vh;
}

.demo-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: #606266;
}

/* 画布内内容样式（业务模板部分，随各单据定制） */
.doc-header {
  text-align: center;
  margin-bottom: 6px;
}

.company-name {
  font-size: 20px;
  font-weight: 700;
  color: #2b5aa7;
  letter-spacing: 2px;
}

.company-contact {
  font-size: 9px;
  color: #888;
  margin-top: 2px;
}

.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 8px;
  margin: 14px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #2b5aa7;
}

.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
  margin-bottom: 12px;
  font-size: 11px;
}

.info-item {
  display: flex;
}

.info-label {
  width: 70px;
  color: #888;
  flex-shrink: 0;
}

.doc-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  margin-bottom: 10px;
}

.doc-items th {
  background: #2b5aa7;
  color: #fff;
  padding: 6px 4px;
  font-weight: 600;
  border: 1px solid #2b5aa7;
}

.doc-items td {
  border: 1px solid #dcdfe6;
  padding: 5px 4px;
}

.doc-items tr:nth-child(even) td {
  background: #f7f9fc;
}

.col-center {
  text-align: center;
}

.col-right {
  text-align: right;
}

.col-spec {
  font-size: 10px;
}

.doc-amounts {
  width: 45%;
  margin-left: auto;
  margin-bottom: 12px;
  font-size: 11px;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  padding: 3px 8px;
  border: 1px solid #dcdfe6;
}

.amount-row + .amount-row {
  border-top: none;
}

.amount-total {
  background: #2b5aa7;
  color: #fff;
  font-weight: 700;
  font-size: 13px;
}

.doc-remark {
  font-size: 10px;
  color: #555;
  margin-bottom: 20px;
}

.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding: 0 20px;
}

.sign-item {
  width: 30%;
  text-align: center;
  font-size: 11px;
}

.sign-line {
  padding-bottom: 4px;
}

.sign-underline {
  border-bottom: 1px solid #999;
}

/* 打印时隐藏工具栏 */
@media print {
  .demo-toolbar {
    display: none !important;
  }

  .a4-demo {
    padding: 0;
    background: #fff;
  }
}
</style>
