import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  VESSEL_MEMBER_BIND_SOURCE_OPTIONS,
  vesselMemberBindSourceLabel,
  vesselMemberBindSourceTagType,
} from '../constants';

/**
 * 船舶成员弹窗的「来源 + 回收」契约。
 *
 * 背景（2026-10-19）：`vessel_member` 有 5 条写入通道，信任级别从「运营审核」
 * 一路低到「任何拿到分享链接的人」。此前所有写入都长一个样——运营看到一条
 * 成员关系无法判断它是审核通过、邀请码、还是分享链接自动补建的；
 * 误绑后也无从按来源识别并回收。修复后要求：
 *
 * 1. 成员表格显示来源标签，低信任来源（SHARE_CART / APPLY_AUDIT_MISMATCH）显著着色；
 * 2. 绑定理由（跨船审核的偏离说明）跟在来源下面，构成可追溯证据；
 * 3. 成员弹窗提供解绑入口（必填原因），权限与页面一致（vessel:member:save）；
 * 4. 发起人不可在管理端授予、也不显示解绑。
 */

const here = dirname(fileURLToPath(import.meta.url));
const adminRoot = resolve(here, '../../../..');
const ARCHIVE = 'src/views/vessel/vessel/index.vue';
const MEMBER_API = 'src/api/vessel/vessel.ts';

function readAdmin(relative: string) {
  return readFileSync(resolve(adminRoot, relative), 'utf8');
}

