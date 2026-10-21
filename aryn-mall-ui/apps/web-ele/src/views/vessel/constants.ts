/**
 * 船舶领域共享常量。
 *
 * 船型字典此前只写在 `vessel/index.vue` 里，审核弹窗「新建船舶后绑定」分支
 * 则把 `newVesselType` 写死成 `'5'`（其他）——审核员没有任何选择入口，
 * 新建出来的船永远归到「其他」，报表里船型维度就失真了。
 *
 * 唯一口径以实体 `VesselInfo.vesselType` 的注释为准：
 * `1集装箱 2散货 3油轮 4杂货 5其他`。新增入口一律引用这里，不要各写一份。
 */

/** 船型缺省值：其他。历史数据可能为空，展示与提交都收敛到它 */
export const VESSEL_TYPE_DEFAULT = '5';

export interface VesselTypeOption {
  label: string;
  value: string;
}

/** 船型下拉选项（直接给 ElSelect/ElOption 用，option 顺序即业务习惯顺序） */
export const VESSEL_TYPE_OPTIONS: VesselTypeOption[] = [
  { label: '集装箱', value: '1' },
  { label: '散货', value: '2' },
  { label: '油轮', value: '3' },
  { label: '杂货', value: '4' },
  { label: '其他', value: VESSEL_TYPE_DEFAULT },
];

/**
 * 船型编码转展示文案。
 * 空值/未知编码统一回落「其他」——比表格里裸露一个 `9` 或空字符串更可读。
 */
export function vesselTypeLabel(value?: null | string): string {
  const matched = VESSEL_TYPE_OPTIONS.find((item) => item.value === value);
  return matched?.label ?? '其他';
}

// ---------------------------------------------------------------------------
// 船舶身份一致性（跨船绑定拦截）
// ---------------------------------------------------------------------------

/** 全角字符区间起点 U+FF01 / 终点 U+FF5E / 与半角的码位差 0xFEE0 */
const FULLWIDTH_FIRST = 65_281;
const FULLWIDTH_LAST = 65_374;
const FULLWIDTH_OFFSET = 65_248;

/** U+0020（空格）：Java `String#trim` 的裁剪上界 */
const ASCII_SPACE = 32;

/**
 * Java `Character.isWhitespace` 的等价判定。
 *
 * 不能直接用 JS 的 `\s`：`\s` 会把 `\u00A0`（不换行空格）、`\u2007`、`\u202F`
 * 都算作空白，而 Java 明确**不算**（`isWhitespace('\u00A0') == false`）。
 * 归一化口径一旦与后端不同，就会出现「前端说一致、后端说一致不了」的错位，
 * 所以这里按 Java 的定义逐类列举：控制字符 + 空格/行/段分隔符，
 * 剔除 `\u00A0` `\u2007` `\u202F`。
 */
function isJavaWhitespace(char: string): boolean {
  const code = char.codePointAt(0) ?? 0;
  // 0x09..0x0D：制表 / 换行 / 垂直制表 / 换页 / 回车
  if (code >= 9 && code <= 13) {
    return true;
  }
  // 0x1C..0x20：文件 / 组 / 记录 / 单元分隔符与空格
  if (code >= 28 && code <= ASCII_SPACE) {
    return true;
  }
  // 0x2000..0x200A 里的空格变体，剔除 0x2007（FIG SPACE，Java 不算空白）
  if (code >= 8192 && code <= 8202) {
    return code !== 8199;
  }
  // 0x1680 / 0x2028 / 0x2029 / 0x205F / 0x3000：中英文空格与行、段分隔符
  return (
    code === 5760 ||
    code === 8232 ||
    code === 8233 ||
    code === 8287 ||
    code === 12_288
  );
}

/** 有效文本：非 null 且含非空白字符（对齐 Java `StringUtils.hasText`） */
function hasText(value?: null | string): value is string {
  if (typeof value !== 'string' || value === '') {
    return false;
  }
  for (const ch of value) {
    if (!isJavaWhitespace(ch)) {
      return true;
    }
  }
  return false;
}

/** 对齐 Java `String#trim`：只裁剪码位 <= U+0020（JS 的 trim 会多裁掉 U+00A0 等） */
function javaTrim(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && (value.codePointAt(start) ?? 0) <= ASCII_SPACE) {
    start += 1;
  }
  while (end > start && (value.codePointAt(end - 1) ?? 0) <= ASCII_SPACE) {
    end -= 1;
  }
  return value.slice(start, end);
}

