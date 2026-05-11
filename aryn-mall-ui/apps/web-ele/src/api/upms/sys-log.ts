import { requestClient } from '#/api/request';

/**
 * 获取系统日志分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/syslog/page', {
    params: query,
  });
}

/**
 * 根据ID获取系统日志
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/syslog/${id}`);
}
