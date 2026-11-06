/**
 * 出清时间阶梯折扣纯逻辑。
 *
 * 业务语义（与后端 `ClearanceRuleService#resolveTierRate` 严格一致）：
 * - 每条规则的 `hoursBeforeExpire` 表示「距到期剩余小时数 ≤ N 时命中」；
 * - `discountRate` 是在基础出清价上再打的折扣率（(0, 1) 小数，如 0.8 表示 8 折）；
 * - 同一时刻可能命中多条规则：只应用最深折扣（discountRate 最小）的一档，不连乘；
 * - `enabled === '1'` 才算启用，`sortOrder` 仅影响列表展示排序、不参与算价；
 * - 因此某些档位可能永远不会改变最终出清价（被更长时间档的等/更深折扣覆盖），
 *   `isRuleEffective` / `explainIneffective` 用于识别这类冗余档位。
 *
 * 这里刻意不依赖 Vue 与 Element Plus，便于 vitest 直接覆盖；组件层只做状态与渲染。
 */

/** 出清折扣规则（后端 `ClearanceRule` 的行数据形状） */
export interface ClearanceRuleRow {
  id: string;
  ruleName: string;
  hoursBeforeExpire: number;
  discountRate: number | string;
  enabled: string;
  sortOrder?: null | number;
  remark?: null | string;
  createTime?: null | string;
  updateTime?: null | string;
}

/** 规则编辑弹窗的草稿模型（数字框可能为 null，enabled 为布尔） */
export interface ClearanceRuleDraft {
  ruleName: string;
  hoursBeforeExpire: null | number;
  discountRate: null | number; // 0-1 小数
  enabled: boolean;
  sortOrder: number;
  remark: string;
}

/** 新增/修改规则提交给后端的 payload（enabled 为 '1' / '0' 字符串） */
export interface ClearanceRulePayload {
  ruleName: string;
  hoursBeforeExpire: number;
  discountRate: number;
  enabled: '0' | '1';
  sortOrder: number;
  remark: string;
}

/** 展示用时间阶梯的一段；`fromHours`/`toHours` 均为「距到期剩余小时数」 */
export interface LadderBand {
  fromHours: null | number; // 开区间下界；null 表示从 0 起（最临近到期的档）
  toHours: null | number; // 闭区间上界；null 表示无上限（原价段）
  rate: null | number; // 该区间实际生效折扣率；null 表示原价
  driverNames: string[]; // 决定该段折扣率的规则名（同率可能出现多条）
  hitCount: number; // 该区间命中的启用规则条数
}

/** 价格试算结果（remainingHours 时刻命中的最深一档） */
export interface PriceSimulation {
  hitCount: number;
  rate: null | number;
  basePrice: number;
  finalPrice: number;
  driverNames: string[];
  bandIndex: number; // 在 buildLadder 结果中的下标；无档位 -1
}

/** 规则列表汇总（看板/警示文案用） */
export interface RuleSummary {
  total: number;
  enabledCount: number;
  deepestRate: null | number;
  shortestHours: null | number;
  longestHours: null | number;
}

/** 折扣率比较精度：先四舍五入到 1e-9，避免 0.7/0.9 等二进制浮点误差误判 */
const RATE_PRECISION = 1e9;

/** 启用且字段合法的规则（后端同样跳过停用、小时数或折扣率为空的规则） */
interface ActiveRule {
  hours: number;
  rate: number;
  row: ClearanceRuleRow;
}

