<template>
  <div class="material-settings-panel">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tabs v-model="tab" :before-leave="beforeOuterLeave" :class="{ 'single-section': singleSection }">
      <el-tab-pane label="分类" name="categories">
        <el-form label-width="110px">
          <el-form-item label="维护分类">
            <el-select
              v-model="categoryEditId"
              clearable
              placeholder="留空新增分类"
              @change="editCategory"
            >
              <el-option
                v-for="c in editableCategories"
                :key="c.categoryId"
                :label="c.pathName"
                :value="c.categoryId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="父分类" required
            ><el-select v-model="categoryForm.parentId" filterable>
              <el-option
                v-for="c in parentCategories"
                :key="c.categoryId"
                :label="c.pathName"
                :value="c.categoryId"
              /> </el-select
          ></el-form-item>
          <el-form-item label="分类编码" required
            ><el-input
              v-model="categoryForm.categoryCode"
              :disabled="!!categoryEditId"
              placeholder="大写字母、数字和下划线"
          /></el-form-item>
          <el-form-item label="型号前缀" :required="categoryPrefixRequired">
            <el-input v-model="categoryForm.modelPrefix" maxlength="8" placeholder="1～8位大写字母或数字" />
          </el-form-item>
          <el-form-item label="分类名称" required
            ><el-input v-model="categoryForm.categoryName" maxlength="100"
          /></el-form-item>
          <el-form-item label="排序"
            ><el-input-number v-model="categoryForm.sortOrder"
          /></el-form-item>
          <el-form-item label="启用"
            ><el-switch
              v-model="categoryForm.status"
              :active-value="CommonStatusEnum.NORMAL.value"
              :inactive-value="CommonStatusEnum.DISABLED.value"
          /></el-form-item>
          <el-form-item
            ><el-button type="primary" :loading="busy" @click="saveCategory"
              >保存分类</el-button
            ></el-form-item
          >
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="属性模板" name="template">
        <el-form label-width="110px" :disabled="busy || templateLoading">
          <el-form-item label="末级品类" required
            ><el-select :model-value="templateCategoryId" :disabled="busy || templateLoading" filterable @update:model-value="changeTemplateCategory">
              <el-option
                v-for="c in leaves"
                :key="c.categoryId"
                :label="c.pathName"
                :value="c.categoryId"
              /> </el-select
          ></el-form-item>
          <template v-if="templateCategoryId">
          <div v-if="focusSection !== 'template'" class="rule-summary" data-test="rule-summary">
            <el-button v-for="item in ruleBlocks" :key="item.key" link :data-test="`summary-${item.key}`"
              @click="jumpToBlock(item.key)">{{ item.label }}：{{ ruleSummary(item.key) }}</el-button>
          </div>
          <el-tabs v-model="activeBlock" :before-leave="beforeBlockLeave" class="block-tabs">
          <el-tab-pane v-if="focusSection !== 'naming'" :label="blockTitle('fields', '属性字段')" name="fields">
            <el-form-item label="模板编码"><el-input v-model="templateCode" /></el-form-item>
            <el-form-item label="模板名称"><el-input v-model="templateName" /></el-form-item>
            <el-form-item label="启用"
              ><el-switch
                v-model="templateStatus"
                :active-value="CommonStatusEnum.NORMAL.value"
                :inactive-value="CommonStatusEnum.DISABLED.value"
            /></el-form-item>
            <el-form-item label="允许材质"
              ><el-select v-model="schema.meta_options" multiple filterable>
                <el-option
                  v-for="o in dictionaries.material_meta"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-form-item label="材质必填"
              ><el-checkbox v-model="schema.meta_required"
            /></el-form-item>
            <el-form-item label="默认材质"
              ><el-select v-model="schema.default_meta" clearable>
                <el-option
                  v-for="o in allowedMeta"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-form-item label="默认形态"
              ><el-select v-model="schema.default_form" clearable>
                <el-option
                  v-for="o in dictionaries.form"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-form-item label="默认单位" required
              ><el-select v-model="schema.default_unit">
                <el-option
                  v-for="o in dictionaries.unit"
                  :key="o.code"
                  :label="`${o.label}/${o.code}`"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-table :data="schema.fields" border>
              <el-table-column label="属性编码" width="150"
                ><template #default="{ row }"><el-input v-model="row.key" /></template
              ></el-table-column>
              <el-table-column label="名称" width="130"
                ><template #default="{ row }"><el-input v-model="row.label" /></template
              ></el-table-column>
              <el-table-column label="类型" width="125"
                ><template #default="{ row }"
                  ><el-select v-model="row.type">
                    <el-option
                      v-for="o in MaterialAttributeTypeEnum.items"
                      :key="o.value"
                      :value="o.value"
                      :label="o.label"
                    /> </el-select></template
              ></el-table-column>
              <el-table-column label="精度" width="90">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.precision"
                    :min="0"
                    :max="8"
                    :precision="0"
                    :controls="false"
                    :disabled="!isNumericType(row.type)"
                    style="width: 72px"
                    placeholder="-"
                  />
                </template>
              </el-table-column>
              <el-table-column label="默认值" width="150">
                <template #default="{ row }">
                  <el-select
                    v-if="row.type === 'boolean'"
                    v-model="row.default"
                    clearable
                    style="width: 100%"
                  >
                    <el-option label="是" :value="true" />
                    <el-option label="否" :value="false" />
                  </el-select>
                  <el-input-number
                    v-else-if="isNumericType(row.type)"
                    v-model="row.default"
                    :controls="false"
                    style="width: 100%"
                  />
                  <el-select
                    v-else-if="row.type === 'enum' || row.type === 'multienum'"
                    v-model="row.default"
                    clearable
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="o in row.options || []"
                      :key="o.code"
                      :label="o.label"
                      :value="o.code"
                    />
                  </el-select>
                  <el-input v-else v-model="row.default" clearable />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="120"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.unit"
                    clearable
                    filterable
                    allow-create
                    default-first-option
                    placeholder="无量纲"
                    style="width: 100%"
                    ><el-option v-for="unit in UNIT_OPTIONS" :key="unit" :label="unit" :value="unit" /></el-select></template
              ></el-table-column>
              <el-table-column label="必填" width="70"
                ><template #default="{ row }"><el-checkbox v-model="row.required" /></template
              ></el-table-column>
              <el-table-column label="常用" width="70">
                <template #default="{ row }"><el-checkbox :model-value="isCommonAttribute(row)" @update:model-value="value => row.common = !!value" /></template>
              </el-table-column>
              <el-table-column label="参与规格" width="90">
                <template #default="{ row }"><el-checkbox :model-value="participatesInSpecification(row)" @update:model-value="value => row.in_spec = !!value" /></template>
              </el-table-column>
              <el-table-column label="规格排序" width="110">
                <template #default="{ row }"><el-input-number v-model="row.spec_order" :min="0" :max="10000" :controls="false" style="width: 85px" /></template>
              </el-table-column>
              <el-table-column label="显示条件" width="200">
                <template #default="{ row }">
                  <el-select :model-value="conditionKey(row)" clearable placeholder="始终显示" @update:model-value="value => setConditionKey(row, value)">
                    <el-option v-for="field in schema.fields.filter(f => f.key !== row.key && !Object.keys(f.visible_when || {}).length)"
                      :key="field.key" :value="field.key" :label="field.label" />
                  </el-select>
                  <el-select v-if="conditionKey(row) && conditionOptions(row).length" :model-value="conditionValue(row)"
                    placeholder="满足此选项时显示" @update:model-value="value => setConditionValue(row, value)">
                    <el-option v-for="option in conditionOptions(row)" :key="option.code" :value="option.code" :label="option.label" />
                  </el-select>
                  <el-input v-else-if="conditionKey(row)" :model-value="conditionValue(row)" placeholder="满足此值时显示"
                    @update:model-value="value => setConditionValue(row, value)" />
                </template>
              </el-table-column>
              <el-table-column label="是／否显示文字" width="150">
                <template #default="{ row }">
                  <template v-if="row.type === 'boolean'">
                    <el-input v-model="row.true_label" placeholder="是，例如带点" maxlength="50" />
                    <el-input v-model="row.false_label" placeholder="否，例如无点" maxlength="50" />
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="选项（每行编码=名称）"
                ><template #default="{ row }">
                  <el-input
                    v-if="row.type === 'enum' || row.type === 'multienum'"
                    :model-value="optionText(row)"
                    type="textarea"
                    :rows="2"
                    @change="setOptions(row, $event)"
                  />
                  <template v-else-if="row.type === 'integer' || row.type === 'decimal'">
                    <el-input-number
                      v-model="row.min"
                      placeholder="下限"
                      :controls="false"
                      style="width: 90px"
                    />
                    <el-input-number
                      v-model="row.max"
                      placeholder="上限"
                      :controls="false"
                      style="width: 90px"
                    />
                  </template>
                  <span v-else>—</span>
                </template></el-table-column
              >
              <el-table-column width="65"
                ><template #default="{ $index }"
                  ><el-button link type="danger" @click="schema.fields.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              >
              <el-table-column label="顺序" width="110">
                <template #default="{ $index }">
                  <el-button
                    link
                    :disabled="$index === 0"
                    aria-label="上移属性"
                    @click="moveField($index, -1)"
                    >上移</el-button
                  >
                  <el-button
                    link
                    :disabled="$index === schema.fields.length - 1"
                    aria-label="下移属性"
                    @click="moveField($index, 1)"
                    >下移</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
            <el-button style="margin-top: 12px" @click="addField">增加属性</el-button>
            <el-button style="margin-top: 12px" @click="addCommonFields">补充本品类常用属性</el-button>
            <el-alert
              title="已有物料的模板可新增无默认值的可选属性、调整显示设置；删除、改类型或单位等需另行处理。常用属性补入草稿后，检查并保存才生效。"
              type="info"
              :closable="false"
              style="margin-top: 12px"
            />
            <el-button
              type="primary"
              :loading="busy"
              :disabled="templateLoading"
              style="margin-top: 12px"
              @click="saveTemplate"
              >保存属性字段</el-button
            >
            <el-button type="danger" plain :disabled="busy" @click="removeTemplate"
              >移除未使用模板</el-button
            >
          </el-tab-pane>
          <el-tab-pane v-if="focusSection !== 'template'" :label="blockTitle('name_pattern', '命名规则')" name="name_pattern">
            <div class="naming-rule">
              <div class="naming-tokens">
                <el-checkbox
                  v-for="token in namingTokens"
                  :key="token.key"
                  :model-value="namePattern.fields.includes(token.key)"
                  @update:model-value="(value) => toggleNameField(token.key, !!value)"
                  >{{ token.label }}</el-checkbox
                >
              </div>
              <div class="naming-actions">
                <el-button link type="primary" @click="selectAllNaming">全选</el-button>
                <el-button link @click="resetNaming">重置</el-button>
              </div>
              <div v-if="namePattern.fields.length" class="naming-order">
                <div
                  v-for="(field, index) in namePattern.fields"
                  :key="field"
                  class="naming-order-row"
                  draggable="true"
                  :class="dragClasses('naming', index)"
                  @dragstart="startDrag('naming', index, $event)"
                  @dragover.prevent="dragOver('naming', index)"
                  @dragleave.self="clearDropTarget"
                  @drop="dropAt('naming', index)"
                  @dragend="endDrag"
                >
                  <span class="naming-order-no">{{ index + 1 }}</span>
                  <span class="naming-order-label">{{ namingLabel(field) }}</span>
                  <el-input
                    :model-value="fieldSeparator(field)"
                    class="naming-sep-input"
                    placeholder="分隔"
                    title="该字段前的分隔符，允许 1–3 个字符；留空=紧贴上一个字段"
                    @update:model-value="(value: any) => setNameSeparator(field, value)"
                  />
                  <el-button link @click="setNameSeparator(field, ' ')">空格</el-button>
                  <el-button link @click="setNameSeparator(field, '-')">-</el-button>
                  <el-button link @click="setNameSeparator(field, '×')">×</el-button>
                  <el-button link @click="setNameSeparator(field, '*')">*</el-button>
                  <el-button link @click="setNameSeparator(field, '')">无</el-button>
                  <el-button
                    link
                    :icon="ArrowUp"
                    :disabled="index === 0"
                    aria-label="上移"
                    title="上移"
                    @click="moveNameField(index, -1)"
                  />
                  <el-button
                    link
                    :icon="ArrowDown"
                    :disabled="index === namePattern.fields.length - 1"
                    aria-label="下移"
                    title="下移"
                    @click="moveNameField(index, 1)"
                  />
                  <el-button link type="danger" @click="toggleNameField(field, false)">移除</el-button>
                </div>
              </div>
              <el-form-item label="分隔符">
                <el-radio-group v-model="namePattern.separator">
                  <el-radio-button label=" ">空格</el-radio-button>
                  <el-radio-button label="-">-</el-radio-button>
                  <el-radio-button label="">无</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="缺值处理">
                <el-radio-group v-model="namePattern.empty">
                  <el-radio-button label="skip">跳过</el-radio-button>
                  <el-radio-button label="placeholder">占位 —</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-button :loading="namingLoading" @click="previewNaming()">刷新效果示例</el-button>
              <span v-if="namingMessage" class="naming-message">{{ namingMessage }}</span>
              <el-divider content-position="left">效果示例（该品类最近 5 条物料）</el-divider>
              <div v-if="!namingSamples.length" class="naming-message">
                该品类暂无可预览的物料；规则仍可保存，仅对以后新建/改名生效。
              </div>
              <el-table
                v-if="namingSamples.length"
                :data="namingSamples"
                border
                size="small"
                style="margin-top: 8px"
              >
                <el-table-column label="物料编码" prop="materialCode" width="150" />
                <el-table-column label="当前名称" prop="oldName" />
                <el-table-column label="按规则生成" prop="newName" />
              </el-table>
              <el-divider />
              <div class="naming-message">保存规则不会自动修改存量物料名称；请保存后预览差异，再使用「按规则批量改名」。</div>
              <el-button :loading="renameLoading" @click="previewRename">预览改名差异</el-button>
              <el-button
                type="danger"
                plain
                :disabled="!renameReport || !renameReport.changeable"
                @click="applyRename"
                >按规则批量改名（{{ renameReport?.changeable ?? 0 }} 条）</el-button
              >
              <span v-if="renameReport" class="naming-message">{{ renameReport.message }}</span>
              <el-table
                v-if="renameReport?.samples.length"
                :data="renameReport.samples"
                border
                size="small"
                max-height="260"
                style="margin-top: 8px"
              >
                <el-table-column label="物料编码" prop="materialCode" width="150" />
                <el-table-column label="当前名称" prop="oldName" />
                <el-table-column label="按规则生成" prop="newName" />
                <el-table-column label="说明" width="120">
                  <template #default="{ row }">{{ skipText(row.skipReason) }}</template>
                </el-table-column>
              </el-table>
            </div>
            <el-button type="primary" :loading="busy" :disabled="!templateExists || templateLoading" @click="savePattern('name_pattern')">保存本块</el-button>
          </el-tab-pane>
          <el-tab-pane v-if="focusSection !== 'template'" :label="blockTitle('model_pattern', '型号规则')" name="model_pattern">
            <div class="naming-rule" data-test="model-pattern">
              <div class="naming-message">仅新建物料自动带出，可手改；未勾选字段时人工填写。编辑与批量重算不改变型号。</div>
              <div class="naming-tokens">
                <el-checkbox v-for="token in modelTokens" :key="token.key"
                  :model-value="modelPattern.fields.includes(token.key)"
                  @update:model-value="value => toggleModelField(token.key, !!value)">{{ token.label }}</el-checkbox>
              </div>
              <div class="naming-actions">
                <el-button link type="primary" @click="selectAllModel">全选</el-button>
                <el-button link @click="resetModel">重置</el-button>
              </div>
              <div v-for="(field, index) in modelPattern.fields" :key="field" class="naming-order-row"
                draggable="true" :class="dragClasses('model', index)"
                @dragstart="startDrag('model', index, $event)"
                @dragover.prevent="dragOver('model', index)" @dragleave.self="clearDropTarget"
                @drop="dropAt('model', index)" @dragend="endDrag">
                <span class="naming-order-no">{{ index + 1 }}</span>
                <span class="naming-order-label">{{ namingLabel(field) }}</span>
                <el-input :model-value="modelPattern.separators?.[field] ?? modelPattern.separator"
                  class="naming-sep-input" placeholder="分隔" title="字段前分隔符，允许 1–3 个字符；空串表示紧贴"
                  @update:model-value="value => setModelSeparator(field, value)" />
                <el-select v-if="isDimensionField(field)" :model-value="modelPattern.units?.[field] || 'mm'"
                  :aria-label="`${namingLabel(field)}型号单位`" style="width: 100px" @update:model-value="value => setModelUnit(field, value)">
                  <el-option v-for="unit in dimensionUnits" :key="unit" :label="unit" :value="unit" />
                </el-select>
                <el-button link :icon="ArrowUp" :disabled="index === 0" aria-label="型号字段上移" @click="moveModelField(index, -1)" />
                <el-button link :icon="ArrowDown" :disabled="index === modelPattern.fields.length - 1" aria-label="型号字段下移" @click="moveModelField(index, 1)" />
                <el-button link type="danger" @click="toggleModelField(field, false)">移除</el-button>
              </div>
              <el-form-item label="分隔符">
                <el-radio-group v-model="modelPattern.separator">
                  <el-radio-button label=" ">空格</el-radio-button><el-radio-button label="-">-</el-radio-button><el-radio-button label="">无</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-button @click="previewModel(true)">刷新型号示例</el-button>
              <span class="naming-message">品类前缀缺失时需先配置；其他缺值跳过；尺寸按所选单位换算，型号内不带单位文字。</span>
              <el-table v-if="modelSamples.length" :data="modelSamples" border size="small" style="margin-top: 8px">
                <el-table-column label="参考物料编码" prop="materialCode" width="150" />
                <el-table-column label="供应商品名" prop="supplierMaterialName" min-width="220" show-overflow-tooltip />
                <el-table-column label="原型号实例" prop="originalModel" min-width="160" show-overflow-tooltip />
                <el-table-column label="型号生成示例" prop="newName" min-width="160" show-overflow-tooltip />
              </el-table>
              <div v-else class="naming-message">该品类暂无示例；配置品类前缀并填写规则所需字段后，新建物料表单会带出型号。</div>
              <div v-if="modelSamples.some(sample => sample.referenceError)" class="naming-message" role="status">部分参考物料资料加载失败，生成示例仍保留，可点击「刷新型号示例」重试。</div>
            </div>
            <el-button type="primary" :loading="busy" :disabled="!templateExists || templateLoading" @click="savePattern('model_pattern')">保存本块</el-button>
          </el-tab-pane>
          <el-tab-pane v-if="focusSection !== 'template'" :label="blockTitle('spec_pattern', '规格模板')" name="spec_pattern">
            <div class="naming-rule">
              <div class="naming-tokens">
                <el-checkbox
                  v-for="token in specTokens"
                  :key="token.key"
                  :model-value="specPattern.fields.some((item) => item.key === token.key)"
                  @update:model-value="(value) => toggleSpecField(token.key, !!value)"
                  >{{ token.label }}</el-checkbox
                >
              </div>
              <div class="naming-actions">
                <el-button link type="primary" @click="selectAllSpec">全选</el-button>
                <el-button link @click="resetSpec">重置</el-button>
              </div>
              <div v-if="specPattern.fields.length" class="naming-order">
                <div
                  v-for="(item, index) in specPattern.fields"
                  :key="item.key"
                  class="naming-order-row"
                  draggable="true"
                  :class="dragClasses('spec', index)"
                  @dragstart="startDrag('spec', index, $event)"
                  @dragover.prevent="dragOver('spec', index)"
                  @dragleave.self="clearDropTarget"
                  @drop="dropAt('spec', index)"
                  @dragend="endDrag"
                >
                  <span class="naming-order-no">{{ index + 1 }}</span>
                  <span class="naming-order-label">{{ specLabel(item.key) }}</span>
                  <el-input
                    :model-value="item.sep"
                    class="naming-sep-input"
                    placeholder="分隔"
                    title="该字段前的分隔符，允许 1–3 个字符；留空=紧贴上一个字段"
                    @update:model-value="(value: any) => setSpecSep(index, value)"
                  />
                  <el-button link @click="setSpecSep(index, '；')">；</el-button>
                  <el-button link @click="setSpecSep(index, ',')">,</el-button>
                  <el-button link @click="setSpecSep(index, '×')">×</el-button>
                  <el-button link @click="setSpecSep(index, '*')">*</el-button>
                  <el-button link @click="setSpecSep(index, '')">无</el-button>
                  <el-button
                    link
                    :icon="ArrowUp"
                    :disabled="index === 0"
                    aria-label="上移"
                    title="上移"
                    @click="moveSpecField(index, -1)"
                  />
                  <el-button
                    link
                    :icon="ArrowDown"
                    :disabled="index === specPattern.fields.length - 1"
                    aria-label="下移"
                    title="下移"
                    @click="moveSpecField(index, 1)"
                  />
                  <template v-if="isDimensionField(item.key)">
                    <label>单位</label>
                    <el-select :model-value="item.unit || ''" :aria-label="`${specLabel(item.key)}单位`" style="width: 125px"
                      @update:model-value="value => setSpecUnit(index, value)">
                      <el-option label="沿用原单位" value="" />
                      <el-option v-for="unit in dimensionUnits" :key="unit" :label="unit" :value="unit" />
                    </el-select>
                    <label>小数位</label>
                    <el-input-number v-model="item.scale" :min="0" :max="6" :precision="0" placeholder="默认"
                      :aria-label="`${specLabel(item.key)}小数位`" style="width: 110px" />
                    <span class="naming-message" :data-test="`unit-example-${item.key}`">{{ dimensionExample(item) }}</span>
                  </template>
                  <el-button link type="danger" @click="toggleSpecField(item.key, false)">移除</el-button>
                </div>
              </div>
              <el-form-item label="显示字段名">
                <el-switch v-model="specPattern.show_label" />
              </el-form-item>
              <el-form-item label="单位位置">
                <el-radio-group v-model="specPattern.unit_position">
                  <el-radio-button label="end">只在末尾</el-radio-button>
                  <el-radio-button label="each">每项都带</el-radio-button>
                  <el-radio-button label="none">不带</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <span class="naming-message">留空字段不输出；未配置时沿用旧的“字段:值；尺寸”拼接。小数位默认：μm 1 位、cm/m 2 位、mm 保留原精度，均去尾零。不同单位逐项显示请选择“每项都带”。</span>
              <el-divider content-position="left">效果示例（该品类最近 5 条物料）</el-divider>
              <div v-if="!specSamples.length" class="naming-message">该品类暂无可预览的物料；模板只影响以后生成。</div>
              <el-table v-if="specSamples.length" :data="specSamples" border size="small" style="margin-top: 8px">
                <el-table-column label="物料编码" prop="materialCode" width="150" />
                <el-table-column label="当前规格" prop="oldSpec" />
                <el-table-column label="按模板生成" prop="newSpec" />
              </el-table>
            </div>
            <el-button type="primary" :loading="busy" :disabled="!templateExists || templateLoading" @click="savePattern('spec_pattern')">保存本块</el-button>
          </el-tab-pane>
          </el-tabs>
          <el-alert v-if="!templateExists && focusSection !== 'template'" title="请先在属性字段入口建立模板，再保存规则。" type="info" :closable="false" />
          </template>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import { MaterialAttributeTypeEnum } from '@/enums/inventory/MaterialAttributeEnum'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { isCommonAttribute } from './materialMasterModel'
