<template>
  <div class="prototype film-management">
    <main>
      <div class="page-heading"><div><div class="breadcrumb">工艺资源 / 菲林管理</div><h1>菲林管理</h1><p>每条菲林集中管理自己的版本、图纸与菲林片。</p></div><el-button v-hasPermi="['engineering:film:edit']" type="primary" size="large" @click="openMaster()">＋ 新增菲林</el-button></div>
      <section class="list-card">
        <div class="filters"><el-input v-model="filters.keyword" aria-label="搜索菲林" placeholder="菲林编号、名称或关联产品" clearable @keyup.enter="loadRows(1)" @clear="loadRows(1)" class="search-input" /><el-select v-model="filters.family" aria-label="筛选类别" placeholder="全部类别" clearable @change="loadRows(1)" style="width:150px"><el-option v-for="item in families" :key="item" :label="item" :value="item" /></el-select><el-button @click="loadRows(1)">查询</el-button><el-button @click="resetFilters">重置</el-button><el-button link @click="openResourceSearch">跨资源搜索</el-button><span class="list-count">共 {{ page.total }} 条菲林</span></div>
        <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
        <el-table :data="rows" v-loading="listLoading" row-key="artworkId" stripe class="master-table" data-testid="film-master-table">
          <el-table-column label="菲林编号" prop="artworkNo" width="130" />
          <el-table-column label="菲林名称" prop="name" min-width="185" />
          <el-table-column label="类别" prop="family" width="85" />
          <el-table-column label="关联产品" prop="productsText" min-width="220" show-overflow-tooltip />
          <el-table-column label="当前版本" width="110"><template #default="{ row }"><el-tag v-if="row.defaultVersionNo" size="small" type="success">{{ row.defaultVersionNo }}</el-tag><span v-else class="muted">未发布</span></template></el-table-column>
          <el-table-column label="菲林片" width="85" align="center"><template #default="{ row }">{{ row.copyCount == null ? '—' : `${row.copyCount} 张` }}</template></el-table-column>
          <el-table-column label="待回片" width="85" align="center"><template #default="{ row }"><span :class="{ pending: (row.pendingFilmCount || 0) > 0 }">{{ row.pendingFilmCount == null ? '—' : `${row.pendingFilmCount} 张` }}</span></template></el-table-column>
          <el-table-column label="操作" width="315" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openVersions(row)">版本 / 图纸</el-button><el-button link type="primary" @click="openCopies(row)">菲林片</el-button><el-button v-hasPermi="['engineering:film:edit']" link type="primary" :disabled="row.releasedVersionCount === 0" @click="openRequest(row)">申请出片</el-button><el-button v-hasPermi="['engineering:film:edit']" link @click="openMaster(row)">编辑</el-button></template></el-table-column>
          <template #empty><el-empty description="没有匹配的菲林，请调整搜索条件" /></template>
        </el-table>
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="page.total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" class="pagination" @current-change="loadRows" @size-change="loadRows(1)" />
        <div class="list-foot">菲林档案建立后就会显示在这里，有无图纸、菲林片都不影响主列表。</div>
      </section>
    </main>

    <el-dialog v-model="masterVisible" :title="masterDraft.id ? '编辑菲林' : '新增菲林'" width="640px" destroy-on-close :show-close="!masterBusy" :close-on-click-modal="!masterBusy" :close-on-press-escape="!masterBusy">
      <el-form :model="masterDraft" v-loading="masterLoading" :disabled="saving" label-width="90px" @submit.prevent="saveMaster">
        <el-form-item label="菲林编号" required><el-input v-model="masterDraft.no" aria-label="菲林编号" maxlength="64" /></el-form-item>
        <el-form-item label="菲林名称" required><el-input v-model="masterDraft.name" aria-label="菲林名称" maxlength="100" /></el-form-item>
        <el-form-item label="类别" required><el-select v-model="masterDraft.family" aria-label="菲林类别"><el-option v-for="item in families" :key="item" :value="item" :label="item" /></el-select></el-form-item>
        <el-form-item label="关联产品"><el-select v-model="masterDraft.productIds" multiple filterable remote :remote-method="searchProducts" :loading="productsLoading" style="width:100%" placeholder="搜索并选择产品，可关联多个"><el-option v-for="product in products" :key="product.productId" :value="product.productId" :label="`${product.productCode || ''} ${product.productName || ''}`" /></el-select></el-form-item>
        <el-form-item label="来源打样单"><el-select v-model="masterDraft.sampleOrderId" clearable filterable remote @visible-change="visible => visible && searchSamples()" :remote-method="searchSamples" :loading="samplesLoading" style="width:100%" placeholder="无关联产品时，选择来源打样单"><el-option v-for="sample in samples" :key="sample.orderId" :value="sample.orderId" :label="`${sample.orderNo} ${sample.sampleProductName || ''}`" /></el-select></el-form-item>
        <el-form-item label="整套分组"><el-input v-model="masterDraft.setCode" maxlength="64" placeholder="可选，同套菲林共用的分组编码" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="masterDraft.purpose" aria-label="用途" type="textarea" :rows="3" /></el-form-item>
        <template v-if="!masterDraft.id && !masterLoading">
          <el-form-item label="图纸">
            <AttachmentCenter :key="masterDraftKey" ref="masterDrawingCenter" variant="upload-only" title="上传图纸" source-type="print_artwork_version" :draft-id="masterDraftKey" :auto-bind="false" :locked="saving" :actions="['remove']" />
          </el-form-item>
          <el-form-item label="技术要求"><el-input v-model="masterDraft.technicalSpec" aria-label="初始版本技术要求" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
          <p class="form-note">保存时自动建立初始草稿版本，图纸一并保存；也可先建档，稍后补充图纸。</p>
        </template>
      </el-form>
      <template #footer><el-button :disabled="masterBusy" @click="masterVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="masterLoading || masterBusy" @click="saveMaster">保存菲林</el-button></template>
    </el-dialog>

    <el-drawer v-model="versionsVisible" size="min(1120px, 96vw)" :title="`版本与图纸 · ${detail?.artworkNo || ''}`">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false"><el-button link @click="reloadSelected">重新加载</el-button></el-alert>
      <div v-loading="detailLoading" class="detail-body">
      <template v-if="detail">
        <div class="identity"><strong>{{ detail.artworkNo }} <span>{{ detail.name }}</span></strong><p>{{ detail.productsText || '未关联产品' }}</p></div>
        <div v-if="detail.versions?.length" class="version-layout">
          <aside class="version-list"><h3>所属版本 <el-button v-hasPermi="['engineering:film:edit']" link type="primary" @click="openVersionForm()">新增</el-button></h3><button v-for="version in detail.versions" :key="version.versionId" class="version-choice" :class="{ chosen: selectedVersionId === version.versionId }" @click="selectVersion(version.versionId)"><span>{{ version.versionNo }} <small v-if="version.versionId === detail.defaultVersionId">当前</small></span><el-tag size="small" :type="ArtworkVersionStatusEnum.getTagProps(version.status || '').type">{{ ArtworkVersionStatusEnum.getLabel(version.status || '') }}</el-tag></button></aside>
          <section v-if="selectedVersion" class="version-content">
            <div class="section-heading"><div><h2>{{ detail.artworkNo }} / {{ selectedVersion.versionNo }}</h2><p>这个版本的图纸和技术要求</p></div><el-tag :type="ArtworkVersionStatusEnum.getTagProps(selectedVersion.status || '').type">{{ ArtworkVersionStatusEnum.getLabel(selectedVersion.status || '') }}</el-tag></div>
            <div class="drawing-title"><strong>{{ selectedVersion.drawingFileName || (selectedVersion.drawingFileId ? `${detail.artworkNo}-${selectedVersion.versionNo} 图纸` : '尚未添加图纸') }}</strong><el-button v-if="selectedVersion.drawingFileId" size="small" :loading="downloading" @click="downloadDrawing">下载图纸</el-button><el-button v-if="selectedVersion.status === ArtworkVersionStatusEnum.DRAFT.value" v-hasPermi="['engineering:film:edit']" size="small" @click="openVersionForm(selectedVersion)">编辑版本 / 图纸</el-button></div>
            <el-alert v-if="drawingError" :title="drawingError" type="error" :closable="false"><el-button link @click="loadDrawing">重试预览</el-button></el-alert>
            <div v-loading="drawingLoading" class="drawing-preview" v-if="selectedVersion.drawingFileId">
              <img v-for="image in drawingImages" :key="image.url" :src="image.url" alt="当前所选菲林版本的图纸" />
              <el-empty v-if="!drawingImages.length && !drawingLoading && !drawingError" description="此格式请下载后使用工程软件查看" :image-size="70" />
            </div>
            <el-empty v-else description="该版本尚无图纸" :image-size="70" />
            <div class="technical"><h3>技术要求</h3><p>{{ selectedVersion.technicalSpec || '未填写' }}</p></div>
            <div class="version-actions">
              <el-button v-if="selectedVersion.status === ArtworkVersionStatusEnum.DRAFT.value" v-hasPermi="['engineering:film:submit']" @click="runVersionAction('submit')">提交审核</el-button>
              <template v-if="selectedVersion.status === ArtworkVersionStatusEnum.REVIEW.value"><el-button v-hasPermi="['engineering:film:approve']" type="success" @click="runVersionAction('approve')">审核通过</el-button><el-button v-hasPermi="['engineering:film:reject']" type="danger" plain @click="runVersionAction('reject')">驳回</el-button></template>
              <el-button v-if="selectedVersion.status === ArtworkVersionStatusEnum.APPROVED.value" v-hasPermi="['engineering:film:release']" type="success" @click="runVersionAction('release')">发布版本</el-button>
              <template v-if="selectedVersion.status === ArtworkVersionStatusEnum.RELEASED.value"><el-button v-if="detail.defaultVersionId !== selectedVersion.versionId" v-hasPermi="['engineering:film:release']" @click="runVersionAction('activate')">设为当前版本</el-button><el-button v-hasPermi="['engineering:film:disable']" type="warning" plain @click="runVersionAction('disable')">停用</el-button><el-button @click="goPlate">去制版</el-button></template>
              <el-button v-hasPermi="['engineering:film:disable']" link @click="showImpact">查看影响</el-button><el-tag v-if="selectedVersion.contentDigest" type="success" size="small">内容已核验</el-tag>
            </div>
            <div class="inline-actions"><el-button @click="showVersionCopies">查看这个版本的菲林片（{{ versionCopies.length }} 张）</el-button><el-button v-hasPermi="['engineering:film:edit']" type="primary" :disabled="selectedVersion.status !== ArtworkVersionStatusEnum.RELEASED.value" @click="openRequest(detail.artworkId, selectedVersion.versionId)">申请此版本出片</el-button></div>
          </section>
        </div>
        <el-empty v-else description="这条菲林已建档，尚未建立版本和图纸"><el-button v-hasPermi="['engineering:film:edit']" type="primary" @click="openVersionForm()">新建版本</el-button></el-empty>
      </template>
      </div>
    </el-drawer>

    <el-drawer v-model="copiesVisible" size="min(1100px, 96vw)" :title="`菲林片与出片记录 · ${detail?.artworkNo || ''}`">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false"><el-button link @click="reloadSelected">重新加载</el-button></el-alert>
      <div v-loading="detailLoading" class="detail-body">
      <template v-if="detail">
        <div class="identity"><strong>{{ detail.artworkNo }} <span>{{ detail.name }}</span></strong><p>{{ detail.productsText || '未关联产品' }}</p></div>
        <div class="copy-toolbar"><span>所属版本</span><el-select v-model="selectedVersionId" aria-label="菲林片所属版本" style="width:200px" :disabled="!detail.versions?.length"><el-option v-for="version in detail.versions" :key="version.versionId" :value="version.versionId" :label="`${version.versionNo}${version.versionId === detail.defaultVersionId ? '（当前版本）' : ''}`" /></el-select><el-button v-hasPermi="['engineering:film:edit']" type="primary" :disabled="!selectedVersion || selectedVersion.status !== ArtworkVersionStatusEnum.RELEASED.value" @click="openRequest(detail.artworkId, selectedVersionId)">申请出片</el-button></div>
        <div class="section-heading compact"><div><h2>菲林片 <span class="count">{{ versionCopies.length }} 张</span></h2><p>已实际回片的记录，逐张编号、存放。</p></div></div>
        <el-table :data="versionCopies" border data-testid="physical-copy-table"><el-table-column label="菲林片编号" prop="filmNo" min-width="140" /><el-table-column label="位置" prop="location" min-width="150" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="FilmCopyStatusEnum.getTagProps(row.status || '').type">{{ FilmCopyStatusEnum.getLabel(row.status || '') }}</el-tag></template></el-table-column><el-table-column label="材质" prop="material" width="80" /><el-table-column label="厚度(mm)" prop="thicknessMm" width="100" /><el-table-column label="尺寸" prop="size" min-width="130" /><el-table-column label="来自申请" min-width="110"><template #default="{ row }">{{ requests.find(request => request.requestId === row.filmRequestId)?.requestNo || '已有菲林片' }}</template></el-table-column><el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === FilmCopyStatusEnum.AVAILABLE.value" v-hasPermi="['engineering:film:archive']" link type="warning" @click="disposeCopy(row, 'archive')">封存</el-button><el-button v-if="[FilmCopyStatusEnum.AVAILABLE.value,FilmCopyStatusEnum.ARCHIVED.value].includes(row.status)" v-hasPermi="['engineering:film:destroy']" link type="danger" @click="disposeCopy(row, 'destroy')">销毁</el-button></template></el-table-column><template #empty>该版本暂无实际菲林片，可以先申请出片</template></el-table>
        <div class="section-heading request-heading"><div><h2>出片申请</h2><p>申请保留原张数，实际回片关联到上面的菲林片记录。</p></div></div>
        <el-alert v-if="requestsError" :title="requestsError" type="error" :closable="false"><el-button link @click="loadRequests()">重新加载申请</el-button></el-alert>
        <el-button link :loading="requestsLoading" @click="reloadSelected">刷新记录</el-button>
        <el-table :data="versionRequests" v-loading="requestsLoading" border data-testid="request-table"><el-table-column label="申请编号" prop="requestNo" width="110" /><el-table-column label="申请" width="75"><template #default="{ row }">{{ row.requestedQuantity }} 张</template></el-table-column><el-table-column label="已回" width="75"><template #default="{ row }">{{ row.receivedQuantity }} 张</template></el-table-column><el-table-column label="未回" width="75"><template #default="{ row }">{{ row.remainingQuantity }} 张</template></el-table-column><el-table-column label="进度" width="110"><template #default="{ row }"><el-tag size="small" :type="Progress.getTagProps(row.progress).type">{{ Progress.getLabel(row.progress) }}</el-tag></template></el-table-column><el-table-column label="期望回片日" prop="dueDate" width="125" /><el-table-column label="说明" prop="remark" min-width="130" show-overflow-tooltip /><el-table-column label="操作" width="115"><template #default="{ row }"><el-button v-if="row.progress !== Progress.RECEIVED.value" v-hasPermi="['engineering:film:edit']" link type="primary" @click="openReceipt(row)">回片登记</el-button><span v-else class="muted">已回齐</span></template></el-table-column><template #empty>该版本还没有出片申请</template></el-table>
      </template>
      </div>
    </el-drawer>

    <el-dialog v-model="requestVisible" title="申请出片" width="640px" append-to-body destroy-on-close>
      <el-alert v-if="requestsError" :title="requestsError" type="error" :closable="false"><el-button link @click="loadRequests()">重试</el-button></el-alert>
      <div v-if="detail" class="form-context">{{ detail.artworkNo }} <strong>{{ detail.name }}</strong></div>
      <el-form v-loading="detailLoading" label-width="110px" @submit.prevent="saveRequest">
        <el-form-item label="菲林版本" required><el-select v-model="requestDraft.versionId" aria-label="申请菲林版本"><el-option v-for="version in releasedVersions" :key="version.versionId" :value="version.versionId" :label="`${version.versionNo}${version.versionId === detail?.defaultVersionId ? '（当前版本）' : ''}`" /></el-select></el-form-item>
        <el-form-item label="申请张数" required><el-input-number v-model="requestDraft.quantity" aria-label="申请张数" :min="1" :max="500" :precision="0" /><span class="unit">张</span></el-form-item>
        <div class="form-pair"><el-form-item label="材质" required><el-input v-model="requestDraft.material" aria-label="片基材质" /></el-form-item><el-form-item label="厚度(mm)" required><el-input-number v-model="requestDraft.thicknessMm" aria-label="片基厚度" :min="0.001" :precision="3" :step="0.025" :controls="false" /></el-form-item></div>
        <el-form-item label="尺寸" required><el-input v-model="requestDraft.size" aria-label="菲林片尺寸" placeholder="例如：300 × 200 mm" /></el-form-item>
        <el-form-item label="期望回片日" required><el-date-picker v-model="requestDraft.dueDate" aria-label="期望回片日" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="requestDraft.remark" aria-label="出片说明" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <p class="form-note">提交申请后不会生成菲林片；实际收到后再逐张登记编号和位置。</p>
      <template #footer><el-button @click="requestVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="detailLoading || requestsLoading || !!requestsError || !requestDraft.versionId" @click="saveRequest">提交出片申请</el-button></template>
    </el-dialog>

    <el-dialog v-model="receiptVisible" title="回片登记" width="700px" append-to-body destroy-on-close>
      <template v-if="receiptRequest && detail">
        <div class="form-context">{{ detail.artworkNo }} / {{ versionOf(receiptRequest.versionId)?.versionNo }} <strong>{{ receiptRequest.requestNo }}</strong></div>
        <div class="receipt-summary"><span>申请 <strong>{{ receiptRequest.requestedQuantity }}</strong> 张</span><span>已回 <strong>{{ receiptRequest.receivedQuantity }}</strong> 张</span><span>未回 <strong>{{ remainingReceipt }}</strong> 张</span></div>
        <div class="copy-toolbar"><span>本次回片</span><el-input-number :model-value="receiptRows.length" :min="1" :max="remainingReceipt" :precision="0" aria-label="本次回片张数" @update:model-value="resizeReceipt" /><span>张</span></div>
        <div class="receipt-labels"><span>菲林片编号</span><span>存放位置</span></div>
        <div v-for="(row, index) in receiptRows" :key="index" class="receipt-row"><el-input v-model="row.filmNo" maxlength="64" :aria-label="`第${index + 1}张编号`" placeholder="例如 FL-003" /><el-input v-model="row.location" maxlength="200" :aria-label="`第${index + 1}张位置`" placeholder="例如 A架 · 02格" /></div>
        <p class="form-note">编号已默认填写，可改为实物已有编号。可以分次回来，本次登记几张就增加几张实物记录。</p>
      </template>
      <template #footer><el-button @click="receiptVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveReceipt">确认本次回片</el-button></template>
    </el-dialog>
    <el-dialog v-model="versionFormVisible" :title="versionDraft.versionId ? '编辑版本 / 图纸' : '新增版本'" width="700px" append-to-body destroy-on-close>
      <el-form label-width="90px"><el-form-item label="所属菲林">{{ detail?.artworkNo }} {{ detail?.name }}</el-form-item><el-form-item label="版本号" required><el-input v-model="versionDraft.versionNo" maxlength="32" /></el-form-item><el-form-item label="技术要求"><el-input v-model="versionDraft.technicalSpec" type="textarea" :rows="4" maxlength="2000" /></el-form-item><el-form-item label="图纸"><template v-if="versionDraft.versionId"><AttachmentCenter ref="versionDrawingCenter" variant="upload-only" :auto-bind="false" :locked="saving" :actions="['remove']" source-type="print_artwork_version" :source-id="versionDraft.versionId" :draft-id="`print-version-${versionDraft.versionId}`" @uploaded="drawingUploaded" /><span v-if="versionDraft.drawingFileId">图纸已选择，保存版本后绑定</span><el-button v-if="versionDraft.drawingFileId" link type="danger" @click="removeVersionDrawing">移除图纸</el-button></template><span v-else class="muted">先保存版本，再编辑版本上传图纸。</span></el-form-item></el-form>
      <template #footer><el-button @click="versionFormVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveVersion">保存版本</el-button></template>
    </el-dialog>
    <el-dialog v-model="impactVisible" title="版本影响范围" width="700px" append-to-body><pre class="impact-text">{{ impactText }}</pre></el-dialog>
    <el-dialog v-model="resourceSearchVisible" title="跨资源搜索" width="1000px" append-to-body><el-input v-model="resourceQuery" placeholder="菲林、版本、菲林片、网版或网框编号" @keyup.enter="searchResources"><template #append><el-button :loading="resourceLoading" @click="searchResources">搜索</el-button></template></el-input><el-table :data="resourceRows" v-loading="resourceLoading"><el-table-column label="匹配资源" prop="displayName" /><el-table-column label="菲林编号" prop="artworkNo" width="140" /><el-table-column label="版本" prop="versionNo" width="100" /><el-table-column label="操作" width="110"><template #default="{row}"><el-button v-if="row.artworkId" link type="primary" @click="openResource(row)">打开菲林</el-button></template></el-table-column></el-table></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { printingArtworkApi as api } from '@/api/product/film'