/** 数字/数字字符串 → 有限数字；null、undefined、NaN、Infinity 统一返回 null */
function toFiniteNumber(
  value: null | number | string | undefined,
): null | number {
  if (value === null || value === undefined) return null;
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

/** 折扣率归一化（保留 9 位小数），用于相等/大小比较与展示 */
function normalizeRate(rate: number): number {
  return Math.round(rate * RATE_PRECISION) / RATE_PRECISION;
}

/** 折扣率比较：1e-9 精度内相等返回 0，a 更小返回负数 */
function compareRates(a: number, b: number): number {
  return normalizeRate(a) - normalizeRate(b);
}

/** 数字展示：抹掉浮点尾差后转字符串（整数不带小数点） */
function formatNumber(value: number): string {
  return String(Math.round(value * 1000) / 1000);
}

/** 价格四舍五入到 2 位小数（分） */
function roundPrice(value: number): number {
  return Math.round(value * 100) / 100;
}

/** 提取「启用 + 时间档/折扣率合法」的规则，后续分档与算价只看这些规则 */
function collectActiveRules(rules: readonly ClearanceRuleRow[]): ActiveRule[] {
  const active: ActiveRule[] = [];
  for (const row of rules) {
    if (row.enabled !== '1') continue;
    const hours = toFiniteNumber(row.hoursBeforeExpire);
    const rate = toFiniteNumber(row.discountRate);
    if (hours === null || rate === null) continue;
    active.push({ hours, rate, row });
  }
  return active;
}

/** 一组规则中的最小折扣率（无有效规则返回 null；返回归一化后的值） */
function minRate(entries: readonly ActiveRule[]): null | number {
  let best: null | number = null;
  for (const entry of entries) {
    if (best === null || compareRates(entry.rate, best) < 0) {
      best = entry.rate;
    }
  }
  return best === null ? null : normalizeRate(best);
}

/**
 * 取得某最小折扣率的全部规则（同率并列）。
 * 排序：时间档从大到小，其次按规则名，保证同一输入输出稳定。
 */
function collectDrivers(
  entries: readonly ActiveRule[],
  rate: number,
): ActiveRule[] {
  return entries
    .filter((entry) => compareRates(entry.rate, rate) === 0)
    .sort((left, right) => {
      if (left.hours !== right.hours) return right.hours - left.hours;
      if (left.row.ruleName < right.row.ruleName) return -1;
      if (left.row.ruleName > right.row.ruleName) return 1;
      return 0;
    });
}

/** 定位剩余小时数落在 buildLadder 结果中的档位下标；找不到返回 -1 */
function locateBand(
  ladder: readonly LadderBand[],
  remainingHours: number,
): number {
  return ladder.findIndex((band) => {
    const afterLower =
      band.fromHours === null || remainingHours > band.fromHours;
    const atMostUpper = band.toHours === null || remainingHours <= band.toHours;
    return afterLower && atMostUpper;
  });
}

/**
 * 出清规则行 → 编辑弹窗草稿。
 * 边界：`sortOrder`/`remark` 为 null 时分别回填 0 / ''；
 * 折扣率字符串（如 `'0.8000'`）转 number；`enabled` 仅 `'1'` 视为启用。
 */
export function toRuleDraft(row: ClearanceRuleRow): ClearanceRuleDraft {
  return {
    discountRate: toFiniteNumber(row.discountRate),
    enabled: row.enabled === '1',
    hoursBeforeExpire: toFiniteNumber(row.hoursBeforeExpire),
    remark: row.remark ?? '',
    ruleName: row.ruleName,
    sortOrder: toFiniteNumber(row.sortOrder) ?? 0,
  };
}

/**
 * 编辑弹窗草稿 → 新增/修改 payload（字段原样提交，不做 trim）。
 * 边界：调用前应先经 `validateDraft` 校验；hoursBeforeExpire / discountRate
 * 为 null 时兜底为 0（后端会再校验并拒绝非法值），sortOrder 缺省 0。
 */
export function buildRulePayload(
  draft: ClearanceRuleDraft,
): ClearanceRulePayload {
  return {
    discountRate: draft.discountRate ?? 0,
    enabled: draft.enabled ? '1' : '0',
    hoursBeforeExpire: draft.hoursBeforeExpire ?? 0,
    remark: draft.remark,
    ruleName: draft.ruleName,
    sortOrder: draft.sortOrder ?? 0,
  };
}

/**
 * 规则行 + 目标启用状态 → 启停 payload。
 * 边界：其余字段原样回填（折扣率字符串转 number、sortOrder/remark 空值回填
 * 0 / ''），保证「只切开关」不会顺手改动规则配置。
 */
export function buildTogglePayload(
  row: ClearanceRuleRow,
  enabled: boolean,
): ClearanceRulePayload {
  return {
    discountRate: toFiniteNumber(row.discountRate) ?? 0,
    enabled: enabled ? '1' : '0',
    hoursBeforeExpire: toFiniteNumber(row.hoursBeforeExpire) ?? 0,
    remark: row.remark ?? '',
    ruleName: row.ruleName,
    sortOrder: toFiniteNumber(row.sortOrder) ?? 0,
  };
}

/**
 * 折扣率 → 几折（0.8 → 8，0.85 → 8.5）。
 * 边界：非法值（null/NaN/Infinity）返回 0；保留 3 位折扣率精度。
 */
export function rateToZhe(rate: number | string): number {
  const value = toFiniteNumber(rate);
  if (value === null) return 0;
  return Math.round(value * 1000) / 100;
}

/**
 * 几折 → 折扣率（8 → 0.8，8.5 → 0.85）。
 * 边界：非法值返回 0；保留 4 位小数精度，避免浮点尾差。
 */
export function zheToRate(zhe: number): number {
  const value = toFiniteNumber(zhe);
  if (value === null) return 0;
  return Math.round(value * 1000) / 10_000;
}

/**
 * 折扣率 → 「8.0 折」展示文案。
 * 边界：非法折扣率返回 `'--'`；固定保留 1 位小数，与列表口径一致。
 */
export function formatDiscount(rate: number | string): string {
  const value = toFiniteNumber(rate);
  if (value === null) return '--';
  return `${rateToZhe(value).toFixed(1)} 折`;
}

/**
 * 折扣率 → 「基础出清价 × 80%」提示文案。
 * 边界：非法折扣率返回「基础出清价（折扣率无效）」；百分比最多保留 2 位小数。
 */
export function formatRateHint(rate: number | string): string {
  const value = toFiniteNumber(rate);
  if (value === null) return '基础出清价（折扣率无效）';
  const percent = Math.round(value * 10_000) / 100;
  return `基础出清价 × ${formatNumber(percent)}%`;
}

/**
 * 距到期小时数 → 「≤ 24 小时」展示文案。
 * 边界：非法值返回 `'--'`。
 */
export function formatHours(hours: number): string {
  const value = toFiniteNumber(hours);
  if (value === null) return '--';
  return `≤ ${formatNumber(value)} 小时`;
}

/**
 * 阶梯区间 → 「剩余 > 48 小时」/「剩余 24–48 小时」/「剩余 ≤ 24 小时」。
 * 边界：`toHours === null` 视为原价段（从 `fromHours` 之上）；`fromHours === null`
 * 视为最临近到期的档（从 0 起，含 0）。
 */
export function formatBandRange(band: LadderBand): string {
  if (band.toHours === null) {
    return band.fromHours === null
      ? '剩余 > 0 小时'
      : `剩余 > ${formatNumber(band.fromHours)} 小时`;
  }
  if (band.fromHours === null) {
    return `剩余 ≤ ${formatNumber(band.toHours)} 小时`;
  }
  return `剩余 ${formatNumber(band.fromHours)}–${formatNumber(band.toHours)} 小时`;
}

/**
 * 阶梯区间折扣 → 「原价」/「9.0 折」。
 * 边界：`rate === null` 表示该段按原价，不参与折后价。
 */
export function formatBandDiscount(band: LadderBand): string {
  return band.rate === null ? '原价' : formatDiscount(band.rate);
}

/**
 * 阶梯区间完整描述 → 「剩余 24–48 小时 · 9.0 折」。
 * 边界：原价段输出「剩余 > 48 小时 · 原价」。
 */
export function formatBandDescription(band: LadderBand): string {
  return `${formatBandRange(band)} · ${formatBandDiscount(band)}`;
}

/**
 * 启用规则 → 时间阶梯（从远到近排列，原价段在最前）。
 *
 * 规则：
 * - 只考虑 `enabled === '1'` 且折扣率/小时数合法的规则，无可用规则返回 `[]`；
 * - 按去重后的小时数升序分档，对每个上界 `h`，区间 `(h_prev, h]`（第一档下界为 null）
 *   的实际折扣率 = 所有 `hoursBeforeExpire ≥ h` 的启用规则里的最小折扣率，
 *   命中条数 = 这些规则的条数；
 * - 最前面固定插入一段 `{ fromHours: 最大档, toHours: null, rate: null }`，
 *   表示「剩余 > 最大档」按原价。
 *
 * 边界：同一小时数重复配置时只产生一档；`rate` 为归一化后的最小折扣率；
 * `driverNames` 含所有取得最小折扣率的规则名（可能多条）。
 */
export function buildLadder(rules: ClearanceRuleRow[]): LadderBand[] {
  const active = collectActiveRules(rules);
  if (active.length === 0) return [];

  const thresholds = [...new Set(active.map((entry) => entry.hours))].sort(
    (left, right) => left - right,
  );
  const maxHours = thresholds[thresholds.length - 1] ?? 0;

  // 先按小时数升序构造真实区间（临近 → 远），再整体反转为「从远到近」
  const ascendingBands: LadderBand[] = [];
  let lower: null | number = null;
  for (const upper of thresholds) {
    const entries = active.filter((entry) => entry.hours >= upper);
    const rate = minRate(entries);
    ascendingBands.push({
      driverNames:
        rate === null
          ? []
          : collectDrivers(entries, rate).map((entry) => entry.row.ruleName),
      fromHours: lower,
      hitCount: entries.length,
      rate,
      toHours: upper,
    });
    lower = upper;
  }
  ascendingBands.reverse();

  // 原价段固定在数组首位：剩余时间超过最大时间档时命中不到任何规则
  return [
    {
      driverNames: [],
      fromHours: maxHours,
      hitCount: 0,
      rate: null,
      toHours: null,
    },
    ...ascendingBands,
  ];
}

/**
 * 合并「相邻且折扣率相同」的阶梯段（仅用于展示，不影响算价口径）。
 *
 * 通常配合 `buildLadder` 使用，把「24 小时档不改变价格」这类冗余档位折叠起来：
 * `>48 原价 / 24–48 8.0 折 / 6–24 8.0 折 / ≤6 7.0 折` → `>48 原价 / 6–48 8.0 折 / ≤6 7.0 折`。
 *
 * 规则：
 * - 输入按 `buildLadder` 的输出顺序（从远到近，原价段在最前）；
 * - 仅当前后两段 `rate` 均非 null 且相等（1e-9 精度内）时合并，原价段（`rate === null`）
 *   永远保持独立；
 * - 合并后的区间取并集：`fromHours` 取较近一段的下界，`toHours` 保留较远一段的上界；
 * - `hitCount` 取被合并各段的最大值（命中数随区间靠近到期单调不减，即合并区间内上限、
 *   也等于最近子区间的命中条数）；
 * - `driverNames` 取并集去重，保持「先出现者在前」的远→近顺序。
 *
 * 边界：空数组返回新数组 `[]`；单段原样（内容不变）返回新数组；
 * 始终返回新数组与新对象，不修改入参。
 */
export function mergeAdjacentBands(bands: LadderBand[]): LadderBand[] {
  const merged: LadderBand[] = [];
  for (const band of bands) {
    const previous = merged[merged.length - 1];
    const sameRate =
      previous !== undefined &&
      previous.rate !== null &&
      band.rate !== null &&
      compareRates(previous.rate, band.rate) === 0;
    if (previous !== undefined && sameRate) {
      // 远→近遍历：合并后下界前移到较近一段，上界沿用较远一段
      previous.fromHours = band.fromHours;
      previous.hitCount = Math.max(previous.hitCount, band.hitCount);
      for (const name of band.driverNames) {
        if (!previous.driverNames.includes(name)) {
          previous.driverNames.push(name);
        }
      }
      continue;
    }
    merged.push({
      driverNames: [...band.driverNames],
      fromHours: band.fromHours,
      hitCount: band.hitCount,
      rate: band.rate,
      toHours: band.toHours,
    });
  }
  return merged;
}

/**
 * 规则对最终出清价是否有效（与后端「取最深一档、不连乘」口径一致）。
 *
 * 边界：
 * - 停用规则（`enabled !== '1'`）、时间档或折扣率非法的规则一律 false；
 * - 存在 `hoursBeforeExpire` 严格更大（更长时间档）的启用规则，且其最小折扣率
 *   ≤ 本规则折扣率 → false（本档被更长时间档的等/更深折扣完全覆盖）；
 * - 同一小时数档位：只有达到该档最小折扣率的规则算有效（同率并列都算有效）。
 */
export function isRuleEffective(
  rules: ClearanceRuleRow[],
  rule: ClearanceRuleRow,
): boolean {
  return explainRule(rules, rule) === null;
}

/**
 * 解释规则为何不会改变最终出清价；有效规则返回 null。
 *
 * 边界/文案：
 * - 停用规则 → 「规则已停用」；
 * - 被更长时间档覆盖 → 「更长时间档已有 8.0 折，本档不会降低出清价」；
 * - 同时间档被更深折扣覆盖 → 「同时间档「规则名」已是 8.0 折」；
 * - 时间档或折扣率非法 → 「时间档或折扣率无效」。
 */
export function explainIneffective(
  rules: ClearanceRuleRow[],
  rule: ClearanceRuleRow,
): null | string {
  return explainRule(rules, rule);
}

/** isRuleEffective / explainIneffective 共用的判定逻辑 */
function explainRule(
  rules: ClearanceRuleRow[],
  rule: ClearanceRuleRow,
): null | string {
  if (rule.enabled !== '1') return '规则已停用';
  const hours = toFiniteNumber(rule.hoursBeforeExpire);
  const rate = toFiniteNumber(rule.discountRate);
  if (hours === null || rate === null) return '时间档或折扣率无效';

  const active = collectActiveRules(rules);

  // 1) 更长时间档已有等/更深折扣 → 本档永远被覆盖
  const longer = active.filter((entry) => entry.hours > hours);
  const longerMin = minRate(longer);
  if (longerMin !== null && compareRates(longerMin, rate) <= 0) {
    return `更长时间档已有 ${formatDiscount(longerMin)}，本档不会降低出清价`;
  }

  // 2) 同小时数档位中未达到该档最小折扣率 → 同档存在更深折扣
  const sameHours = active.filter((entry) => entry.hours === hours);
  const sameMin = minRate(sameHours);
  if (sameMin !== null && compareRates(sameMin, rate) < 0) {
    const driver = sameHours.find(
      (entry) =>
        entry.row.id !== rule.id && compareRates(entry.rate, sameMin) === 0,
    );
    return `同时间档「${driver?.row.ruleName ?? rule.ruleName}」已是 ${formatDiscount(
      sameMin,
    )}`;
  }

  return null;
}

/**
 * 查找同小时数档位的规则（编辑时用于提示重复档位）。
 *
 * 边界：`excludeId` 用于排除正在编辑的规则本身；停用规则也参与查找
 * （重复档位无论启停都值得提示）；小时数相等按数值比较，找不到返回 undefined。
 */
export function findSameHoursRule(
  rules: ClearanceRuleRow[],
  hours: number,
  excludeId?: string,
): ClearanceRuleRow | undefined {
  const target = toFiniteNumber(hours);
  if (target === null) return undefined;
  return rules.find((row) => {
    if (excludeId !== undefined && row.id === excludeId) return false;
    return toFiniteNumber(row.hoursBeforeExpire) === target;
  });
}

/**
 * 价格试算：剩余 `remainingHours` 小时时命中的最深一档折扣。
 *
 * 边界：
 * - 命中所有 `hoursBeforeExpire ≥ remainingHours` 的启用规则，取最小折扣率只应用一次
 *   （不连乘）；`finalPrice` 保留 2 位小数；
 * - 剩余时间超过最大时间档（或没有任何启用规则）→ 不命中：`rate=null`、
 *   `hitCount=0`、`finalPrice=basePrice`；`bandIndex` 为原价段下标 0（无阶梯为 -1）；
 * - `remainingHours` 为负（商品已过期）时不命中，与后端 `resolveTierRate` 一致；
 * - `basePrice` 非法时按 0 处理。
 */
export function simulatePrice(
  rules: ClearanceRuleRow[],
  basePrice: number,
  remainingHours: number,
): PriceSimulation {
  const price = toFiniteNumber(basePrice) ?? 0;
  const remaining = toFiniteNumber(remainingHours);
  const ladder = buildLadder(rules);
  const noHitBandIndex = ladder.length > 0 ? 0 : -1;

  if (remaining === null) {
    return {
      bandIndex: noHitBandIndex,
      basePrice: price,
      driverNames: [],
      finalPrice: price,
      hitCount: 0,
      rate: null,
    };
  }

  // 与后端 resolveTierRate 对齐：剩余小时数为负（已过期）时不命中任何档位
  if (remaining < 0) {
    return {
      bandIndex: noHitBandIndex,
      basePrice: price,
      driverNames: [],
      finalPrice: price,
      hitCount: 0,
      rate: null,
    };
  }

  const active = collectActiveRules(rules);
  const hits = active.filter((entry) => entry.hours >= remaining);
  const rate = minRate(hits);
  if (rate === null) {
    return {
      bandIndex: noHitBandIndex,
      basePrice: price,
      driverNames: [],
      finalPrice: price,
      hitCount: 0,
      rate: null,
    };
  }

  return {
    bandIndex: locateBand(ladder, remaining),
    basePrice: price,
    driverNames: collectDrivers(hits, rate).map((entry) => entry.row.ruleName),
    finalPrice: roundPrice(price * rate),
    hitCount: hits.length,
    rate,
  };
}

/**
 * 规则列表汇总（仅启用规则参与折扣率/时间极值统计）。
 *
 * 边界：`total` 为全部规则条数（含停用）；没有任何启用规则时
 * `deepestRate`/`shortestHours`/`longestHours` 均为 null。
 */
export function summarizeRules(rules: ClearanceRuleRow[]): RuleSummary {
  const active = collectActiveRules(rules);
  const hours = active.map((entry) => entry.hours);
  return {
    deepestRate: minRate(active),
    enabledCount: rules.filter((row) => row.enabled === '1').length,
    longestHours: hours.length > 0 ? Math.max(...hours) : null,
    shortestHours: hours.length > 0 ? Math.min(...hours) : null,
    total: rules.length,
  };
}

/**
 * 编辑草稿校验，返回首个中文错误文案；合法返回 null。
 *
 * 边界：规则名称 trim 后必须非空；`hoursBeforeExpire` 必须为正整数（>0）；
 * `discountRate` 必须在开区间 (0, 1) 内（不含 0 与 1）。
 */
export function validateDraft(draft: ClearanceRuleDraft): null | string {
  if (!draft.ruleName || draft.ruleName.trim() === '') {
    return '请输入规则名称';
  }
  const hours = toFiniteNumber(draft.hoursBeforeExpire);
  if (hours === null) return '请填写距到期小时数';
  if (hours <= 0) return '距到期小时数必须大于 0';
  if (!Number.isInteger(hours)) return '距到期小时数必须为正整数';
  const rate = toFiniteNumber(draft.discountRate);
  if (rate === null) return '请填写折扣力度';
  if (rate <= 0 || rate >= 1) return '折扣率必须在 (0, 1) 区间内';
  return null;
}
