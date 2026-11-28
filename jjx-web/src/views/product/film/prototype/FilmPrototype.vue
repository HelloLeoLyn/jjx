<template>
  <div class="prototype">
    <header class="topbar">
      <strong>JJX <span>工艺资源</span></strong
      ><span class="preview-badge">前端原型 · 示例数据 · 刷新还原</span>
    </header>
    <main>
      <div class="page-heading">
        <div>
          <div class="breadcrumb">工艺资源 / 菲林管理</div>
          <h1>菲林管理</h1>
          <p>每条菲林集中管理自己的版本、图纸与菲林片。</p>
        </div>
        <el-button type="primary" size="large" @click="openMaster()">＋ 新增菲林</el-button>
      </div>
      <section class="list-card">
        <div class="filters">
          <el-input
            v-model="keyword"
            aria-label="搜索菲林"
            placeholder="菲林编号、名称或关联产品"
            clearable
            class="search-input"
          /><el-select
            v-model="family"
            aria-label="筛选类别"
            placeholder="全部类别"
            clearable
            style="width: 150px"
            ><el-option
              v-for="item in families"
              :key="item"
              :label="item"
              :value="item" /></el-select
          ><span class="list-count">共 {{ filteredFilms.length }} 条菲林</span>
        </div>
        <el-table
          :data="filteredFilms"
          row-key="id"
          stripe
          class="master-table"
          data-testid="film-master-table"
        >
          <el-table-column label="菲林编号" prop="no" width="130" />
          <el-table-column label="菲林名称" prop="name" min-width="185" />
          <el-table-column label="类别" prop="family" width="85" />
          <el-table-column label="关联产品" prop="products" min-width="220" show-overflow-tooltip />
          <el-table-column label="当前版本" width="110"
            ><template #default="{ row }"
              ><el-tag v-if="currentVersion(row)" size="small" type="success">{{
                currentVersion(row)?.no
              }}</el-tag
              ><span v-else class="muted">未发布</span></template
            ></el-table-column
          >
          <el-table-column label="菲林片" width="85" align="center"
            ><template #default="{ row }"
              >{{ copiesFor(row.id).length }} 张</template
            ></el-table-column
          >
          <el-table-column label="待回片" width="85" align="center"
            ><template #default="{ row }"
              ><span :class="{ pending: pendingFor(row.id) > 0 }"
                >{{ pendingFor(row.id) }} 张</span
              ></template
            ></el-table-column
          >
          <el-table-column label="操作" width="315" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="openVersions(row)">版本 / 图纸</el-button
              ><el-button link type="primary" @click="openCopies(row)">菲林片</el-button
              ><el-button
                link
                type="primary"
                :disabled="!releasedVersions(row).length"
                @click="openRequest(row)"
                >申请出片</el-button
              ><el-button link @click="openMaster(row)">编辑</el-button></template
            ></el-table-column
          >
          <template #empty><el-empty description="没有匹配的菲林，请调整搜索条件" /></template>
        </el-table>
        <div class="list-foot">菲林档案建立后就会显示在这里，有无图纸、菲林片都不影响主列表。</div>
      </section>
    </main>

    <el-dialog
      v-model="masterVisible"
      :title="masterDraft.id ? '编辑菲林' : '新增菲林'"
      width="1400px"
      destroy-on-close
    >
      <el-form :model="masterDraft" label-width="90px" @submit.prevent="saveMaster">
        <el-form-item label="菲林编号" required
          ><el-input v-model="masterDraft.no" aria-label="菲林编号" maxlength="64"
        /></el-form-item>
        <el-form-item label="菲林名称" required
          ><el-input v-model="masterDraft.name" aria-label="菲林名称" maxlength="100"
        /></el-form-item>
        <el-form-item label="类别" required
          ><el-select v-model="masterDraft.family" aria-label="菲林类别"
            ><el-option
              v-for="item in families"
              :key="item"
              :value="item"
              :label="item" /></el-select
        ></el-form-item>
        <el-form-item label="关联产品"
          ><el-input
            v-model="masterDraft.products"
            aria-label="关联产品"
            placeholder="原型示例：产品编号 + 名称，可填多个产品"
        /></el-form-item>
        <el-form-item label="用途"
          ><el-input v-model="masterDraft.purpose" aria-label="用途" type="textarea" :rows="3"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="masterVisible = false">取消</el-button
        ><el-button type="primary" @click="saveMaster">保存菲林</el-button></template
      >
    </el-dialog>

    <el-drawer
      v-model="versionsVisible"
      size="min(1120px, 96vw)"
      :title="`版本与图纸 · ${selectedFilm?.no || ''}`"
    >
      <template v-if="selectedFilm">
        <div class="identity">
          <strong
            >{{ selectedFilm.no }} <span>{{ selectedFilm.name }}</span></strong
          >
          <p>{{ selectedFilm.products || '未关联产品' }}</p>
        </div>
        <div v-if="selectedFilm.versions.length" class="version-layout">
          <aside class="version-list">
            <h3>所属版本</h3>
            <button
              v-for="version in selectedFilm.versions"
              :key="version.id"
              class="version-choice"
              :class="{ chosen: selectedVersionId === version.id }"
              @click="selectedVersionId = version.id"
            >
              <span
                >{{ version.no }}
                <small v-if="version.id === selectedFilm.currentVersionId">当前</small></span
              ><el-tag
                size="small"
                :type="ArtworkVersionStatusEnum.getTagProps(version.status).type"
                >{{ ArtworkVersionStatusEnum.getLabel(version.status) }}</el-tag
              >
            </button>
          </aside>
          <section v-if="selectedVersion" class="version-content">
            <div class="section-heading">
              <div>
                <h2>{{ selectedFilm.no }} / {{ selectedVersion.no }}</h2>
                <p>这个版本的图纸和技术要求</p>
              </div>
              <el-tag :type="ArtworkVersionStatusEnum.getTagProps(selectedVersion.status).type">{{
                ArtworkVersionStatusEnum.getLabel(selectedVersion.status)
              }}</el-tag>
            </div>
            <div class="drawing-title">
              <strong>{{ selectedVersion.drawingName || '尚未添加图纸' }}</strong
              ><el-button size="small" @click="fileInput?.click()">选择本地图纸预览</el-button
              ><input
                ref="fileInput"
                type="file"
                accept="image/png,image/jpeg,image/svg+xml,application/pdf"
                hidden
                @change="chooseDrawing"
              />
            </div>
            <div v-if="selectedVersion.drawingName" class="drawing-preview">
              <img
                v-if="
                  selectedVersion.drawingUrl && selectedVersion.drawingMime !== 'application/pdf'
                "
                :src="selectedVersion.drawingUrl"
                alt="所选菲林版本图纸"
              />
              <iframe
                v-else-if="selectedVersion.drawingUrl"
                :src="selectedVersion.drawingUrl"
                title="所选菲林版本PDF图纸"
              />
              <svg
                v-else
                viewBox="0 0 740 430"
                role="img"
                :aria-label="`${selectedFilm.no} ${selectedVersion.no} 图纸示意`"
              >
                <defs>
                  <pattern id="drawing-grid" width="20" height="20" patternUnits="userSpaceOnUse">
                    <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#e8edf4" stroke-width="1" />
                  </pattern>
                </defs>
                <rect width="740" height="430" fill="url(#drawing-grid)" />
                <rect
                  x="125"
                  y="85"
                  width="490"
                  height="260"
                  rx="12"
                  fill="white"
                  stroke="#35475f"
                  stroke-width="2"
                />
                <rect
                  x="174"
                  y="122"
                  width="392"
                  height="70"
                  rx="4"
                  fill="#eef4f7"
                  stroke="#35475f"
                />
                <text x="370" y="164" text-anchor="middle" font-size="24" fill="#35475f">
                  {{ selectedFilm.name }}
                </text>
                <g
                  v-for="(label, index) in [
                    '启动',
                    '停止',
                    selectedVersion.no === 'V1' ? '开关' : '复位',
                  ]"
                  :key="label"
                >
                  <circle
                    :cx="235 + index * 135"
                    cy="265"
                    r="32"
                    fill="white"
                    stroke="#35475f"
                    stroke-width="2"
                  />
                  <text
                    :x="235 + index * 135"
                    y="271"
                    text-anchor="middle"
                    font-size="15"
                    fill="#35475f"
                  >
                    {{ label }}
                  </text>
                </g>
                <path d="M125 365H615M125 355V375M615 355V375" stroke="#8c9bad" />
                <text x="370" y="388" text-anchor="middle" font-size="13" fill="#6b7c90">
                  300 mm · 原型示意图
                </text>
                <text x="20" y="30" font-size="13" fill="#6b7c90">
                  {{ selectedFilm.no }} / {{ selectedVersion.no }}
                </text>
              </svg>
            </div>
            <el-empty v-else description="该版本尚无图纸" :image-size="70" />
            <div class="technical">
              <h3>技术要求</h3>
              <p>{{ selectedVersion.technicalSpec || '未填写' }}</p>
            </div>
            <div class="inline-actions">
              <el-button @click="showVersionCopies"
                >查看这个版本的菲林片（{{ versionCopies.length }} 张）</el-button
              ><el-button
                type="primary"
                :disabled="selectedVersion.status !== ArtworkVersionStatusEnum.RELEASED.value"
                @click="openRequest(selectedFilm, selectedVersion.id)"
                >申请此版本出片</el-button
              >
            </div>
          </section>
        </div>
        <el-empty v-else description="这条菲林已建档，尚未建立版本和图纸" />
      </template>
    </el-drawer>

    <el-drawer
      v-model="copiesVisible"
      size="min(1100px, 96vw)"
      :title="`菲林片与出片记录 · ${selectedFilm?.no || ''}`"
    >
      <template v-if="selectedFilm">
        <div class="identity">
          <strong
            >{{ selectedFilm.no }} <span>{{ selectedFilm.name }}</span></strong
          >
          <p>{{ selectedFilm.products || '未关联产品' }}</p>
        </div>
        <div class="copy-toolbar">
          <span>所属版本</span
          ><el-select
            v-model="selectedVersionId"
            aria-label="菲林片所属版本"
            style="width: 200px"
            :disabled="!selectedFilm.versions.length"
            ><el-option
              v-for="version in selectedFilm.versions"
              :key="version.id"
              :value="version.id"
              :label="`${version.no}${version.id === selectedFilm.currentVersionId ? '（当前版本）' : ''}`" /></el-select
          ><el-button
            type="primary"
            :disabled="
              !selectedVersion || selectedVersion.status !== ArtworkVersionStatusEnum.RELEASED.value
            "
            @click="openRequest(selectedFilm, selectedVersionId)"
            >申请出片</el-button
          >
        </div>
        <div class="section-heading compact">
          <div>
            <h2>
              菲林片 <span class="count">{{ versionCopies.length }} 张</span>
            </h2>
            <p>已实际回片的记录，逐张编号、存放。</p>
          </div>
        </div>
        <el-table :data="versionCopies" border data-testid="physical-copy-table"
          ><el-table-column label="菲林片编号" prop="no" min-width="140" /><el-table-column
            label="位置"
            prop="location"
            min-width="150"
          /><el-table-column label="状态" width="100"
            ><template #default="{ row }"
              ><el-tag size="small" :type="FilmCopyStatusEnum.getTagProps(row.status).type">{{
                FilmCopyStatusEnum.getLabel(row.status)
              }}</el-tag></template
            ></el-table-column
          ><el-table-column label="材质" prop="material" width="80" /><el-table-column
            label="厚度(mm)"
            prop="thickness"
            width="100"
          /><el-table-column label="尺寸" prop="size" min-width="130" /><el-table-column
            label="来自申请"
            min-width="110"
            ><template #default="{ row }">{{
              state.requests.find((request) => request.id === row.requestId)?.no || '已有菲林片'
            }}</template></el-table-column
          ><template #empty>该版本暂无实际菲林片，可以先申请出片</template></el-table
        >
        <div class="section-heading request-heading">
          <div>
            <h2>出片申请</h2>
            <p>申请保留原张数，实际回片关联到上面的菲林片记录。</p>
          </div>
        </div>
        <el-table :data="versionRequests" border data-testid="request-table"
          ><el-table-column label="申请编号" prop="no" width="110" /><el-table-column
            label="申请"
            width="75"
            ><template #default="{ row }">{{ row.quantity }} 张</template></el-table-column
          ><el-table-column label="已回" width="75"
            ><template #default="{ row }"
              >{{ receivedCount(state, row) }} 张</template
            ></el-table-column
          ><el-table-column label="未回" width="75"
            ><template #default="{ row }"
              >{{ row.quantity - receivedCount(state, row) }} 张</template
            ></el-table-column
          ><el-table-column label="进度" width="110"
            ><template #default="{ row }"
              ><el-tag size="small" :type="requestProgress(state, row).tagProps.type">{{
                requestProgress(state, row).label
              }}</el-tag></template
            ></el-table-column
          ><el-table-column label="期望回片日" prop="dueDate" width="125" /><el-table-column
            label="说明"
            prop="note"
            min-width="130"
            show-overflow-tooltip
          /><el-table-column label="操作" width="115"
            ><template #default="{ row }"
              ><el-button
                v-if="requestProgress(state, row).value !== Progress.RECEIVED.value"
                link
                type="primary"
                @click="openReceipt(row)"
                >回片登记</el-button
              ><span v-else class="muted">已回齐</span></template
            ></el-table-column
          ><template #empty>该版本还没有出片申请</template></el-table
        >
      </template>
    </el-drawer>

    <el-dialog
      v-model="requestVisible"
      title="申请出片"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <div v-if="selectedFilm" class="form-context">
        {{ selectedFilm.no }} <strong>{{ selectedFilm.name }}</strong>
      </div>
      <el-form label-width="110px" @submit.prevent="saveRequest">
        <el-form-item label="菲林版本" required
          ><el-select v-model="requestDraft.versionId" aria-label="申请菲林版本"
            ><el-option
              v-for="version in releasedVersions(selectedFilm)"
              :key="version.id"
              :value="version.id"
              :label="`${version.no}${version.id === selectedFilm?.currentVersionId ? '（当前版本）' : ''}`" /></el-select
        ></el-form-item>
        <el-form-item label="申请张数" required
          ><el-input-number
            v-model="requestDraft.quantity"
            aria-label="申请张数"
            :min="1"
            :max="100"
            :precision="0"
          /><span class="unit">张</span></el-form-item
        >
        <div class="form-pair">
          <el-form-item label="材质" required
            ><el-input v-model="requestDraft.material" aria-label="片基材质" /></el-form-item
          ><el-form-item label="厚度(mm)" required
            ><el-input-number
              v-model="requestDraft.thickness"
              aria-label="片基厚度"
              :min="0.001"
              :precision="3"
              :step="0.025"
              :controls="false"
          /></el-form-item>
        </div>
        <el-form-item label="尺寸" required
          ><el-input
            v-model="requestDraft.size"
            aria-label="菲林片尺寸"
            placeholder="例如：300 × 200 mm"
        /></el-form-item>
        <el-form-item label="期望回片日" required
          ><el-date-picker
            v-model="requestDraft.dueDate"
            aria-label="期望回片日"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="说明"
          ><el-input v-model="requestDraft.note" aria-label="出片说明" type="textarea" :rows="2"
        /></el-form-item>
      </el-form>
      <p class="form-note">提交申请后不会生成可用菲林片；实际收到后再登记编号和位置。</p>
      <template #footer
        ><el-button @click="requestVisible = false">取消</el-button
        ><el-button type="primary" @click="saveRequest">提交出片申请</el-button></template
      >
    </el-dialog>

    <el-dialog
      v-model="receiptVisible"
      title="回片登记"
      width="700px"
      append-to-body
      destroy-on-close
    >
      <template v-if="receiptRequest && selectedFilm">
        <div class="form-context">
          {{ selectedFilm.no }} / {{ versionOf(receiptRequest.versionId)?.no }}
          <strong>{{ receiptRequest.no }}</strong>
        </div>
        <div class="receipt-summary">
          <span
            >申请 <strong>{{ receiptRequest.quantity }}</strong> 张</span
          ><span
            >已回 <strong>{{ receivedCount(state, receiptRequest) }}</strong> 张</span
          ><span
            >未回 <strong>{{ remainingReceipt }}</strong> 张</span
          >
        </div>
        <div class="copy-toolbar">
          <span>本次回片</span
          ><el-input-number
            :model-value="receiptRows.length"
            :min="1"
            :max="remainingReceipt"
            :precision="0"
            aria-label="本次回片张数"
            @update:model-value="resizeReceipt"
          /><span>张</span>
        </div>
        <div class="receipt-labels"><span>菲林片编号</span><span>存放位置</span></div>
        <div v-for="(row, index) in receiptRows" :key="index" class="receipt-row">
          <el-input
            v-model="row.no"
            :aria-label="`第${index + 1}张编号`"
            placeholder="例如 FL-003"
          /><el-input
            v-model="row.location"
            :aria-label="`第${index + 1}张位置`"
            placeholder="例如 A架 · 02格"
          />
        </div>
        <p class="form-note">
          可以分次回来。本次登记几张，就增加几张实物记录，剩余张数仍留在原申请里。
        </p>
      </template>
      <template #footer
        ><el-button @click="receiptVisible = false">取消</el-button
        ><el-button type="primary" @click="saveReceipt">确认本次回片</el-button></template
      >
    </el-dialog>
    <div v-if="toast" class="prototype-toast" role="status">{{ toast }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ArtworkVersionStatusEnum, FilmCopyStatusEnum } from '@/enums/product/film'
