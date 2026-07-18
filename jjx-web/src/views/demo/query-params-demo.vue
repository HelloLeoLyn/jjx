<template>
  <div class="demo-page">
    <h1>useQueryParams 演示</h1>
    <p class="description">这个演示展示了如何使用useQueryParams组合式函数来简化查询参数管理</p>

    <div class="demo-section">
      <h2>1. 传统方式 vs useQueryParams</h2>

      <div class="comparison">
        <div class="traditional">
          <h3>传统方式</h3>
          <div class="code-block">
            <pre><code>// 需要手动赋值每个字段
const handleSearch = (params) => {
  queryParams.userName = params.userName || ''
  queryParams.phoneNumber = params.phoneNumber || ''
  queryParams.status = params.status || ''
  queryParams.pageNum = 1
  getList()
}</code></pre>
          </div>
          <p class="badge bad">❌ 重复代码，容易出错</p>
        </div>

        <div class="new-way">
          <h3>使用useQueryParams</h3>
          <div class="code-block">
            <pre><code>// 自动处理参数转换
const { queryParams, searchParams, reset, search } = useQueryParams(
  {
    pageNum: 1,
    pageSize: 10,
    userName: '',
    phoneNumber: '',
    status: '',
  },
  { onSearch: getList }
)

// 搜索和重置变得非常简单
const handleSearch = () => search()
const handleReset = () => reset()</code></pre>
          </div>
          <p class="badge good">✅ 代码简洁，自动处理</p>
        </div>
      </div>
    </div>

    <div class="demo-section">
      <h2>2. 实际效果演示</h2>
      <div class="demo-container">
        <div class="demo-form">
          <h3>搜索表单</h3>
          <div class="form-group">
            <label>用户名：</label>
            <input v-model="demoSearchParams.userName" placeholder="请输入用户名" />
          </div>
          <div class="form-group">
            <label>手机号：</label>
            <input v-model="demoSearchParams.phoneNumber" placeholder="请输入手机号" />
          </div>
          <div class="form-group">
            <label>状态：</label>
            <select v-model="demoSearchParams.status">
              <option value="">全部</option>
              <option value="active">启用</option>
              <option value="inactive">停用</option>
            </select>
          </div>
          <div class="button-group">
            <button @click="demoSearch" class="btn btn-primary">搜索</button>
            <button @click="demoReset" class="btn btn-secondary">重置</button>
          </div>
        </div>

        <div class="demo-result">
          <h3>查询参数状态</h3>
          <div class="result-item">
            <h4>搜索参数（表单绑定）</h4>
            <pre>{{ JSON.stringify(demoSearchParams, null, 2) }}</pre>
          </div>
          <div class="result-item">
            <h4>查询参数（API请求）</h4>
            <pre>{{ JSON.stringify(demoQueryParams, null, 2) }}</pre>
          </div>
          <div class="result-item">
            <h4>是否有查询条件</h4>
            <p :class="{ 'has-conditions': demoHasConditions }">
              {{ demoHasConditions ? '✅ 有查询条件' : '❌ 无查询条件' }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <div class="demo-section">
      <h2>3. 核心功能</h2>
      <div class="features-grid">
        <div class="feature">
          <div class="feature-icon">🔍</div>
          <h4>自动过滤空值</h4>
          <p>自动过滤空字符串、null、undefined，保留0和false</p>
        </div>
        <div class="feature">
          <div class="feature-icon">📅</div>
          <h4>日期范围转换</h4>
          <p>自动将日期范围转换为开始和结束时间参数</p>
        </div>
        <div class="feature">
          <div class="feature-icon">🔄</div>
          <h4>自动重置分页</h4>
          <p>查询条件变化时自动重置分页到第一页</p>
        </div>
        <div class="feature">
          <div class="feature-icon">⏱️</div>
          <h4>防抖搜索</h4>
          <p>支持防抖搜索，减少不必要的API请求</p>
        </div>
        <div class="feature">
          <div class="feature-icon">🔄</div>
          <h4>实时搜索</h4>
          <p>支持表单变化时自动触发搜索</p>
        </div>
        <div class="feature">
          <div class="feature-icon">🔄</div>
          <h4>一键重置</h4>
          <p>提供reset方法，一键恢复到默认状态</p>
        </div>
      </div>
    </div>

    <div class="demo-section">
      <h2>4. 如何使用</h2>
      <div class="steps">
        <div class="step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h3>导入useQueryParams</h3>
            <pre><code>import { useQueryParams } from '@/composables/useQueryParams'</code></pre>
          </div>
        </div>
        <div class="step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h3>定义默认参数</h3>
            <pre><code>const defaultParams = {
  pageNum: 1,
  pageSize: 10,
  userName: '',
  phoneNumber: '',
  status: '',
}</code></pre>
          </div>
        </div>
        <div class="step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h3>使用useQueryParams</h3>
            <pre><code>const { queryParams, searchParams, reset, search } = useQueryParams(
  defaultParams,
  { onSearch: getList }
)</code></pre>
          </div>
        </div>
        <div class="step">
          <div class="step-number">4</div>
          <div class="step-content">
            <h3>绑定到SearchContainer</h3>
            <pre><code></code></pre>
          </div>
        </div>
      </div>
    </div>

    <div class="demo-section">
      <h2>5. 改造现有页面</h2>
      <p>要改造现有页面，只需要以下几个步骤：</p>
      <ol class="migration-list">
        <li><strong>导入useQueryParams函数</strong> - 在页面顶部添加导入语句</li>
        <li><strong>替换原有的queryParams定义</strong> - 使用useQueryParams返回的queryParams</li>
        <li><strong>删除手动赋值的代码</strong> - 不再需要手动赋值每个字段</li>
        <li><strong>使用useQueryParams提供的方法</strong> - 使用search和reset方法</li>
        <li><strong>绑定searchParams到SearchContainer</strong> - 使用v-model绑定</li>
      </ol>
      <div class="tip">
        <strong>💡 提示：</strong>用户管理页面已经完成了改造，可以作为参考。查看
        <code>jjx-web/src/views/system/user/index.vue</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useQueryParams } from '@/composables/useQueryParams'