import { displayDimensionOf } from './materialSize'
import { addRecommendedAttributes } from './materialAttributePresets'
import { participatesInSpecification } from '../bomSpecification'
import type {
  MaterialDimensionUnit,
  MaterialPatternBlock,
  MaterialPatternSave,
  MaterialSpecField,
  MaterialAttributeField,
  MaterialAttributeSchema,
  MaterialNamingPattern,
  MaterialNamingPreview,
  MaterialRenameReport,
  MaterialDictionaries,
  MaterialMasterCategory,
} from '@/types/inventory/materialMaster'
const props = withDefaults(
  defineProps<{
    /** 独立页面使用：隐藏分类/属性模板的 tab 头，只显示 initialTab 指向的那一页（dev-20261029-022） */
    singleSection?: boolean
    initialTab?: 'categories' | 'template'
    /** 指定时保留原入口范围：template 只看字段，naming 只看三套规则；未指定时展示四块。 */
    focusSection?: 'template' | 'naming'
  }>(),
  { singleSection: false, initialTab: 'categories' }
)
const emit = defineEmits<{ (e: 'success'): void }>()
const tab = ref(props.initialTab),
  error = ref(''),
  busy = ref(false)
const isNumericType = (type?: string) => type === 'decimal' || type === 'integer'
const categories = ref<MaterialMasterCategory[]>([]),
  dictionaries = ref<MaterialDictionaries>({})