import { printingSearchApi, type PrintSearchRow } from '@/api/engineering/printingSearch'
import { getProductInfo, listProductPage, searchProduct } from '@/api/product'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import type { SampleOrder } from '@/types/sales/sampleOrder'
import type { ProductItem } from '@/types/product'
import type { ArtworkFilm, ArtworkListRow, ArtworkVersion, ArtworkCommand, FilmRequest } from '@/types/engineering/printingArtwork'
import { ArtworkVersionStatusEnum, FilmCopyStatusEnum, FilmRequestProgressEnum as Progress } from '@/enums/product/film'
import { downloadFile, fileKind, renderFile, type FileImage } from '@/components/product/productFilePreviewUtils'
import { filmError, newFilmRequestId, useFilmManagement } from '../composables/useFilmManagement'

defineOptions({ name: 'FilmManagement' })
const router = useRouter()
const management = useFilmManagement()
const { filters, page, rows, listLoading, listError, detail, detailLoading, detailError, requests, requestsLoading, requestsError,
  selectedArtworkId, selectedVersionId, selectedVersion, releasedVersions, versionCopies, versionRequests,
  loadRows, loadDetail, loadRequests, refreshDetails, applyRequest, receiveRequest } = management
const families = ['面板','上线','下线']
const saving = ref(false)
const versionsVisible = ref(false)
const copiesVisible = ref(false)
const versionOf = (id: number) => detail.value?.versions?.find(row => row.versionId === id)
async function openVersions(row: ArtworkListRow | number) {
  copiesVisible.value = false; versionsVisible.value = true
  await loadDetail(typeof row === 'number' ? row : row.artworkId)
}
async function openCopies(row: ArtworkListRow) {
  versionsVisible.value = false; copiesVisible.value = true
  if (await loadDetail(row.artworkId)) await loadRequests(row.artworkId)
}
async function showVersionCopies() { versionsVisible.value = false; copiesVisible.value = true; await loadRequests() }
function selectVersion(versionId: number) { selectedVersionId.value = versionId }
async function reloadSelected() { await refreshDetails() }
function resetFilters() { filters.keyword=''; filters.family=''; loadRows(1) }

