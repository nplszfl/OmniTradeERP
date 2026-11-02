/// <reference types="vite/client" />

/**
 * Vue 单文件组件的模块声明。
 *
 * ⚠️ 之前 erp-web 没有 tsconfig.json 也没有 env.d.ts，
 *    而 CI 的 code-quality.yml 里有一条 `npx tsc --noEmit`
 *    —— 没有 tsconfig 时 tsc 找不到任何编译目标，直接 exit 1
 *    → 这个 workflow 步骤必然失败（而且因为 workflow 从没被触发过，一直没人发现）。
 *
 *    补上 tsconfig.json + env.d.ts 后，类型检查用 `vue-tsc --noEmit`
 *    （普通 tsc 读不了 .vue 单文件组件，必须用 vue-tsc）。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