const categoryEditId = ref<number>(),
  templateCategoryId = ref<number>()
const categoryForm = reactive({
  parentId: undefined as number | undefined,
  categoryCode: '',
  modelPrefix: '',
  categoryName: '',
  sortOrder: 0,
  status: CommonStatusEnum.NORMAL.value as number,
})
const templateCode = ref(''),
  templateName = ref('')
const templateStatus = ref<number>(CommonStatusEnum.NORMAL.value)
const templateLoading = ref(false)
const schema = ref<MaterialAttributeSchema>({ version: 1, meta_options: [], fields: [] })
/** 规格模板：字段顺序 + 每字段 1 字符分隔符 + 是否显示字段名/带单位，存入 attribute_schema.spec_pattern（dev-20261029-027）。 */
const specPattern = ref<{
  fields: MaterialSpecField[]
  show_label: boolean
  with_unit: boolean
  unit_position: 'each' | 'end' | 'none'
}>({
  fields: [],
  show_label: false,
  with_unit: true,
  unit_position: 'end',
})
const specSamples = ref<{ materialId: number; materialCode: string; oldSpec: string | null; newSpec: string | null }[]>([])
const specLoading = ref(false)
let specTimer: ReturnType<typeof setTimeout> | undefined
async function previewSpecification(quiet = false) {
  if (!templateCategoryId.value) return
  specLoading.value = true
  try {
    const response = await materialMasterApi.specPreview(templateCategoryId.value)
    specSamples.value = response.data?.samples || []
  } catch (e) {
    if (!quiet) ElMessage.error(e instanceof Error ? e.message : '规格预览失败')
  } finally {
    specLoading.value = false
  }
}
watch(
  () => [templateCategoryId.value, JSON.stringify(specPattern.value)],
  () => {
    if (!templateCategoryId.value) return
    if (specTimer) clearTimeout(specTimer)
    specTimer = setTimeout(() => void previewSpecification(true), 300)
  },
  { immediate: true }
)