const masterVisible=ref(false), masterLoading=ref(false)
const masterDraft=reactive({ id:undefined as number|undefined,no:'',name:'',family:'面板',purpose:'',setCode:'',productIds:[] as number[],sampleOrderId:undefined as number|undefined,revision:0,technicalSpec:'' })
const masterDrawingCenter=ref<InstanceType<typeof AttachmentCenter>>()
const masterDraftKey=ref('')
const masterBusy=computed(()=>saving.value || !!masterDrawingCenter.value?.uploading)
const products=ref<ProductItem[]>([]), productsLoading=ref(false)
const samples=ref<SampleOrder[]>([]), samplesLoading=ref(false)
let masterRequestId='', masterGeneration=0, productGeneration=0, sampleGeneration=0
async function searchProducts(keyword='') {
  const generation=++productGeneration; productsLoading.value=true
  try {
    const result:any=keyword.trim() ? await searchProduct(keyword.trim()) : await listProductPage({pageNum:1,pageSize:50})
    if(generation!==productGeneration) return
    const incoming:ProductItem[]=keyword.trim() ? result.data || [] : result.data?.records || result.data?.rows || []
    const selected=products.value.filter(row=>masterDraft.productIds.includes(row.productId))
    products.value=Array.from(new Map([...selected,...incoming].map(row=>[row.productId,row])).values())
  } catch(error) { ElMessage.error(filmError(error,'产品查询失败')) }
  finally { if(generation===productGeneration) productsLoading.value=false }
}
async function searchSamples(orderNo='') {
  const generation=++sampleGeneration; samplesLoading.value=true
  try {
    const result=await sampleOrderApi.page({pageNum:1,pageSize:30,orderNo:orderNo.trim() || undefined})
    if(generation===sampleGeneration) {
      const selected=samples.value.filter(row=>row.orderId===masterDraft.sampleOrderId)
      samples.value=Array.from(new Map([...selected,...(result.data?.records || [])].map(row=>[row.orderId,row])).values())
    }
  } catch(error) { ElMessage.error(filmError(error,'打样单查询失败')) }
  finally { if(generation===sampleGeneration) samplesLoading.value=false }
}
async function openMaster(row?:ArtworkListRow) {
  if(masterBusy.value) return
  const generation=++masterGeneration;masterRequestId=newFilmRequestId();masterVisible.value=true;masterLoading.value=!!row
  masterDraftKey.value=`film-initial-${masterRequestId}`
  Object.assign(masterDraft,{id:undefined,no:`PW-${new Date().toISOString().slice(2,7).replace('-','')}-${String(Date.now()%1000).padStart(3,'0')}`,name:'',family:filters.family || '面板',purpose:'',setCode:'',productIds:[],sampleOrderId:undefined,revision:0,technicalSpec:''})
  try {
    if(row) {
      const result:any=await api.detail(row.artworkId)
      if(generation!==masterGeneration || !masterVisible.value) return
      const item=result.data
      Object.assign(masterDraft,{id:item.artworkId,no:item.artworkNo,name:item.name,family:item.family,purpose:item.purpose||'',setCode:item.setCode||'',productIds:[...(item.productIds||[])],sampleOrderId:item.sampleOrderId||undefined,revision:item.revision})
      const missing=masterDraft.productIds.filter(id=>!products.value.some(product=>product.productId===id))
      const loaded=await Promise.allSettled(missing.map(id=>getProductInfo(id)))
      if(generation!==masterGeneration) return
      for(const result of loaded) if(result.status==='fulfilled' && result.value.data) products.value.push(result.value.data as ProductItem)
    }
    await searchProducts()
  } catch(error) { ElMessage.error(filmError(error,'菲林资料加载失败'));masterVisible.value=false }
  finally { if(generation===masterGeneration) masterLoading.value=false }
}
async function saveMaster() {
  if(masterBusy.value || masterLoading.value) return
  if(!masterDraft.no.trim() || !masterDraft.name.trim()) return ElMessage.warning('请填写菲林编号和名称')
  if(!masterDraft.productIds.length && !masterDraft.sampleOrderId) return ElMessage.warning('请关联至少一个产品或选择来源打样单')
  const drawings=masterDraft.id ? [] : masterDrawingCenter.value?.getPendingEntries() || []
  if(drawings.length>1) return ElMessage.warning('初始版本只保存一份图纸，请移除多余文件后再保存')
  if(drawings.length && !drawings[0].documentType) return ElMessage.warning('请选择图纸资料类型（二级）')
  saving.value=true
  const command:ArtworkCommand={requestId:masterRequestId,expectedRevision:masterDraft.revision,artworkNo:masterDraft.no.trim(),name:masterDraft.name.trim(),family:masterDraft.family,purpose:masterDraft.purpose.trim()||undefined,setCode:masterDraft.setCode.trim()||undefined,productIds:[...masterDraft.productIds],sampleOrderId:masterDraft.sampleOrderId}
  if(!masterDraft.id) {
    command.drawingFileId=drawings[0]?.attachmentId
    command.drawingDocumentType=drawings[0]?.documentType||undefined
    command.technicalSpec=masterDraft.technicalSpec.trim()||undefined
  }
  try {
    if(masterDraft.id) await api.edit(masterDraft.id,command)
    else {
      await api.createWithInitialVersion(command)
      masterDrawingCenter.value?.clearPending()
    }
    masterVisible.value=false;ElMessage.success('菲林已保存');await loadRows()
    if(masterDraft.id===selectedArtworkId.value) await refreshDetails()
  } catch(error) { ElMessage.error(filmError(error,'菲林保存失败，输入已保留')) }
  finally { saving.value=false }
}

