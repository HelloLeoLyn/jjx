<template>
  <div class="jjx-icon-demo">
    <el-card>
      <OrderExport />
    </el-card>
    <el-card>
      <template #header>
        <span>JJX 图标选择器示例</span>
      </template>

      <!-- 示例1: 基本使用 -->
      <el-divider content-position="left">基本使用</el-divider>
      <div class="demo-section">
        <p class="demo-desc">使用 v-model 绑定选中的图标名称</p>
        <div class="demo-row">
          <JJXIcon v-model="selectedIcon1" />
          <el-tag v-if="selectedIcon1" type="success" style="margin-left: 12px">
            已选择: {{ selectedIcon1 }}
          </el-tag>
          <el-tag v-else type="info" style="margin-left: 12px">未选择图标</el-tag>
        </div>
      </div>

      <!-- 示例2: 在对话框中使用 -->
      <el-divider content-position="left">在对话框中使用</el-divider>
      <div class="demo-section">
        <p class="demo-desc">点击按钮打开对话框，选择图标</p>
        <div class="demo-row">
          <el-button type="primary" @click="dialogVisible = true">选择图标</el-button>
          <el-tag v-if="selectedIcon2" type="success" style="margin-left: 12px">
            已选择: {{ selectedIcon2 }}
          </el-tag>
          <el-tag v-else type="info" style="margin-left: 12px">未选择图标</el-tag>
        </div>

        <el-dialog v-model="dialogVisible" title="选择 JJX 图标" width="700px">
          <JJXIcon v-model="selectedIcon2" />
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="dialogVisible = false">确认</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- 示例3: 显示选中的图标 -->
      <el-divider content-position="left">显示选中的图标</el-divider>
      <div class="demo-section">
        <p class="demo-desc">使用 SvgIcon 组件渲染选中的图标</p>
        <div class="demo-row">
          <el-input v-model="iconName" placeholder="输入图标名称" style="width: 200px" />
          <el-button type="primary" @click="showIcon = iconName">显示图标</el-button>
        </div>
        <div v-if="showIcon" class="icon-preview-box">
          <SvgIcon :name="'jjx-' + showIcon" :size="64" />
          <p class="icon-code">SvgIcon name="jjx-{{ showIcon }}"</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import OrderExport from '../purchase/order/components/OrderExport.vue'
import { ref } from 'vue'

// 示例1
const selectedIcon1 = ref('')

// 示例2
const dialogVisible = ref(false)
const selectedIcon2 = ref('')

// 示例3
const iconName = ref('')
const showIcon = ref('')
</script>

<style scoped lang="scss">
.jjx-icon-demo {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;

  .demo-section {
    margin-bottom: 16px;

    .demo-desc {
      font-size: 13px;
      color: #909399;
      margin-bottom: 12px;
    }

    .demo-row {
      display: flex;
      align-items: center;
    }

    .icon-preview-box {
      margin-top: 16px;
      padding: 24px;
      border: 1px dashed #dcdfe6;
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;

      .icon-code {
        font-size: 13px;
        color: #409eff;
        background: #ecf5ff;
        padding: 4px 12px;
        border-radius: 4px;
        font-family: monospace;
      }
    }
  }
}
</style>