import { FilmPrototypeRequestProgressEnum as Progress } from '@/enums/product/filmPrototype'
import {
  addRequest,
  createPrototypeState,
  receiveCopies,
  receivedCount,
  requestProgress,
} from './prototypeData'
import type { PrototypeFilm, PrototypeRequest } from './prototypeData'

defineOptions({ name: 'FilmPrototype' })
const state = reactive(createPrototypeState())
const families = ['面板', '上线', '下线']
const keyword = ref('')
const family = ref('')
const filteredFilms = computed(() =>
  state.films.filter(
    (row) =>
      (!family.value || row.family === family.value) &&
      `${row.no} ${row.name} ${row.products}`
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase())
  )
)
const copiesFor = (filmId: number) => state.copies.filter((row) => row.filmId === filmId)
const pendingFor = (filmId: number) =>
  state.requests
    .filter((row) => row.filmId === filmId)
    .reduce((sum, row) => sum + row.quantity - receivedCount(state, row), 0)
const currentVersion = (film: PrototypeFilm) =>
  film.versions.find((version) => version.id === film.currentVersionId)
const releasedVersions = (film?: PrototypeFilm) =>
  film?.versions.filter((version) => version.status === ArtworkVersionStatusEnum.RELEASED.value) ||
  []
const selectedFilmId = ref<number>()
const selectedVersionId = ref<number>()
const selectedFilm = computed(() => state.films.find((row) => row.id === selectedFilmId.value))
const selectedVersion = computed(() =>
  selectedFilm.value?.versions.find((row) => row.id === selectedVersionId.value)
)
const versionOf = (id: number) => selectedFilm.value?.versions.find((row) => row.id === id)
const versionCopies = computed(() =>
  state.copies.filter(
    (row) => row.filmId === selectedFilmId.value && row.versionId === selectedVersionId.value
  )
)
const versionRequests = computed(() =>
  state.requests.filter(
    (row) => row.filmId === selectedFilmId.value && row.versionId === selectedVersionId.value
  )
)
const versionsVisible = ref(false)
const copiesVisible = ref(false)
const selectFilm = (film: PrototypeFilm) => {
  selectedFilmId.value = film.id
  selectedVersionId.value = film.currentVersionId ?? film.versions[0]?.id
}
const openVersions = (film: PrototypeFilm) => {
  selectFilm(film)
  copiesVisible.value = false
  versionsVisible.value = true
}
const openCopies = (film: PrototypeFilm) => {
  selectFilm(film)
  versionsVisible.value = false
  copiesVisible.value = true
}
const showVersionCopies = () => {
  versionsVisible.value = false
  copiesVisible.value = true
}

