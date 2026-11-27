<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="类别">
          <el-select v-model="queryFamily" placeholder="全部" clearable style="width: 140px" @change="loadArtworks(1)">
            <el-option v-for="family in familyTabs" :key="family.value" :label="family.label" :value="family.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryStatus" placeholder="全部" clearable style="width: 140px" @change="loadArtworks(1)">
            <el-option
              v-for="s in FilmCopyStatusEnum.items"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="菲林片编号/菲林编号/名称/位置"
            clearable
            style="width: 240px"
            @keyup.enter="doSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="doSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="search-tip">
        按类别与菲林片状态筛选；每行是一张菲林片，需求按张生成，回片后逐张登记。
      </div>
    </el-card>

    <!-- 统一搜索结果 -->
    <el-card v-if="searched" shadow="never" class="table-card">
      <template #header>
        <span>统一搜索（{{ searchRows.length }}）</span>
          <el-button link type="primary" style="float: right" @click="searched = false">收起</el-button>
      </template>
      <el-table :data="searchRows" border size="small" v-loading="searchLoading">
        <el-table-column label="命中" prop="matchedType" width="100" />
        <el-table-column label="候选" prop="displayName" min-width="240" show-overflow-tooltip />
        <el-table-column label="菲林编号" prop="artworkNo" width="140" />
        <el-table-column label="版本" prop="versionNo" width="100" />
        <el-table-column label="菲林片号" prop="filmNo" width="130" />
        <el-table-column label="网版/网框" min-width="120">
          <template #default="scope">{{ scope.row.plateNo || scope.row.frameNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="产品" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.productCode }} {{ scope.row.productName }}
          </template>
        </el-table-column>
        <el-table-column label="可用" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.availability ? 'success' : 'warning'" size="small">
              {{ scope.row.availability ? '可用' : '受限' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.artworkId"
              link
              type="primary"
              size="small"
              @click="openDetailById(scope.row.artworkId)"
            >
              打开菲林
            </el-button>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" v-hasPermi="['engineering:film:edit']" @click="openFilmDialog()">新建需求</el-button>
        <el-button @click="openDesigns">菲林设计</el-button>
      </div>
      <el-table :data="filmRows" v-loading="listLoading" border stripe row-key="filmId">
        <el-table-column label="菲林片编号" prop="filmNo" width="170" />
        <el-table-column label="菲林" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.artworkNo }} {{ row.name }}</template>
        </el-table-column>
        <el-table-column label="版本" width="100">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row.artworkId)">{{ row.versionNo }}</el-button></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="FilmCopyStatusEnum.getTagProps(row.status).type">{{ FilmCopyStatusEnum.getLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="回片日期" prop="dueDate" width="120" />
        <el-table-column label="位置" prop="location" min-width="120" show-overflow-tooltip />
        <el-table-column label="材质" prop="material" width="100" />
        <el-table-column label="厚度(mm)" prop="thicknessMm" width="100" />
        <el-table-column label="尺寸" prop="size" min-width="120" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === FilmCopyStatusEnum.REQUESTED.value" v-hasPermi="['engineering:film:edit']" link type="primary" :disabled="saving" @click="sendFilm(row)">发送</el-button>
            <el-button v-if="row.status === FilmCopyStatusEnum.SENT.value" v-hasPermi="['engineering:film:edit']" link type="success" @click="openFilmDialog(row)">回片登记</el-button>
            <el-button v-if="row.status === FilmCopyStatusEnum.AVAILABLE.value" v-hasPermi="['engineering:film:archive']" link type="warning" @click="runFilmAction(row, 'archive', '封存')">封存</el-button>
            <el-button v-if="[FilmCopyStatusEnum.AVAILABLE.value, FilmCopyStatusEnum.ARCHIVED.value].includes(row.status)" v-hasPermi="['engineering:film:destroy']" link type="danger" @click="runFilmAction(row, 'destroy', '销毁')">销毁</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="page.total"
        :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
        @current-change="(p: number) => loadArtworks(p)" @size-change="() => loadArtworks(1)" />
    </el-card>
    <el-dialog v-model="designVisible" title="菲林设计" width="1100px" append-to-body>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button
          type="primary"
          :icon="Plus"
          v-hasPermi="['engineering:film:edit']"
          @click="openArtworkDialog()"
        >
          新建菲林
        </el-button>
        <span class="toolbar-tip">
          「上线」= 当前有生效版本的菲林；「下线 / 草稿」= 尚未生效。
        </span>
      </div>
      <el-table
        :data="artworks"
        v-loading="listLoading"
        border
        stripe
        row-key="artworkId"
      >
        <el-table-column label="菲林编号" prop="artworkNo" width="160" />
        <el-table-column label="名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="类别" width="100" align="center">
          <template #default="scope">
            <el-tag size="small">{{ scope.row.family || '面板' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用途" prop="purpose" min-width="120" show-overflow-tooltip />
        <el-table-column label="生效版本" width="130" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.defaultVersionNo" type="success" size="small">
              {{ scope.row.defaultVersionNo }}
            </el-tag>
            <span v-else class="muted">未生效</span>
          </template>
        </el-table-column>
        <el-table-column label="版本数" width="90" align="center">
          <template #default="scope">{{ scope.row.versionCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="最新状态" width="120" align="center">
          <template #default="scope">
            <el-tag
              v-if="scope.row.latestStatus"
              :type="ArtworkVersionStatusEnum.getTagProps(scope.row.latestStatus).type"
              size="small"
            >
              {{ ArtworkVersionStatusEnum.getLabel(scope.row.latestStatus) }}
            </el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.artworkId)">
              详情
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              v-hasPermi="['engineering:film:edit']"
              @click="openArtworkDialog(scope.row.artworkId)"
            >
              编辑
            </el-button>
            <el-button
              v-if="scope.row.defaultVersionId"
              link
              type="success"
              size="small"
              @click="goPlate(scope.row.defaultVersionId)"
            >
              制版
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="designPage.pageNum"
        v-model:page-size="designPage.pageSize"
        :total="designPage.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="(p: number) => loadDesigns(p)"
        @size-change="() => loadDesigns(1)"
      />
    </el-card>

    </el-dialog>

    <!-- 菲林详情 -->
    <el-drawer v-model="detailVisible" size="1100px" title="菲林详情" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="菲林编号">{{ detail.artworkNo }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ detail.family || '-' }}</el-descriptions-item>
            <el-descriptions-item label="用途">{{ detail.purpose || '-' }}</el-descriptions-item>
            <el-descriptions-item label="整套分组">{{ detail.setCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源打样单">
              {{ detail.sampleOrderId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="关联产品" :span="3">
              <template v-if="detail.productIds && detail.productIds.length">
                <el-tag
                  v-for="pid in detail.productIds"
                  :key="pid"
                  size="small"
                  style="margin-right: 6px"
                >
                  {{ productLabel(pid) }}
                </el-tag>
              </template>
              <span v-else class="muted">未关联</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <span>版本</span>
            <el-button
              link
              type="primary"
              v-hasPermi="['engineering:film:edit']"
              @click="openVersionDialog()"
            >
              新建版本
            </el-button>
          </div>
          <el-table :data="detail.versions || []" border size="small">
            <el-table-column label="版本" prop="versionNo" width="90" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="scope">
                <el-tag
                  :type="ArtworkVersionStatusEnum.getTagProps(scope.row.status).type"
                  size="small"
                >
                  {{ ArtworkVersionStatusEnum.getLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="图纸" width="80" align="center">
              <template #default="scope">
                <el-link
                  v-if="scope.row.drawingFileId"
                  type="primary"
                  :href="downloadUrl(scope.row.drawingFileId)"
                  target="_blank"
                  underline="never"
                >
                  下载
                </el-link>
                <span v-else class="muted">无</span>
              </template>
            </el-table-column>
            <el-table-column label="技术规格" prop="technicalSpec" min-width="160" show-overflow-tooltip />
            <el-table-column label="审批/发布时间" min-width="150">
              <template #default="scope">
                <div v-if="scope.row.approvedTime">批 {{ scope.row.approvedTime }}</div>
                <div v-if="scope.row.releasedTime">发 {{ scope.row.releasedTime }}</div>
                <span v-if="!scope.row.approvedTime && !scope.row.releasedTime" class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="330" align="center">
              <template #default="scope">
                <el-button
                  v-if="scope.row.status === ArtworkVersionStatusEnum.DRAFT.value"
                  link
                  type="primary"
                  size="small"
                  v-hasPermi="['engineering:film:edit']"
                  @click="openVersionDialog(scope.row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="scope.row.status === ArtworkVersionStatusEnum.DRAFT.value"
                  link
                  type="warning"
                  size="small"
                  v-hasPermi="['engineering:film:submit']"
                  @click="runVersionAction(scope.row, 'submit', '提交审核')"
                >
                  提交审核
                </el-button>
                <template v-if="scope.row.status === ArtworkVersionStatusEnum.REVIEW.value">
                  <el-button
                    link
                    type="success"
                    size="small"
                    v-hasPermi="['engineering:film:approve']"
                    @click="runVersionAction(scope.row, 'approve', '审核通过')"
                  >
                    通过
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    v-hasPermi="['engineering:film:reject']"
                    @click="runVersionAction(scope.row, 'reject', '驳回', true)"
                  >
                    驳回
                  </el-button>
                </template>
                <el-button
                  v-if="scope.row.status === ArtworkVersionStatusEnum.APPROVED.value"
                  link
                  type="success"
                  size="small"
                  v-hasPermi="['engineering:film:release']"
                  @click="runVersionAction(scope.row, 'release', '发布')"
                >
                  发布
                </el-button>
                <template v-if="scope.row.status === ArtworkVersionStatusEnum.RELEASED.value">
                  <el-button
                    v-if="detail.defaultVersionId !== scope.row.versionId"
                    link
                    type="primary"
                    size="small"
                    v-hasPermi="['engineering:film:release']"
                    @click="runVersionAction(scope.row, 'activate', '生效')"
                  >
                    生效
                  </el-button>
                  <el-button
                    link
                    type="warning"
                    size="small"
                    v-hasPermi="['engineering:film:disable']"
                    @click="runVersionAction(scope.row, 'disable', '停用', true)"
                  >
                    停用
                  </el-button>
                  <el-button link type="success" size="small" @click="goPlate(scope.row.versionId)">
                    制版
                  </el-button>
                </template>
                <el-button
                  link
                  type="info"
                  size="small"
                  v-hasPermi="['engineering:film:disable']"
                  @click="showImpact(scope.row)"
                >
                  影响
                </el-button>
              </template>
            </el-table-column>
          </el-table>

        </template>
      </div>
    </el-drawer>

    <!-- 新建/编辑菲林 -->
    <el-dialog
      v-model="artworkDialogVisible"
      :title="artworkForm.artworkId ? '编辑菲林' : '新建菲林'"
      width="800px"
      append-to-body
    >
      <el-form ref="artworkFormRef" :model="artworkForm" :rules="artworkRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菲林编号" prop="artworkNo">
              <el-input v-model="artworkForm.artworkNo" maxlength="64" placeholder="如 PW-0001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类别" prop="family">
              <el-select v-model="artworkForm.family" placeholder="面板/上线/下线" style="width: 100%">
                <el-option label="面板" value="面板" />
                <el-option label="上线" value="上线" />
                <el-option label="下线" value="下线" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="名称" prop="name">
          <el-input v-model="artworkForm.name" maxlength="200" placeholder="菲林名称" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用途">
              <el-input v-model="artworkForm.purpose" maxlength="200" placeholder="如 面板菲林" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="整套分组">
              <el-input v-model="artworkForm.setCode" maxlength="64" placeholder="同一套菲林共用编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联产品" prop="productIds">
          <el-select
            v-model="artworkForm.productIds"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchProducts"
            :loading="productSearching"
            placeholder="搜索并选择产品（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.productId"
              :label="`${p.productCode || ''} ${p.productName || ''}`"
              :value="p.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源打样单">
          <el-input-number
            v-model="artworkForm.sampleOrderId"
            :min="1"
            :controls="false"
            placeholder="无产品时可填打样单 ID"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="artworkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveArtwork">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑版本 -->
    <el-dialog
      v-model="versionDialogVisible"
      :title="versionForm.versionId ? '编辑版本' : '新建版本'"
      width="800px"
      append-to-body
    >
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="100px">
        <el-form-item label="版本号" prop="versionNo">
          <el-input v-model="versionForm.versionNo" maxlength="32" placeholder="如 v1 / v2" />
        </el-form-item>
        <el-form-item label="技术规格">
          <el-input v-model="versionForm.technicalSpec" type="textarea" :rows="2" maxlength="2000" />
        </el-form-item>
        <el-form-item label="图纸">
          <div v-if="versionForm.versionId" class="upload-row">
            <AttachmentCenter
              variant="upload-only"
              source-type="print_artwork_version"
              :source-id="versionForm.versionId"
              :draft-id="`print-version-${versionForm.versionId}`"
              @uploaded="onDrawingUploaded"
            />
            <span v-if="versionForm.drawingFileId" class="file-tip">
              <el-link
                type="primary"
                :href="downloadUrl(versionForm.drawingFileId)"
                target="_blank"
                underline="never"
              >
                已上传图纸
              </el-link>
              <el-button link type="danger" size="small" @click="versionForm.drawingFileId = undefined">
                移除
              </el-button>
            </span>
            <span v-else class="muted">未上传</span>
          </div>
          <div v-else class="muted">先保存版本，再上传图纸</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveVersion">保存</el-button>
      </template>
    </el-dialog>

    <!-- 菲林片需求 / 回片登记 -->
    <el-dialog v-model="filmDialogVisible" :title="filmForm.filmId ? '回片登记' : '新建菲林片需求'" width="800px" append-to-body>
      <el-form ref="filmFormRef" :model="filmForm" :rules="filmRules" label-width="100px">
        <template v-if="!filmForm.filmId">
          <el-form-item label="菲林版本" prop="versionId"><PrintResourceSelector :model-value="demandVersion" @select="pickDemandVersion" /></el-form-item>
          <el-form-item label="张数" prop="quantity"><el-input-number v-model="filmForm.quantity" :min="1" :max="500" :precision="0" /></el-form-item>
          <el-form-item label="回片日期" prop="dueDate"><el-date-picker v-model="filmForm.dueDate" value-format="YYYY-MM-DD" /></el-form-item>
        </template>
        <el-row v-if="filmForm.filmId" :gutter="20">
          <el-col :span="12">
            <el-form-item label="菲林片编号" prop="filmNo">
              <el-input v-model="filmForm.filmNo" maxlength="64" placeholder="如 FL-0001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="位置" prop="location">
              <el-input v-model="filmForm.location" maxlength="200" placeholder="存放位置" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="材质" prop="material">
              <el-input v-model="filmForm.material" maxlength="200" placeholder="如 PET" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="厚度(mm)" prop="thicknessMm">
              <el-input-number
                v-model="filmForm.thicknessMm"
                :min="0.001"
                :precision="3"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="尺寸" prop="size">
              <el-input v-model="filmForm.size" maxlength="100" placeholder="如 300x200mm" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="filmDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveFilm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 影响范围 -->
    <el-dialog v-model="impactVisible" title="版本影响范围" width="700px" append-to-body>
      <pre class="impact-box">{{ impactText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { printingArtworkApi } from '@/api/product/film'
import { printingSearchApi, type PrintSearchRow } from '@/api/engineering/printingSearch'
import { listProductPage, searchProduct } from '@/api/product'
import { attachmentApi } from '@/api/system/attachment'
import type { ProductItem } from '@/types/product'
import type { ArtworkCommand, ArtworkView } from '@/types/engineering/printingArtwork'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import PrintResourceSelector from '@/components/Selector/PrintResourceSelector.vue'
import { ArtworkVersionStatusEnum, FilmCopyStatusEnum } from '@/enums/product/film'

defineOptions({ name: 'EngineeringFilm' })

const router = useRouter()


const familyTabs = [
  { value: '面板', label: '面板' },
  { value: '上线', label: '上线' },
  { value: '下线', label: '下线' },
]

const listLoading = ref(false)
const searchLoading = ref(false)
const saving = ref(false)
const searched = ref(false)
const keyword = ref('')
const searchRows = ref<PrintSearchRow[]>([])
const queryFamily = ref('')
const queryStatus = ref('')
const productOptions = ref<ProductItem[]>([])
const productSearching = ref(false)
const artworks = ref<any[]>([])
const filmRows = ref<any[]>([])
const designVisible = ref(false)
const designPage = reactive({ pageNum: 1, pageSize: 20, total: 0 })
async function openDesigns() { designVisible.value = true; await loadDesigns(1) }
async function loadDesigns(pageNo = designPage.pageNum) {
  designPage.pageNum = pageNo
  try {
    const res: any = await printingArtworkApi.list({ family: queryFamily.value || undefined,
      pageNum: designPage.pageNum, pageSize: designPage.pageSize })
    artworks.value = res.data?.records || []
    designPage.total = res.data?.total || 0
  } catch { /* 拦截器已提示 */ }
}
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

function productLabel(pid: number) {
  const p = productOptions.value.find((item) => item.productId === pid)
  return p ? `${p.productCode || ''} ${p.productName || ''}`.trim() || String(pid) : String(pid)
}

/** 菲林编号自动生成：PW-yyMM-序号（可手改，唯一性由后端兜底） */
function nextArtworkNo(): string {
  const d = new Date()
  const yy = String(d.getFullYear()).slice(2)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const seq = String(Date.now() % 1000).padStart(3, '0')
  return `PW-${yy}${mm}-${seq}`
}

function genRequestId() {
  return `p06-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function downloadUrl(fileId?: number | null): string {
  return fileId ? attachmentApi.downloadUrl(Number(fileId)) : ''
}

async function loadProducts() {
  try {
    const res: any = await listProductPage({ pageNum: 1, pageSize: 100 })
    productOptions.value = res?.data?.records || res?.data?.rows || res?.data || []
  } catch {
    productOptions.value = []
  }
}

async function searchProducts(text: string) {
  if (!text || text.trim().length < 1) {
    loadProducts()
    return
  }
  productSearching.value = true
  try {
    const res: any = await searchProduct(text.trim())
    productOptions.value = res?.data || []
  } catch {
    productOptions.value = []
  } finally {
    productSearching.value = false
  }
}

async function loadArtworks(pageNo = page.pageNum) {
  page.pageNum = pageNo
  listLoading.value = true
  try {
    const res: any = await printingArtworkApi.listFilms({
      keyword: keyword.value.trim() || undefined,
      family: queryFamily.value || undefined,
      status: queryStatus.value || undefined,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
    })
    filmRows.value = res?.data?.records || []
    page.total = res?.data?.total || 0
  } catch {
    filmRows.value = []
    page.total = 0
  } finally {
    listLoading.value = false
  }
}

async function doSearch() {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入关键字')
    return
  }
  searchLoading.value = true
  searched.value = true
  loadArtworks(1)
  try {
    const res: any = await printingSearchApi.search({
      q: keyword.value.trim(),
      pageNum: 1,
      pageSize: 20,
    })
    searchRows.value = res?.data?.records || []
  } catch {
    searchRows.value = []
  } finally {
    searchLoading.value = false
  }
}

function resetQuery() {
  keyword.value = ''
  queryFamily.value = ''
  queryStatus.value = ''
  searchRows.value = []
  searched.value = false
  loadArtworks(1)
}

/* ==================== 详情 ==================== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ArtworkView | null>(null)

async function openDetail(artworkId: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res: any = await printingArtworkApi.detail(artworkId)
    detail.value = res?.data || null
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function openDetailById(artworkId: number) {
  openDetail(artworkId)
}

async function reloadDetail() {
  if (!detail.value) return
  const res: any = await printingArtworkApi.detail(detail.value.artworkId)
  detail.value = res?.data || null
}

function versionNoOf(versionId: number) {
  const v = detail.value?.versions?.find((item) => item.versionId === versionId)
  return v?.versionNo || String(versionId)
}

/* ==================== 菲林表单 ==================== */
const artworkDialogVisible = ref(false)
const artworkFormRef = ref<FormInstance>()
const artworkForm = reactive<any>({
  artworkId: undefined,
  artworkNo: '',
  name: '',
  purpose: '',
  family: '面板',
  setCode: '',
  productIds: [] as number[],
  sampleOrderId: undefined,
  revision: 0,
})
const artworkRules: FormRules = {
  artworkNo: [{ required: true, message: '请输入菲林编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  family: [{ required: true, message: '请选择类别', trigger: 'change' }],
  productIds: [
    {
      validator: (_rule, value, callback) => {
        if ((!value || !value.length) && !artworkForm.sampleOrderId) {
          callback(new Error('至少关联一个产品或填写来源打样单'))
        } else callback()
      },
      trigger: 'change',
    },
  ],
}

async function openArtworkDialog(artworkId?: number) {
  if (artworkId) {
    detailLoading.value = true
    try {
      const res: any = await printingArtworkApi.detail(artworkId)
      const data: ArtworkView | undefined = res?.data
      Object.assign(artworkForm, {
        artworkId,
        artworkNo: data?.artworkNo || '',
        name: data?.name || '',
        purpose: data?.purpose || '',
        family: data?.family || '面板',
        setCode: data?.setCode || '',
        productIds: data?.productIds ? [...data.productIds] : [],
        sampleOrderId: data?.sampleOrderId || undefined,
        revision: data?.revision ?? 0,
      })
    } finally {
      detailLoading.value = false
    }
  } else {
    Object.assign(artworkForm, {
      artworkId: undefined,
      artworkNo: nextArtworkNo(),
      name: '',
      purpose: '',
      family: queryFamily.value || '面板',
      setCode: '',
      productIds: [],
      sampleOrderId: undefined,
      revision: 0,
    })
  }
  artworkDialogVisible.value = true
}

async function saveArtwork() {
  const valid = await artworkFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  const command: ArtworkCommand = {
    requestId: genRequestId(),
    expectedRevision: artworkForm.revision || 0,
    artworkNo: artworkForm.artworkNo.trim(),
    name: artworkForm.name.trim(),
    purpose: artworkForm.purpose || undefined,
    family: artworkForm.family,
    setCode: artworkForm.setCode || undefined,
    productIds: artworkForm.productIds?.length ? artworkForm.productIds : undefined,
    sampleOrderId: artworkForm.sampleOrderId || undefined,
  }
  try {
    if (artworkForm.artworkId) await printingArtworkApi.edit(artworkForm.artworkId, command)
    else await printingArtworkApi.create(command)
    ElMessage.success('保存成功')
    artworkDialogVisible.value = false
    await loadArtworks()
    if (designVisible.value) await loadDesigns()
    if (detail.value) await reloadDetail()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ==================== 版本表单 ==================== */
const versionDialogVisible = ref(false)
const versionFormRef = ref<FormInstance>()
const versionForm = reactive<any>({
  versionId: undefined,
  versionNo: '',
  technicalSpec: '',
  drawingFileId: undefined,
  revision: 0,
  artworkRevision: 0,
})
const versionRules: FormRules = {
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
}

function openVersionDialog(row?: any) {
  if (!detail.value) return
  if (row) {
    Object.assign(versionForm, {
      versionId: row.versionId,
      versionNo: row.versionNo,
      technicalSpec: row.technicalSpec || '',
      drawingFileId: row.drawingFileId || undefined,
      revision: row.revision ?? 0,
      artworkRevision: detail.value.revision ?? 0,
    })
  } else {
    Object.assign(versionForm, {
      versionId: undefined,
      versionNo: suggestNextVersionNo(),
      technicalSpec: '',
      drawingFileId: undefined,
      revision: 0,
      artworkRevision: detail.value.revision ?? 0,
    })
  }
  versionDialogVisible.value = true
}

function suggestNextVersionNo() {
  const versions = detail.value?.versions || []
  let max = 0
  for (const v of versions) {
    const match = /v?(\d+)/i.exec(v.versionNo || '')
    if (match) max = Math.max(max, Number(match[1]))
  }
  return `v${max + 1}`
}

function onDrawingUploaded(payload: { attachmentId: number }) {
  versionForm.drawingFileId = payload.attachmentId
}

async function saveVersion() {
  const valid = await versionFormRef.value?.validate().catch(() => false)
  if (!valid || !detail.value) return
  saving.value = true
  const command: ArtworkCommand = {
    requestId: genRequestId(),
    expectedRevision: versionForm.versionId
      ? versionForm.revision || 0
      : versionForm.artworkRevision || 0,
    versionNo: versionForm.versionNo.trim(),
    technicalSpec: versionForm.technicalSpec || undefined,
    drawingFileId: versionForm.drawingFileId || undefined,
  }
  try {
    if (versionForm.versionId) await printingArtworkApi.editVersion(versionForm.versionId, command)
    else await printingArtworkApi.createVersion(detail.value.artworkId, command)
    ElMessage.success('保存成功')
    versionDialogVisible.value = false
    await reloadDetail()
    await loadArtworks()
    if (designVisible.value) await loadDesigns()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ==================== 版本动作 ==================== */
const actionText: Record<string, string> = {
  submit: '提交审核',
  approve: '审核通过',
  reject: '驳回',
  release: '发布',
  activate: '设为生效版本',
  disable: '停用',
}

async function promptReason(title: string, message?: string) {
  const { value } = await ElMessageBox.prompt(message || `请填写${title}原因`, title, {
    inputPlaceholder: '原因（必填）',
    inputValidator: (val: string) => (val && val.trim() ? true : '原因不能为空'),
  })
  return value.trim()
}

/** 影响预览：返回 impactToken 与明细（P08 未接入时后端会明确报错） */
async function fetchImpact(versionId: number) {
  const res: any = await printingArtworkApi.impact(versionId)
  const data = res?.data || {}
  return { token: String(data.impactToken || ''), data }
}

function impactSummary(data: any) {
  const count = (arr: any) => (Array.isArray(arr) ? arr.length : 0)
  return [
    `工艺路线 ${count(data.routingIds)} 条`,
    `已发布规范 ${count(data.specVersionIds)} 个`,
    `未结工单 ${count(data.orderIds)} 张`,
    `在用生产执行 ${count(data.executionIds)} 个`,
  ].join('，')
}

async function runVersionAction(row: any, action: string, label: string, needReason = false) {
  let reason = ''
  let impactToken: string | undefined
  let disablePrevious: boolean | undefined
  let expectedRevision = row.revision ?? 0
  try {
    if (action === 'activate') {
      expectedRevision = detail.value?.revision ?? 0
      const currentId = detail.value?.defaultVersionId
      if (currentId && currentId !== row.versionId) {
        let choice: 'disable' | 'keep' | null = null
        try {
          await ElMessageBox.confirm(
            '切换生效版本会改变生产用版。是否同时停用当前生效版本？',
            '设为生效版本',
            {
              confirmButtonText: '同时停用旧版',
              cancelButtonText: '仅切换生效（保留旧版）',
              distinguishCancelAndClose: true,
              type: 'warning',
            },
          )
          choice = 'disable'
        } catch (e: any) {
          if (e === 'cancel') choice = 'keep'
        }
        if (!choice) return
        if (choice === 'disable') {
          disablePrevious = true
          reason = await promptReason('同步停用旧版本')
          const preview = await fetchImpact(currentId)
          impactToken = preview.token
        }
      }
    } else if (action === 'disable') {
      const preview = await fetchImpact(row.versionId)
      impactToken = preview.token
      reason = await promptReason('停用版本', `停用影响：${impactSummary(preview.data)}\n请输入停用原因`)
    } else if (needReason) {
      reason = await promptReason(label)
    } else {
      if ((action === 'submit' || action === 'release') && !row.drawingFileId) {
        ElMessage.warning('该版本未绑定图纸，不能提交或发布')
        return
      }
      await ElMessageBox.confirm(`确认对版本 ${row.versionNo} 执行「${label}」？`, label, {
        type: 'info',
      })
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.warning(e?.message || '操作已取消')
    return
  }
  const command: ArtworkCommand = {
    requestId: genRequestId(),
    expectedRevision,
    reason: reason || undefined,
    impactToken,
    disablePrevious,
  }
  try {
    const api = (printingArtworkApi as any)[action]
    if (typeof api !== 'function') throw new Error('未实现的动作：' + action)
    await api(row.versionId, command)
    ElMessage.success(label + '成功')
    await reloadDetail()
    await loadArtworks()
    if (designVisible.value) await loadDesigns()
  } catch {
    /* 拦截器已提示 */
  }
}

async function showImpact(row: any) {
  try {
    const preview = await fetchImpact(row.versionId)
    impactText.value = impactSummary(preview.data) + '\n\n' + JSON.stringify(preview.data, null, 2)
    impactVisible.value = true
  } catch {
    /* 拦截器已提示（P08 未接入时后端会明确报错） */
  }
}
const impactVisible = ref(false)
const impactText = ref('')

/* ==================== 菲林片 ==================== */
const filmDialogVisible = ref(false)
const filmFormRef = ref<FormInstance>()
const demandVersion = ref<PrintSearchRow | null>(null)
const filmForm = reactive<any>({ filmId: undefined, versionId: undefined, revision: 0, filmNo: '',
  location: '', material: '', thicknessMm: undefined, size: '', quantity: 1, dueDate: '' })
let filmRequestId = ''
let demandPickSequence = 0
const filmRules: FormRules = {
  versionId: [{ required: true, message: '请选择已发布菲林版本', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入需求张数', trigger: 'change' }],
  dueDate: [{ required: true, message: '请选择回片日期', trigger: 'change' }],
  filmNo: [{ required: true, message: '请输入菲林片编号', trigger: 'blur' }],
  location: [{ required: true, message: '请填写存放位置', trigger: 'blur' }],
  material: [{ required: true, message: '请输入材质', trigger: 'blur' }],
  thicknessMm: [{ required: true, message: '请输入厚度', trigger: 'blur' }],
  size: [{ required: true, message: '请输入尺寸', trigger: 'blur' }],
}

function openFilmDialog(row?: any) {
  demandPickSequence++
  demandVersion.value = null
  Object.assign(filmForm, { filmId: row?.filmId, versionId: row?.versionId, revision: row?.revision ?? 0,
    filmNo: row?.filmNo || '', location: row?.location || '', material: row?.material || '',
    thicknessMm: row?.thicknessMm, size: row?.size || '', quantity: 1, dueDate: row?.dueDate || '' })
  filmRequestId = genRequestId()
  filmDialogVisible.value = true
  filmFormRef.value?.clearValidate()
}

async function pickDemandVersion(item: PrintSearchRow) {
  const sequence = ++demandPickSequence
  filmForm.versionId = undefined
  demandVersion.value = item
  if (!item?.versionId || !item?.artworkId) return
  const res: any = await printingArtworkApi.detail(Number(item.artworkId))
  if (sequence !== demandPickSequence || !filmDialogVisible.value) return
  const version = res.data?.versions?.find((v: any) => v.versionId === item.versionId)
  if (version?.status !== ArtworkVersionStatusEnum.RELEASED.value) {
    ElMessage.warning('请选择已发布可用菲林版本'); return
  }
  filmForm.versionId = version.versionId
  filmForm.revision = version.revision
}

async function saveFilm() {
  if (saving.value) return
  const valid = await filmFormRef.value?.validate().catch(() => false)
  if (!valid || !filmForm.versionId) return
  saving.value = true
  const command: ArtworkCommand = { requestId: filmRequestId, expectedRevision: filmForm.revision,
    material: filmForm.material.trim(), thicknessMm: Number(filmForm.thicknessMm), size: filmForm.size.trim() }
  try {
    if (filmForm.filmId) {
      await printingArtworkApi.receiveFilm(filmForm.filmId, { ...command,
        filmNo: filmForm.filmNo.trim(), location: filmForm.location.trim() })
      ElMessage.success('菲林片已登记为可用')
    } else {
      await printingArtworkApi.requestFilms(filmForm.versionId, { ...command,
        quantity: filmForm.quantity, dueDate: filmForm.dueDate })
      ElMessage.success('需求已建立，菲林片待出片')
    }
    filmDialogVisible.value = false
    await loadArtworks()
  } catch { /* 失败保留表单，拦截器已提示 */ }
  finally { saving.value = false }
}

async function sendFilm(row: any) {
  try { await ElMessageBox.confirm(`确认将菲林片 ${row.filmNo} 的需求发出制作？`, '发送') }
  catch { return }
  saving.value = true
  try {
    await printingArtworkApi.sendFilm(row.filmId, { requestId: genRequestId(), expectedRevision: row.revision })
    ElMessage.success('已发出制作'); await loadArtworks()
  } catch { /* 拦截器已提示 */ }
  finally { saving.value = false }
}

async function runFilmAction(film: any, action: string, label: string) {
  try {
    const { value } = await ElMessageBox.prompt(`请填写${label}原因`, label, {
      inputPlaceholder: '原因（必填）',
      inputValidator: (val: string) => (val && val.trim() ? true : '原因不能为空'),
    })
    const command: ArtworkCommand = {
      requestId: genRequestId(),
      expectedRevision: film.revision ?? 0,
      reason: value.trim(),
    }
    if (action === 'archive') await printingArtworkApi.archiveFilm(film.filmId, command)
    else await printingArtworkApi.destroyFilm(film.filmId, command)
    ElMessage.success(label + '成功')
    await loadArtworks()
  } catch {
    /* 取消或拦截器已提示 */
  }
}

/* ==================== 制版跳转 ==================== */
function goPlate(versionId: number) {
  router.push({
    path: '/engineering/print-resources',
    query: { versionId: String(versionId) },
  })
}

onMounted(async () => {
  await loadProducts()
  await loadArtworks(1)
})
</script>

<style scoped>
.search-card,
.table-card {
  margin-bottom: 16px;
}
.search-tip {
  color: #909399;
  font-size: 12px;
}
.toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-tip {
  color: #909399;
  font-size: 12px;
}
.section-head {
  margin: 18px 0 8px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.file-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.muted {
  color: #c0c4cc;
  font-size: 12px;
}
.impact-box {
  max-height: 420px;
  overflow: auto;
  background: #f5f7fa;
  padding: 12px;
  font-size: 12px;
  white-space: pre-wrap;
}
</style>
