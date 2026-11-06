import type { MessageUploadResult } from './types';

import { requestClient } from '#/api/request';

/**
 * 消息专用图片上传（契约 §17）。
 *
 * <p>multipart 字段名固定 `file`；**不要手写 Content-Type**，交给浏览器自动带
 * boundary。路径以 `/message` 微服务域开头，boot 模式由 requestClient 统一改写。
 * 文件大小/类型/魔数由后端最终校验，前端只做同口径预校验（见 service-state.ts）。</p>
 */
export function uploadConversationImage(file: File) {
  const form = new FormData();
  form.append('file', file);
  return requestClient.post<MessageUploadResult>(
    '/message/staff/upload/image',
    form,
  );
}
