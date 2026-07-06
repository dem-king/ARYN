/**
 * 文件下载工具函数
 */

/**
 * 通用文件下载（通过 Blob 触发浏览器下载）
 * @param blob - 文件 Blob 数据
 * @param fileName - 下载的文件名
 */
export function downloadFile(blob: Blob, fileName: string): void {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

/**
 * Excel 导出请求（调用后端 GET 接口，responseType 为 blob）
 * @param url - 导出接口路径（如 /orderinfo/export）
 * @param fileName - 下载的文件名（含扩展名，如 订单列表.xlsx）
 * @param params - 查询参数（可选，用于传递筛选条件）
 */
export async function downloadExcel(
  url: string,
  fileName: string,
  params?: Record<string, any>,
): Promise<void> {
  // 动态导入 requestClient，避免循环依赖
  const { requestClient } = await import('#/api/request');
  const blob = await requestClient.download<Blob>(url, { params });
  downloadFile(blob, fileName);
}