/** 对齐 Java `trimToNull` 的「去空白后为空即视为未填」语义，统一返回空串 */
function trimOrEmpty(value?: null | string): string {
  return typeof value === 'string' ? javaTrim(value) : '';
}

/**
 * Unicode 16.0 新增的大小写字符：**JDK 17/21 的 Unicode 表里没有它们**，
 * `Character.toLowerCase` 原样返回；而现代浏览器的 ICU 已按 Unicode 16 映射。
 *
 * 已用「遍历 BMP 全部码位比对 Java 与 TS 归一化结果」验证：不加这张表时
 * 63487 个码位里有 5 个不一致，且恰好就是这 5 个（U+1C89、U+A7CB、U+A7CC、
 * U+A7DA、U+A7DC）。它们不会出现在船名里，但归一化口径必须严格一致，
 * 所以这里钉死后端运行时（Java 17）的行为。
 */
const UNICODE16_UNMAPPED = new Set([7305, 42_955, 42_956, 42_970, 42_972]);

/**
 * 单字符小写化，对齐 Java `Character.toLowerCase(char)`。
 *
 * 不能用 `str.toLowerCase()` 的**完整映射**：JS 会把 U+0130「İ」扩成
 * `i` + U+0307 两个码位，而 Java 的 `char` 版本是**一对一简单映射**
 * （`Character.toLowerCase('İ') == 'i'`，长度不变）。两侧归一化长度不同，
 * 判据就会错位。
 *
 * 实现方式：取 JS 完整映射的**首字符**——Simple Case Mapping 规定扩展映射的
 * 首字符就是简单映射的结果（U+0130 → 'i'），这与 Java 行为逐字符一致。
 * 仍用 `codePointAt` 走码位，避免代理对（emoji 等）被拆开。
 */
function javaToLowerCase(char: string): string {
  const code = char.codePointAt(0) ?? 0;
  // ASCII 大写快速路径（覆盖绝大多数船名）
  if (code >= 65 && code <= 90) {
    return String.fromCodePoint(code + 32);
  }
  // JDK 17 的 Unicode 表（13/15）不认这些新字符，原样返回
  if (UNICODE16_UNMAPPED.has(code)) {
    return char;
  }
  const lower = char.toLowerCase();
  // 取首「码位」：扩展映射（1→N）时 Java 保留的是简单映射那一个字符
  const first = lower.codePointAt(0) ?? code;
  return String.fromCodePoint(first);
}

/**
 * 船名归一化：**必须与后端 `VesselBindServiceImpl#normalizeVesselName` 完全一致**。
 *
 * 顺序：全角转半角 → 丢弃空白与 `-` `_` `.` → 转小写 → 去掉尾部「号」。
 *
 * 为什么前端也要做一份：审核弹窗要在提交前提示「申请船名与所选船舶不一致」，
 * 判据必须和后端同一口径。任何一方单独演进（例如只在前端加「去括号」），
 * 都会让运营看到「提示一致」却被服务端拒绝，或者反过来——提示不一致、
 * 服务端却放行，拦截形同虚设。`vessel-mismatch.test.ts` 里有与 Java 源码的
 * 对齐断言，改这里必须同时改后端。
 */
export function normalizeVesselName(name?: null | string): string {
  if (!hasText(name)) {
    return '';
  }
  let normalized = '';
  for (const ch of javaTrim(name)) {
    const code = ch.codePointAt(0) ?? 0;
    // 全角字符（U+FF01..U+FF5E，含全角括号/冒号等）统一转半角
    const halfWidth =
      code >= FULLWIDTH_FIRST && code <= FULLWIDTH_LAST
        ? String.fromCodePoint(code - FULLWIDTH_OFFSET)
        : ch;
    if (
      isJavaWhitespace(halfWidth) ||
      halfWidth === '-' ||
      halfWidth === '_' ||
      halfWidth === '.'
    ) {
      continue;
    }
    normalized += javaToLowerCase(halfWidth);
  }
  // 「悦航6」「悦航6号」视为同一写法：与后端一致，循环去掉尾部所有「号」
  while (normalized.endsWith('号')) {
    normalized = normalized.slice(0, -1);
  }
  return normalized;
}

/** 申请单里用于认船的字段（与申请 VO 同名） */
export interface VesselApplyIdentity {
  applyVesselCallSign?: null | string;
  applyVesselImo?: null | string;
  applyVesselName?: null | string;
}

