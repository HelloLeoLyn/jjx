<template>
  <div class="product-film-panel">
    <!-- 菲林配置标题 -->
    <el-divider content-position="left">
      菲林配置
      <el-tag v-if="filmList.length > 0" size="small" type="primary" class="film-count">
        {{ filmList.length }} 个菲林
      </el-tag>
    </el-divider>

    <!-- 菲林列表 -->
    <el-table
      v-if="filmList.length > 0"
      :data="filmList"
      border
      size="small"
      max-height="400"
      row-key="filmCode"
      class="film-table"
    >
      <el-table-column label="菲林编码" prop="filmCode" width="130" show-overflow-tooltip />

      <el-table-column label="菲林名称" prop="filmName" min-width="150" show-overflow-tooltip />

      <el-table-column label="菲林类型" prop="filmTypeName" width="100" />

      <el-table-column label="版本" prop="version" width="80" align="center" />

      <el-table-column label="当前版本" prop="isCurrentName" width="100" align="center" />

      <el-table-column label="尺寸" prop="filmSize" width="120" />

      <el-table-column label="厚度(mm)" prop="filmThickness" width="110" align="right" />

      <el-table-column label="材料" prop="filmMaterial" width="120" show-overflow-tooltip />

      <el-table-column label="颜色" prop="color" width="90" />

      <el-table-column label="审核状态" prop="approveStatusName" width="100" align="center" />

      <el-table-column label="设计人员" prop="designerName" width="110" />

      <!-- 下发状态 -->
      <el-table-column label="是否下发" prop="isReleased" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isReleased === 1 ? 'success' : 'info'" size="small">
            {{ row.isReleased === 1 ? '已下发' : '未下发' }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- 创建时间 -->
      <el-table-column label="创建时间" prop="createTime" width="165">
        <template #default="{ row }">
          {{ row.createTime ? parseTime(row.createTime) : '-' }}
        </template>
      </el-table-column>

      <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.remark || '-' }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态 -->
    <div v-else class="empty-section">
      <el-empty description="未配置菲林" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { parseTime } from '@/utils/format'
import type { ProductFullVO } from '@/types/product'

/**
 * 菲林类型
 * 直接复用产品详情中的菲林类型，避免重复定义。
 */
type ProductFilm = NonNullable<ProductFullVO['films']>[number]

/**
 * 组件 Props
 */
interface Props {
  /** 菲林数据列表 */
  films?: ProductFilm[]
}

const props = withDefaults(defineProps<Props>(), {
  films: () => [],
})

/**
 * 菲林列表
 */
const filmList = computed<ProductFilm[]>(() => props.films ?? [])
</script>

<style scoped lang="scss">
.product-film-panel {
  width: 100%;
  min-width: 0;
}

.film-count {
  margin-left: 8px;
}

.film-table {
  width: 100%;
}

.empty-section {
  padding: 20px;
  text-align: center;
  color: #909399;
}
</style>
