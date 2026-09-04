<template>
  <div class="m-quality">
    <div class="m-filter">
      <div class="m-chips">
        <span
          v-for="t in tabs"
          :key="t.value"
          class="m-chip"
          :class="{ active: activeTab === t.value }"
          @click="activeTab = t.value"
          >{{ t.label }}</span
        >
      </div>
    </div>

    <div v-loading="loading" class="m-list">
      <!-- 待判定 -->
      <template v-if="activeTab === 'pending'">
        <div v-for="q in pendingList" :key="q.inspectionId" class="m-quality-item">
          <div class="m-quality-item-head">
            <span class="m-quality-item-no">{{ q.inspectionNo }}</span>
            <span class="m-tag" :class="typeTag(q.inspectionType)">{{
              q.inspectionTypeName || q.inspectionType
            }}</span>
          </div>
          <div class="m-quality-item-meta">
            <div class="m-line">🏷 {{ q.orderNo || '-' }}<span v-if="q.processName"> · {{ q.processName }}</span></div>
            <div class="m-line">📦 {{ q.productName || q.materialName || '-' }}</div>
            <div class="m-line sub">检验数 {{ fmtQty(q.totalQty) }} · 创建 {{ fmtTime(q.createTime) }}</div>
          </div>
          <div class="m-quality-item-actions">
            <button class="m-act m-act-primary" @click="openJudge(q)">判定</button>
          </div>
        </div>
        <div v-if="!loading && !pendingList.length" class="m-empty">暂无待判定检验单</div>
      </template>

      <!-- 已判定 -->
      <template v-else>
        <div v-for="q in doneList" :key="q.inspectionId" class="m-quality-item">
          <div class="m-quality-item-head">
            <span class="m-quality-item-no">{{ q.inspectionNo }}</span>
            <span class="m-tag" :class="resultTag(q.result)">{{ q.resultName || q.result }}</span>
          </div>
          <div class="m-quality-item-meta">
            <div class="m-line">🏷 {{ q.orderNo || '-' }}<span v-if="q.processName"> · {{ q.processName }}</span></div>
            <div class="m-line qty">
              合格 <b class="ok">{{ fmtQty(q.passQty) }}</b>
              <span v-if="Number(q.failQty || 0) > 0" class="bad"> · 不合格 {{ fmtQty(q.failQty) }}</span>
              <span v-if="q.defectDesc" class="desc"> · {{ q.defectDesc }}</span>
            </div>
            <div class="m-line sub">判定 {{ fmtTime(q.inspectTime) }} · {{ q.inspector || '-' }}</div>
          </div>
        </div>
        <div v-if="!loading && !doneList.length" class="m-empty">暂无已判定记录</div>
      </template>
    </div>

    <!-- 判定表单 -->
    <el-dialog v-model="judgeVisible" title="检验判定" width="92%" append-to-body class="m-judge-dialog">
      <div v-if="judgeRow" class="m-judge-form">
        <div class="m-judge-info">
          {{ judgeRow.inspectionNo }} · {{ judgeRow.inspectionTypeName || judgeRow.inspectionType }}
          <div class="m-judge-sub">工单 {{ judgeRow.orderNo || '-' }}{{ judgeRow.processName ? ' · ' + judgeRow.processName : '' }}</div>
          <div class="m-judge-sub">检验数 {{ fmtQty(judgeRow.totalQty) }}</div>
        </div>
        <el-form label-width="88px" label-position="left">
          <el-form-item label="判定结果" required>
            <el-radio-group v-model="judgeForm.result">
              <el-radio-button value="PASS">合格</el-radio-button>
              <el-radio-button value="FAIL">不合格</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="检验数量">
            <el-input-number v-model="judgeForm.totalQty" :min="0" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="合格数量">
            <el-input-number v-model="judgeForm.passQty" :min="0" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="不合格数">
            <el-input-number v-model="judgeForm.failQty" :min="0" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="缺陷描述" v-if="judgeForm.result === 'FAIL'">
            <el-input v-model="judgeForm.defectDesc" type="textarea" :rows="2" placeholder="不合格原因（建议填写）" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="judgeForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <div class="m-judge-tip">已判定不可修改；判 FAIL 后可用「复检」新建检验单</div>
      </div>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="primary" :loading="judging" @click="submitJudge">提交判定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  qualityApi,
  type QualityVO,
} from '@/api/production/quality'

