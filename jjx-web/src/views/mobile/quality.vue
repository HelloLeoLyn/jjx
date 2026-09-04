<template>
  <div class="m-quality">
    <div class="m-quality-body">
      <el-tabs v-model="activeTab" class="m-quality-tabs">
        <el-tab-pane label="待判定" name="pending">
          <div v-loading="loading" class="m-quality-list">
            <div v-for="q in pendingList" :key="q.inspectionId" class="m-quality-item">
              <div class="m-quality-item-head">
                <span class="m-quality-item-no">{{ q.inspectionNo }}</span>
                <el-tag size="small" :type="typeTag(q.inspectionType)">
                  {{ q.inspectionTypeName || q.inspectionType }}
                </el-tag>
              </div>
              <div class="m-quality-item-meta">
                <div>工单：{{ q.orderNo || '-' }}<span v-if="q.processName"> · {{ q.processName }}</span></div>
                <div>产品：{{ q.productName || q.materialName || '-' }}</div>
                <div>检验数：{{ fmtQty(q.totalQty) }} · 创建：{{ fmtTime(q.createTime) }}</div>
              </div>
              <div class="m-quality-item-actions">
                <el-button size="small" type="primary" @click="openJudge(q)">判定</el-button>
              </div>
            </div>
            <el-empty v-if="!loading && !pendingList.length" description="暂无待判定检验单" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="已判定" name="done">
          <div v-loading="loading" class="m-quality-list">
            <div v-for="q in doneList" :key="q.inspectionId" class="m-quality-item">
              <div class="m-quality-item-head">
                <span class="m-quality-item-no">{{ q.inspectionNo }}</span>
                <el-tag size="small" :type="resultTag(q.result)">{{ q.resultName || q.result }}</el-tag>
              </div>
              <div class="m-quality-item-meta">
                <div>工单：{{ q.orderNo || '-' }}<span v-if="q.processName"> · {{ q.processName }}</span></div>
                <div>
                  合格 {{ fmtQty(q.passQty) }}
                  <span v-if="Number(q.failQty || 0) > 0" class="m-fail"> · 不合格 {{ fmtQty(q.failQty) }}</span>
                  <span v-if="q.defectDesc"> · {{ q.defectDesc }}</span>
                </div>
                <div>判定：{{ fmtTime(q.inspectTime) }} · {{ q.inspector || '-' }}</div>
              </div>
            </div>
            <el-empty v-if="!loading && !doneList.length" description="暂无已判定记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
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
function typeTag(t?: string): any {
  return t === 'FQC' ? 'success' : 'warning'
}
function resultTag(r?: string): any {
  return r === 'pass' ? 'success' : 'danger'
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
}
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 10;
}
.m-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-quality-body {
  padding: 8px 12px;
}
.m-quality-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}
.m-quality-item {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-quality-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.m-quality-item-no {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.m-quality-item-meta {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.m-fail {
  color: #f56c6c;
}
.m-quality-item-actions {
  margin-top: 8px;
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
