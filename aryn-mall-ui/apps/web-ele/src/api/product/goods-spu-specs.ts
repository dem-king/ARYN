import { requestClient } from '#/api/request';

export async function getList(query: any) {
  return requestClient.get('/product/goodsspuspecs/list', { params: query });
}