const masterVisible = ref(false)
const masterDraft = reactive({
  id: undefined as number | undefined,
  no: '',
  name: '',
  family: '面板',
  products: '',
  purpose: '',
})
function openMaster(film?: PrototypeFilm) {
  Object.assign(
    masterDraft,
    film
      ? {
          id: film.id,
          no: film.no,
          name: film.name,
          family: film.family,
          products: film.products,
          purpose: film.purpose,
        }
      : {
          id: undefined,
          no: `PW-${String(Math.max(0, ...state.films.map((row) => row.id)) + 1).padStart(3, '0')}`,
          name: '',
          family: '面板',
          products: '',
          purpose: '',
        }
  )
  masterVisible.value = true
}
function saveMaster() {
  if (!masterDraft.no.trim() || !masterDraft.name.trim()) return notify('请填写菲林编号和名称')
  if (
    state.films.some(
      (row) =>
        row.id !== masterDraft.id && row.no.toUpperCase() === masterDraft.no.trim().toUpperCase()
    )
  )
    return notify('菲林编号已存在')
  const data = {
    no: masterDraft.no.trim(),
    name: masterDraft.name.trim(),
    family: masterDraft.family,
    products: masterDraft.products.trim(),
    purpose: masterDraft.purpose.trim(),
  }
  const existing = state.films.find((row) => row.id === masterDraft.id)
  if (existing) Object.assign(existing, data)
  else
    state.films.push({
      ...data,
      id: Math.max(0, ...state.films.map((row) => row.id)) + 1,
      versions: [],
    })
  masterVisible.value = false
  notify('菲林已保存在本次原型预览中')
}

