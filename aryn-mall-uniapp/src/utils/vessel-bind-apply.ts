/**
 * 船舶绑定申请：提交失败原因判定与文案。
 *
 * 后端规则（vessel 域 `VesselBindServiceImpl.submitApply`）：**同一用户仅允许
 * 一条待审核申请**，重复提交会抛业务异常，前端收到 HTTP 200 + `code=1` +
 * 「您有一条待审核的申请，请等待运营处理」。这是刻意保留的防重，不是 bug；
 * 但用户从弹层里点「提交申请」时只会看到一个错误 toast，既不知道是哪条申请
 * 卡着，也不知道下一步该做什么（2026-10-18 反馈「点提交没反应」）。
 *
 * 因此把判定与文案收敛成纯函数：页面负责展示，测试负责钉住措辞里的可执行指引。
 */

/** 与后端 `VesselBindApply.STATUS_PENDING` 对齐 */
export const PENDING_APPLY_STATUS = '1'

export interface VesselBindApplyLike {
  applyVesselName?: string
  status?: string
}

/**
 * 「申请船名 vs 实际绑定船名」判定所需的字段。
 *
 * 与提交失败文案无关，单独声明：`resolveApplyErrorMessage` 的入参只关心船名与状态，
 * 而这里要能读到审核通过后的匹配结果。
 */
export interface VesselBindMismatchLike {
  applyVesselName?: string
  matchedVesselName?: string
  vesselMismatch?: boolean
}

/** Java `Character.isWhitespace` 判为非空白、而 JS `\s` 会误判的字符 */
const NON_BREAKING_SPACES = new Set(['\u00A0', '\u2007', '\u202F', '\uFEFF'])

function isVesselNameWhitespace(char: string): boolean {
  return !NON_BREAKING_SPACES.has(char) && /\s/.test(char)
}

/**
 * 船名归一化：**必须与后端 `VesselBindServiceImpl#normalizeVesselName` 逐条一致**。
 *
 * 规则：全角字符与全角空格转半角 → 去掉空白、`-`、`_`、`.` → 转小写 →
 * 去掉尾部「号」（「悦航6」与「悦航6号」视为同一写法）。
 *
 * 之所以要在前端复刻：服务端下发的 `vesselMismatch` 是权威判定，但它只在
 * `matchedVesselName` 有值时才计算；旧数据/降级响应里可能没有该字段，
 * 前端据此兜底比对，避免「明明绑到了另一条船却显示一致」。
 */
export function normalizeVesselName(name?: null | string): string {
  if (!name) {
    return ''
  }
  let normalized = ''
  // 不用 `name.trim()`：JS 的 trim 会吃掉 NBSP/BOM，而后端 `String.trim()` 只去 ≤ U+0020，
  // 且后续循环本来就按 Java 的 isWhitespace 语义丢弃空白字符。
  for (const char of name) {
    const code = char.charCodeAt(0)
    // 全角字符（U+FF01–U+FF5E）与半角字符差 0xFEE0；全角空格 U+3000 交给空白判定
    const halfWidth = code >= 0xFF01 && code <= 0xFF5E
      ? String.fromCharCode(code - 0xFEE0)
      : char
    if (isVesselNameWhitespace(halfWidth) || halfWidth === '-' || halfWidth === '_' || halfWidth === '.') {
      continue
    }
    normalized += halfWidth.toLowerCase()
  }
  // 去掉尾部的「号」：与后端循环剥离保持一致（极端输入「悦航6号号」也能收敛）
  while (normalized.endsWith('号')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized
}

/**
 * 「申请船名 ≠ 实际绑定船名」的提示文案。
 *
 * 真实场景（2026-10-19）：用户申请「悦航8号」，运营在审核弹窗里把船选成了
 * 「悦航7号」。申请单冻结为「已通过」，卡片只显示申请船名，用户完全看不出
 * 自己实际进了哪条船 —— 而船舶成员身份决定了他能看谁的成员、靠港计划与配送单。
 *
 * 判定：服务端 `vesselMismatch` 优先；未下发时用与后端相同的归一化规则兜底。
 * 缺少 `matchedVesselName` 时返回空串 —— 说「实际绑定到了某处」却拿不出船名
 * 只会让用户更困惑，此时由卡片单独展示申请状态即可。
 */
export function mismatchTip(apply?: null | VesselBindMismatchLike): string {
  const appliedVesselName = apply?.applyVesselName?.trim() ?? ''
  const matchedVesselName = apply?.matchedVesselName?.trim() ?? ''
  if (!matchedVesselName) {
    return ''
  }
  const mismatch = apply?.vesselMismatch === true
    || (!!appliedVesselName
      && normalizeVesselName(appliedVesselName) !== normalizeVesselName(matchedVesselName))
  if (!mismatch) {
    return ''
  }
  // 申请船名缺失时不要拼出「你申请的「」」：只说清实际绑定到了哪条船
  if (!appliedVesselName) {
    return `实际绑定到了「${matchedVesselName}」，如有疑问请联系运营`
  }
  return `你申请的「${appliedVesselName}」实际绑定到了「${matchedVesselName}」，如有疑问请联系运营`
}

/** 从「我的申请」列表里挑出待审核的那条（没有则 null） */
export function findPendingApply<T extends VesselBindApplyLike>(
  applies?: readonly T[] | null,
): T | null {
  if (!applies || applies.length === 0) {
    return null
  }
  return applies.find(apply => apply?.status === PENDING_APPLY_STATUS) ?? null
}

/**
 * 后端返回的错误是否为「已有待审核申请」防重拦截。
 *
 * 只认文案特征而不是错误码：这里所有业务错误都是 `code=1`，用 code 无法区分
 * 「船名为空」与「已有待审核申请」。
 *
 * 判定要**同时**包含「待审核」与「等待运营」：只要求「待审核 + 申请」会误把
 * 撤回失败的「只有待审核的申请可以撤回」当成防重拦截，替换成一句让用户去撤回
 * 的提示 —— 而他刚刚正是在撤回。
 */
export function isPendingApplyBlocked(message?: null | string): boolean {
  if (!message) {
    return false
  }
  return message.includes('待审核') && message.includes('等待运营')
}

/** 待审核拦截的完整提示：说清是哪条船，并指出唯一出路是撤回后重填 */
export function pendingApplyTip(apply?: null | VesselBindApplyLike): string {
  const vessel = apply?.applyVesselName?.trim()
  const which = vessel ? `「${vessel}」的申请` : '一条绑定申请'
  return `你已提交${which}，正在等待运营审核（同一账号同时只能有一条待审核申请）；如需修改船名或重新提交，请先在下方「我的申请」里撤回。`
}

/** 把任意错误翻译成弹层内可展示的一句话 */
export function resolveApplyErrorMessage(
  error: unknown,
  pendingApply?: null | VesselBindApplyLike,
): string {
  const raw = extractMessage(error)
  if (isPendingApplyBlocked(raw)) {
    return pendingApplyTip(pendingApply)
  }
  if (raw) {
    return raw
  }
  return '提交失败，请检查网络后稍后重试'
}

function extractMessage(error: unknown): string {
  if (!error) {
    return ''
  }
  if (typeof error === 'string') {
    return error.trim()
  }
  if (typeof error === 'object') {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string') {
      return message.trim()
    }
    const errMsg = (error as { errMsg?: unknown }).errMsg
    if (typeof errMsg === 'string') {
      return errMsg.trim()
    }
  }
  return ''
}