const SPEC_DIM_TOKENS = [
  { key: 'width', label: '宽' },
  { key: 'length', label: '长' },
  { key: 'thickness_mm', label: '厚' },
]
const specTokens = computed(() => {
  if (fieldOptions.value.length) return fieldOptions.value.filter(o => o.key !== 'categoryprefix').map((o) => ({ key: o.key, label: o.label }))
  return [
    ...SPEC_DIM_TOKENS,
    ...schema.value.fields.filter((f) => f.key).map((f) => ({ key: f.key, label: f.label || f.key })),
  ]
})
const dimensionUnits: MaterialDimensionUnit[] = ['mm', 'cm', 'm', 'μm']
const isDimensionField = (key: string) => ['width', 'length', 'thickness_mm'].includes(key)
function setSpecUnit(index: number, unit: MaterialDimensionUnit | '') {
  const field = specPattern.value.fields[index]
  if (unit) field.unit = unit
  else delete field.unit
}
function dimensionExample(field: MaterialSpecField): string {
  const value = field.key === 'thickness_mm' ? 0.254 : field.key === 'length' ? 1000000 : 315
  const unit = field.unit || 'mm'
  return `示例：${value}mm → ${displayDimensionOf(value, unit, field.scale)}${unit}`
}
function specLabel(key: string) {
  return specTokens.value.find((token) => token.key === key)?.label || key
}
function toggleSpecField(key: string, checked: boolean) {
  const rest = specPattern.value.fields.filter((item) => item.key !== key)
  specPattern.value.fields = checked ? [...rest, { key, sep: rest.length ? '；' : '' }] : rest
}
function moveSpecField(index: number, offset: -1 | 1) {
  const fields = specPattern.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [item] = fields.splice(index, 1)
  fields.splice(target, 0, item)
}
function setSpecSep(index: number, value: string | number) {
  const next = String(value ?? '')
  specPattern.value.fields[index] = { ...specPattern.value.fields[index], sep: next }
}

