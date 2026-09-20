import { describe, expect, it } from 'vitest'

import {
  CART_STATUS_CLOSED,
  CART_STATUS_COLLECTING,
  CART_STATUS_DRAFT,
  CART_STATUS_SUBMITTED,
  CART_STATUS_WAITING_CONFIRM,
  cartStatusLabel,
  cartStatusTheme,
  isCartCollecting,
  isCartReadonly,
  MEMBER_ROLE_CONFIRMATOR,
  MEMBER_ROLE_MEMBER,
  MEMBER_ROLE_OWNER,
  memberRoleLabel,
} from './shared-cart'

describe('cartStatusLabel', () => {
  it('covers all five backend statuses', () => {
    expect(cartStatusLabel(CART_STATUS_DRAFT)).toBe('草稿')
    expect(cartStatusLabel(CART_STATUS_COLLECTING)).toBe('收集中')
    expect(cartStatusLabel(CART_STATUS_WAITING_CONFIRM)).toBe('待确认')
    expect(cartStatusLabel(CART_STATUS_SUBMITTED)).toBe('已提交')
    expect(cartStatusLabel(CART_STATUS_CLOSED)).toBe('已关闭')
  })

  it('does not silently fall back to a concrete status for unknown values', () => {
    expect(cartStatusLabel('9')).toBe('未知状态')
    expect(cartStatusLabel('')).toBe('')
    expect(cartStatusLabel(null)).toBe('')
  })
})

describe('cartStatusTheme', () => {
  it('returns a theme for every known status', () => {
    for (const status of [
      CART_STATUS_DRAFT,
      CART_STATUS_COLLECTING,
      CART_STATUS_WAITING_CONFIRM,
      CART_STATUS_SUBMITTED,
      CART_STATUS_CLOSED,
    ]) {
      const theme = cartStatusTheme(status)
      expect(theme.bg).toMatch(/^#/)
      expect(theme.text).toMatch(/^#/)
    }
  })

  it('falls back to a neutral theme for unknown status', () => {
    expect(cartStatusTheme('9')).toEqual({ bg: '#F1EFE8', text: '#5F5E5A' })
    expect(cartStatusTheme(undefined)).toEqual({ bg: '#F1EFE8', text: '#5F5E5A' })
  })
})

describe('cart editability', () => {
  it('only treats collecting carts as editable', () => {
    expect(isCartCollecting(CART_STATUS_COLLECTING)).toBe(true)
    expect(isCartCollecting(CART_STATUS_DRAFT)).toBe(false)
    expect(isCartCollecting(CART_STATUS_WAITING_CONFIRM)).toBe(false)
    expect(isCartCollecting(CART_STATUS_SUBMITTED)).toBe(false)
    expect(isCartCollecting(CART_STATUS_CLOSED)).toBe(false)
  })

  it('treats submitted and closed carts as readonly', () => {
    expect(isCartReadonly(CART_STATUS_SUBMITTED)).toBe(true)
    expect(isCartReadonly(CART_STATUS_CLOSED)).toBe(true)
    expect(isCartReadonly(CART_STATUS_COLLECTING)).toBe(false)
  })
})

describe('memberRoleLabel', () => {
  it('covers all three member roles', () => {
    expect(memberRoleLabel(MEMBER_ROLE_OWNER)).toBe('发起人')
    expect(memberRoleLabel(MEMBER_ROLE_MEMBER)).toBe('成员')
    expect(memberRoleLabel(MEMBER_ROLE_CONFIRMATOR)).toBe('确认人')
  })

  it('returns empty for missing role', () => {
    expect(memberRoleLabel('')).toBe('')
    expect(memberRoleLabel(null)).toBe('')
  })
})
