import { requestClient } from '#/api/request';

/** 导入行结构（与后端 ProductImportRowDTO 对齐） */
export interface ProductImportRow {
  rowNo?: number;
  name: string;
  nameEn?: string;
  saleScope?: string;
  impaCode?: string;
  issaCode?: string;
  internalItemCode?: string;
  barcode?: string;
  matchType?: string;
  matchValue?: string;
  skuId?: string;
  categorySecondId?: string;
  brandId?: string;
  salesPrice?: number;
  stock?: number;
  purchaseUnit?: string;
  packageSpec?: string;
  moq?: number;
  stepQty?: number;
  storageType?: string;
}

export interface ImportPreviewResult {
  jobId: string;
  totalRows: number;
  successRows: number;
  errorRows: number;
  confirmable: boolean;
  errors: {
    errorMessage: string;
    errorType: string;
    rowNo: number;
  }[];
}

/**
 * 获取导入模板字段定义
 */
export async function getTemplate() {
  return requestClient.get('/product/product-import/template');
}

/**
 * 预览校验（前端解析后的结构化行）
 */
export async function previewImport(data: {
  fileName: string;
  rows: ProductImportRow[];
}) {
  return requestClient.post<ImportPreviewResult>(
    '/product/product-import/preview',
    data,
  );
}

/**
 * 确认导入
 */
export async function confirmImport(data: {
  fileName: string;
  jobId: string;
  rows: ProductImportRow[];
}) {
  return requestClient.post<number>('/product/product-import/confirm', data);
}

/**
 * 查询任务错误明细
 */
export async function getImportErrors(jobId: string) {
  return requestClient.get(`/product/product-import/${jobId}/errors`);
}

/**
 * 简易 CSV 行解析（首行为表头，按字段名映射；值去除包裹引号）
 */
export function parseCsvToRows(content: string): ProductImportRow[] {
  const lines = content
    .replaceAll('\r\n', '\n')
    .split('\n')
    .filter((line) => line.trim().length > 0);
  if (lines.length < 2) {
    return [];
  }
  const headers = lines[0]?.split(',').map((h) => h.trim()) ?? [];
  if (headers.length === 0) {
    return [];
  }
  return lines.slice(1).map((line, index) => {
    const values = line
      .split(',')
      .map((v) => v.trim().replaceAll(/^"|"$/g, ''));
    const record: Record<string, string> = {};
    headers.forEach((header, col) => {
      record[header] = values[col] ?? '';
    });
    return {
      rowNo: index + 1,
      name: record.name ?? '',
      nameEn: record.nameEn || undefined,
      saleScope: record.saleScope || undefined,
      impaCode: record.impaCode || undefined,
      issaCode: record.issaCode || undefined,
      internalItemCode: record.internalItemCode || undefined,
      barcode: record.barcode || undefined,
      matchType: record.matchType || undefined,
      matchValue: record.matchValue || undefined,
      categorySecondId: record.categorySecondId || undefined,
      brandId: record.brandId || undefined,
      salesPrice: record.salesPrice ? Number(record.salesPrice) : undefined,
      stock: record.stock ? Number(record.stock) : undefined,
      purchaseUnit: record.purchaseUnit || undefined,
      packageSpec: record.packageSpec || undefined,
      moq: record.moq ? Number(record.moq) : undefined,
      stepQty: record.stepQty ? Number(record.stepQty) : undefined,
      storageType: record.storageType || undefined,
    } as ProductImportRow;
  });
}

/**
 * 生成 CSV 模板内容并触发下载
 */
export function downloadCsvTemplate(headers: string[]) {
  const content = headers.join(',');
  const blob = new Blob([`\uFEFF${content}\n`], {
    type: 'text/csv;charset=utf-8;',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'product-import-template.csv';
  link.click();
  URL.revokeObjectURL(link.href);
}