describe('vessel member bind source', () => {
  it('成员列表显示来源列与来源标签', () => {
    const source = readAdmin(ARCHIVE);

    expect(source).toContain('vesselMemberBindSourceLabel');
    expect(source).toContain('vesselMemberBindSourceTagType');
    expect(source).toContain('bindSource');
    expect(source).toMatch(/label="来源"/);

    // bindReason 有值时在来源下方小字展示——它是跨船审核的偏离理由，
    // 只显示来源码的话运营仍然看不出「为什么绑到这条船」
    expect(source).toContain('bindReason');
    expect(source).toMatch(/v-if="scope\.row\.bindReason"/);
  });

  it('低信任来源用醒目配色：SHARE_CART warning、APPLY_AUDIT_MISMATCH danger', () => {
    // 配色收敛到纯函数，避免弹窗里散落 if/else；这里直接钉死映射
    expect(vesselMemberBindSourceTagType('SHARE_CART')).toBe('warning');
    expect(vesselMemberBindSourceTagType('APPLY_AUDIT_MISMATCH')).toBe(
      'danger',
    );
    expect(vesselMemberBindSourceTagType('APPLY_AUDIT')).toBe('info');
    expect(vesselMemberBindSourceTagType('INVITE_CODE')).toBe('info');
    expect(vesselMemberBindSourceTagType('MANUAL')).toBe('info');
    expect(vesselMemberBindSourceTagType(undefined)).toBe('info');
  });

  it('来源中文名：优先用服务端 label，缺失时按码值本地回落', () => {
    // 管理端成员列表走实体接口，只带 bindSource 不带 label；
    // 不回退会渲染成空白标签
    expect(vesselMemberBindSourceLabel('SHARE_CART')).toBe(
      '分享购物车自动加入',
    );
    expect(
      vesselMemberBindSourceLabel('SHARE_CART', '服务端下发的中文名'),
    ).toBe('服务端下发的中文名');
    // 未知值兜底与后端 VesselMemberBindSource#label 一致
    expect(vesselMemberBindSourceLabel('WHATEVER')).toBe('手工/历史');
    expect(vesselMemberBindSourceLabel(undefined)).toBe('手工/历史');
    // 空串 label 不能当成有效值（否则会渲染成空标签）
    expect(vesselMemberBindSourceLabel('SHARE_CART', '')).toBe(
      '分享购物车自动加入',
    );
  });

  it('来源字典与后端 VesselMemberBindSource 取值一致（7 项，含兜底 MANUAL）', () => {
    const declared = readAdmin(MEMBER_API);
    const values = VESSEL_MEMBER_BIND_SOURCE_OPTIONS.map((item) => item.value);

    expect(values).toEqual([
      'APPLY_AUDIT',
      'APPLY_AUDIT_MISMATCH',
      'INVITE_CODE',
      'MEMBER_ADD',
      'ADMIN_ADD',
      'SHARE_CART',
      'MANUAL',
    ]);
    // API 层必须声明这些码值，否则页面写死字符串、后端新增来源时静默漏标
    for (const value of values) {
      expect(declared).toContain(value);
    }
  });

  it('成员弹窗提供解绑入口：必填原因 → 调接口 → 刷新列表', () => {
    const source = readAdmin(ARCHIVE);

    expect(source).toContain('unbindMember');
    expect(source).toContain('unbindVesselMember');
    expect(source).toContain('ElMessageBox.prompt');
    // 原因必填：它既是站内信正文，也是服务端审计表的 reason
    expect(source).toMatch(/inputValidator[\s\S]{0,200}解绑必须填写原因/);
    // 解绑后原地刷新成员列表（否则列表还显示已解绑的人）
    expect(source).toMatch(/unbindMember[\s\S]{0,1400}loadMembers\(\)/);
    // 权限沿用所在页面
    expect(source).toMatch(/v-access:code="'vessel:member:save'"/);
  });

  it('发起人不可在管理端授予、也不显示解绑', () => {
    const source = readAdmin(ARCHIVE);

    // 添加成员下拉只允许 船员/确认人，补了提交前白名单校验（不再允许传 "1"）
    expect(source).toMatch(/MEMBER_ASSIGNABLE_ROLE_OPTIONS/);
    expect(source).toMatch(/MEMBER_ASSIGNABLE_ROLE_OPTIONS\.some/);
    expect(source).toMatch(/该成员角色不可由管理端授予/);
    // 发起人（memberRole==='1'）不渲染解绑按钮：
    // 移出最高管理角色会把这条船变成没人管的孤儿关系。
    // 停用船同理：服务端 requireVesselActive 会拒绝，按钮点了必然报错。
    const unbindButtonAt = source.indexOf('@click="unbindMember(scope.row)"');
    expect(unbindButtonAt).toBeGreaterThan(-1);
    const unbindButton = source.slice(
      Math.max(0, unbindButtonAt - 800),
      unbindButtonAt,
    );
    expect(unbindButton).toMatch(/scope\.row\.memberRole !== '1'/);
    expect(unbindButton).toMatch(/memberVessel\.status !== '0'/);

    // 白名单里不得出现 value: '1'
    const optionsAt = source.indexOf('MEMBER_ASSIGNABLE_ROLE_OPTIONS = [');
    expect(optionsAt).toBeGreaterThan(-1);
    const optionsSection = source.slice(optionsAt, optionsAt + 400);
    expect(optionsSection).not.toMatch(/value:\s*'1'/);
  });

  it('解绑接口走成员维度路径，不复用绑定申请的解绑入口', () => {
    const api = readAdmin(MEMBER_API);

    // POST /vessel/admin/{vesselId}/members/{userId}/unbind，body { reason }
    // 路径分段断言（不带插值，避免 no-template-curly-in-string）
    const unbindAt = api.indexOf('export async function unbindVesselMember(');
    expect(unbindAt).toBeGreaterThan(-1);
    const unbindFn = api.slice(unbindAt, unbindAt + 800);
    expect(unbindFn).toContain('/vessel/admin/');
    expect(unbindFn).toContain('/members/');
    expect(unbindFn).toContain('/unbind');
    expect(unbindFn).toMatch(/\{ reason \}/);
    expect(api).toMatch(/unbindVesselMember/);
    // 绑定申请页的解绑仍走它自己的入口，两条链路不能混用（权限不同）
    expect(api).toContain('unbindVesselMemberByApply');
    expect(api).toContain('/vessel/admin/bind-applies/unbind');
  });
});
