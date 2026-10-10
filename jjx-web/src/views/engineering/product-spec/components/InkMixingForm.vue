<template>
  <div class="ink-mixing-container">
    <div class="print-header">
      <h2>精捷信塑胶五金电子制品厂</h2>
      <h3>油墨调配记录表</h3>
    </div>

    <table class="ink-table">
      <thead>
        <tr>
          <th rowspan="2" class="col-machine">机种<br />色号及色样</th>
          <th rowspan="2" class="col-material">原料名称</th>
          <th rowspan="2" class="col-weight">重量(g)</th>
          <th rowspan="2" class="col-percent">百分比</th>
          <th rowspan="2" class="col-tank">油墨罐号</th>
          <th class="col-worker">材料</th>
          <th rowspan="2" class="col-date">调墨日期</th>
          <th rowspan="2" class="col-solvent">溶剂</th>
        </tr>
        <tr>
          <th class="col-worker">调墨员</th>
        </tr>
      </thead>
      <tbody>
        <!-- 循环多个调配记录块 -->
        <template v-for="(record, rIndex) in formData.records" :key="rIndex">
          <tr v-for="(item, mIndex) in record.materials" :key="mIndex">
            <!-- 左侧合并单元格列 -->
            <td v-if="mIndex === 0" :rowspan="record.materials.length" class="merged-col">
              <div class="label">机种:</div>
              <el-input v-model="record.machineType" size="small" placeholder="请输入" />
              <div class="label mt-2">色号:</div>
              <el-input v-model="record.colorCode" size="small" placeholder="请输入" />
              <div class="label mt-2">色样:</div>
              <div class="color-sample-box">色样区</div>
            </td>

            <!-- 原料数据列 -->
            <td>
              <el-input v-model="item.name" size="small" placeholder="原料名称" />
            </td>
            <td>
              <el-input-number
                v-model="item.weight"
                :controls="false"
                size="small"
                placeholder="重量"
                style="width: 100%"
              />
            </td>
            <td>
              <el-input v-model="item.percentage" size="small" placeholder="百分比" />
            </td>
            <td>
              <el-input v-model="item.tankNo" size="small" placeholder="罐号" />
            </td>

            <!-- 右侧合并单元格列 -->
            <td v-if="mIndex === 0" :rowspan="record.materials.length" class="merged-col">
              <el-input v-model="record.worker" size="small" placeholder="调墨员" />
            </td>
            <td v-if="mIndex === 0" :rowspan="record.materials.length" class="merged-col">
              <el-date-picker
                v-model="record.date"
                type="date"
                size="small"
                placeholder="选择日期"
                style="width: 100%"
              />
            </td>
            <td v-if="mIndex === 0" :rowspan="record.materials.length" class="merged-col">
              <el-input v-model="record.solvent" size="small" placeholder="溶剂" />
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <div class="actions">
      <el-button type="primary" @click="addRecord">添加记录</el-button>
      <el-button type="success" @click="submitForm">保存</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

// 定义原料项接口
interface MaterialItem {
  name: string
  weight: number | undefined
  percentage: string
  tankNo: string
}

// 定义单条记录接口
interface InkRecord {
  machineType: string
  colorCode: string
  colorSample: string // 如果是图片可以存 url
  materials: MaterialItem[]
  worker: string
  date: string
  solvent: string
}

// 初始化单条记录
const createEmptyRecord = (): InkRecord => ({
  machineType: '',
  colorCode: '',
  colorSample: '',
  materials: Array.from({ length: 5 }).map(() => ({
    name: '',
    weight: undefined,
    percentage: '',
    tankNo: '',
  })), // 图片上每个区块大约有5行原料
  worker: '',
  date: '',
  solvent: '',
})

const formData = reactive({
  records: [createEmptyRecord(), createEmptyRecord()], // 默认两条记录
})

const addRecord = () => {
  formData.records.push(createEmptyRecord())
}

const submitForm = () => {
  console.log('提交的数据：', formData.records)
  ElMessage.success('保存成功！')
}
</script>

<style scoped>
.ink-mixing-container {
  padding: 20px;
  background: #fff;
  max-width: 1000px;
  margin: 0 auto;
}
.print-header {
  text-align: center;
  margin-bottom: 20px;
}
.print-header h2 {
  margin: 0 0 10px 0;
  font-size: 20px;
}
.print-header h3 {
  margin: 0;
  font-size: 18px;
}
.ink-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.ink-table th,
.ink-table td {
  border: 1px solid #000;
  padding: 4px;
  text-align: center;
  vertical-align: middle;
}
.ink-table th {
  background-color: #f5f7fa;
  font-weight: bold;
  font-size: 14px;
}
.merged-col {
  padding: 8px !important;
  text-align: left !important;
}
.label {
  font-size: 12px;
  margin-bottom: 4px;
  font-weight: bold;
}
.mt-2 {
  margin-top: 8px;
}
.color-sample-box {
  border: 1px dashed #999;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.col-machine {
  width: 100px;
}
.col-material {
  width: 130px;
}
.col-weight {
  width: 80px;
}
.col-percent {
  width: 70px;
}
.col-tank {
  width: 80px;
}
.col-worker {
  width: 90px;
}
.col-date {
  width: 120px;
}
.col-solvent {
  width: 80px;
}
.actions {
  margin-top: 20px;
  text-align: center;
}
</style>