/** 候选/已选船舶里用于认船的字段（与船舶实体/候选 VO 同名） */
export interface VesselTargetIdentity {
  callSign?: null | string;
  imoCode?: null | string;
  vesselName?: null | string;
}

/**
 * 申请船与目标船是否「可以认为是同一条船」。
 *
 * 判据是客观身份键，不是船名——船改名、换旗、租船都是合法场景：
 *   1. IMO 均非空且相等（船的终身唯一标识，最强键）；
 *   2. 呼号均非空且相等（换旗/租船会变，但仍强于船名）；
 *   3. 船名归一化后相等（写法差异：空格、全角、大小写、尾部「号」）。
 *
 * 与后端 `VesselBindServiceImpl#isApplyIdentityMatched` 逐条对齐：
 * 强键先 `trimToNull` 再比（后端如此），且**两侧都必须非空**——两个空串
 * 相等不能算「确认是同一条船」。
 *
 * 任一条件不满足即视为**不是同一条船**（保守）：目标船信息缺失、两侧船名都为空、
 * 字段都是脏值时都不敢放行。宁可多让运营勾一次确认，也不能把「悦航8号」
 * 静默绑到「悦航7号」——一条误绑等于把另一条船的采购、配送与人员信息交出去。
 */
export function isSameVesselIdentity(
  apply?: null | VesselApplyIdentity,
  target?: null | VesselTargetIdentity,
): boolean {
  if (!apply || !target) {
    return false;
  }
  const applyImo = trimOrEmpty(apply.applyVesselImo);
  const targetImo = trimOrEmpty(target.imoCode);
  if (applyImo !== '' && applyImo === targetImo) {
    return true;
  }
  const applyCallSign = trimOrEmpty(apply.applyVesselCallSign);
  const targetCallSign = trimOrEmpty(target.callSign);
  if (applyCallSign !== '' && applyCallSign === targetCallSign) {
    return true;
  }
  const applyName = normalizeVesselName(apply.applyVesselName);
  const targetName = normalizeVesselName(target.vesselName);
  // 两侧都为空时不能算「同名」：那只是什么都没填，不构成同一条船的证据
  return applyName !== '' && applyName === targetName;
}

/**
 * 成员绑定来源中文名。
 *
 * 优先用服务端下发的 `bindSourceLabel`；缺失时按码值本地回落——管理端成员列表
 * 走的是实体接口（只有 `bindSource`，没有 label），不回退会渲染成空白标签。
 * 兜底文案与后端 `VesselMemberBindSource#label` 一致：未知值 →「手工/历史」。
 */
export function vesselMemberBindSourceLabel(
  source?: null | string,
  providedLabel?: null | string,
): string {
  if (hasText(providedLabel)) {
    return providedLabel as string;
  }
  const matched = VESSEL_MEMBER_BIND_SOURCE_OPTIONS.find(
    (item) => item.value === source,
  );
  return matched?.label ?? '手工/历史';
}

export interface VesselMemberBindSourceOption {
  label: string;
  value: string;
}

/** 绑定来源下拉/图例（顺序与后端 `VesselMemberBindSource#OPTIONS` 一致） */
export const VESSEL_MEMBER_BIND_SOURCE_OPTIONS: VesselMemberBindSourceOption[] =
  [
    { label: '申请审核', value: 'APPLY_AUDIT' },
    { label: '申请审核（船名不一致）', value: 'APPLY_AUDIT_MISMATCH' },
    { label: '邀请码', value: 'INVITE_CODE' },
    { label: '船内成员添加', value: 'MEMBER_ADD' },
    { label: '管理端绑定', value: 'ADMIN_ADD' },
    { label: '分享购物车自动加入', value: 'SHARE_CART' },
    { label: '手工/历史', value: 'MANUAL' },
  ];

/** 来源标签配色：低信任来源必须一眼认出来 */
export function vesselMemberBindSourceTagType(
  source?: null | string,
): 'danger' | 'info' | 'warning' {
  if (source === 'SHARE_CART') {
    // 任何拿到分享链接的人都会被自动补建，信任级别最低
    return 'warning';
  }
  if (source === 'APPLY_AUDIT_MISMATCH') {
    // 运营签字背书的跨船绑定，事后最需要复核
    return 'danger';
  }
  return 'info';
}