/** 命名/规格可选字段（后端 /naming/fields 提供，新增字段无需改前端） */
const fieldOptions = ref<{ key: string; label: string; group: string }[]>([])
async function loadFieldOptions(categoryId?: number) {
  try {
    const response = await materialMasterApi.fieldOptions(categoryId)
    fieldOptions.value = response.data || []
  } catch {
    fieldOptions.value = []
  }
}

/** 命名规则：勾选字段 + 顺序 + 分隔符 + 缺值处理，保存进 attribute_schema.name_pattern。 */
const namePattern = ref<MaterialNamingPattern>({ fields: [], separator: ' ', empty: 'skip' })
const modelPattern = ref<MaterialNamingPattern>({ fields: [], separator: '', empty: 'skip' })
type TemplateBlock = 'fields' | MaterialPatternBlock
const activeBlock = ref<TemplateBlock>(props.focusSection === 'naming' ? 'name_pattern' : 'fields')
const templateExists = ref(false)
const loadedCategoryId = ref<number>()
const ruleBlocks: { key: MaterialPatternBlock; label: string }[] = [
  { key: 'name_pattern', label: '命名规则' },
  { key: 'model_pattern', label: '型号规则' },
  { key: 'spec_pattern', label: '规格模板' },
]
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const baselines = reactive<Record<TemplateBlock, string>>({ fields: '', name_pattern: '', model_pattern: '', spec_pattern: '' })
function patternDraft(block: MaterialPatternBlock) {
  return block === 'name_pattern' ? namePattern.value : block === 'model_pattern' ? modelPattern.value : specPattern.value
}
function fieldSnapshot() {
  const { name_pattern, model_pattern, spec_pattern, ...fields } = schema.value
  return JSON.stringify({ fields, code: templateCode.value, name: templateName.value, status: templateStatus.value })
}
function captureBaselines() {
  baselines.fields = fieldSnapshot()
  ruleBlocks.forEach(({ key }) => { baselines[key] = JSON.stringify(patternDraft(key)) })
}
function isDirty(block: TemplateBlock) {
  return !!loadedCategoryId.value && baselines[block] !== (block === 'fields' ? fieldSnapshot() : JSON.stringify(patternDraft(block)))
}
const hasUnsaved = computed(() => isDirty('fields') || ruleBlocks.some(({ key }) => isDirty(key)))
function blockTitle(block: TemplateBlock, label: string) { return `${label}${isDirty(block) ? '（未保存）' : ''}` }
async function confirmSwitch(message: string) {
  try {
    await ElMessageBox.confirm(message, '有未保存的修改', { confirmButtonText: '继续切换', cancelButtonText: '留在本块', type: 'warning' })
    return true
  } catch { return false }
}
async function beforeBlockLeave(next: string | number, previous: string | number) {
  if (next === previous) return true
  if (busy.value || templateLoading.value) return false
  // 摘要已确认后更新 v-model，Element Plus 会再调用一次钩子，此时无需重复询问。
  if (next === activeBlock.value) return true
  return !isDirty(previous as TemplateBlock) || confirmSwitch('本块尚未保存，切换后会保留草稿，请返回本块保存。是否继续切换？')
}
async function beforeOuterLeave(next: string | number, previous: string | number) {
  if (next === previous) return true
  if (busy.value || templateLoading.value) return false
  return previous !== 'template' || !hasUnsaved.value || confirmSwitch('模板中有未保存的修改，切换后会保留草稿。是否继续切换？')
}
async function jumpToBlock(block: MaterialPatternBlock) {
  if (await beforeBlockLeave(block, activeBlock.value)) activeBlock.value = block
}
async function changeTemplateCategory(id: number) {
  if (id === templateCategoryId.value || busy.value || templateLoading.value) return
  if (hasUnsaved.value && !await confirmSwitch('当前品类有未保存的修改，切换品类会丢弃这些草稿。是否继续切换？')) return
  templateCategoryId.value = id
  await loadTemplate(id)
}
function canSave() { return !templateLoading.value && !busy.value && loadedCategoryId.value === templateCategoryId.value && !!loadedCategoryId.value }
/** 摘要展示已保存的配置；仅翻译字段标签，不在前端实现生成规则。 */
function ruleSummary(block: MaterialPatternBlock) {
  const pattern = schema.value[block]
  if (!pattern?.fields?.length) return '未配置'
  return block === 'spec_pattern'
    ? (pattern as NonNullable<MaterialAttributeSchema['spec_pattern']>).fields.map(item => specLabel(item.key)).join(' + ')
    : (pattern as MaterialNamingPattern).fields.map(namingLabel).join(' + ')
}
type ModelReference = { supplierMaterialName: string; originalModel: string }
type ModelSample = MaterialNamingPreview['samples'][number] & ModelReference & { referenceError?: boolean }
const modelSamples = ref<ModelSample[]>([])
// 同一批参考物料跨规则编辑复用只读资料；显式刷新或切换品类时重新读取。
const modelReferences = new Map<number, Promise<ModelReference>>()
function loadModelReference(materialId: number): Promise<ModelReference> {
  const cached = modelReferences.get(materialId)
  if (cached) return cached
  const pending = materialMasterApi.detail(materialId).then(response => {
    const material = response.data?.material
    if (!material) throw new Error('参考物料资料为空')
    return { supplierMaterialName: material.supplierMaterialName || '-', originalModel: material.model || '-' }
  })
  modelReferences.set(materialId, pending)
  return pending
}
const modelTokens = computed(() => namingTokens.value.filter(token => token.key !== 'model'))
function toggleModelField(key: string, checked: boolean) {
  const rest = modelPattern.value.fields.filter(field => field !== key)
  modelPattern.value.fields = checked ? [...rest, key] : rest
}
function moveModelField(index: number, offset: number) {
  const fields = modelPattern.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [field] = fields.splice(index, 1)
  fields.splice(target, 0, field)
}
function setModelSeparator(field: string, value: string | number) {
  modelPattern.value.separators = { ...modelPattern.value.separators, [field]: String(value ?? '') }
}
function setModelUnit(field: string, value: string) {
  modelPattern.value.units = { ...modelPattern.value.units, [field]: value as MaterialDimensionUnit }
}
let modelPreviewRequest = 0
async function previewModel(refreshReferences = false) {
  const categoryId = templateCategoryId.value
  const request = ++modelPreviewRequest
  if (refreshReferences) modelReferences.clear()
  if (!categoryId) return
  try {
    const rule = modelPattern.value
    const separators = rule.separator === ''
      ? { ...Object.fromEntries(rule.fields.map(field => [field, ''])), ...rule.separators }
      : rule.separators
    const response = await materialMasterApi.previewNaming(categoryId, { ...rule, separators, empty: 'skip' })
    if (request !== modelPreviewRequest) return
    const samples = response.data?.samples || []
    const references = await Promise.allSettled(samples.map(sample => loadModelReference(sample.materialId)))
    if (request !== modelPreviewRequest) return
    modelSamples.value = samples.map((sample, index) => {
      const reference = references[index]
      return reference.status === 'fulfilled'
        ? { ...sample, ...reference.value }
        : { ...sample, supplierMaterialName: '加载失败', originalModel: '加载失败', referenceError: true }
    })
  } catch (e) {
    if (request === modelPreviewRequest) {
      modelSamples.value = []
      ElMessage.error(e instanceof Error ? e.message : '型号示例生成失败')
    }
  }
}
let modelPreviewTimer: ReturnType<typeof setTimeout> | undefined
watch(() => [templateCategoryId.value, JSON.stringify(modelPattern.value)], (value, previous) => {
  if (value[0] !== previous?.[0]) modelReferences.clear()
  modelPreviewRequest++
  modelSamples.value = []
  if (modelPreviewTimer) clearTimeout(modelPreviewTimer)
  if (templateCategoryId.value) modelPreviewTimer = setTimeout(() => void previewModel(), 300)
})
const namingLoading = ref(false)
const namingSamples = ref<MaterialNamingPreview['samples']>([])
const namingMessage = ref('')
const COMMON_NAMING_TOKENS = [
  { key: 'categoryprefix', label: '品类前缀' },
  { key: 'brand', label: '品牌' },
  { key: 'brandabbr', label: '品牌·缩写' },
  { key: 'suppliershort', label: '供应商·简称' },
  { key: 'supplierabbr', label: '供应商·缩写' },
  { key: 'model', label: '型号' },
  { key: 'color', label: '颜色' },
  { key: 'meta', label: '材质' },
  { key: 'form', label: '形态' },
  { key: 'thickness_mm', label: '厚度(mm)' },
  { key: 'width', label: '宽(mm)' },
  { key: 'length', label: '长(mm)' },
  { key: 'grammage', label: '克重' },
  { key: 'customersupplied', label: '客供' },
  { key: 'sampleonly', label: '样品' },
  { key: 'category', label: '品类' },
]
/** 属性单位白名单：与后端 MaterialAttributeSchema.UNITS 一致，避免 mm/MM/毫米 混写。 */
const UNIT_OPTIONS = ['%', 'g/m²', 'g', 'kg/m³', 'mm', 'μm', 'N', 'gf', '℃', 's']
/** 可选字段 = 公共字段 + 本品类属性字段（品类不同，可勾选的属性就不同）。 */
const namingTokens = computed(() => {
  if (fieldOptions.value.length) return fieldOptions.value.map((o) => ({ key: o.key, label: o.label }))
  return [
    ...COMMON_NAMING_TOKENS,
    ...schema.value.fields
      .filter((field) => field.key && !COMMON_NAMING_TOKENS.some((token) => token.key === field.key))
      .map((field) => ({ key: field.key, label: field.label || field.key })),
  ]
})
const namingLabel = (key: string) => namingTokens.value.find((token) => token.key === key)?.label || key
function toggleNameField(key: string, checked: boolean) {
  const rest = namePattern.value.fields.filter((field) => field !== key)
  namePattern.value.fields = checked ? [...rest, key] : rest
}
/** 命名规则：全选当前品类的全部可选项 */
function selectAllNaming() {
  namePattern.value.fields = namingTokens.value.map((token) => token.key)
}
/** 命名规则：清空选择，分隔符与缺值恢复默认 */
function resetNaming() {
  namePattern.value = { fields: [], separator: ' ', empty: 'skip', separators: {} }
}
/** 规格模板：全选当前品类的全部可选项（首项无分隔符，其余默认 ；） */
function selectAllSpec() {
  specPattern.value.fields = specTokens.value.map((token, index) => ({
    key: token.key,
    sep: index === 0 ? '' : '；',
  }))
}
/** 规格模板：清空选择并恢复默认显示设置 */
function resetSpec() {
  specPattern.value = { fields: [], show_label: false, with_unit: true, unit_position: 'end' }
}

