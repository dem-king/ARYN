import { requestClient } from '#/api/request';

/**
 * 获取数据源分页
 */
export async function getPage(query: any) {
  return requestClient.get('/gen/gen-table/page', {
    params: query,
    headers: {
      isSwitchTenant: false,
    },
  });
}

export async function genTabledDetail(dsName: string, tableName: string) {
  return requestClient.get(`/gen/gen-table/${dsName}/${tableName}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 编辑代码
 */
export async function editObj(data: any) {
  return requestClient.put('/gen/gen-table', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}
