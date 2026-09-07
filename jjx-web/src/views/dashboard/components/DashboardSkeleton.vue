<template>
  <div>
    <div class="skeleton-stats">
      <el-card v-for="i in 4" :key="i" shadow="never" class="skeleton-card">
        <el-skeleton :rows="0" animated>
          <template #template>
            <div class="skeleton-stat-body">
              <el-skeleton-item variant="rect" class="skeleton-icon" />
              <div class="skeleton-lines">
                <el-skeleton-item variant="text" class="value-line" />
                <el-skeleton-item variant="text" class="label-line" />
              </div>
            </div>
          </template>
        </el-skeleton>
      </el-card>
    </div>
    <div class="skeleton-widgets">
      <el-card v-for="i in 2" :key="i" shadow="never" class="skeleton-card">
        <el-skeleton :rows="4" animated />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.skeleton-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.skeleton-widgets {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.skeleton-card {
  border-radius: 12px;
}
.skeleton-stat-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.skeleton-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
}
.skeleton-lines {
  flex: 1;
}
.value-line {
  width: 60%;
  height: 28px;
}
.label-line {
  width: 40%;
  height: 14px;
  margin-top: 4px;
}
</style>
