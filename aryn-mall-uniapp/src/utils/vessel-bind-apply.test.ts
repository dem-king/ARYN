import { describe, expect, it } from 'vitest'

import {
  findPendingApply,
  isPendingApplyBlocked,
  mismatchTip,
  normalizeVesselName,
  pendingApplyTip,
  resolveApplyErrorMessage,
} from './vessel-bind-apply'

function apply(overrides: Record<string, any>) {
  return {
    id: '1',
    applyNo: 'VB1',
    applyRole: '2',
    applyVesselName: '悦航7号',
    status: '1',
    ...overrides,
  } as any
}

/**
 * 船舶绑定申请提交失败的原因判定。
 *
 * 背景：后端对「同一用户仅一条待审核申请」有防重拦截（vessel 域
 * `VesselBindServiceImpl.submitApply`），返回 HTTP 200 + `code=1` +
 * 文案「您有一条待审核的申请，请等待运营处理」。前端必须把这条文案
 * 变成弹层内的可见说明，并引导用户去撤回，而不是静默失败。
 */
describe('findPendingApply', () => {
  it('从申请列表里找出待审核那条', () => {
    const pending = apply({ id: 'p', status: '1' })
    const applies = [apply({ id: 'a', status: '2' }), pending, apply({ id: 'c', status: '3' })]

    expect(findPendingApply(applies)?.id).toBe('p')
  })

  it('没有待审核申请时返回 null', () => {
    expect(findPendingApply([apply({ status: '2' }), apply({ status: '4' })])).toBeNull()
    expect(findPendingApply([])).toBeNull()
    expect(findPendingApply(undefined)).toBeNull()
    expect(findPendingApply(null)).toBeNull()
  })
})

describe('isPendingApplyBlocked', () => {
  it('识别后端「待审核申请」防重拦截文案', () => {
    expect(isPendingApplyBlocked('您有一条待审核的申请，请等待运营处理')).toBe(true)
  })

  it('普通校验错误与空值不误判', () => {
    expect(isPendingApplyBlocked('船名不能为空')).toBe(false)
    expect(isPendingApplyBlocked('')).toBe(false)
    expect(isPendingApplyBlocked(undefined)).toBe(false)
    expect(isPendingApplyBlocked(null)).toBe(false)
  })

  it('撤回失败「只有待审核的申请可以撤回」不算防重拦截', () => {
    // 用户刚点撤回时收到这句，若误判成防重会回他一句「请先撤回」，自相矛盾
    expect(isPendingApplyBlocked('只有待审核的申请可以撤回')).toBe(false)
  })
})

describe('pendingApplyTip', () => {
  it('带上待审核申请的船名，并明确指出改船名要先撤回', () => {
    const tip = pendingApplyTip(apply({ applyVesselName: '悦航7号' }))

    expect(tip).toContain('悦航7号')
    expect(tip).toContain('撤回')
  })

  it('拿不到申请详情时仍给出可执行指引', () => {
    const tip = pendingApplyTip(null)

    expect(tip).toContain('待审核')
    expect(tip).toContain('撤回')
  })
})

describe('resolveApplyErrorMessage', () => {
  it('优先使用后端错误文案（ApiError.message）', () => {
    expect(resolveApplyErrorMessage(new Error('船名不能为空'))).toBe('船名不能为空')
  })

  it('命中待审核拦截时替换成带撤回指引的完整提示', () => {
    const error = new Error('您有一条待审核的申请，请等待运营处理')
    const message = resolveApplyErrorMessage(error, apply({ applyVesselName: '悦航7号' }))

    expect(message).toContain('悦航7号')
    expect(message).toContain('撤回')
    expect(message).not.toBe('您有一条待审核的申请，请等待运营处理')
  })

  it('网络失败等无文案错误给出可重试的兜底提示', () => {
    expect(resolveApplyErrorMessage({ name: 'NetworkError' })).toContain('稍后重试')
    expect(resolveApplyErrorMessage(undefined)).toContain('稍后重试')
  })

  it('不把对象直接拼进用户可见文案', () => {
    expect(resolveApplyErrorMessage({})).not.toContain('[object Object]')
  })
})

