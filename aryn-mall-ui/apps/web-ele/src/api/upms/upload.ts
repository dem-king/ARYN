import { requestClient } from '#/api/request';

/**
 * 文件上传
 */
export async function uploadFile(data: any) {
  return requestClient.post('/upms/file/upload', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}