const requestVisible=ref(false)
const requestDraft=reactive({versionId:undefined as number|undefined,quantity:2,material:'PET',thicknessMm:0.125,size:'',dueDate:'',remark:''})
let applicationRequestId='', applicationGeneration=0
async function openRequest(row:ArtworkListRow|number,versionId?:number) {
  const artworkId=typeof row==='number' ? row : row.artworkId
  const generation=++applicationGeneration;requestVisible.value=true;applicationRequestId=newFilmRequestId()
  Object.assign(requestDraft,{versionId:undefined,quantity:2,material:'PET',thicknessMm:0.125,size:'',dueDate:'',remark:''})
  if(detail.value?.artworkId!==artworkId && !await loadDetail(artworkId)) return
  if(generation!==applicationGeneration || !requestVisible.value || detail.value?.artworkId!==artworkId) return
  requestDraft.versionId=versionId ?? releasedVersions.value.find(version=>version.versionId===detail.value?.defaultVersionId)?.versionId ?? releasedVersions.value[0]?.versionId
  if(!releasedVersions.value.length) ElMessage.warning('这条菲林尚无已发布版本，请先维护版本和图纸')
  await loadRequests(artworkId)
}
async function saveRequest() {
  if(saving.value || detailLoading.value || requestsLoading.value || requestsError.value || !requestDraft.versionId) return
  if(!requestDraft.material.trim() || !requestDraft.size.trim() || !requestDraft.dueDate || !requestDraft.thicknessMm || !requestDraft.quantity) return ElMessage.warning('请填写申请张数、材质、厚度、尺寸和期望回片日期')
  const version=versionOf(requestDraft.versionId)
  if(!version) return ElMessage.warning('菲林版本已变化，请重新打开申请')
  saving.value=true
  try {
    await applyRequest(version.versionId,{requestId:applicationRequestId,expectedRevision:version.revision ?? 0,quantity:requestDraft.quantity,material:requestDraft.material.trim(),thicknessMm:requestDraft.thicknessMm,size:requestDraft.size.trim(),dueDate:requestDraft.dueDate,remark:requestDraft.remark.trim()||undefined})
    selectedVersionId.value=version.versionId;requestVisible.value=false;versionsVisible.value=false;copiesVisible.value=true;ElMessage.success('出片申请已保存，等待实际回片')
  } catch(error) { ElMessage.error(filmError(error,'出片申请保存失败，输入已保留')) }
  finally { saving.value=false }
}

