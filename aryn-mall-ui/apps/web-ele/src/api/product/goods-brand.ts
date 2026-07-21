import { requestClient } from '#/api/request';

export async function getPage(params?: any) {
  return requestClient.get('/product/goodsbrand/page', { params });
}

export async function getList() {
  return requestClient.get('/product/goodsbrand/list');
}

export async function getById(id: string) {
  return requestClient.get(`/product/goodsbrand/${id}`);
}

export async function addObj(data: any) {
  return requestClient.post('/product/goodsbrand', data);
}

export async function editObj(data: any) {
  return requestClient.put('/product/goodsbrand', data);
}

export async function delObj(id: string) {
  return requestClient.delete(`/product/goodsbrand/${id}`);
}
