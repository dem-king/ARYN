import { requestClient } from '#/api/request';

export async function getPage(params?: any) {
  return requestClient.get('/product/goodscategorybadge/page', { params });
}

export async function getList() {
  return requestClient.get('/product/goodscategorybadge/list');
}

export async function getById(id: string) {
  return requestClient.get(`/product/goodscategorybadge/${id}`);
}

export async function addObj(data: any) {
  return requestClient.post('/product/goodscategorybadge', data);
}

export async function editObj(data: any) {
  return requestClient.put('/product/goodscategorybadge', data);
}

export async function delObj(id: string) {
  return requestClient.delete(`/product/goodscategorybadge/${id}`);
}