const receiptVisible=ref(false), receiptRequest=ref<FilmRequest>()
const receiptRows=ref<{filmNo:string;location:string}[]>([])
const remainingReceipt=computed(()=>receiptRequest.value?.remainingQuantity || 0)
let receiptCommandId=''
function openReceipt(request:FilmRequest) {
  if(request.remainingQuantity<=0) return
  receiptRequest.value={...request};receiptRows.value=[];resizeReceipt(1);receiptCommandId=newFilmRequestId();receiptVisible.value=true
}
function resizeReceipt(count: number | undefined) {
  const size = Math.max(1, Math.min(remainingReceipt.value, count || 1))
  const request = receiptRequest.value
  if (!request) return
  const retained = receiptRows.value.slice(0, size)
  const used = new Set([
    ...(detail.value?.films || []).map(copy => copy.filmNo.trim().toUpperCase()),
    ...retained.map(copy => copy.filmNo.trim().toUpperCase()),
  ])
  // 只预填可修改的表单建议；实物唯一性与并发仍由原回片接口校验。
  const prefix = request.requestNo.length > 54 ? `CP-${request.requestId}` : request.requestNo
  let serial = request.receivedQuantity + retained.length + 1
  receiptRows.value = Array.from({ length: size }, (_, index) => {
    if (retained[index]) return retained[index]
    let filmNo: string
    do { filmNo = `${prefix}-${String(serial++).padStart(3, '0')}` }
    while (used.has(filmNo.toUpperCase()))
    used.add(filmNo.toUpperCase())
    return { filmNo, location: '' }
  })
}
async function saveReceipt() {
  if(saving.value || !receiptRequest.value) return
  if(receiptRows.value.some(row=>!row.filmNo.trim() || !row.location.trim())) return ElMessage.warning('请逐张填写编号和存放位置')
  saving.value=true
  try {
    await receiveRequest(receiptRequest.value.requestId,{requestId:receiptCommandId,expectedRevision:receiptRequest.value.revision,copies:receiptRows.value.map(row=>({filmNo:row.filmNo.trim(),location:row.location.trim()}))})
    receiptVisible.value=false;ElMessage.success('本次回片已登记，申请原张数保持不变')
  } catch(error) { ElMessage.error(filmError(error,'回片登记失败，输入已保留，请核对后重试')) }
  finally { saving.value=false }
}