const requestVisible = ref(false)
const requestDraft = reactive({
  versionId: undefined as number | undefined,
  quantity: 2,
  material: 'PET',
  thickness: 0.125,
  size: '',
  dueDate: '',
  note: '',
})
function openRequest(film: PrototypeFilm, versionId?: number) {
  selectedFilmId.value = film.id
  Object.assign(requestDraft, {
    versionId: versionId ?? currentVersion(film)?.id ?? releasedVersions(film)[0]?.id,
    quantity: 2,
    material: 'PET',
    thickness: 0.125,
    size: '300 × 200 mm',
    dueDate: '2026-12-01',
    note: '',
  })
  requestVisible.value = true
}
function saveRequest() {
  if (!selectedFilm.value || !requestDraft.versionId) return notify('请选择菲林版本')
  try {
    const request = addRequest(state, {
      ...requestDraft,
      filmId: selectedFilm.value.id,
      versionId: requestDraft.versionId,
    })
    selectedVersionId.value = request.versionId
    requestVisible.value = false
    versionsVisible.value = false
    copiesVisible.value = true
    notify(`${request.no} 已申请 ${request.quantity} 张，等待回片`)
  } catch (error) {
    notify(error instanceof Error ? error.message : '申请信息不完整')
  }
}

const receiptVisible = ref(false)
const receiptRequestId = ref<number>()
const receiptRequest = computed(() =>
  state.requests.find((row) => row.id === receiptRequestId.value)
)
const remainingReceipt = computed(() =>
  receiptRequest.value
    ? receiptRequest.value.quantity - receivedCount(state, receiptRequest.value)
    : 0
)
const receiptRows = ref<{ no: string; location: string }[]>([])
function openReceipt(request: PrototypeRequest) {
  receiptRequestId.value = request.id
  receiptRows.value = [{ no: '', location: '' }]
  receiptVisible.value = true
}
function resizeReceipt(count: number | undefined) {
  const value = Math.max(1, Math.min(remainingReceipt.value, count || 1))
  receiptRows.value = Array.from(
    { length: value },
    (_, index) => receiptRows.value[index] || { no: '', location: '' }
  )
}
function saveReceipt() {
  try {
    receiveCopies(state, receiptRequestId.value!, receiptRows.value)
    receiptVisible.value = false
    notify('本次回片已登记，菲林片记录与申请进度已更新')
  } catch (error) {
    notify(error instanceof Error ? error.message : '回片信息不完整')
  }
}
const fileInput = ref<HTMLInputElement>()
const drawingUrls: string[] = []
function chooseDrawing(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !selectedVersion.value) return
  if (!['image/png', 'image/jpeg', 'image/svg+xml', 'application/pdf'].includes(file.type))
    return notify('请选择 PNG、JPG、SVG 或 PDF 图纸')
  const url = URL.createObjectURL(file)
  drawingUrls.push(url)
  Object.assign(selectedVersion.value, {
    drawingName: file.name,
    drawingUrl: url,
    drawingMime: file.type,
  })
  notify('仅在当前原型中预览，不上传文件')
}
const toast = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined
function notify(message: string) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = ''
  }, 4500)
}
onBeforeUnmount(() => {
  clearTimeout(toastTimer)
  drawingUrls.forEach((url) => URL.revokeObjectURL(url))
})
</script>

