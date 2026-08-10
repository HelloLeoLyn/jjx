<template>
  <div class="wb-page">
    <!-- ① 样品单信息（底部内嵌汇总） -->
    <el-card class="wb-card" shadow="never">
      <template #header>
        <span style="font-weight:600">样品单信息</span>
        <span class="desc">Round {{ card.sampleRound || 1 }} · {{ card.orderNo || '' }}</span>
        <span style="float:right">
          <el-button size="small" icon="CopyDocument" @click="openHistoryCopy">📋 从历史打样复制</el-button>
          <el-button link type="primary" style="margin-left:8px" @click="goBack">← 返回打样平台</el-button>
        </span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="单号">{{ card.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ card.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ card.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="轮次">Round {{ card.sampleRound || 1 }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" type="warning">工程打样中</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="接单人">{{ card.engineeringAcceptor || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="summary-inline">
        <div class="summary-item">
          <div class="summary-num">{{ doneCount }} / {{ planList.length }}</div>
          <div class="summary-label">工序完成</div>
        </div>
        <div class="summary-item">
          <div class="summary-num">{{ summary.totalHours ?? '-' }}</div>
          <div class="summary-label">总工时(小时)</div>
        </div>
        <div class="summary-item">
          <div class="summary-num">¥{{ summary.materialCost ?? '-' }}</div>
          <div class="summary-label">材料成本(估算)</div>
        </div>
        <div class="summary-tip">工时=已完成工序耗时之和；材料成本=材料用量×标准单价</div>
      </div>
      <div v-if="!card.engineeringAcceptor" class="accept-row">
        <el-button type="primary" @click="handleAccept" :loading="saving">✅ 工程接单</el-button>
        <el-button type="danger" plain style="margin-left:8px" @click="handleReject">✋ 工程拒单</el-button>
      </div>
      <div v-else class="accept-row">
        <el-tag type="success">已接单：{{ card.engineeringAcceptor }}</el-tag>
        <span style="margin-left:12px;color:#909399;font-size:12px">接单后开始记录打样过程</span>
      </div>
    </el-card>

    <!-- 轮次切换（DEV-500） -->
    <el-tabs v-model="activeRound" style="margin-bottom:10px">
      <el-tab-pane
        v-for="r in roundList"
        :key="r.roundNo"
        :name="String(r.roundNo)"
        :label="`Round ${r.roundNo}${r.roundNo === (card.sampleRound || 1) ? '（当前）' : ''}`"
      />
    </el-tabs>

    <template v-if="isCurrentRound">
      <!-- ② 中间：左选择器 + 右工序计划 -->
      <div class="mid-row">
        <el-card class="col-picker" shadow="never">
          <template #header>
            <span style="font-weight:600">① 选择作业项目</span>
            <span class="desc">选结构 → 拖拽工序到右侧卡片组合</span>
          </template>
          <WorkProjectPicker />
        </el-card>

        <el-card class="col-plan" shadow="never">
          <template #header>
            <span style="font-weight:600">② 打样工序计划</span>
            <span class="desc">在哪个标签编辑，卡片就属于哪个项目结构</span>
            <el-button size="small" :type="batchMode ? 'warning' : 'default'" icon="Grid" @click="toggleBatchMode" style="float: right; margin-top: -2px">
            {{ batchMode ? '退出批量编辑' : '批量编辑' }}
          </el-button>
            <el-button
              type="success" size="small" :loading="savingPlan" @click="savePlan"
              style="float: right; margin-top: -2px"
            >💾 保存工序计划</el-button>
          </template>

          <!-- 常用物料快捷区（批次3：历史高频物料） -->
          <div v-if="frequentMaterials.length" class="freq-materials">
            <span class="freq-label">⭐ 常用物料</span>
            <el-tooltip
              v-for="fm in frequentMaterials" :key="fm.materialId || fm.name"
              :content="`点击添加到当前卡片材料（${fm.count}次）`" placement="top"
            >
              <el-tag
                class="freq-tag" size="small" effect="plain"
                @click="addFrequentMaterial(fm)"
              >{{ fm.name }}{{ fm.spec ? ' ' + fm.spec : '' }}</el-tag>
            </el-tooltip>
            <span class="desc">基于历史打样统计，点击加入当前卡片</span>
          </div>
          <el-tabs v-model="activePlanTab" type="border-card" style="min-height: 420px">
            <el-tab-pane v-for="tab in planTabs" :key="tab.value" :name="tab.value" :label="`${tab.label}（${cardsByTab(tab.value).length}）`">
              <div
                class="plan-scroll"
                @dragover.prevent
                @drop="onPlanDrop"
              >
                <div
                  v-for="(pc, idx) in cardsByTab(tab.value)" :key="pc.uid"
                  class="plan-card"
                  :class="{ 'drag-over': pc.draggingOver, 'batch-selected': batchSelected.has(pc.uid) }"
                  @dragover.prevent="onCardDragOver(pc)"
                  @dragleave="onCardDragLeave(pc)"
                  @drop.stop="onCardDrop($event, pc)"
                >
                  <!-- 行1：序号 + 状态 + 操作 -->
                  <div class="pc-head">
                    <el-checkbox
                      v-if="batchMode"
                      :model-value="batchSelected.has(pc.uid)"
                      @change="(v: boolean | string | number) => toggleBatchSelect(pc, !!v)"
                      class="batch-check"
                    />
                    <span class="pc-num">{{ idx + 1 }}</span>
                    <span class="save-state" :class="`save-${pc.saveState || 'synced'}`">
                      {{ saveStateText(pc) }}
                    </span>
                    <div class="pc-head-right">
                      <el-tag v-if="pc.status === 2" size="small" type="success">✓ 已完成</el-tag>
                      <el-tag v-else-if="pc.status === 1" size="small" type="warning">⏳ 进行中</el-tag>
                      <el-tag v-else size="small" type="info">待做</el-tag>
                      <el-button
                        v-if="pc.status !== 2" type="primary" size="small"
                        @click="advancePlan(pc)" :loading="pc.advancing"
                      >{{ pc.status === 1 ? '✓ 完成' : '▶ 开始' }}</el-button>
                      <span v-if="pc.status === 2 && pc.durationMinutes" style="color:#909399;font-size:12px">⏱ {{ pc.durationMinutes }}分钟</span>
                    </div>
                  </div>
                  <!-- 行2：作业项目（组合，任意结构） -->
                  <div class="pc-row">
                    <div class="pc-row-label">作业项目</div>
                    <div class="pc-items">
                      <el-tag
                        v-for="(it, ii) in pc.items" :key="ii" size="small"
                        :closable="pc.editing" :disable-transitions="false"
                        @close="removeCardItem(pc, Number(ii))"
                        style="margin-right:6px;margin-bottom:4px"
                      >
                        <SvgIcon v-if="it.icon" :name="it.icon" :size="14" style="vertical-align:-2px;margin-right:4px" />
                        {{ it.processName }}
                        <!-- <span v-if="it.processType" style="color:#909399;font-size:11px;margin-left:2px">{{ typeLabel(it.processType) }}</span> -->
                      </el-tag>
                      <span v-if="!pc.items.length" style="color:#c0c4cc;font-size:12px">未选择作业项目（点「＋ 添加作业项目」）</span>
                    </div>
                  </div>
                  <!-- 行3：材料表格 -->
                  <div class="pc-row">
                    <div class="pc-row-label">🧾 材料</div>
                    <div class="pc-mat">
                      <el-table
                        v-if="(pc.editing ? pc.materialRows : parseMaterials(pc.materials)).length"
                        :data="pc.editing ? pc.materialRows : parseMaterials(pc.materials)"
                        size="small" border style="width:100%"
                      >
                        <el-table-column label="材料" min-width="150">
                          <template #default="{ row }">
                            <template v-if="pc.editing">
                              <el-select
                                v-model="row.materialId"
                                filterable
                                remote
                                :remote-method="(q: string) => searchMaterials(q, row)"
                                :loading="row.loading"
                                :popper-class="`material-popper-${row.uid}`"
                                placeholder="搜索物料档案"
                                style="width:100%"
                                @change="(v: any) => onMaterialSelected(row, v)"
                                @visible-change="(v: boolean) => onSelectVisibleChange(row, v)"
                              >
                                <el-option v-for="opt in row.options" :key="opt.materialId" :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`" :value="opt.materialId" />
                                <el-option v-if="row.loading" value="__loading__" disabled style="text-align:center;color:#909399;font-size:12px">加载中…</el-option>
                                <el-option v-else-if="row.options.length && row.total > row.options.length" value="__more__" disabled style="text-align:center;color:#909399;font-size:12px">下拉加载更多（还有 {{ row.total - row.options.length }} 条）</el-option>
                                <el-option v-else-if="row.options.length" value="__all__" disabled style="text-align:center;color:#c0c4cc;font-size:12px">已加载全部（共 {{ row.total }} 条）</el-option>
                              </el-select>
                            </template>
                            <template v-else>{{ row.name }}</template>
                          </template>
                        </el-table-column>
                        <el-table-column label="规格" width="110">
                          <template #default="{ row }">
                            <el-input v-if="pc.editing" v-model="row.spec" size="small" placeholder="规格" :disabled="!!row.materialId" />
                            <span v-else>{{ row.spec || '-' }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column label="用量" width="90">
                          <template #default="{ row }">
                            <el-input-number v-if="pc.editing" v-model="row.qty" :min="0" :precision="4" :controls="false" size="small" style="width:100%" />
                            <span v-else>{{ row.qty }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column label="单位" width="70">
                          <template #default="{ row }">
                            <el-input v-if="pc.editing" v-model="row.unit" size="small" :disabled="!!row.materialId" />
                            <span v-else>{{ row.unit || '-' }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column v-if="pc.editing" label="操作" width="60" align="center">
                          <template #default="{ $index }">
                            <el-button size="small" link type="danger" @click="pc.materialRows.splice($index, 1)">删</el-button>
                          </template>
                        </el-table-column>
                      </el-table>
                      <div v-if="pc.editing" style="margin-top:6px;display:flex;gap:6px">
                        <el-button size="small" plain icon="Plus" @click="addMaterialRow(pc)">添加材料</el-button>
                        <el-button size="small" link type="primary" @click="openMaterialCreate(pc, null)">新建物料</el-button>
                      </div>
                      <span v-else-if="!parseMaterials(pc.materials).length" style="color:#c0c4cc;font-size:12px">无材料</span>
                    </div>
                  </div>
                  <!-- 行4：描述 -->
                  <div class="pc-row">
                    <div class="pc-row-label">📝 描述</div>
                    <el-input v-if="pc.editing" v-model="pc.processNote" type="textarea" :rows="2" placeholder="如：丝印机200目网版，刮刀压力3kg，室温干燥30分钟" />
                    <div v-else class="pc-desc-readonly">{{ pc.processNote || '—' }}</div>
                  </div>
                  <!-- 右下角：删除/保存/编辑 -->
                  <div class="pc-footer">
                    <el-button v-if="!pc.editing" size="small" @click="startEdit(pc)">✏️ 编辑</el-button>
                    <template v-else>
                      <el-button size="small" @click="pc.editing = false">取消</el-button>
                      <el-button size="small" type="danger" plain @click="removePlanCard(pc)">🗑 删除</el-button>
                      <el-button size="small" type="primary" :loading="pc.savingCard" @click="saveCard(pc)">💾 保存</el-button>
                    </template>
                  </div>
                </div>
                <div v-if="!cardsByTab(tab.value).length" class="plan-drop-hint">🖐 拖拽到这里</div>
              </div>
            </el-tab-pane>
          </el-tabs>

          <!-- 批量操作栏（批量编辑模式下出现） -->
          <div v-if="batchMode && batchSelected.size > 0" class="batch-bar">
            <span class="batch-info">已选 <b>{{ batchSelected.size }}</b> 张卡片</span>
            <span class="batch-label">统一设置工序类别：</span>
            <el-select v-model="batchCategory" size="small" placeholder="选择类别" clearable style="width:120px" @change="applyBatchCategory">
              <el-option v-for="c in categoryOptions" :key="c.itemValue" :label="c.label" :value="c.itemValue" />
            </el-select>
            <el-button size="small" type="primary" plain icon="Plus" @click="openBatchMaterial">批量添加材料</el-button>
            <el-button size="small" type="danger" plain icon="Delete" @click="batchDelete">批量删除</el-button>
          </div>
        </el-card>
      </div>

      <!-- ③ 底部：左执行时间线 + 右 BOM -->
      <div class="bottom-row">
        <el-card class="col-timeline" shadow="never">
          <template #header><span style="font-weight:600">③ 执行时间线</span><span class="desc">Round {{ card.sampleRound || 1 }} · 按计划流转</span></template>
          <div v-if="planList.length" class="timeline">
            <div
              v-for="pc in planList" :key="pc.uid"
              class="tl-item" :class="pc.status === 2 ? 'done' : pc.status === 1 ? 'doing' : ''"
            >
              <div class="t">
                {{ pc.items.map((i: any) => i.processName).join(' + ') || '未命名工序' }}
                <el-tag v-if="pc.status === 2" size="small" type="success">完成</el-tag>
                <el-tag v-else-if="pc.status === 1" size="small" type="warning">进行中</el-tag>
                <el-tag v-else size="small" type="info">待做</el-tag>
              </div>
              <div class="s">
                <template v-if="pc.operator">{{ pc.operator }} · </template>
                <template v-if="pc.startTime">{{ formatTime(pc.startTime) }}</template>
                <template v-if="pc.endTime"> - {{ formatTime(pc.endTime) }}</template>
                <template v-if="pc.durationMinutes"> · {{ pc.durationMinutes }}分钟</template>
                <template v-if="!pc.startTime && pc.status === 0">—</template>
              </div>
              <div v-if="pc.processNote" class="n">🔧 {{ pc.processNote }}</div>
              <div v-if="parseMaterials(pc.materials).length" class="n" style="margin-top:2px">
                <el-tag v-for="(m, mi) in parseMaterials(pc.materials)" :key="mi" size="small" type="info" style="margin-right:4px">{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag>
              </div>
            </div>
          </div>
          <div v-else style="color:#999;font-size:13px">暂无工序计划</div>
        </el-card>

        <el-card class="col-bom" shadow="never">
          <template #header><span style="font-weight:600">BOM 物料清单</span><span class="desc">各工序材料自动聚合</span></template>
          <el-table v-if="bomList.length > 0" :data="bomList" size="small" border style="width:100%">
            <el-table-column prop="process" label="来源工序" width="100" />
            <el-table-column prop="name" label="材料" min-width="120" />
            <el-table-column prop="spec" label="规格" min-width="100" />
            <el-table-column prop="qty" label="用量" width="80" />
            <el-table-column prop="unit" label="单位" width="60" />
          </el-table>
          <div v-else style="color:#999;font-size:13px">暂无材料（在工序中添加材料后自动汇总）</div>
          <div class="transfer-zone">
            <el-button type="success" size="small" @click="handleTransfer">📦 资料转移（建档产品/BOM/工艺路线）</el-button>
            <div class="desc">打样确认后，把本轮工序计划+材料建档为产品/BOM/工艺路线（可预览匹配/人工调整）</div>
          </div>
        </el-card>
      </div>

      <!-- 工艺参数 / 图纸 -->
      <div class="bottom-row">
        <el-card class="col-note" shadow="never">
          <template #header><span style="font-weight:600">工艺参数 / 工程备注</span></template>
          <el-input v-model="form.note" type="textarea" :rows="3"
            placeholder="填写工艺参数/材料规格/丝印要求/模切尺寸等"
            maxlength="2000" show-word-limit />
          <el-button type="primary" size="small" style="margin-top:8px" @click="saveNote" :loading="saving">💾 保存工艺参数</el-button>
        </el-card>
        <el-card class="col-files" shadow="never">
          <template #header><span style="font-weight:600">图纸 / 工艺文件</span></template>
          <el-upload ref="engUploadRef" :http-request="engUploadFile" :on-remove="engRemoveFile"
            :file-list="engFileList" :before-upload="engBeforeUpload"
            list-type="text" multiple>
            <el-button type="primary" size="small">📤 上传图纸/文件</el-button>
          </el-upload>
          <div v-if="engFileList.length > 0" style="margin-top:8px">
            <div v-for="f in engFileList" :key="f.uid || f.name" style="padding:4px 0;display:flex;align-items:center;gap:8px;border-bottom:1px solid #f0f0f0">
              <el-link v-if="f.url" :href="f.url" target="_blank" type="primary" underline="never">📎 {{ f.name }}</el-link>
              <span v-else>{{ f.name }} <el-tag size="small" type="warning">待上传</el-tag></span>
            </div>
          </div>
          <div v-else style="color:#999;font-size:12px;margin-top:6px">菲林图 / 丝印图 / 模切图 / 规格书（≤10MB）</div>
        </el-card>
      </div>

      <!-- 标记完成 -->
      <div style="text-align:center;margin:12px 0 4px">
        <el-button type="success" size="large" @click="handleMarkReady" :loading="saving" style="width:220px">🎯 标记样品完成（送样）</el-button>
      </div>
    </template>

    <!-- 历史轮次（只读，DEV-500） -->
    <div v-else class="round-readonly">
      <el-alert
        type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="历史轮次（只读）"
        description="该轮次已归档，如需调整请在当前轮次重新打样"
      />
      <div v-if="activeRoundData" style="margin-bottom:12px">
        <el-tag :type="activeRoundData.result === 'confirmed' ? 'success' : activeRoundData.result === 'rejected' ? 'danger' : 'info'">
          {{ activeRoundData.result === 'confirmed' ? '✅ 已确认' : activeRoundData.result === 'rejected' ? '⛔ 已退回' : '🔄 进行中' }}
        </el-tag>
        <span v-if="activeRoundData.rejectReason" style="margin-left:8px;color:#f56c6c;font-size:13px">
          退回原因：{{ activeRoundData.rejectReason }}
        </span>
        <span v-if="activeRoundData.engineeringNote" style="margin-left:12px;color:#606266;font-size:13px">
          工艺参数：{{ activeRoundData.engineeringNote }}
        </span>
      </div>
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">📜 工序快照</span></template>
        <el-timeline v-if="activeRoundProcesses.length" style="padding-left:2px">
          <el-timeline-item v-for="(p, i) in activeRoundProcesses" :key="i" :timestamp="formatTime(p.startTime)" placement="top" :type="i === activeRoundProcesses.length - 1 ? 'primary' : 'info'">
            <div style="font-size:13px">
              <span style="font-weight:600">{{ p.processName }}</span>
              <span v-if="p.durationMinutes" style="margin-left:8px;color:#606266;font-size:12px">⏱ {{ p.durationMinutes }}分钟</span>
              <span v-if="p.operator" style="margin-left:8px;color:#909399;font-size:12px">操作人：{{ p.operator }}</span>
              <div v-if="p.processNote" style="color:#606266;font-size:12px;margin-top:2px">🔧 {{ p.processNote }}</div>
              <div v-if="p.materials" style="margin-top:2px">
                <el-tag v-for="(m, mi) in parseMaterials(p.materials)" :key="mi" size="small" type="info" style="margin-right:4px">{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <div v-else style="color:#999;font-size:13px">该轮次无工序快照</div>
      </el-card>
      <el-card shadow="never">
        <template #header><span style="font-weight:600">🧾 BOM 物料快照</span></template>
        <el-table v-if="activeRoundBom.length" :data="activeRoundBom" size="small" border style="width:100%">
          <el-table-column prop="process" label="工序" width="90" />
          <el-table-column prop="name" label="材料" min-width="140" />
          <el-table-column prop="spec" label="规格" min-width="120" />
          <el-table-column prop="qty" label="用量" width="90" />
          <el-table-column prop="unit" label="单位" width="70" />
        </el-table>
        <div v-else style="color:#999;font-size:13px">该轮次无物料快照</div>
      </el-card>
    </div>

    <!-- 卡片作业项目追加选择器（多选，任意结构） -->
    <el-dialog v-model="cardPickerVisible" title="＋ 添加作业项目（可多选）" width="620px" append-to-body>
      <WorkProjectPicker v-model="cardPickerIds" @confirm="onCardPickerConfirm" />
    </el-dialog>

    <!-- 物料建档弹窗 -->
    <MaterialFormDialog
      v-model="materialCreateVisible"
      :preset-data="materialPreset"
      @success="onMaterialCreated"
    />

    <!-- 打样转标准·轻量版弹窗（DEV-764：资料转移统一入口） -->
    <SampleTransferDialog
      v-model="transferDialogVisible"
      :order-id="orderId"
      @success="onTransferSuccess"
    />

    <!-- 从历史打样复制弹窗 -->
    <el-dialog v-model="historyCopyVisible" title="📋 从历史打样复制" width="640px" append-to-body>
      <el-alert
        type="info" :closable="false" show-icon
        title="选择已转标准的样品单，复制其工序计划（工序/分组/材料）到当前打样单，追加到现有卡片后面"
        style="margin-bottom:12px"
      />
      <el-table
        v-loading="historyLoading" :data="historyOrders" size="small" border stripe
        max-height="360" highlight-current-row
        @current-change="(row: any) => (historySelected = row)"
      >
        <el-table-column prop="orderNo" label="样品单号" width="150" />
        <el-table-column prop="customerName" label="客户" min-width="130" />
        <el-table-column prop="sampleRound" label="轮次" width="70" align="center" />
        <el-table-column prop="orderDate" label="日期" width="110" />
      </el-table>
      <template #footer>
        <el-button @click="historyCopyVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!historySelected" :loading="historyCopying" @click="confirmHistoryCopy">
          复制到当前
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量添加材料弹窗 -->
    <el-dialog v-model="batchMaterialVisible" title="批量添加材料" width="460px" append-to-body>
      <el-select
        v-model="batchMaterialId"
        filterable
        remote
        :remote-method="(q: string) => searchBatchMaterial(q)"
        :loading="batchMaterialLoading"
        placeholder="搜索物料档案"
        style="width:100%"
      >
        <el-option
          v-for="opt in batchMaterialOptions" :key="opt.materialId"
          :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
          :value="opt.materialId"
        />
      </el-select>
      <div style="font-size:12px;color:#909399;margin-top:8px">将添加到 {{ batchSelected.size }} 张选中卡片的材料列表</div>
      <template #footer>
        <el-button @click="batchMaterialVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!batchMaterialId" @click="confirmBatchMaterial">添加</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps } from 'element-plus'
import request from '@/utils/request'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { materialApi } from '@/api/inventory/material'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import WorkProjectPicker from '@/views/sales/sample-order/components/WorkProjectPicker.vue'
import SampleTransferDialog from '@/views/sales/sample-order/components/SampleTransferDialog.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'SampleWorkbenchPage' })

const route = useRoute()
const router = useRouter()

const card = ref<any>({})
const orderId = computed(() => Number(route.query.orderId))

const saving = ref(false)
const savingPlan = ref(false)
const form = reactive({ note: '' })

// ===== 工序计划（方案A：卡片 = 一个工序单元，可挂多个作业项目）=====
const planList = ref<any[]>([])

// ===== 批次3：常用物料 / 保存状态 / 批量编辑 / 历史复制 =====

// 常用物料快捷区（历史高频物料 Top10）
const frequentMaterials = ref<any[]>([])

// 保存状态：synced 已同步 / dirty 未同步 / saving 保存中 / error 失败
function saveStateText(pc: any): string {
  switch (pc.saveState) {
    case 'dirty': return '⏳ 未同步'
    case 'saving': return '🔄 保存中'
    case 'error': return '❌ 保存失败'
    default: return '✅ 已同步'
  }
}

// 标记卡片已修改（未同步）
function markDirty(pc: any) {
  if (pc.saveState !== 'saving') pc.saveState = 'dirty'
}

// 批量编辑模式
const batchMode = ref(false)
const batchSelected = ref<Set<string>>(new Set())
const batchCategory = ref<string | null>(null)
function toggleBatchMode() {
  batchMode.value = !batchMode.value
  batchSelected.value = new Set()
  batchCategory.value = null
}
function toggleBatchSelect(pc: any, v: boolean) {
  const s = new Set(batchSelected.value)
  if (v) s.add(pc.uid)
  else s.delete(pc.uid)
  batchSelected.value = s
}
function batchSelectedCards(): any[] {
  return planList.value.filter((pc) => batchSelected.value.has(pc.uid))
}
// 统一设置工序类别
function applyBatchCategory(cat: string | undefined) {
  if (!cat) return
  batchSelectedCards().forEach((pc) => {
    pc.category = cat
    pc.items.forEach((it: any) => (it.processCategory = cat))
    markDirty(pc)
  })
  batchCategory.value = null
}
// 批量删除
function batchDelete() {
  const n = batchSelected.value.size
  if (!n) return
  ElMessageBox.confirm(`确定删除选中的 ${n} 张卡片？`, '批量删除', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  }).then(() => {
    planList.value = planList.value.filter((pc) => !batchSelected.value.has(pc.uid))
    batchSelected.value = new Set()
  }).catch(() => {})
}
// 批量添加材料
const batchMaterialVisible = ref(false)
const batchMaterialId = ref<number | null>(null)
const batchMaterialOptions = ref<any[]>([])
const batchMaterialLoading = ref(false)
function openBatchMaterial() {
  batchMaterialVisible.value = true
  batchMaterialId.value = null
  batchMaterialOptions.value = []
  searchBatchMaterial('')
}
async function searchBatchMaterial(query: string) {
  batchMaterialLoading.value = true
  try {
    const params: any = { pageNum: 1, pageSize: 20 }
    if (query.trim()) params.materialName = query.trim()
    const res: any = await materialApi.search(params)
    batchMaterialOptions.value = res?.data?.records || res?.data || []
  } catch {
    batchMaterialOptions.value = []
  } finally {
    batchMaterialLoading.value = false
  }
}
function confirmBatchMaterial() {
  if (!batchMaterialId.value) return
  const mat = batchMaterialOptions.value.find((o) => o.materialId === batchMaterialId.value)
  if (!mat) return
  batchSelectedCards().forEach((pc) => {
    const mats = parseMaterials(pc.materials) || []
    mats.push({
      name: mat.materialName, spec: mat.specification || '', qty: 1,
      unit: mat.unit || 'PCS', materialId: mat.materialId, materialCode: mat.materialCode || '',
    })
    pc.materials = JSON.stringify(mats)
    // 编辑态同步到 materialRows
    if (pc.editing) {
      pc.materialRows.push({
        name: mat.materialName, spec: mat.specification || '', qty: 1, unit: mat.unit || 'PCS',
        materialId: mat.materialId, materialCode: mat.materialCode || '',
        options: [], loading: false, uid: genUid(), pageNum: 1, total: 0, lastQuery: '',
      })
    }
    markDirty(pc)
  })
  batchMaterialVisible.value = false
  batchMaterialId.value = null
  ElMessage.success('已批量添加材料')
}

// 从历史打样复制
const historyCopyVisible = ref(false)
const historyOrders = ref<any[]>([])
const historyLoading = ref(false)
const historySelected = ref<any>(null)
const historyCopying = ref(false)
async function openHistoryCopy() {
  historyCopyVisible.value = true
  historySelected.value = null
  historyLoading.value = true
  try {
    const res: any = await sampleOrderApi.list({ sampleStatus: 7 }) // 已转标准
    historyOrders.value = (res.data || []).filter((o: any) => o.orderId !== orderId.value)
  } catch {
    historyOrders.value = []
  } finally {
    historyLoading.value = false
  }
}
async function confirmHistoryCopy() {
  const src = historySelected.value
  if (!src) return
  historyCopying.value = true
  try {
    const res = await sampleOrderApi.listProcesses(src.orderId)
    const list: any[] = (res.data || []).sort(
      (a: any, b: any) => (a.processOrder || 999) - (b.processOrder || 999) || (a.processId || 0) - (b.processId || 0)
    )
    if (!list.length) {
      ElMessage.warning('该样品单没有工序计划')
      return
    }
    // 按 processOrder 分组为卡片，追加到当前 planList 后面（不覆盖）
    const groups = new Map<number, any[]>()
    for (const p of list) {
      const k = p.processOrder || 999
      if (!groups.has(k)) groups.set(k, [])
      groups.get(k)!.push(p)
    }
    let added = 0
    for (const [, rows] of groups) {
      const first = rows[0]
      const enriched = rows.map((r: any) => {
        const src2 = allProcesses.value.find((x) => x.processId === r.stdProcessId)
        return src2 ? { ...r, processType: src2.processType, processCategory: src2.processCategory, icon: src2.icon } : r
      })
      const pc = makeCard(enriched, {
        processOrder: 0, // 追加，保存时重新编号
        category: first.processCategory || '',
        status: 0,
        processNote: first.processNote || '',
        materials: first.materials || null,
      })
      planList.value.push(pc)
      added++
    }
    historyCopyVisible.value = false
    ElMessage.success(`已复制 ${added} 张卡片（追加到现有卡片后，保存后生效）`)
  } catch (e: any) {
    ElMessage.error(e?.message || '复制失败')
  } finally {
    historyCopying.value = false
  }
}

// 常用物料统计：优先产品线历史（同产品），不足则客户历史
async function loadFrequentMaterials() {
  if (!orderId.value) return
  try {
    const all: any[] = (await sampleOrderApi.list({})).data || []
    const others = all.filter((o: any) => o.orderId !== orderId.value && o.sampleStatus === 7)
    const customerId = card.value?.customerId
    let candidates = others
    // 产品线优先：先按客户筛（SalesOrder 无产品字段，客户维度最可靠），有足够数据用客户
    if (customerId) {
      const byCustomer = others.filter((o: any) => o.customerId === customerId)
      if (byCustomer.length >= 1) candidates = byCustomer
    }
    const freq = new Map<string, any>()
    for (const o of candidates.slice(0, 10)) {
      try {
        const procs = (await sampleOrderApi.listProcesses(o.orderId)).data || []
        for (const p of procs) {
          if (!p.materials) continue
          const mats = parseMaterials(p.materials)
          for (const m of mats) {
            const key = m.materialId ? `id:${m.materialId}` : `name:${m.name}`
            const cur = freq.get(key)
            if (cur) cur.count++
            else freq.set(key, { name: m.name, spec: m.spec, materialId: m.materialId, count: 1 })
          }
        }
      } catch { /* ignore */ }
    }
    frequentMaterials.value = Array.from(freq.values()).sort((a, b) => b.count - a.count).slice(0, 10)
  } catch {
    frequentMaterials.value = []
  }
}
// 常用物料点击 → 加入当前编辑/激活卡片
function addFrequentMaterial(fm: any) {
  const tabCards = cardsByTab(activePlanTab.value)
  const target = tabCards.find((c) => c.editing) || tabCards[tabCards.length - 1]
  if (!target) {
    ElMessage.warning('请先添加工序卡片')
    return
  }
  const mats = parseMaterials(target.materials) || []
  mats.push({
    name: fm.name, spec: fm.spec || '', qty: 1, unit: 'PCS',
    materialId: fm.materialId, materialCode: '',
  })
  target.materials = JSON.stringify(mats)
  if (target.editing) {
    target.materialRows.push({
      name: fm.name, spec: fm.spec || '', qty: 1, unit: 'PCS',
      materialId: fm.materialId, materialCode: '',
      options: [], loading: false, uid: genUid(), pageNum: 1, total: 0, lastQuery: '',
    })
  }
  markDirty(target)
  ElMessage.success(`已添加 ${fm.name} 到「${target.items.map((i: any) => i.processName).join('+') || '未命名'}」`)
}

// 计划标签：面板/上线/下线/未分类（卡片属于哪个标签 = 它的项目结构）
const planTabs = [
  { value: 'PANEL', label: '面板' },
  { value: 'UP_LINE', label: '上线' },
  { value: 'DOWN_LINE', label: '下线' },
  { value: '', label: '未分类' },
]
const activePlanTab = ref('PANEL')

function cardsByTab(value: string) {
  return planList.value.filter((pc) => (pc.category || '') === value)
}

const { options: typeOptions } = useDict('process_type')
const { options: categoryOptions } = useDict('process_category')

function typeLabel(value: string): string {
  return typeOptions.value.find((i) => i.itemValue === value)?.label || value || ''
}
function categoryLabel(value: string): string {
  return categoryOptions.value.find((i) => i.itemValue === value)?.label || value || ''
}

const allProcesses = ref<any[]>([])
async function loadAllProcesses() {
  try {
    const res = await request.get('/engineering/standard-processes/page', {
      params: { pageNum: 1, pageSize: 100, isEnabled: 1, orderByColumn: 'displayOrder', isAsc: 'asc' },
    })
    allProcesses.value = res.data?.records || []
  } catch {
    allProcesses.value = []
  }
}

function genUid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
}

// 新建卡片（items: 作业项目数组）
function makeCard(items: any[], extra: any = {}) {
  return {
    uid: extra.uid || `new-${genUid()}`,
    processOrder: extra.processOrder ?? 0,
    items: items.map((i) => ({
      stdProcessId: i.stdProcessId ?? i.processId ?? null,
      processName: i.processName,
      processType: i.processType || '',
      processCategory: i.processCategory || '',
      icon: i.icon || '',
      processId: i.processId ?? null,
    })),
    category: extra.category ?? '',
    draggingOver: false,
    status: extra.status ?? 0,
    processNote: extra.processNote || '',
    materials: extra.materials || null,
    durationMinutes: extra.durationMinutes ?? null,
    startTime: extra.startTime || null,
    endTime: extra.endTime || null,
    operator: extra.operator || '',
    materialRows: [] as any[],
    editing: false,
    advancing: false,
    savingCard: false,
    // 批次3：保存状态（synced/dirty/saving/error）
    saveState: extra.saveState || 'synced',
  }
}

// 保存工序计划（整单覆盖当前轮次；卡片展开为多行，同卡片同行序）
async function savePlan() {
  if (!orderId.value) return
  if (!planList.value.length) {
    ElMessage.warning('工序计划为空，请先勾选作业项目')
    return
  }
  // 保存状态：全部标记为保存中
  planList.value.forEach((pc) => (pc.saveState = 'saving'))
  savingPlan.value = true
  try {
    const items: any[] = []
    planList.value.forEach((pc, i) => {
      const order = i + 1
      pc.items.forEach((it: any) => {
        items.push({
          processOrder: order,
          processId: it.processId ?? undefined,
          stdProcessId: it.stdProcessId ?? undefined,
          processName: it.processName,
          processCategory: pc.category || undefined,
          materials: pc.materials,
          processNote: pc.processNote,
          status: pc.status ?? 0,
        })
      })
    })
    await sampleOrderApi.saveProcessPlan(orderId.value, { items })
    planList.value.forEach((pc) => (pc.saveState = 'synced'))
    ElMessage.success(`工序计划已保存（${planList.value.length}道）`)
    await loadPlan()
    await refreshCard()
    await loadSummary()
  } catch (e: any) {
    planList.value.forEach((pc) => (pc.saveState = 'error'))
    ElMessage.error(e?.message || '保存工序计划失败')
  } finally {
    savingPlan.value = false
  }
}

// 卡片内追加作业项目（弹窗多选，任意结构）
const cardPickerVisible = ref(false)
const cardPickerTarget = ref<any>(null)
const cardPickerIds = ref<number[]>([])

function openCardPicker(pc: any) {
  cardPickerTarget.value = pc
  cardPickerIds.value = pc.items
    .map((i: any) => i.stdProcessId)
    .filter((id: any): id is number => !!id)
  cardPickerVisible.value = true
}

// 弹窗确认：追加到卡片（去重，保留自定义项）
function onCardPickerConfirm(items: any[]) {
  cardPickerVisible.value = false
  const target = cardPickerTarget.value
  if (!target) return
  const existing = new Set(target.items.map((i: any) => i.stdProcessId).filter(Boolean))
  for (const i of items) {
    if (existing.has(i.processId)) continue
    target.items.push({
      stdProcessId: i.processId,
      processName: i.processName,
      processType: i.processType,
      processCategory: i.processCategory,
      icon: i.icon,
      processId: target.processId ?? null,
    })
    existing.add(i.processId)
  }
  markDirty(target)
}

// ===== 拖拽接收（左侧工序 → 右侧卡片组合）=====
function parseDragData(e: DragEvent): any {
  try {
    const raw = e.dataTransfer?.getData('application/json')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// 拖到空白区 → 新建卡片（自动进入编辑状态）
function onPlanDrop(e: DragEvent) {
  const data = parseDragData(e)
  if (!data) return
  // 在哪个标签拖入，卡片就属于哪个项目结构（未分类标签=不设结构）
  const pc = makeCard([data], { category: activePlanTab.value === '' ? undefined : activePlanTab.value })
  planList.value.push(pc)
  startEdit(pc)
  markDirty(pc)
  clearDragOver()
}

// 拖到卡片 → 追加组合（去重），并自动进入编辑状态
function onCardDrop(e: DragEvent, pc: any) {
  const data = parseDragData(e)
  pc.draggingOver = false
  if (!data) return
  if (!pc.items.some((i: any) => i.stdProcessId === data.processId)) {
    pc.items.push({
      stdProcessId: data.processId,
      processName: data.processName,
      processType: data.processType || '',
      processCategory: data.processCategory || '',
      icon: data.icon || '',
      processId: pc.processId ?? null,
    })
    markDirty(pc)
  }
  if (!pc.editing) startEdit(pc)
}

function onCardDragOver(pc: any) {
  pc.draggingOver = true
}

function onCardDragLeave(pc: any) {
  pc.draggingOver = false
}

function clearDragOver() {
  planList.value.forEach((pc: any) => (pc.draggingOver = false))
}

// 移除卡片内作业项目
function removeCardItem(pc: any, idx: number) {
  // 只移除作业项目，卡片保留（可再拖入）；删卡片走右下角删除按钮
  pc.items.splice(idx, 1)
  markDirty(pc)
}

// 删除整张卡片（保存计划时生效）
function removePlanCard(pc: any) {
  planList.value = planList.value.filter((x) => x !== pc)
  if (batchSelected.value.has(pc.uid)) {
    const s = new Set(batchSelected.value)
    s.delete(pc.uid)
    batchSelected.value = s
  }
}
async function advancePlan(pc: any) {
  if (!orderId.value) return
  const next = pc.status === 1 ? 2 : 1
  pc.advancing = true
  try {
    const targetIds = pc.items.map((i: any) => i.processId).filter(Boolean)
    for (const pid of targetIds) {
      await sampleOrderApi.updateProcessItemStatus(orderId.value, pid, { status: next })
    }
    ElMessage.success(next === 2 ? '工序已完成' : '工序已开始')
    await loadPlan()
    await refreshCard()
    await loadSummary()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    pc.advancing = false
  }
}

// 保存卡片（整单保存，数据一致）
async function saveCard(pc: any) {
  pc.savingCard = true
  pc.saveState = 'saving'
  try {
    // 材料行 → JSON
    const validMats = (pc.materialRows || [])
      .filter((m: any) => m.name && m.name.trim())
      .map((m: any) => ({
        name: m.name,
        spec: m.spec || '',
        qty: m.qty ?? 1,
        unit: m.unit || 'PCS',
        materialId: m.materialId,
        materialCode: m.materialCode || '',
      }))
    pc.materials = validMats.length ? JSON.stringify(validMats) : null
    pc.editing = false
    await savePlan()
    pc.saveState = 'synced'
    ElMessage.success('已保存')
  } catch (e: any) {
    pc.saveState = 'error'
    ElMessage.error(e?.message || '保存失败')
  } finally {
    pc.savingCard = false
  }
}

// 材料行
function addMaterialRow(pc: any) {
  pc.materialRows.push({
    name: '', spec: '', qty: 1, unit: 'PCS',
    materialId: undefined as number | undefined, materialCode: '',
    options: [], loading: false, uid: genUid(),
    pageNum: 1, total: 0, lastQuery: '',
  })
}

// 进入编辑：初始化材料行
function startEdit(pc: any) {
  pc.editing = true
  if (!pc.materialRows.length) {
    pc.materialRows = (parseMaterials(pc.materials) || []).map((m: any) => ({
      name: m.name || '',
      spec: m.spec || '',
      qty: m.qty ?? 1,
      unit: m.unit || 'PCS',
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      options: [], loading: false, uid: genUid(),
      pageNum: 1, total: 0, lastQuery: '',
    }))
    if (!pc.materialRows.length) addMaterialRow(pc)
  }
}

// 远程搜索物料档案（分页）
async function searchMaterials(query: string, m: any, pageNum = 1, append = false) {
  m.loading = true
  m.lastQuery = (query || '').trim()
  try {
    const params: any = { pageNum, pageSize: 20 }
    if (m.lastQuery) params.materialName = m.lastQuery
    const res: any = await materialApi.search(params)
    const records = res?.data?.records || res?.data || []
    if (append) {
      const seen = new Set((m.options || []).map((o: any) => o.materialId))
      for (const r of records) {
        if (!seen.has(r.materialId)) {
          m.options.push(r)
          seen.add(r.materialId)
        }
      }
    } else {
      m.options = records
    }
    m.total = res?.data?.total ?? records.length
    m.pageNum = pageNum
  } catch {
    if (!append) m.options = []
  } finally {
    m.loading = false
  }
}

// 下拉滚动加载下一页
function onSelectVisibleChange(m: any, visible: boolean) {
  if (!visible) return
  if (!m.options?.length && !m.loading) {
    searchMaterials(m.lastQuery || '', m, 1, false)
  }
  nextTick(() => {
    const wrap = document.querySelector(`.material-popper-${m.uid} .el-select-dropdown__wrap`) as HTMLElement | null
    if (!wrap) return
    wrap.onscroll = () => {
      if (wrap.scrollTop + wrap.clientHeight >= wrap.scrollHeight - 30) {
        loadMoreMaterials(m)
      }
    }
  })
}

async function loadMoreMaterials(m: any) {
  if (m.loading) return
  if (!m.total || (m.options?.length || 0) >= m.total) return
  await searchMaterials(m.lastQuery || '', m, (m.pageNum || 1) + 1, true)
}

// 选中物料 → 自动填名称/规格/单位
function onMaterialSelected(m: any, materialId: number) {
  const mat = (m.options || []).find((o: any) => o.materialId === materialId)
  if (!mat) return
  m.name = mat.materialName
  m.spec = mat.specification || ''
  m.unit = mat.unit || 'PCS'
  m.materialCode = mat.materialCode || ''
}

// 建档弹窗
const materialCreateVisible = ref(false)
const materialPreset = ref<any>({})
const materialTarget = ref<any>(null)
function openMaterialCreate(pc: any, m: any) {
  materialTarget.value = { card: pc, row: m }
  materialPreset.value = { materialName: m?.name || '', specification: m?.spec || '', unit: m?.unit || 'PCS' }
  materialCreateVisible.value = true
}

// 建档成功 → 填入目标行（无目标行则加到卡片材料表）
function onMaterialCreated(mat: any) {
  const t = materialTarget.value
  if (t?.row) {
    t.row.materialId = mat.materialId
    t.row.materialCode = mat.materialCode || ''
    t.row.name = mat.materialName
    t.row.spec = mat.specification || ''
    t.row.unit = mat.unit || 'PCS'
    t.row.options = [mat]
  } else if (t?.card) {
    t.card.materialRows.push({
      name: mat.materialName,
      spec: mat.specification || '',
      qty: 1,
      unit: mat.unit || 'PCS',
      materialId: mat.materialId,
      materialCode: mat.materialCode || '',
      options: [mat], loading: false, uid: genUid(),
      pageNum: 1, total: 0, lastQuery: '',
    })
  }
}

// 解析材料JSON
function parseMaterials(json?: string | null) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

// ===== 汇总 =====
const doneCount = computed(() => planList.value.filter((p) => p.status === 2).length)
const summary = ref<any>({})
async function loadSummary() {
  if (!orderId.value) return
  try {
    const res = await request.get(`/sales/sample-order/summary/${orderId.value}`)
    summary.value = res.data || {}
  } catch {
    summary.value = {}
  }
}

// ===== 轮次展示（DEV-500）=====
const roundList = ref<any[]>([])
const activeRound = ref('')
const isCurrentRound = computed(() => Number(activeRound.value) === (card.value?.sampleRound || 1))
const activeRoundData = computed(() => roundList.value.find((r) => String(r.roundNo) === activeRound.value) || null)
const activeRoundProcesses = computed(() => {
  const d = activeRoundData.value
  if (!d?.processSnapshot) return []
  try {
    const arr = JSON.parse(d.processSnapshot)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
const activeRoundBom = computed(() => {
  const d = activeRoundData.value
  if (!d?.bomSnapshot) return []
  try {
    const arr = JSON.parse(d.bomSnapshot)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
async function loadRounds() {
  if (!orderId.value) return
  try {
    const res = await sampleOrderApi.getRounds(orderId.value)
    const rounds: any[] = res.data || []
    const current = card.value?.sampleRound || 1
    if (!rounds.some((r) => r.roundNo === current)) {
      rounds.push({ roundNo: current, result: 'pending' })
    }
    roundList.value = rounds.sort((a, b) => a.roundNo - b.roundNo)
    activeRound.value = String(current)
  } catch {
    roundList.value = [{ roundNo: card.value?.sampleRound || 1, result: 'pending' }]
    activeRound.value = String(card.value?.sampleRound || 1)
  }
}
const bomList = ref<any[]>([])
const engUploadRef = ref()
const engFileList = ref<any[]>([])

function goBack() {
  router.push('/engineering/sample-workbench')
}

// 有未同步卡片时离开拦截（onBeforeRouteLeave 内联实现，此处不留死代码）
// 路由离开守卫（组合式 API）
onBeforeRouteLeave(async () => {
  const dirty = planList.value.some((pc) => pc.saveState === 'dirty' || pc.saveState === 'error')
  if (!dirty) return true
  try {
    await ElMessageBox.confirm('有卡片尚未同步保存，确定离开吗？未保存的修改将丢失。', '未保存修改', {
      confirmButtonText: '仍要离开', cancelButtonText: '留下继续编辑', type: 'warning',
    })
    return true
  } catch {
    return false
  }
})

async function loadDetail() {
  if (!orderId.value) return
  try {
    const res = await sampleOrderApi.getInfo(orderId.value)
    card.value = res.data || {}
    form.note = card.value.engineeringNote || ''
    await Promise.all([loadRounds(), loadPlan(), loadBom(), loadEngFiles(), loadSummary(), loadAllProcesses(), loadFrequentMaterials()])
  } catch (e: any) {
    ElMessage.error(e?.message || '加载样品单失败')
  }
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

// 接单
async function handleAccept() {
  if (!orderId.value) return
  try {
    await ElMessageBox.confirm('确认接单开始打样？', '工程接单', { confirmButtonText: '确认接单', cancelButtonText: '取消', type: 'info' })
    const userStore = useUserStore()
    const name = userStore.nickName || '工程'
    await sampleOrderApi.acceptEngineering(orderId.value, name)
    ElMessage.success('接单成功')
    await refreshCard()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '接单失败')
  }
}

// 拒单
async function handleReject() {
  if (!orderId.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写拒单原因', '工程拒单', {
      confirmButtonText: '确认拒单', cancelButtonText: '取消', type: 'warning',
      inputPlaceholder: '拒单原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '拒单原因不能为空'),
    })
    await sampleOrderApi.rejectEngineering(orderId.value, value.trim())
    ElMessage.success('已拒单，退回待审核')
    goBack()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '拒单失败')
  }
}

// 保存工艺参数
async function saveNote() {
  if (!orderId.value) return
  saving.value = true
  try {
    await sampleOrderApi.startEngineering(orderId.value, form.note)
    card.value.engineeringNote = form.note
    ElMessage.success('工艺参数已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 资料转移（DEV-764：改为打开轻量版弹窗，复用样品单列表同一组件/store）
function handleTransfer() {
  if (!orderId.value) return
  transferDialogVisible.value = true
}

// 打样转标准·轻量版弹窗
const transferDialogVisible = ref(false)
function onTransferSuccess() {
  transferDialogVisible.value = false
  ElMessage.success('资料转移完成')
  // 刷新当前数据（BOM/路线已建档，刷新汇总）
  loadSummary()
}

// 图纸
const engBeforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过10MB')
    return false
  }
  return true
}
async function engUploadFile(options: any) {
  if (!orderId.value) return
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('bizType', 'sample')
  fd.append('bizId', String(orderId.value))
  if (card.value?.traceId) {
    fd.append('traceId', card.value.traceId)
  }
  try {
    const res = await request.post('/system/attachment/upload', fd)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('上传成功')
      await loadEngFiles()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  }
}
async function engRemoveFile(file: any) {
  if (file.id) {
    try {
      await request.delete(`/system/attachment/${file.id}`)
    } catch { /* ignore */ }
  }
  engFileList.value = engFileList.value.filter(f => f.uid !== file.uid)
}
async function loadEngFiles() {
  if (!orderId.value) return
  try {
    const res = await request.get(`/system/attachment/list?bizType=sample&bizId=${orderId.value}`)
    engFileList.value = (res.data || []).map((a: any) => ({
      uid: a.id, name: a.fileName, url: a.filePath, id: a.id,
    }))
  } catch {
    engFileList.value = []
  }
}

// 标记完成
async function handleMarkReady() {
  if (!orderId.value) return
  try {
    await ElMessageBox.confirm('确认样品制作完成？将进入待送样状态', '标记完成', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'success',
    })
    await sampleOrderApi.markReady(orderId.value)
    ElMessage.success('已标记完成，待送样')
    goBack()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

// 加载工序计划（当前轮次，按 processOrder 分组为卡片）
async function loadPlan() {
  if (!orderId.value) return
  try {
    const res = await sampleOrderApi.listProcesses(orderId.value, card.value?.sampleRound || undefined)
    const list: any[] = res.data || []
    list.sort((a, b) => (a.processOrder || 999) - (b.processOrder || 999) || (a.processId || 0) - (b.processId || 0))
    // 按 processOrder 分组（同卡片多行组合）
    const groups = new Map<number, any[]>()
    for (const p of list) {
      const k = p.processOrder || 999
      if (!groups.has(k)) groups.set(k, [])
      groups.get(k)!.push(p)
    }
    planList.value = Array.from(groups.entries()).map(([order, rows]) => {
      const first = rows[0]
      const enriched = rows.map((r: any) => {
        const src = allProcesses.value.find((x) => x.processId === r.stdProcessId)
        return src ? { ...r, processType: src.processType, processCategory: src.processCategory, icon: src.icon } : r
      })
      return makeCard(enriched, {
        uid: `db-${order}`,
        processOrder: order,
        category: first.processCategory || '',
        status: first.status ?? 0,
        processNote: first.processNote || '',
        materials: first.materials || null,
        durationMinutes: first.durationMinutes ?? null,
        startTime: first.startTime || null,
        endTime: first.endTime || null,
        operator: first.operator || '',
      })
    })

  } catch {
    planList.value = []
  }
}

async function loadBom() {
  if (!orderId.value) return
  try {
    const res = await sampleOrderApi.listProcesses(orderId.value)
    const procs = res.data || []
    const agg: any[] = []
    for (const p of procs) {
      if (!p.materials) continue
      try {
        const mats = JSON.parse(p.materials)
        for (const m of mats) {
          agg.push({ process: p.processName, name: m.name, spec: m.spec, qty: m.qty, unit: m.unit })
        }
      } catch { /* ignore */ }
    }
    bomList.value = agg
  } catch { bomList.value = [] }
}

async function refreshCard() {
  if (!orderId.value) return
  try {
    const res = await sampleOrderApi.getInfo(orderId.value)
    card.value = res.data
  } catch { /* ignore */ }
}

// 卡片内容修改自动标记未同步（材料行/描述/作业项目等 v-model 直接绑定）
// 用 deep watch 检测：editing 中的卡片内容变化 → dirty
watch(
  () => planList.value.map((pc: any) => JSON.stringify({
    items: pc.items, materials: pc.editing ? pc.materialRows : pc.materials,
    processNote: pc.processNote, category: pc.category,
  })),
  () => {
    planList.value.forEach((pc: any) => {
      if (pc.editing && pc.saveState === 'synced') markDirty(pc)
    })
  },
  { deep: true }
)

// 加载（页面打开即载入）
loadDetail()
</script>

<style scoped>
.wb-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 8px;
}

.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}

.wb-card {
  margin-bottom: 14px;
}

/* 汇总（样品单信息底部） */
.summary-inline {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 36px;
  flex-wrap: wrap;
}
.summary-item {
  text-align: center;
}
.summary-num {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}
.summary-item:nth-child(2) .summary-num {
  color: #67c23a;
}
.summary-item:nth-child(3) .summary-num {
  color: #e6a23c;
}
.summary-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.summary-tip {
  font-size: 12px;
  color: #999;
}

.accept-row {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
}

/* 中间左右分栏 */
.mid-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}
.col-picker {
  width: 400px;
  flex-shrink: 0;
}
.col-plan {
  flex: 1;
  min-width: 0;
}
.picker-actions {
  margin-top: 10px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 常用物料快捷区 */
.freq-materials {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #f8fbff;
  border: 1px dashed #b3d8ff;
  border-radius: 8px;
}
.freq-label {
  font-size: 12px;
  font-weight: 600;
  color: #e6a23c;
  margin-right: 2px;
}
.freq-tag {
  cursor: pointer;
  transition: all 0.15s;
}
.freq-tag:hover {
  border-color: #e6a23c;
  color: #e6a23c;
  background: #fdf6ec;
}

/* 保存状态标记 */
.save-state {
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.save-synced {
  color: #67c23a;
}
.save-dirty {
  color: #909399;
}
.save-saving {
  color: #409eff;
}
.save-error {
  color: #f56c6c;
}

/* 批量编辑 */
.batch-check {
  margin-right: 2px;
}
.plan-card.batch-selected {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.25);
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 10px 14px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 8px;
}
.batch-info {
  font-size: 13px;
  color: #b88230;
}
.batch-label {
  font-size: 12px;
  color: #606266;
}

/* 底部左右分栏 */
.bottom-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}
.col-timeline {
  flex: 1.2;
  min-width: 0;
}
.col-bom {
  flex: 1;
  min-width: 0;
}
.col-note {
  flex: 1.2;
  min-width: 0;
}
.col-files {
  flex: 1;
  min-width: 0;
}

/* 工序卡片（四行布局） */
.plan-scroll {
  max-height: 560px;
  overflow-y: auto;
  padding-right: 6px;
  scrollbar-width: thin;
}
.plan-scroll::-webkit-scrollbar {
  width: 6px;
}
.plan-scroll::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

.plan-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(31, 45, 61, 0.05);
  transition: box-shadow 0.2s, border-color 0.2s;
}
.plan-card.drag-over {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
  background: #f5f9ff;
}

.plan-drop-hint {
  border: 2px dashed #c0c4cc;
  border-radius: 10px;
  padding: 60px 0;
  text-align: center;
  color: #909399;
  font-size: 16px;
  background: #fafbfc;
}

.plan-card:hover {
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.1);
  border-color: #c6d9f5;
}

.pc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.pc-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.pc-head-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.pc-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.pc-row-label {
  font-size: 12px;
  color: #909399;
  width: 72px;
  flex-shrink: 0;
  line-height: 26px;
}
.pc-items {
  flex: 1;
  min-width: 0;
}
.pc-mat {
  flex: 1;
  min-width: 0;
}
.pc-desc-readonly {
  flex: 1;
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
  background: #fafbfc;
  border-radius: 4px;
  padding: 6px 8px;
  min-height: 26px;
  white-space: pre-wrap;
}

.pc-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #e8ecf1;
  padding-top: 8px;
}

/* 执行时间线 */
.timeline {
  position: relative;
  padding-left: 20px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 4px;
  bottom: 4px;
  width: 2px;
  background: #e4e7ed;
}
.tl-item {
  position: relative;
  padding-bottom: 16px;
}
.tl-item::before {
  content: '';
  position: absolute;
  left: -17px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.tl-item.done::before {
  background: #67c23a;
}
.tl-item.doing::before {
  background: #409eff;
  box-shadow: 0 0 0 3px #ecf5ff;
}
.tl-item .t {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tl-item .s {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.tl-item .n {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
}

/* BOM 转移区 */
.transfer-zone {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.transfer-zone .desc {
  display: block;
  margin-left: 0;
  margin-top: 6px;
  line-height: 1.6;
}
</style>