const versionFormVisible=ref(false)
const versionDraft=reactive({versionId:undefined as number|undefined,versionNo:'',technicalSpec:'',drawingFileId:undefined as number|undefined,revision:0,artworkRevision:0})
const versionDrawingCenter=ref<InstanceType<typeof AttachmentCenter>>()
let originalVersionDrawingFileId:number|undefined
let versionCommandId=''
function openVersionForm(version?:ArtworkVersion) {
  if(!detail.value) return
  versionCommandId=newFilmRequestId()
  originalVersionDrawingFileId=version?.drawingFileId||undefined
  Object.assign(versionDraft,{versionId:version?.versionId,versionNo:version?.versionNo||`V${(detail.value.versions?.length||0)+1}`,technicalSpec:version?.technicalSpec||'',drawingFileId:version?.drawingFileId||undefined,revision:version?.revision||0,artworkRevision:detail.value.revision||0})
  versionFormVisible.value=true
}
function drawingUploaded(payload:{attachmentId:number}) {versionDraft.drawingFileId=payload.attachmentId}
function removeVersionDrawing() {versionDraft.drawingFileId=undefined;versionDrawingCenter.value?.clearPending()}
async function saveVersion() {
  if(saving.value || versionDrawingCenter.value?.uploading || !detail.value) return
  const drawings=versionDrawingCenter.value?.getPendingEntries()||[]
  if(drawings.length>1) return ElMessage.warning('一个版本只保存一份图纸，请移除多余文件')
  if(drawings.length && !drawings[0].documentType) return ElMessage.warning('请选择图纸资料类型（二级）')
  if(!versionDraft.versionNo.trim()) return ElMessage.warning('请填写版本号')
  saving.value=true
  try {
    const command:ArtworkCommand={requestId:versionCommandId,expectedRevision:versionDraft.versionId ? versionDraft.revision : versionDraft.artworkRevision,versionNo:versionDraft.versionNo.trim(),technicalSpec:versionDraft.technicalSpec||undefined,drawingFileId:drawings[0]?.attachmentId??(versionDraft.drawingFileId?originalVersionDrawingFileId:undefined),drawingDocumentType:drawings[0]?.documentType||undefined}
    const result:any=versionDraft.versionId ? await api.editVersion(versionDraft.versionId,command) : await api.createVersion(detail.value.artworkId,command)
    const id=versionDraft.versionId || result.data?.versionId
    versionDrawingCenter.value?.clearPending()
    versionFormVisible.value=false;await Promise.all([refreshDetails(),loadRows()]);if(id) selectedVersionId.value=id
    ElMessage.success('版本已保存')
  } catch(error) {ElMessage.error(filmError(error,'版本保存失败，输入已保留'))}
  finally {saving.value=false}
}
const versionActionLabels={submit:'提交审核',approve:'审核通过',reject:'驳回',release:'发布版本',activate:'设为当前版本',disable:'停用版本'}
type VersionAction=keyof typeof versionActionLabels
async function reason(title:string,message=`请填写${title}原因`) {
  const result=await ElMessageBox.prompt(message,title,{inputPlaceholder:'原因（必填）',inputValidator:value=>value?.trim() && value.trim().length<=500 ? true : '原因须为1至500字'})
  return result.value.trim()
}
function impactSummary(value:any) {return `路线 ${value.routingIds?.length||0} 条，规范 ${value.specVersionIds?.length||0} 个，未结工单 ${value.orderIds?.length||0} 张，在用执行 ${value.executionIds?.length||0} 个`}
async function runVersionAction(action:VersionAction) {
  if(saving.value || !selectedVersion.value || !detail.value) return
  const version={...selectedVersion.value},artwork={...detail.value};saving.value=true
  try {
    const command:ArtworkCommand={requestId:newFilmRequestId(),expectedRevision:action==='activate' ? artwork.revision||0 : version.revision||0}
    if(action==='reject') command.reason=await reason('驳回')
    else if(action==='disable') {
      const result:any=await api.impact(version.versionId)
      command.impactToken=result.data?.impactToken;command.reason=await reason('停用版本',`${impactSummary(result.data)}；请填写停用原因`)
    } else if(action==='activate' && artwork.defaultVersionId && artwork.defaultVersionId!==version.versionId) {
      try {await ElMessageBox.confirm('设为当前版本时，是否同时停用原当前版本？','切换当前版本',{confirmButtonText:'同时停用原版',cancelButtonText:'仅切换，保留原版',distinguishCancelAndClose:true});command.disablePrevious=true}
      catch(error) {if(error!=='cancel') throw error;command.disablePrevious=false}
      if(command.disablePrevious) {const result:any=await api.impact(artwork.defaultVersionId);command.impactToken=result.data?.impactToken;command.reason=await reason('同时停用原版')}
    } else await ElMessageBox.confirm(`确认对 ${artwork.artworkNo} / ${version.versionNo} 执行${versionActionLabels[action]}？`,versionActionLabels[action])
    await api[action](version.versionId,command);ElMessage.success(`${versionActionLabels[action]}成功`);await Promise.all([refreshDetails(artwork.artworkId),loadRows()])
  } catch(error) {if(error!=='cancel' && error!=='close') ElMessage.error(filmError(error,'版本操作失败'))}
  finally {saving.value=false}
}
const impactVisible=ref(false),impactText=ref('')
async function showImpact() {
  if(!selectedVersion.value) return
  try {const result:any=await api.impact(selectedVersion.value.versionId);impactText.value=`${impactSummary(result.data)}\n\n${JSON.stringify(result.data,null,2)}`;impactVisible.value=true}
  catch(error) {ElMessage.error(filmError(error,'影响范围加载失败'))}
}
function goPlate() {if(selectedVersion.value) router.push({path:'/engineering/print-resources',query:{versionId:String(selectedVersion.value.versionId)}})}
async function disposeCopy(copy:ArtworkFilm,action:'archive'|'destroy') {
  if(saving.value) return
  saving.value=true
  try {
    const label=action==='archive' ? '封存' : '销毁'
    const command:ArtworkCommand={requestId:newFilmRequestId(),expectedRevision:copy.revision||0,reason:await reason(label,`请填写菲林片 ${copy.filmNo} 的${label}原因`)}
    if(action==='archive') await api.archiveFilm(copy.filmId,command);else await api.destroyFilm(copy.filmId,command)
    ElMessage.success(`${label}成功`);await Promise.all([refreshDetails(),loadRows()])
  } catch(error) {if(error!=='cancel' && error!=='close') ElMessage.error(filmError(error,'菲林片处置失败'))}
  finally {saving.value=false}
}

