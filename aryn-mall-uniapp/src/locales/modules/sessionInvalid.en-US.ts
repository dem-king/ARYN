/**
 * Session-invalid dialog copy. Keys mirror `sessionInvalid.zh-CN.ts`.
 *
 * Semantics follow the frozen Chinese copy in
 * docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md §4 (reason) and
 * 二期实现契约.md §2 (subReason + single-button rule).
 */
export default {
  sessionInvalid: {
    title: {
      beReplaced: 'Signed in on another device',
      beReplacedDelivery: 'Delivery account signed in on another device',
      tokenExpired: 'Session expired',
      invalid: 'Session no longer valid',
      // Phase 2: KICK_OUT + subReason titles
      adminKick: 'Signed out by an administrator',
      passwordChanged: 'Password changed',
      accountDisabled: 'Account disabled',
      accountDeleted: 'Account deleted',
      tenantDisabled: 'Tenant disabled',
      memberDisabled: 'Account banned',
      deliveryRevoked: 'Delivery access changed',
    },
    content: {
      beReplaced: 'Your account signed in on another device at {time} and you were signed out.',
      beReplacedNoTime: 'Your account is signed in on another device. Please sign in again to continue.',
      beReplacedTail: 'Signing in again will sign that device out.',
      tokenExpired: 'Your session has expired. Please sign in again.',
      invalid: 'Your session is no longer valid. Please sign in again.',
      // Phase 2: KICK_OUT + subReason bodies
      adminKick: 'An administrator has signed you out.',
      passwordChanged: 'Your password was changed. Please sign in again with the new password.',
      accountDisabled: 'This account has been disabled. Please contact an administrator.',
      accountDeleted: 'This account has been deleted.',
      tenantDisabled: 'The store this account belongs to has been disabled.',
      memberDisabled: 'This account has been banned. Please contact customer service.',
      deliveryRevoked: 'Your delivery access has changed. Please sign in to the delivery app again.',
    },
    deviceLine: 'Device: {device}',
    relogin: 'Sign in again',
    exit: 'Exit',
    // Phase 2: the only button when re-login cannot succeed
    backToLogin: 'Back to sign in',
  },
}
