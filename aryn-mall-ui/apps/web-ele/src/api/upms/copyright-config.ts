import { requestClient } from '#/api/request';

/** 端类型：app 用户端（C 端商城）；merchant 商户端（个人信息页页脚 / 品牌关于弹窗）；platform 平台端（后台登录页） */
export type CopyrightClientType = 'app' | 'merchant' | 'platform';

export interface CopyrightConfig {
  clientType: CopyrightClientType;
  copyrightLink: string;
  copyrightText: string;
  icp: string;
  icpLink: string;
  loginIcon: string;
  loginSubtitle: string;
  loginTitle: string;
  logoUrl: string;
  name: string;
}

/** 渲染端下发值：名称与 Logo 已由服务端回落租户品牌 */
export type CopyrightRenderConfig = CopyrightConfig;

/**
 * 读取指定端的版权配置（管理端原始值，不做品牌回落）。
 *
 * 用户端凭 upms:copyright:get，商户端/平台端需平台管理员（upms:copyright:platform:get）。
 */
export async function getCopyrightConfig(clientType: CopyrightClientType) {
  return requestClient.get<CopyrightConfig>('/upms/copyright-config', {
    params: { clientType },
  });
}

/**
 * 保存指定端的版权配置。
 *
 * 清空名称/Logo 表示跟随租户品牌，服务端按全字段覆盖写入。
 */
export async function saveCopyrightConfig(data: CopyrightConfig) {
  return requestClient.put('/upms/copyright-config', data);
}

/**
 * 免登读取版权配置（登录页/个人信息页/关于弹窗用）。
 *
 * platform 端不需要租户：服务端固定取平台租户配置，登录前即可调用；
 * merchant/app 端按当前租户取值，登录后调用时应带 token（租户来自会话，
 * 平台管理员切换租户时由 tenant-id 头覆盖）。
 */
export async function getPublicCopyrightConfig(
  clientType: CopyrightClientType,
  options?: { withToken?: boolean },
) {
  return requestClient.get<CopyrightRenderConfig>(
    '/upms/app/copyright-config',
    {
      headers: { isToken: options?.withToken === true },
      params: { clientType },
    },
  );
}
