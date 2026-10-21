import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * 船舶绑定申请审核弹窗的契约。
 *
 * 背景（2026-10-16）：原先审核员面对三个选项，但系统里其实知道答案——
 * 「申请里的船在不在系统内」打开弹窗时就能查出来，却要靠提交失败去试。
 * 更严重的是三个选项都没用 IMO/呼号：申请单专门收集了这个字段（C 端占位文案
 * 写的就是「便于运营匹配」），而 IMO 是船的终身唯一标识、表上还有唯一键，
 * 船名却只是普通索引。船名一旦有出入（空格、全角、英文名），
 * 三个选项没有一个能把它认出来，审核员只能去点「新建」——
 * 船名没有唯一约束，于是静默产出重复档案，而全仓没有任何合并工具。
 *
 * 本测试钉死修复后的口径，防止回退：
 * 1. 打开弹窗即预检，把结论摆在选项之前；
 * 2. 自动匹配必须认 IMO/呼号，不只是船名；
 * 3. 新建前拦同名；
 * 4. 样式类名必须真实存在（幽灵类名陷阱：mb10/text-12px 这类写法不产生任何 CSS）。
 */
const here = dirname(fileURLToPath(import.meta.url));
const adminRoot = resolve(here, '../../../..');
const repoRoot = resolve(adminRoot, '../../..');

function readAdmin(relative: string) {
  return readFileSync(resolve(adminRoot, relative), 'utf8');
}

function readBackend(relative: string) {
  return readFileSync(resolve(repoRoot, 'aryn-mall-java', relative), 'utf8');
}

const DIALOG = 'src/views/vessel/bind-apply/index.vue';
const BIND_SERVICE =
  'aryn-vessel/aryn-vessel-biz/src/main/java/com/aryn/cloud/vessel/service/impl/VesselBindServiceImpl.java';
const VESSEL_SERVICE =
  'aryn-vessel/aryn-vessel-biz/src/main/java/com/aryn/cloud/vessel/service/impl/VesselServiceImpl.java';
const BIND_ADMIN_CONTROLLER =
  'aryn-vessel/aryn-vessel-biz/src/main/java/com/aryn/cloud/vessel/controller/admin/VesselBindAdminController.java';