/** 该字段当前生效的分隔符（未单配时取全局分隔符） */
function fieldSeparator(field: string): string {
  const map = namePattern.value.separators || {}
  return Object.prototype.hasOwnProperty.call(map, field)
    ? map[field]
    : (namePattern.value.separator ?? ' ')
}

/** 设置单字段分隔符：允许 1–3 个字符，空串表示紧贴上一个字段 */
function setNameSeparator(field: string, value: string | number) {
  const next = String(value ?? '')
  namePattern.value.separators = { ...(namePattern.value.separators || {}), [field]: next }
}

function selectAllModel() {
  modelPattern.value.fields = modelTokens.value.map(token => token.key)
}
function resetModel() {
  modelPattern.value.fields = []
}

type DragKind = 'naming' | 'model' | 'spec'
const dropTarget = ref<{ kind: DragKind; index: number } | null>(null)
function dragClasses(kind: DragKind, index: number) {
  return {
    dragging: dragState.value?.kind === kind && dragState.value.index === index,
    'drop-target': dropTarget.value?.kind === kind && dropTarget.value.index === index,
  }
}
function clearDropTarget() { dropTarget.value = null }
function dragOver(kind: DragKind, index: number) {
  dropTarget.value = dragState.value?.kind === kind && dragState.value.index !== index ? { kind, index } : null
}
const dragState = ref<{ kind: DragKind; index: number } | null>(null)
function startDrag(kind: DragKind, index: number, event?: DragEvent) {
  clearDropTarget()
  dragState.value = { kind, index }
  if (event?.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `${kind}:${index}`)
  }
}
function endDrag() {
  clearDropTarget()
  dragState.value = null
}
/** 拖拽落位：同类型列表内把 from 移到 to（dev-20261030-004） */
function dropAt(kind: DragKind, index: number) {
  const from = dragState.value
  endDrag()
  if (!from || from.kind !== kind || from.index === index) return
  const list: any[] = kind === 'naming' ? namePattern.value.fields : kind === 'model' ? modelPattern.value.fields : specPattern.value.fields
  const [item] = list.splice(from.index, 1)
  list.splice(index, 0, item)
}