<style scoped>
.prototype {
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #25334a;
  min-height: 100vh;
  background: #f4f6f9;
  font-size: 14px;
}
.topbar {
  height: 62px;
  background: white;
  border-bottom: 1px solid #e5e9ef;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 36px;
}
.topbar strong {
  font-size: 21px;
  letter-spacing: 0.6px;
  color: #285bbc;
}
.topbar strong span {
  color: #526176;
  border-left: 1px solid #e0e5ed;
  margin-left: 18px;
  padding-left: 18px;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0;
}
.preview-badge {
  font-size: 12px;
  color: #6b7890;
  background: #f1f4fa;
  border: 1px solid #e2e8f2;
  padding: 7px 12px;
  border-radius: 5px;
}
main {
  max-width: 1500px;
  margin: auto;
  padding: 30px 36px;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.breadcrumb {
  font-size: 12px;
  color: #8793a5;
  margin-bottom: 12px;
}
h1 {
  font-size: 25px;
  margin: 0 0 9px;
  font-weight: 600;
  letter-spacing: 0.4px;
}
.page-heading p {
  margin: 0;
  color: #7e8a9c;
  font-size: 13px;
}
.list-card {
  background: white;
  border: 1px solid #e3e8ef;
  border-radius: 7px;
  overflow: hidden;
  box-shadow: 0 2px 6px #233d5a03;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px;
}
.search-input {
  max-width: 340px;
}
.list-count {
  margin-left: auto;
  color: #8793a5;
  font-size: 12px;
}
.list-foot {
  font-size: 12px;
  color: #8793a5;
  padding: 18px 20px;
  border-top: 1px solid #eef1f6;
}
.muted {
  color: #98a2b1;
}
.pending {
  color: #c77a18;
  font-weight: 600;
}
.master-table :deep(th.el-table__cell) {
  background: #f7f9fc;
  color: #68768a;
  font-weight: 500;
}
.master-table :deep(td.el-table__cell) {
  padding: 19px 0;
}
.identity {
  background: #f5f8fc;
  border: 1px solid #e3eaf4;
  border-radius: 6px;
  padding: 17px 20px;
  margin-bottom: 24px;
}
.identity strong {
  font-size: 17px;
}
.identity strong span {
  margin-left: 12px;
  font-size: 15px;
  font-weight: 500;
}
.identity p {
  margin: 10px 0 0;
  font-size: 12px;
  color: #78869b;
}
.version-layout {
  display: grid;
  grid-template-columns: 165px minmax(0, 1fr);
  gap: 24px;
}
.version-list h3,
.technical h3 {
  font-size: 13px;
  font-weight: 500;
  margin: 0 0 14px;
  color: #7a889c;
}
.version-choice {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
  border: 1px solid #e5eaf2;
  background: white;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 10px;
  cursor: pointer;
  color: #33445c;
  font-size: 16px;
}
.version-choice.chosen {
  border-color: #7fa6ec;
  background: #f1f6ff;
}
.version-choice small {
  font-size: 10px;
  color: #3975d7;
  margin-left: 10px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.section-heading h2 {
  font-size: 17px;
  font-weight: 600;
  margin: 0;
}
.section-heading p {
  font-size: 12px;
  color: #8793a5;
  margin: 8px 0 0;
}
.drawing-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 10px;
  gap: 10px;
}
.drawing-preview {
  border: 1px solid #dfe6ee;
  border-radius: 5px;
  overflow: hidden;
  background: white;
}
.drawing-preview svg {
  width: 100%;
  display: block;
}
.drawing-preview img {
  display: block;
  width: 100%;
  max-height: 450px;
  object-fit: contain;
}
.drawing-preview iframe {
  width: 100%;
  height: 450px;
  border: 0;
}
.technical {
  padding: 18px 0;
}
.technical p {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.8;
  margin: 0;
}
.inline-actions {
  border-top: 1px solid #e9edf4;
  padding-top: 18px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.copy-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.copy-toolbar > span {
  color: #67758a;
  font-size: 13px;
}
.copy-toolbar .el-button {
  margin-left: auto;
}
.compact {
  margin-bottom: 14px;
}
.count {
  font-weight: 400;
  color: #8c98aa;
  font-size: 13px;
  margin-left: 8px;
}
.request-heading {
  margin-top: 34px;
}
.form-context {
  background: #f4f7fc;
  color: #667890;
  padding: 13px 15px;
  border-radius: 5px;
  margin-bottom: 24px;
}
.form-context strong {
  color: #33445c;
  margin-left: 10px;
}
.unit {
  margin-left: 10px;
  color: #8b96a6;
}
.form-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.form-pair :deep(.el-input-number) {
  width: 100%;
}
.form-note {
  font-size: 12px;
  color: #8c96a5;
  line-height: 1.8;
  margin-top: 18px;
}
.receipt-summary {
  display: flex;
  gap: 35px;
  background: #f8fafc;
  padding: 16px;
  margin-bottom: 22px;
  color: #7e8b9e;
}
.receipt-summary strong {
  color: #34445d;
  font-size: 18px;
  margin: 0 6px;
}
.receipt-labels,
.receipt-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 12px;
}
.receipt-labels {
  color: #7d8898;
  font-size: 12px;
}
.prototype-toast {
  position: fixed;
  z-index: 10000;
  top: 22px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 22px;
  border: 1px solid #cbdcf7;
  border-radius: 6px;
  background: #f0f6ff;
  color: #3266ae;
  box-shadow: 0 5px 20px #244a7f14;
  max-width: 80vw;
}
:deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 24px;
  border-bottom: 1px solid #edf0f4;
  color: #36445c;
}
:deep(.el-drawer__body) {
  padding: 24px;
}
:deep(.el-dialog) {
  border-radius: 9px;
}
:deep(.el-dialog__header) {
  padding: 8px 8px 18px;
  margin-bottom: 8px;
}
:deep(.el-dialog__footer) {
  border-top: 1px solid #edf0f4;
  padding-top: 18px;
}
:deep(.el-table__empty-text) {
  color: #95a0ae;
  font-size: 12px;
}
@media (max-width: 800px) {
  main {
    padding: 22px 16px;
  }
  .topbar {
    padding: 0 16px;
  }
  .preview-badge {
    font-size: 10px;
  }
  .page-heading {
    gap: 12px;
  }
  .page-heading p {
    max-width: 220px;
    line-height: 1.6;
  }
  .version-layout {
    grid-template-columns: 1fr;
  }
  .version-list {
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }
  .version-list h3 {
    display: none;
  }
  .version-choice {
    max-width: 150px;
  }
  .filters {
    padding: 14px;
    flex-wrap: wrap;
  }
  .list-count {
    width: 100%;
  }
  .form-pair {
    grid-template-columns: 1fr;
  }
}
</style>