describe('vessel bind apply audit dialog', () => {
  it('打开弹窗即预检，先给结论再给选项', () => {
    const source = readAdmin(DIALOG);

    expect(source).toContain('getBindApplyPrecheck');
    // 预检结论必须渲染在选项之前：审核员先看到「在不在系统里」再决定怎么处理
    const precheckIndex = source.indexOf('prechecking');
    const radioIndex = source.indexOf('绑定已有船舶');
    expect(precheckIndex).toBeGreaterThan(-1);
    expect(radioIndex).toBeGreaterThan(precheckIndex);
  });

  it('预检失败不阻断审核，退化为手动选择', () => {
    const source = readAdmin(DIALOG);

    // 预检与候选列表放在 try/catch 内：接口挂了仍能人工选船完成审核
    expect(source).toMatch(/catch\s*\{[\s\S]*?vesselOptions\.value = \[\]/);
    expect(source).toContain('prechecking');
  });

  it('候选下拉按关键字远程检索且展示 IMO/呼号，不再写死 200 条', () => {
    const source = readAdmin(DIALOG);

    expect(source).toContain('remote-method');
    expect(source).toContain('getBindApplyVesselOptions');
    // 列表项要带上认船用的身份信息，否则同名船无法区分
    expect(source).toContain('optionLabel');
    expect(source).toMatch(/imoCode/);
    expect(source).toMatch(/callSign/);
    // 预检预选的船必须补进选项，否则下拉渲染成裸 ID（选不中也看不懂）
    expect(source).toMatch(
      /candidates[\s\S]*?vesselOptions\.value = \[candidate, \.\.\.vesselOptions\.value\]/,
    );
    // 旧的写死尺寸调用必须消失，否则又会退化成「翻不到目标船」
    expect(source).not.toContain('getVesselPage');
  });

  it('新建分支拦同名并要显式确认，改了船名后确认作废', () => {
    const source = readAdmin(DIALOG);

    expect(source).toContain('confirmDuplicateName');
    expect(source).toContain('duplicateNameHit');
    // 船名变更必须重置确认勾选，否则改了名也能带着旧勾选提交，等于没拦
    expect(source).toMatch(
      /checkDuplicateName[\s\S]*?confirmDuplicateName = false/,
    );
    // 提交前也要核一次：船名由申请单预填，审核员可能没碰输入框、没触发 blur，
    // 那勾选框就不会渲染，而服务端会回「请勾选确认」——审核员会被卡死
    expect(source).toMatch(
      /submitApprove[\s\S]*?await checkDuplicateName\(false\)/,
    );
  });

  it('自动匹配与新建都用 IMO/呼号认船，不只是船名', () => {
    const service = readBackend(BIND_SERVICE);

    // 自动匹配：IMO 优先、呼号次之、船名最后。
    // 分列采集后各查各的列——此前一个输入框同时比两列（imo_code OR call_sign），
    // 会出现「填的是呼号却按 IMO 报重复」的误导，故这里钉死「分开查」。
    expect(service).toContain('matchActiveVesselForApply');
    expect(service).toMatch(
      /matchActiveVesselForApply[\s\S]{0,900}getImoCode, imo\)/,
    );
    expect(service).toMatch(
      /matchActiveVesselForApply[\s\S]{0,1400}getCallSign, callSign\)/,
    );
    // 不能回退成两列混比的 OR 条件
    expect(service).not.toMatch(/getImoCode, imoOrCallSign/);
    // 新建时 IMO 撞库要拦住（唯一键，撞上说明这条船已录入）
    expect(service).toContain('这条船很可能已经录入');
    // 同名在营船要显式确认
    expect(service).toContain('已存在同名在营船舶');
  });

  it('预检分列查询：IMO 只比 imo_code、呼号只比 call_sign', () => {
    const service = readBackend(BIND_SERVICE);

    // 强身份键必须精确对列，混比会产生错误的「已存在」结论
    expect(service).toMatch(/getImoCode, imo\)/);
    expect(service).toMatch(/getCallSign, callSign\)/);
    expect(service).not.toMatch(
      /eq\(VesselInfo::getImoCode, [^)]*\)\.or\(\)\.eq\(VesselInfo::getCallSign/,
    );
  });

  it('候选与预检只认在营船舶，且显式带租户条件', () => {
    const service = readBackend(BIND_SERVICE);

    // 停用船绑了也无法下单，不该出现在候选里
    expect(service).toMatch(/activeVesselQuery\(String tenantId\)/);
    expect(service).toMatch(
      /activeVesselQuery\(String tenantId\)[\s\S]{0,200}getStatus/,
    );
    // 租户条件不能只依赖拦截器：本文件既有代码一律显式带上
    expect(service).toMatch(
      /activeVesselQuery\(String tenantId\)[\s\S]{0,200}getTenantId/,
    );
  });

  it('预检与候选接口沿用审核权限，不额外要求船舶档案权限', () => {
    const controller = readBackend(BIND_ADMIN_CONTROLLER);

    // 审核岗可能只有 vessel:bindapply:audit；若新接口要求 vessel:vessel:page 会 403。
    // 逐段取「路由注解 → 下一个路由注解」之间的内容，避免被类注释里的同名文案干扰。
    const sections = controller.split('@Operation');
    const precheckSection = sections.find((section) =>
      section.includes('/precheck'),
    );
    const optionsSection = sections.find((section) =>
      section.includes('vessel-options'),
    );

    expect(precheckSection).toBeDefined();
    expect(optionsSection).toBeDefined();
    for (const section of [precheckSection, optionsSection]) {
      expect(section).toContain('vessel:bindapply:audit');
      expect(section).not.toContain('vessel:vessel:page');
    }
  });

  it('新增代码不使用幽灵样式类名', () => {
    const source = readAdmin(DIALOG);

    // 这些写法不是合法 Tailwind 类，编译产物里根本没有对应规则，
    // 写了等于没写（仓库里另有 19 处历史遗留，见 memory: phantom class）。
    for (const phantom of [
      'mb10',
      'mb4',
      'text-12px',
      'text-13px',
      'gap-8px',
      'px-8px',
      'py-4px',
      'rounded-4px',
      'ml4',
      'ml6',
    ]) {
      expect(source).not.toMatch(new RegExp(`class="[^"]*\\b${phantom}\\b`));
    }
  });

  it('iMO 与呼号分列采集：申请单拆两列、预填不再靠位数猜', () => {
    const source = readAdmin(DIALOG);

    // 旧实现：申请单只有一个「IMO/呼号」输入框，审核时用 /^\d{7}$/ 猜是哪个。
    // 猜错的代价是把 7 位呼号写进 IMO 列，而 IMO 是唯一键级的认船强键。
    expect(source).not.toMatch(/\^\\d\{7\}\$/);
    expect(source).not.toContain('imoLike');
    expect(source).not.toContain('label="IMO/呼号"');
    // 两列各自渲染、各自预填
    expect(source).toContain('applyVesselCallSign');
    expect(source).toMatch(/newVesselImo:\s*row\.applyVesselImo/);
    expect(source).toMatch(/newVesselCallSign:\s*row\.applyVesselCallSign/);
    expect(source).toMatch(/prop="applyVesselImo"\s+label="IMO"/);
  });

  it('船型字典收敛到唯一常量，且审核建船可选船型', () => {
    const dialog = readAdmin(DIALOG);
    const archive = readAdmin('src/views/vessel/vessel/index.vue');
    const constants = readAdmin('src/views/vessel/constants.ts');

    // 字典只有一份：`1集装箱 2散货 3油轮 4杂货 5其他`
    expect(constants).toContain('VESSEL_TYPE_OPTIONS');
    for (const label of ['集装箱', '散货', '油轮', '杂货', '其他']) {
      expect(constants).toContain(label);
    }
    expect(dialog).toContain('VESSEL_TYPE_OPTIONS');
    expect(archive).toContain('VESSEL_TYPE_OPTIONS');
    // 两个页面都不许再各自写一份字面量数组
    expect(dialog).not.toMatch(/label:\s*'集装箱'/);
    expect(archive).not.toMatch(/label:\s*'集装箱'/);

    // 审核弹窗此前 newVesselType 恒为 '5'（无控件），新建的船一律归「其他」
    expect(dialog).toMatch(/v-model="approveForm\.newVesselType"/);
  });

  it('审核结果通知申请人，且延后到事务提交后发送', () => {
    const service = readBackend(BIND_SERVICE);

    // 此前审核通过/驳回都不通知，申请人只能反复打开小程序看状态
    expect(service).toContain('notifyApplyResult');
    expect(service).toContain('MessageIdentityType.MALL_USER');
    // 站内信跳转必须是 JSON 对象（裸串会被消费端校验拒绝）
    expect(service).toContain('MessageJumpConstants.ROUTE');
    // 事务内直接发会在回滚后留下「审核通过」的误报
    expect(service).toMatch(/TransactionalMqUtils\.sendAfterCommit/);
    // 通知失败不能影响审核结论：方法体整体包在 try/catch 里（catch 只告警）
    const notifySection = service.slice(
      service.indexOf('private void notifyApplyResult'),
    );
    expect(notifySection).toMatch(/^\s*(?:\{\s*)?try\s*\{/m);
    expect(notifySection).toMatch(/catch \(Exception ex\)/);
  });

  it('解绑走统一入口并作废邀请码，与 C 端退出同口径', () => {
    const service = readBackend(BIND_SERVICE);

    expect(service).toContain('adminUnbindMember');
    // 发起人解绑后该船将没有管理角色
    expect(service).toMatch(/adminUnbindMember[\s\S]*?ROLE_OWNER/);
    // 解绑后其有效邀请码必须作废，避免继续拉人进船
    expect(service).toMatch(
      /adminUnbindMember[\s\S]*?vesselInviteCodeMapper\.delete/,
    );
  });

  it('成员关系落库收敛到统一入口，避免唯一键复活坑散落各处', () => {
    const service = readBackend(BIND_SERVICE);
    const adminService = readBackend(VESSEL_SERVICE);

    // 唯一键 (tenant, vessel, user) 不含 del_flag，而移除是逻辑删除：
    // 被移出过的人重新加入时直接 insert 必然撞键 1062
    const registrar = readBackend(
      'aryn-vessel/aryn-vessel-biz/src/main/java/com/aryn/cloud/vessel/support/VesselMemberRegistrar.java',
    );
    expect(registrar).toContain('reviveRemovedMember');
    expect(registrar).toMatch(/REVIVED/);

    // 四个入口（审核通过/邀请码/现场拉人/管理端绑定）都必须走它
    expect(service).not.toMatch(/vesselMemberMapper\.insert/);
    expect(adminService).not.toMatch(/vesselMemberMapper\.insert/);
    expect(service).toContain('ensureOnboard');
    expect(adminService).toContain('ensureOnboard');
  });

  // =========================================================================
  // 跨船一致性（2026-10-19 误绑事故）
  //
  // 事故现场：「悦航8号」的申请在审核弹窗里被选中「悦航7号」后直接通过，
  // 零比对、零提示、零留痕。下面的断言钉死修复后的三道闸：
  //   1. 弹窗内识别不一致并把两条船名摆出来 + 要求显式勾选；
  //   2. 提交前拦下（未确认 / 未填理由各有独立提示）；
  //   3. 列表里把「申请名 → 实际绑定名」渲染出来，事后可见。
  // =========================================================================

  it('审核弹窗识别跨船不一致并要求显式确认 + 必填理由', () => {
    const source = readAdmin(DIALOG);

    // 确认位要进表单状态，并且提交时随 payload 送到服务端
    expect(source).toContain('confirmVesselMismatch');
    expect(source).toMatch(/confirmVesselMismatch:\s*false/);
    expect(source).toMatch(
      /payload\.confirmVesselMismatch\s*=\s*approveForm\.confirmVesselMismatch/,
    );

    // 判定走与后端同源的纯函数，不许在弹窗里手搓一套归一化
    expect(source).toContain('isSameVesselIdentity');
    expect(source).toContain('vesselMismatch');

    // 不一致时给出提示（标题里必须同时出现「申请船名」与「所选船舶」）
    expect(source).toMatch(
      /申请船名「[\s\S]{0,80}」与所选船舶「[\s\S]{0,80}」不一致/,
    );
    // 且必须有确认勾选框，文案点明「申请船名与所选船舶不一致」
    expect(source).toMatch(/确认绑定到该船（申请船名与所选船舶不一致）/);

    // 提交前两道拦截：未确认先拦，理由为空再拦（顺序不能反：
    // 没勾确认时提「必须填理由」会让运营以为理由才是唯一阻碍）
    const submitAt = source.indexOf('const submitApprove');
    expect(submitAt).toBeGreaterThan(-1);
    const submitSection = source.slice(submitAt, submitAt + 3000);
    expect(submitSection).toMatch(
      /confirmVesselMismatch[\s\S]{0,300}ElMessage\.warning/,
    );
    expect(submitSection).toMatch(
      /auditRemark[\s\S]{0,200}必须填写审核意见说明原因/,
    );
  });

  it('换船或换审核方式必须作废旧的一致确认，否则旧勾选能绕过拦截', () => {
    const source = readAdmin(DIALOG);

    // 运营在 A 船勾了确认、又改选 B 船时，勾选若跟着带过去，
    // 等于「确认过的不是这条船」——拦截被完全绕过
    expect(source).toMatch(
      /watch\(\s*\(\)\s*=>\s*approveForm\.matchedVesselId[\s\S]{0,400}confirmVesselMismatch = false/,
    );
    expect(source).toMatch(
      /watch\(approveMode[\s\S]{0,300}confirmVesselMismatch = false/,
    );
  });

  it('列表「绑定船舶」列渲染「申请名 → 实际绑定名」与不一致标记', () => {
    const source = readAdmin(DIALOG);

    // vesselMismatch 是服务端算好的（列表接口下发），前端只负责渲染，
    // 不再二次判定——否则两边归一化口径漂移会出现「列表说一致、服务端说不一致」
    expect(source).toMatch(/scope\.row\.vesselMismatch/);
    expect(source).toMatch(/船名不一致/);
    // 两个船名都要出现，且申请名在前、绑定名在后（箭头语义）
    const columnAt = source.indexOf('label="绑定船舶"');
    expect(columnAt).toBeGreaterThan(-1);
    const columnSection = source.slice(columnAt, columnAt + 1400);
    expect(columnSection).toMatch(/scope\.row\.applyVesselName/);
    expect(columnSection).toMatch(/scope\.row\.matchedVesselName/);
    expect(columnSection.indexOf('applyVesselName')).toBeLessThan(
      columnSection.indexOf('matchedVesselName'),
    );
  });

  it('不一致提示与来源标签只用真实存在的 Tailwind 类', () => {
    const dialog = readAdmin(DIALOG);
    const archive = readAdmin('src/views/vessel/vessel/index.vue');

    // 幽灵类名在编译产物里没有对应规则，写了等于没写（仓库里另有历史遗留，
    // 见本文件既有用例；新增代码一律用 mb-3/text-xs 这类合法类）
    const PHANTOM = [
      'mb10',
      'mb4',
      'text-12px',
      'text-13px',
      'gap-8px',
      'px-8px',
      'py-4px',
      'rounded-4px',
      'ml4',
      'ml6',
    ];
    for (const phantom of PHANTOM) {
      expect(dialog).not.toMatch(new RegExp(`class="[^"]*\\b${phantom}\\b`));
    }

    // 船舶档案页另有历史遗留的 mb10（改它属于无关格式化，本次不动）。
    // 这里只校验**本次新增的两块 UI**：成员表格的来源列与解绑入口。
    const sourceColumnAt = archive.indexOf('label="来源" min-width="170"');
    expect(sourceColumnAt).toBeGreaterThan(-1);
    const sourceColumn = archive.slice(sourceColumnAt, sourceColumnAt + 1500);
    for (const phantom of PHANTOM) {
      expect(sourceColumn).not.toMatch(
        new RegExp(`class="[^"]*\\b${phantom}\\b`),
      );
    }
    // 新加的类必须是真实存在的 Tailwind 工具类
    expect(sourceColumn).toMatch(/class="mt-1 text-xs text-gray-500"/);
    const unbindAt = archive.indexOf('@click="unbindMember(scope.row)"');
    expect(unbindAt).toBeGreaterThan(-1);
    expect(archive.slice(Math.max(0, unbindAt - 600), unbindAt)).toMatch(
      /v-access:code="'vessel:member:save'"/,
    );
  });
});