const route = useRoute()
const router = useRouter()

const activeTab = ref<'pending' | 'done'>('pending')

const tabs = [
  { value: 'pending', label: '待判定' },
  { value: 'done', label: '已判定' },
] as const
const loading = ref(false)
const pendingList = ref<QualityVO[]>([])
const doneList = ref<QualityVO[]>([])

const judgeVisible = ref(false)
const judging = ref(false)
const judgeRow = ref<QualityVO | null>(null)
const judgeForm = ref({
  result: 'PASS' as 'PASS' | 'FAIL',
  totalQty: undefined as number | undefined,
  passQty: undefined as number | undefined,
  failQty: undefined as number | undefined,
  defectDesc: '',
  remark: '',
})

function fmtQty(v?: number | string | null): string {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}
function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(5, 16)
}
function typeTag(t?: string): string {
  return t === 'FQC' ? 'st-fqc' : 'st-iqc'
}
function resultTag(r?: string): string {
  return r === 'pass' ? 'st-pass' : 'st-fail'
}

async function loadData() {
  loading.value = true
  try {
    const [pendingRes, doneRes]: any = await Promise.all([
      qualityApi.page({ pageNum: 1, pageSize: 50, result: 'pending' }),
      qualityApi.page({ pageNum: 1, pageSize: 50 }),
    ])
    const all = doneRes?.data?.records || []
    pendingList.value = pendingRes?.data?.records || []
    doneList.value = all.filter((q: QualityVO) => q.result !== 'pending').slice(0, 50)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openJudge(q: QualityVO) {
  judgeRow.value = q
  judgeForm.value = {
    result: 'PASS',
    totalQty: Number(q.totalQty ?? 0) || undefined,
    passQty: undefined,
    failQty: undefined,
    defectDesc: '',
    remark: '',
  }
  judgeVisible.value = true
}

async function submitJudge() {
  if (!judgeRow.value?.inspectionId) return
  const f = judgeForm.value
  if (f.result === 'PASS' && Number(f.passQty || 0) <= 0) {
    ElMessage.warning('判定合格时合格数量必须 > 0')
    return
  }
  if (f.result === 'FAIL' && !f.defectDesc?.trim()) {
    ElMessage.warning('判定不合格请填写缺陷描述')
    return
  }
  judging.value = true
  try {
    await qualityApi.judge(judgeRow.value.inspectionId, {
      result: f.result,
      totalQty: f.totalQty,
      passQty: f.passQty,
      failQty: f.failQty,
      defectDesc: f.defectDesc?.trim() || undefined,
      remark: f.remark?.trim() || undefined,
    })
    ElMessage.success('判定已提交')
    judgeVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '判定失败')
  } finally {
    judging.value = false
  }
}

watch(activeTab, () => loadData())
onMounted(() => loadData())
</script>

<style scoped>
.m-quality {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 12px 12px 70px;
}
.m-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.m-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}
.m-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 16px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(43, 90, 167, 0.06);
}
.m-chip.active {
  background: #2b5aa7;
  color: #fff;
  font-weight: 600;
}
.m-quality-item {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 2px 10px rgba(43, 90, 167, 0.05);
}
.m-quality-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.m-quality-item-no {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  font-family: ui-monospace, monospace;
}
.m-tag {
  flex-shrink: 0;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}
.m-tag.st-fqc {
  color: #67c23a;
  background: #f0f9eb;
}
.m-tag.st-iqc {
  color: #2b5aa7;
  background: #ecf3ff;
}
.m-tag.st-pass {
  color: #67c23a;
  background: #f0f9eb;
}
.m-tag.st-fail {
  color: #f56c6c;
  background: #fef0f0;
}
.m-quality-item-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f7f9fc;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.m-line {
  font-size: 13px;
  color: #303133;
}
.m-line .ok {
  color: #67c23a;
  font-size: 16px;
}
.m-line .bad {
  color: #f56c6c;
  font-size: 16px;
}
.m-line .desc {
  color: #909399;
}
.m-line.sub {
  color: #c0c4cc;
  font-size: 12px;
}
.m-quality-item-actions {
  display: flex;
  gap: 10px;
}
.m-act {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.m-act-primary {
  background: linear-gradient(135deg, #2b5aa7, #4a7fd4);
  color: #fff;
}
.m-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 70px 0;
}
.m-judge-info {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}
.m-judge-sub {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-top: 2px;
}
.m-judge-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
