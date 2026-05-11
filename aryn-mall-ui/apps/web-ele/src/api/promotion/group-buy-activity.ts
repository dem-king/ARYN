import { requestClient } from '#/api/request';

export async function getPage(query: any) {
  return requestClient.get('/promotion/groupbuy/activity/page', {
    params: query,
  });
}

export async function getById(id: string) {
  return requestClient.get(`/promotion/groupbuy/activity/${id}`);
}

export async function addObj(data: any) {
  return requestClient.post('/promotion/groupbuy/activity', data);
}

export async function delObj(id: string) {
  return requestClient.delete(`/promotion/groupbuy/activity/${id}`);
}

export async function editObj(data: any) {
  return requestClient.put('/promotion/groupbuy/activity', data);
}

export async function getRecordPage(query: any) {
  return requestClient.get('/promotion/app/groupbuy/record/page', {
    params: query,
  });
}
