// .eslintrc.cjs
module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2021: true,
  },
  // 扩展配置[citation:2][citation:6]
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-essential', // Vue 3 核心规则
    'plugin:@typescript-eslint/recommended',
    '@vue/eslint-config-typescript', // Vue + TypeScript 规则
    '@vue/eslint-config-prettier', // 关闭与 Prettier 冲突的 ESLint 规则
    'plugin:prettier/recommended', // 将 Prettier 规则作为 ESLint 规则运行[citation:3]
  ],
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: {
      jsx: true,
    },
  },
  plugins: ['vue', '@typescript-eslint', 'prettier'],
  rules: {
    // 自定义规则
    'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
    'vue/multi-word-component-names': 'off', // 允许单词组件名
    '@typescript-eslint/no-explicit-any': 'warn', // any 类型警告
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    'prettier/prettier': [
      'error',
      {
        endOfLine: 'auto',
      },
    ],
  },
  ignorePatterns: ['node_modules', 'dist', 'build', '*.d.ts'],
}