const drawingImages=ref<FileImage[]>([]),drawingLoading=ref(false),drawingError=ref(''),downloading=ref(false)
let drawingGeneration=0
function clearDrawing() {drawingImages.value.forEach(image=>URL.revokeObjectURL(image.url));drawingImages.value=[]}
const drawingFile=computed(()=>selectedVersion.value?.drawingFileId ? {id:selectedVersion.value.drawingFileId,fileName:selectedVersion.value.drawingFileName||`${detail.value?.artworkNo}-${selectedVersion.value.versionNo}`,fileType:selectedVersion.value.drawingFileType||undefined} : undefined)
async function loadDrawing() {
  const generation=++drawingGeneration;clearDrawing();drawingError.value='';drawingLoading.value=false
  const file=drawingFile.value
  if(!versionsVisible.value || !file || fileKind(file)==='other') return
  drawingLoading.value=true
  try {const images=await renderFile({id:file.id,kind:fileKind(file)});if(generation===drawingGeneration) drawingImages.value=images;else images.forEach(image=>URL.revokeObjectURL(image.url))}
  catch(error) {if(generation===drawingGeneration) drawingError.value=filmError(error,'图纸预览失败，请重试')}
  finally {if(generation===drawingGeneration) drawingLoading.value=false}
}
async function downloadDrawing() {
  if(!drawingFile.value) return
  downloading.value=true
  try {await downloadFile(drawingFile.value)}catch(error) {ElMessage.error(filmError(error,'图纸下载失败'))}finally {downloading.value=false}
}
watch([versionsVisible,()=>selectedVersion.value?.drawingFileId],()=>loadDrawing())
onBeforeUnmount(()=>{++drawingGeneration;clearDrawing()})

const resourceSearchVisible=ref(false),resourceQuery=ref(''),resourceRows=ref<PrintSearchRow[]>([]),resourceLoading=ref(false)
let resourceGeneration=0
function openResourceSearch() {resourceSearchVisible.value=true;resourceQuery.value=filters.keyword}
async function searchResources() {
  if(!resourceQuery.value.trim()) return
  const generation=++resourceGeneration;resourceLoading.value=true
  try {const result:any=await printingSearchApi.search({q:resourceQuery.value.trim(),pageNum:1,pageSize:30});if(generation===resourceGeneration) resourceRows.value=result.data?.records||[]}
  catch(error) {ElMessage.error(filmError(error,'资源搜索失败'))}
  finally {if(generation===resourceGeneration) resourceLoading.value=false}
}
async function openResource(row:PrintSearchRow) {if(row.artworkId) {resourceSearchVisible.value=false;await openVersions(row.artworkId);if(row.versionId) selectedVersionId.value=row.versionId}}
onMounted(()=>loadRows(1))
</script>

