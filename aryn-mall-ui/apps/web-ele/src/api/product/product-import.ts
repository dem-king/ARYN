import type { SkuMeasurementForm } from './goods-spu';

import { requestClient } from '#/api/request';

/** 导入行结构（与后端 ProductImportRowDTO 对齐；船供资料列已随船供化下线） */
export interface ProductImportRow extends SkuMeasurementForm {
  rowNo?: number;
  name: string;
  matchValue?: string;
  skuId?: string;
  categorySecondId?: string;
  brandId?: string;
  salesPrice?: number;
  stock?: number;
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
 * 上传 Excel（.xlsx/.xls）并由服务端解析预览；解析行落库，确认时服务端重新校验
 */
export async function uploadImport(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return requestClient.post<ImportPreviewResult>(
    '/product/product-import/upload',
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );
}

/**
 * 结构化行预览（CSV 前端解析后的兼容入口）
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
 * 确认导入（服务端从解析行读取数据，重新校验后写入）
 */
export async function confirmImport(jobId: string) {
  return requestClient.post<number>(`/product/product-import/${jobId}/confirm`);
}

/**
 * 查询任务错误明细
 */
export async function getImportErrors(jobId: string) {
  return requestClient.get(`/product/product-import/${jobId}/errors`);
}

/**
 * 下载 Excel 导入模板（服务端生成 .xlsx）
 */
export function downloadExcelTemplate() {
  return requestClient
    .download('/product/product-import/template')
    .then((response: any) => {
      const blob =
        response instanceof Blob
          ? response
          : new Blob([response], {
              type: 'application/octet-stream',
            });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = '商品导入模板.xlsx';
      document.body.append(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    });
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
      name: record.name || record['商品中文名'] || '',
      matchValue:
        record['更新匹配值(SKU编号)'] || record.matchValue || undefined,
      categorySecondId: record.categorySecondId || undefined,
      brandId: record.brandId || undefined,
      salesPrice:
        record.salesPrice || record['售价(元)']
          ? Number(record.salesPrice || record['售价(元)'])
          : undefined,
      stock:
        record.stock || record['库存']
          ? Number(record.stock || record['库存'])
          : undefined,
      salesUnit: record.salesUnit || record['销售单位'] || undefined,
      baseDimension: record.baseDimension || record['基准量纲'] || undefined,
      baseUnit: record.baseUnit || record['基准单位'] || undefined,
      baseQtyPerSalesUnit:
        record.baseQtyPerSalesUnit || record['每销售单位基准量'] || undefined,
      quantityPrecision:
        record.quantityPrecision ||
        record['度量精度(EXACT/APPROX/UNKNOWN)'] ||
        undefined,
      minOrderQty:
        record.minOrderQty || record['起订量']
          ? Number(record.minOrderQty || record['起订量'])
          : undefined,
      stepQty:
        record.stepQty || record['数量步长']
          ? Number(record.stepQty || record['数量步长'])
          : undefined,
      unitSpecText: record.unitSpecText || record['展示规格'] || undefined,
      measurementRevision:
        record.measurementRevision || record['度量版本']
          ? Number(record.measurementRevision || record['度量版本'])
          : undefined,
    } as ProductImportRow;
  });
}
