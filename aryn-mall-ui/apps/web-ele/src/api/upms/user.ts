import { requestClient } from '#/api/request';

/**
 * 获取用户分页
 */
export async function getPage(query: any) {
  return requestClient.get('/upms/user/page', {
    params: query,
  });
}

/**
 * 根据ID获取用户
 */
export async function getById(id: string) {
  return requestClient.get(`/upms/user/${id}`);
}

/**
 * 查看员工完整手机号
 *
 * 列表与详情响应中的手机号默认脱敏；联系同事需要完整号码时单独调用，
 * 后端要求 upms:sysuser:phone 权限并写入操作日志。
 */
export async function getPhone(id: string) {
  return requestClient.get(`/upms/user/${id}/phone`);
}

/**
 * 添加用户
 */
export async function addObj(data: any) {
  return requestClient.post('/upms/user', data);
}

/**
 * 编辑用户
 */
export async function editObj(data: any) {
  return requestClient.put('/upms/user', data);
}

/**
 * 删除用户
 */
export async function delObj(id: string) {
  return requestClient.delete(`/upms/user/${id}`);
}

/**
 * 检查手机号数量
 */
export async function checkPhoneCount(query: any) {
  return requestClient.get('/upms/user/check/phone', {
    params: query,
    headers: {
      isToken: false,
    },
  });
}

/**
 * 修改密码
 */
export async function editPassword(data: any) {
  return requestClient.post('/upms/user/password', data);
}

/**
 * 修改当前登录用户资料（昵称、邮箱）
 *
 * 个人资料接口始终作用于当前会话用户，平台管理员切换租户后也不能串到别的租户，
 * 因此固定不走租户切换请求头。
 */
export async function updateMyProfile(data: {
  email: string;
  nickname: string;
}) {
  return requestClient.put('/upms/profile', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 修改当前登录账号（需校验当前密码）
 */
export async function updateMyUsername(data: {
  password: string;
  username: string;
}) {
  return requestClient.put('/upms/profile/username', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 修改当前登录密码
 */
export async function updateMyPassword(data: {
  checkPassword: string;
  newPassword: string;
  password: string;
}) {
  return requestClient.put('/upms/profile/password', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 上传并保存当前用户头像
 */
export async function uploadMyAvatar(data: FormData) {
  return requestClient.post('/upms/profile/avatar', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
      isSwitchTenant: false,
    },
  });
}