<style scoped>
.prototype{font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif;color:#25334a;min-height:100vh;background:#f4f6f9;font-size:14px}.topbar{height:62px;background:white;border-bottom:1px solid #e5e9ef;display:flex;align-items:center;justify-content:space-between;padding:0 36px}.topbar strong{font-size:21px;letter-spacing:.6px;color:#285bbc}.topbar strong span{color:#526176;border-left:1px solid #e0e5ed;margin-left:18px;padding-left:18px;font-size:14px;font-weight:500;letter-spacing:0}.preview-badge{font-size:12px;color:#6b7890;background:#f1f4fa;border:1px solid #e2e8f2;padding:7px 12px;border-radius:5px}main{max-width:1600px;margin:auto;padding:20px}.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}.breadcrumb{font-size:12px;color:#8793a5;margin-bottom:12px}h1{font-size:25px;margin:0 0 9px;font-weight:600;letter-spacing:.4px}.page-heading p{margin:0;color:#7e8a9c;font-size:13px}.list-card{background:white;border:1px solid #e3e8ef;border-radius:7px;overflow:hidden;box-shadow:0 2px 6px #233d5a03}.filters{display:flex;align-items:center;gap:12px;padding:20px}.search-input{max-width:340px}.list-count{margin-left:auto;color:#8793a5;font-size:12px}.list-foot{font-size:12px;color:#8793a5;padding:18px 20px;border-top:1px solid #eef1f6}.muted{color:#98a2b1}.pending{color:#c77a18;font-weight:600}.master-table :deep(th.el-table__cell){background:#f7f9fc;color:#68768a;font-weight:500}.master-table :deep(td.el-table__cell){padding:19px 0}.identity{background:#f5f8fc;border:1px solid #e3eaf4;border-radius:6px;padding:17px 20px;margin-bottom:24px}.identity strong{font-size:17px}.identity strong span{margin-left:12px;font-size:15px;font-weight:500}.identity p{margin:10px 0 0;font-size:12px;color:#78869b}.version-layout{display:grid;grid-template-columns:165px minmax(0,1fr);gap:24px}.version-list h3,.technical h3{font-size:13px;font-weight:500;margin:0 0 14px;color:#7a889c}.version-choice{display:flex;width:100%;flex-direction:column;gap:10px;align-items:flex-start;border:1px solid #e5eaf2;background:white;border-radius:6px;padding:16px;margin-bottom:10px;cursor:pointer;color:#33445c;font-size:16px}.version-choice.chosen{border-color:#7fa6ec;background:#f1f6ff}.version-choice small{font-size:10px;color:#3975d7;margin-left:10px}.section-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.section-heading h2{font-size:17px;font-weight:600;margin:0}.section-heading p{font-size:12px;color:#8793a5;margin:8px 0 0}.drawing-title{display:flex;align-items:center;justify-content:space-between;font-size:13px;margin-bottom:10px;gap:10px}.drawing-preview{border:1px solid #dfe6ee;border-radius:5px;overflow:hidden;background:white}.drawing-preview svg{width:100%;display:block}.drawing-preview img{display:block;width:100%;max-height:450px;object-fit:contain}.drawing-preview iframe{width:100%;height:450px;border:0}.technical{padding:18px 0}.technical p{white-space:pre-wrap;font-size:13px;line-height:1.8;margin:0}.inline-actions{border-top:1px solid #e9edf4;padding-top:18px;display:flex;gap:8px;flex-wrap:wrap}.copy-toolbar{display:flex;align-items:center;gap:12px;margin-bottom:24px}.copy-toolbar>span{color:#67758a;font-size:13px}.copy-toolbar .el-button{margin-left:auto}.compact{margin-bottom:14px}.count{font-weight:400;color:#8c98aa;font-size:13px;margin-left:8px}.request-heading{margin-top:34px}.form-context{background:#f4f7fc;color:#667890;padding:13px 15px;border-radius:5px;margin-bottom:24px}.form-context strong{color:#33445c;margin-left:10px}.unit{margin-left:10px;color:#8b96a6}.form-pair{display:grid;grid-template-columns:1fr 1fr}.form-pair :deep(.el-input-number){width:100%}.form-note{font-size:12px;color:#8c96a5;line-height:1.8;margin-top:18px}.receipt-summary{display:flex;gap:35px;background:#f8fafc;padding:16px;margin-bottom:22px;color:#7e8b9e}.receipt-summary strong{color:#34445d;font-size:18px;margin:0 6px}.receipt-labels,.receipt-row{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:12px}.receipt-labels{color:#7d8898;font-size:12px}.prototype-toast{position:fixed;z-index:10000;top:22px;left:50%;transform:translateX(-50%);padding:12px 22px;border:1px solid #cbdcf7;border-radius:6px;background:#f0f6ff;color:#3266ae;box-shadow:0 5px 20px #244a7f14;max-width:80vw} :deep(.el-drawer__header){margin-bottom:0;padding:24px;border-bottom:1px solid #edf0f4;color:#36445c} :deep(.el-drawer__body){padding:24px} :deep(.el-dialog){border-radius:9px} :deep(.el-dialog__header){padding:8px 8px 18px;margin-bottom:8px} :deep(.el-dialog__footer){border-top:1px solid #edf0f4;padding-top:18px} :deep(.el-table__empty-text){color:#95a0ae;font-size:12px}
@media(max-width:800px){main{padding:22px 16px}.topbar{padding:0 16px}.preview-badge{font-size:10px}.page-heading{gap:12px}.page-heading p{max-width:220px;line-height:1.6}.version-layout{grid-template-columns:1fr}.version-list{display:flex;gap:10px;align-items:flex-start}.version-list h3{display:none}.version-choice{max-width:150px}.filters{padding:14px;flex-wrap:wrap}.list-count{width:100%}.form-pair{grid-template-columns:1fr}}
.pagination{padding:16px 20px;justify-content:flex-end}.detail-body{min-height:140px}.version-actions{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 20px}.version-list h3{display:flex;justify-content:space-between;align-items:center}.impact-text{white-space:pre-wrap;font-size:12px;line-height:1.8}.film-management .page-heading .breadcrumb{display:none}.drawing-preview{min-height:220px}.drawing-preview img{max-height:none}
</style>
