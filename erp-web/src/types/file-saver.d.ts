/**
 * file-saver 的类型声明
 *
 * ⚠️ erp-web 用了 `saveAs`（订单导出 CSV / 报表导出），
 *    但 package.json 里没有 `@types/file-saver` —— `vue-tsc --noEmit` 会报
 *    TS7016「Could not find a declaration file for module 'file-saver'」。
 *
 *    这里手写一份最小声明，避免为了一个函数去装一个类型包
 *    （装包要联网，CI 里 npm ci 也得能复现）。
 */
declare module 'file-saver' {
  export function saveAs(
    data: Blob | string | ArrayBuffer | ArrayBufferView,
    filename?: string,
    options?: { autoBom?: boolean }
  ): void
}