/**
 * 船名归一化必须与后端 `VesselBindServiceImpl#normalizeVesselName` 一致：
 * 全角转半角、去空白与 `-` `_` `.`、转小写、去尾部「号」。
 * 前后端规则一旦漂移，「一致」与「不一致」会给出相反的结论。
 */
describe('normalizeVesselName', () => {
  it('去掉尾部「号」：「悦航8」与「悦航8号」等价', () => {
    expect(normalizeVesselName('悦航8号')).toBe('悦航8')
    expect(normalizeVesselName('悦航8')).toBe('悦航8')
    expect(normalizeVesselName('悦航8号号')).toBe('悦航8')
  })

  it('忽略空白与 - _ . 分隔符', () => {
    expect(normalizeVesselName('  悦航 8 号 ')).toBe('悦航8')
    expect(normalizeVesselName('YUE HANG-8')).toBe('yuehang8')
    expect(normalizeVesselName('yue_hang.8')).toBe('yuehang8')
  })

  it('全角字符与全角空格转半角', () => {
    expect(normalizeVesselName('ＡＢＣ１２３')).toBe('abc123')
    expect(normalizeVesselName('悦航　8号')).toBe('悦航8')
    expect(normalizeVesselName('ｙｕｅ－ｈａｎｇ８')).toBe('yuehang8')
  })

  it('英文统一小写', () => {
    expect(normalizeVesselName('Ocean Star')).toBe('oceanstar')
    expect(normalizeVesselName('OCEANSTAR')).toBe('oceanstar')
  })

  it('空值与非字符串安全返回空串', () => {
    expect(normalizeVesselName('')).toBe('')
    expect(normalizeVesselName('   ')).toBe('')
    expect(normalizeVesselName(undefined)).toBe('')
    expect(normalizeVesselName(null)).toBe('')
  })
})

/**
 * 「我的申请」卡片只能显示申请船名时，用户看不出实际绑到了哪条船。
 * 真实事故（2026-10-19）：申请「悦航8号」被运营绑到「悦航7号」，
 * 用户直到发现看不到本船信息才察觉。
 */
describe('mismatchTip', () => {
  it('服务端标记 vesselMismatch 时给出申请名与实际绑定名的对照', () => {
    const tip = mismatchTip({
      applyVesselName: '悦航8号',
      matchedVesselName: '悦航7号',
      vesselMismatch: true,
    })

    expect(tip).toContain('悦航8号')
    expect(tip).toContain('悦航7号')
    expect(tip).toContain('联系运营')
  })

  it('未下发 vesselMismatch 时用归一化规则兜底判定', () => {
    expect(mismatchTip({ applyVesselName: '悦航8号', matchedVesselName: '悦航7号' })).toContain('悦航7号')
  })

  it('归一化后一致时返回空串（写法差异不算不一致）', () => {
    expect(mismatchTip({ applyVesselName: '悦航 8 号', matchedVesselName: '悦航8号' })).toBe('')
    expect(mismatchTip({ applyVesselName: 'YUE HANG-8', matchedVesselName: 'yuehang8' })).toBe('')
    expect(mismatchTip({ applyVesselName: '悦航8号', matchedVesselName: '悦航8号', vesselMismatch: false })).toBe('')
  })

  it('缺 matchedVesselName 时返回空串（拿不出实际绑定船名就不要下结论）', () => {
    expect(mismatchTip({ applyVesselName: '悦航8号' })).toBe('')
    expect(mismatchTip({ applyVesselName: '悦航8号', vesselMismatch: true })).toBe('')
    expect(mismatchTip({ applyVesselName: '悦航8号', matchedVesselName: '  ', vesselMismatch: true })).toBe('')
    expect(mismatchTip(null)).toBe('')
    expect(mismatchTip(undefined)).toBe('')
  })

  it('申请船名缺失时不拼出空引号', () => {
    const tip = mismatchTip({ matchedVesselName: '悦航7号', vesselMismatch: true })

    expect(tip).toContain('悦航7号')
    expect(tip).not.toContain('「」')
    expect(tip).not.toContain('你申请的')
  })
})