function moveNameField(index: number, offset: -1 | 1) {
  const fields = namePattern.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [field] = fields.splice(index, 1)
  fields.splice(target, 0, field)
}
async function previewNaming(quiet = false) {
  if (!templateCategoryId.value) return
  namingLoading.value = true
  try {
    const response = await materialMasterApi.previewNaming(templateCategoryId.value, namePattern.value)
    namingSamples.value = response.data?.samples || []
    namingMessage.value = response.data?.message || ''
  } catch (e) {
    if (!quiet) ElMessage.error(e instanceof Error ? e.message : '命名预览失败')
  } finally {
    namingLoading.value = false
  }
}

// 效果示例自动跟着规则变：改品类/勾选/排序/分隔符后 300ms 自动重算（dev-20261029-021）
let previewTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => [
    templateCategoryId.value,
    namePattern.value.fields.join(','),
    namePattern.value.separator,
    namePattern.value.empty,
  ],
  () => {
    if (!templateCategoryId.value) return
    if (previewTimer) clearTimeout(previewTimer)
    previewTimer = setTimeout(() => void previewNaming(true), 300)
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  modelPreviewRequest++
  if (specTimer) clearTimeout(specTimer)
  if (previewTimer) clearTimeout(previewTimer)
  if (modelPreviewTimer) clearTimeout(modelPreviewTimer)
})
const renameLoading = ref(false)
const renameReport = ref<MaterialRenameReport>()
const skipText = (reason: string | null) =>
  reason === 'blank' ? '规则为空，跳过' : reason === 'same' ? '已一致' : '将改名'
async function previewRename() {
  if (!templateCategoryId.value) return
  renameLoading.value = true
  try {
    const response = await materialMasterApi.renameChanges(templateCategoryId.value)
    renameReport.value = response.data ?? undefined
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '改名预演失败')
  } finally {
    renameLoading.value = false
  }
}
async function applyRename() {
  if (!templateCategoryId.value || !renameReport.value?.changeable) return
  try {
    await ElMessageBox.confirm(
      `将按当前命名规则改 ${renameReport.value.changeable} 条物料名称，逐条留痕，是否继续？`,
      '批量改名'
    )
  } catch {
    return
  }
  renameLoading.value = true
  try {
    const response = await materialMasterApi.applyRename(templateCategoryId.value)
    ElMessage.success(`已改名 ${response.data ?? 0} 条`)
    emit('success')
    renameLoading.value = false
    await previewRename()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量改名失败')
    renameLoading.value = false
  }
}
const editableCategories = computed(() => categories.value.filter((c) => c.categoryLevel > 1))
// 仅用于表单提示与提交预检；保存规则由后端统一校验。
const categoryPrefixRequired = computed(() => !categoryEditId.value || !categories.value.some(
  child => child.parentId === categoryEditId.value && child.status === CommonStatusEnum.NORMAL.value
))
const leaves = computed(() =>
  categories.value.filter((c) => !categories.value.some((child) => child.parentId === c.categoryId))
)
const parentCategories = computed(() =>
  categories.value.filter(
    (c) => c.categoryLevel < 4 && c.categoryId !== categoryEditId.value
  )
)
const allowedMeta = computed(() =>
  (dictionaries.value.material_meta || []).filter((o) => schema.value.meta_options.includes(o.code))
)
let templateRequest = 0
async function refresh() {
  const [categoryResponse, dictionaryResponse] = await Promise.all([
    materialMasterApi.categories(true),
    materialMasterApi.dictionaries(),
  ])
  categories.value = categoryResponse.data || []
  dictionaries.value = dictionaryResponse.data || {}
}
onMounted(async () => {
  error.value = ''
  try {
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载配置失败'
  }
})
function editCategory(id?: number) {
  const category = categories.value.find((c) => c.categoryId === id)
  Object.assign(categoryForm, {
    parentId: category?.parentId ?? undefined,
    categoryCode: category?.categoryCode || '',
    modelPrefix: category?.modelPrefix || '',
    categoryName: category?.categoryName || '',
    sortOrder: category?.sortOrder ?? 0,
    status: category?.status ?? CommonStatusEnum.NORMAL.value,
  })
}
async function saveCategory() {
  busy.value = true
  error.value = ''
  try {
    if (categoryPrefixRequired.value) {
      if (!categoryForm.modelPrefix?.trim()) throw new Error('型号前缀不能为空，请配置品类型号前缀')
      if (!/^[A-Z0-9]{1,8}$/.test(categoryForm.modelPrefix)) throw new Error('型号前缀须为1～8位大写字母或数字')
    }
    await materialMasterApi.saveCategory({ ...categoryForm, categoryId: categoryEditId.value })
    await refresh()
    ElMessage.success('分类已保存')
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '分类保存失败'
  } finally {
    busy.value = false
  }
}
async function loadTemplate(id: number) {
  const request = ++templateRequest
  templateExists.value = false
  loadedCategoryId.value = undefined
  templateLoading.value = true
  error.value = ''
  schema.value = { version: 1, meta_options: [], fields: [] }
  namePattern.value = { fields: [], separator: ' ', empty: 'skip', separators: {} }
  modelPattern.value = { fields: [], separator: '', empty: 'skip' }
  modelSamples.value = []
  void loadFieldOptions(id)
  specPattern.value = { fields: [], show_label: false, with_unit: true, unit_position: 'end' }
  namingSamples.value = []
  namingMessage.value = ''
  renameReport.value = undefined
  templateCode.value = ''
  templateName.value = ''
  const category = categories.value.find((c) => c.categoryId === id)
  try {
    const response = await materialMasterApi.template(id)
    if (request !== templateRequest) return
    if (!response.data) {
      templateName.value = `${category?.categoryName || ''}属性模板`
      templateStatus.value = CommonStatusEnum.NORMAL.value
      return
    }
    templateExists.value = true
    schema.value = clone(response.data.schema)
    void loadFieldOptions(id)
    specPattern.value = response.data.schema.spec_pattern
      ? {
          fields: (response.data.schema.spec_pattern.fields || []).map(item => ({ ...item, sep: item.sep || '' })),
          show_label: !!response.data.schema.spec_pattern.show_label,
          with_unit: response.data.schema.spec_pattern.with_unit !== false,
          unit_position: (response.data.schema.spec_pattern.unit_position as 'each' | 'end' | 'none') || 'end',
        }
      : { fields: [], show_label: false, with_unit: true, unit_position: 'end' }
    modelPattern.value = response.data.schema.model_pattern
      ? JSON.parse(JSON.stringify(response.data.schema.model_pattern))
      : { fields: [], separator: '', empty: 'skip' }
    namePattern.value = response.data.schema.name_pattern
      ? {
          ...response.data.schema.name_pattern,
          fields: [...response.data.schema.name_pattern.fields],
          separator: response.data.schema.name_pattern.separator ?? ' ',
          separators: { ...(response.data.schema.name_pattern.separators || {}) },
          empty: response.data.schema.name_pattern.empty === 'placeholder' ? 'placeholder' : 'skip',
        }
      : { fields: [], separator: ' ', empty: 'skip' }
    templateCode.value = response.data.template.templateCode
    templateName.value = response.data.template.templateName
    templateStatus.value = response.data.template.status
  } catch (e) {
    if (request === templateRequest) error.value = e instanceof Error ? e.message : '模板加载失败'
  } finally {
    if (request === templateRequest) {
      templateLoading.value = false
      if (!error.value) { loadedCategoryId.value = id; captureBaselines() }
    }
  }
}
function addField() {
  schema.value.fields.push({ key: '', label: '', type: 'string', required: false, common: true, in_spec: true, spec_order: schema.value.fields.length + 1 })
}