// 演示用的查询参数管理
const {
  queryParams: demoQueryParams,
  searchParams: demoSearchParams,
  reset: demoReset,
  search: demoSearch,
  hasSearchConditions: demoHasConditions,
} = useQueryParams(
  {
    pageNum: 1,
    pageSize: 10,
    userName: '',
    phoneNumber: '',
    status: '',
  },
  {
    onSearch: () => {
      console.log('搜索参数已更新:', demoQueryParams)
      // 在实际项目中，这里会调用API获取数据
    },
    immediate: true, // 实时搜索演示
  }
)
</script>

<style scoped lang="scss">
.demo-page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
  background-color: #f8f9fa;
  min-height: 100vh;

  h1 {
    color: #2c3e50;
    margin-bottom: 8px;
  }

  .description {
    color: #7f8c8d;
    margin-bottom: 32px;
    font-size: 16px;
  }

  .demo-section {
    background: white;
    border-radius: 8px;
    padding: 24px;
    margin-bottom: 24px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

    h2 {
      color: #3498db;
      margin-top: 0;
      margin-bottom: 16px;
      border-bottom: 2px solid #ecf0f1;
      padding-bottom: 8px;
    }

    h3 {
      color: #2c3e50;
      margin-top: 0;
    }

    h4 {
      color: #34495e;
      margin-top: 0;
    }
  }

  .comparison {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
    margin-top: 24px;

    @media (max-width: 768px) {
      grid-template-columns: 1fr;
    }

    .traditional,
    .new-way {
      padding: 20px;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
    }

    .traditional {
      border-color: #feb2b2;
      background: #fff5f5;
    }

    .new-way {
      border-color: #9ae6b4;
      background: #f0fff4;
    }
  }

  .code-block {
    background: #2d3748;
    color: #e2e8f0;
    padding: 16px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 16px 0;

    pre {
      margin: 0;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      font-size: 14px;
      line-height: 1.5;
    }

    code {
      font-family: inherit;
    }
  }

  .badge {
    display: inline-block;
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 14px;
    font-weight: 500;

    &.bad {
      background: #fed7d7;
      color: #9b2c2c;
    }

    &.good {
      background: #c6f6d5;
      color: #22543d;
    }
  }

  .demo-container {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
    margin-top: 24px;

    @media (max-width: 768px) {
      grid-template-columns: 1fr;
    }
  }

  .demo-form {
    background: #f8fafc;
    padding: 20px;
    border-radius: 8px;
    border: 1px solid #e2e8f0;

    .form-group {
      margin-bottom: 16px;

      label {
        display: block;
        margin-bottom: 6px;
        font-weight: 500;
        color: #4a5568;
      }

      input,
      select {
        width: 100%;
        padding: 8px 12px;
        border: 1px solid #cbd5e0;
        border-radius: 4px;
        font-size: 14px;

        &:focus {
          outline: none;
          border-color: #4299e1;
          box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.1);
        }
      }
    }

    .button-group {
      display: flex;
      gap: 12px;
      margin-top: 20px;

      .btn {
        padding: 10px 20px;
        border: none;
        border-radius: 4px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s;

        &-primary {
          background-color: #4299e1;
          color: white;

          &:hover {
            background-color: #3182ce;
          }
        }

        &-secondary {
          background-color: #e2e8f0;
          color: #4a5568;

          &:hover {
            background-color: #cbd5e0;
          }
        }
      }
    }
  }

  .demo-result {
    background: #f8fafc;
    padding: 20px;
    border-radius: 8px;
    border: 1px solid #e2e8f0;

    .result-item {
      margin-bottom: 20px;

      pre {
        background: #2d3748;
        color: #e2e8f0;
        padding: 12px;
        border-radius: 4px;
        font-size: 13px;
        overflow-x: auto;
        margin: 8px 0;
      }

      p {
        padding: 12px;
        border-radius: 4px;
        font-weight: 500;

        &.has-conditions {
          background-color: #d1fae5;
          color: #065f46;
        }
      }
    }
  }

  .features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 20px;
    margin-top: 24px;

    .feature {
      padding: 20px;
      background: #f8fafc;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      text-align: center;

      .feature-icon {
        font-size: 32px;
        margin-bottom: 12px;
      }

      h4 {
        margin: 12px 0 8px;
        color: #2c3e50;
      }

      p {
        color: #718096;
        font-size: 14px;
        line-height: 1.5;
        margin: 0;
      }
    }
  }

  .steps {
    margin-top: 24px;

    .step {
      display: flex;
      align-items: flex-start;
      margin-bottom: 24px;
      padding: 16px;
      background: #f8fafc;
      border-radius: 8px;
      border-left: 4px solid #4299e1;

      .step-number {
        flex-shrink: 0;
        width: 32px;
        height: 32px;
        background: #4299e1;
        color: white;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: bold;
        margin-right: 16px;
      }

      .step-content {
        flex: 1;

        h3 {
          margin-top: 0;
          margin-bottom: 12px;
          color: #2c3e50;
        }
      }
    }
  }

  .migration-list {
    padding-left: 24px;
    margin: 16px 0;

    li {
      margin-bottom: 12px;
      padding-left: 8px;
      line-height: 1.6;
    }
  }

  .tip {
    background: #e3f2fd;
    padding: 16px;
    border-radius: 6px;
    color: #1565c0;
    border-left: 4px solid #2196f3;
    margin-top: 16px;

    code {
      background: #bbdefb;
      padding: 2px 6px;
      border-radius: 4px;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      font-size: 14px;
    }
  }
}
</style>