/** 调整属性显示顺序；规格排序（spec_order）由用户在该列单独维护，不随移动自动改写。 */
function moveField(index: number, offset: -1 | 1) {
  const fields = schema.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [field] = fields.splice(index, 1)
  fields.splice(target, 0, field)
}

function addCommonFields() {
  const category = categories.value.find(c => c.categoryId === templateCategoryId.value)
  const added = addRecommendedAttributes(schema.value, category?.categoryCode || '')
  ElMessage.success(`已补充 ${added} 项可选属性，请检查后保存模板`)
}
const conditionKey = (field: MaterialAttributeField) => Object.keys(field.visible_when || {})[0] || ''
const conditionValue = (field: MaterialAttributeField) => field.visible_when?.[conditionKey(field)] || ''
function setConditionKey(field: MaterialAttributeField, key?: string) {
  field.visible_when = key ? { [key]: '' } : undefined
}
function setConditionValue(field: MaterialAttributeField, value: string) {
  const key = conditionKey(field)
  if (key) field.visible_when = { ...field.visible_when, [key]: value }
}
function conditionOptions(field: MaterialAttributeField) {
  const dependency = schema.value.fields.find(f => f.key === conditionKey(field))
  if (dependency?.type === 'boolean') return [{ code: 'true', label: dependency.true_label || '是' }, { code: 'false', label: dependency.false_label || '否' }]
  return dependency?.options || []
}
function optionText(field: MaterialAttributeField) {
  return (field.options || []).map((o) => `${o.code}=${o.label}`).join('\n')
}
function setOptions(field: MaterialAttributeField, value: string) {
  field.options = value
    .split('\n')
    .filter((line) => line.trim())
    .map((line) => {
      const index = line.indexOf('=')
      return {
        code: (index < 0 ? line : line.slice(0, index)).trim(),
        label: (index < 0 ? line : line.slice(index + 1)).trim(),
      }
    })
}
async function saveTemplate() {
  if (!canSave()) return
  busy.value = true
  error.value = ''
  try {
    const { name_pattern, model_pattern, spec_pattern, ...fields } = schema.value
    const response = await materialMasterApi.saveTemplateFields({
      categoryId: templateCategoryId.value,
      templateCode: templateCode.value,
      templateName: templateName.value,
      attributeSchema: clone(fields),
      status: templateStatus.value,
    })
    if (!response.data) throw new Error('属性字段保存结果为空，请重新加载确认')
    // 只确认属性块；规则草稿与它们的未保存标记保留。
    schema.value = { ...response.data.schema }
    templateExists.value = true
    baselines.fields = fieldSnapshot()
    await refresh()
    ElMessage.success('属性字段已保存')
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '模板保存失败'
  } finally {
    busy.value = false
  }
}
async function savePattern(block: MaterialPatternBlock) {
  if (!canSave() || !templateExists.value || !templateCategoryId.value) return
  busy.value = true
  error.value = ''
  const pattern = clone(patternDraft(block))
  try {
    const separators = block === 'spec_pattern'
      ? specPattern.value.fields.map(field => field.sep ?? '')
      : [((pattern as MaterialNamingPattern).separator ?? ''), ...Object.values((pattern as MaterialNamingPattern).separators ?? {})]
    if (separators.some(value => Array.from(value).length > 3)) throw new Error('分隔符最多允许3个字符；留空表示紧贴')
    if (separators.some(value => /[\u0000-\u001f\u007f-\u009f]/.test(value))) throw new Error('分隔符不能包含换行、制表符或其它控制字符')
    const response = await materialMasterApi.savePattern({ categoryId: templateCategoryId.value, block, pattern } as MaterialPatternSave)
    if (!response.data) throw new Error('规则保存结果为空，请重新加载确认')
    // 回读只合入本块，不重载整个表单，不覆盖其它块的草稿。
    const saved = response.data.schema[block]
    if (block === 'spec_pattern') schema.value.spec_pattern = saved as MaterialAttributeSchema['spec_pattern']
    else schema.value[block] = saved as MaterialNamingPattern | null
    baselines[block] = JSON.stringify(pattern)
    if (block === 'spec_pattern') await previewSpecification()
    if (block === 'name_pattern') renameReport.value = undefined
    ElMessage.success('本块已保存')
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '规则保存失败'
  } finally {
    busy.value = false
  }
}
async function removeTemplate() {
  if (!templateCategoryId.value) return
  try {
    await ElMessageBox.confirm(
      '移除未使用模板后可在此分类下继续建子分类，已有物料引用的模板无法移除。',
      '移除属性模板'
    )
  } catch {
    return
  }
  busy.value = true
  error.value = ''
  try {
    await materialMasterApi.removeUnusedTemplate(templateCategoryId.value)
    await refresh()
    await loadTemplate(templateCategoryId.value)
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '模板移除失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
/* 独立页面（物料分类 / 属性模板 / 命名规则）只显示自己那一页，不出现平级 tab 头 */
.single-section > :deep(.el-tabs__header) {
  display: none;
}

.rule-summary { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-bottom: 12px; }
.block-tabs { margin-top: 12px; }
.naming-rule { margin-top: 8px; }
.naming-tokens { display: flex; flex-wrap: wrap; gap: 4px 12px; margin-bottom: 8px; }
.naming-order { display: flex; flex-direction: column; gap: 2px; margin-bottom: 8px; }
.naming-order-row { display: flex; align-items: center; gap: 4px; }
.naming-order-label { min-width: 90px; color: var(--el-text-color-regular); }
.naming-sep-input { width: 90px; }
.naming-actions { display: flex; gap: 8px; margin: -4px 0 8px; }
.naming-order-no { min-width: 18px; text-align: right; color: var(--el-text-color-placeholder); font-size: 12px; }
.naming-order-row { cursor: grab; }
.naming-order-row.drop-target { outline: 2px solid var(--el-color-primary); background: var(--el-color-primary-light-9); }
.naming-order-row.dragging { background: var(--el-color-primary-light-9); }
.naming-order-row .el-button { opacity: 0.35; }
.naming-order-row:hover .el-button { opacity: 1; }
.naming-message { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
