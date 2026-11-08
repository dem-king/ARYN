import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'
import { translate } from '@/locales'

/**
 * 预支付统一契约：C 端只消费自有字段，不直接依赖微信/支付宝 SDK Response。
 */
export type PayInvokeType =
  | 'WX_MINI'
  | 'WX_APP'
  | 'ALI_APP'
  | 'ALI_MINI'
  | 'REDIRECT'
  | 'ALREADY_PAID'
  | 'NONE'

export interface PayPrepayResult {
  channel: string
  terminalType: string
  invokeType: PayInvokeType
  orderNo: string
  alreadyPaid?: boolean
  params?: Record<string, any>
  message?: string
}

export type ClientPlatform = 'WX_MA' | 'ALI_MINI' | 'H5' | 'APP' | ''

/** 当前客户端平台：用于能力查询参数与前端二次过滤，不作为资金安全依据。 */
export function detectClientPlatform(): ClientPlatform {
  const buildPlatform = (globalThis as any).process?.env?.UNI_PLATFORM
  let platform: ClientPlatform = ''
  // #ifdef MP-WEIXIN
  if (buildPlatform === 'mp-weixin') {
    platform = 'WX_MA'
  }
  // #endif
  // #ifdef MP-ALIPAY
  if (buildPlatform === 'mp-alipay') {
    platform = 'ALI_MINI'
  }
  // #endif
  if (platform) {
    return platform
  }
  if (typeof uni !== 'undefined' && typeof uni.getSystemInfoSync === 'function') {
    const runtime = uni.getSystemInfoSync().uniPlatform
    if (runtime === 'mp-weixin') return 'WX_MA'
    if (runtime === 'mp-alipay') return 'ALI_MINI'
    if (runtime === 'web') return 'H5'
    if (runtime === 'app') return 'APP'
  }
  return ''
}

/**
 * 小程序端能力二次过滤：微信小程序只看微信渠道，支付宝小程序只看支付宝渠道。
 * 前端过滤只保证不展示不可调起的入口，服务端 createOrder 仍会做平台/tradeType 校验。
 */
export function filterCapabilitiesForClientPlatform<T extends { channel: string }>(
  capabilities: T[],
  platform: ClientPlatform = detectClientPlatform(),
): T[] {
  if (platform === 'WX_MA') {
    return capabilities.filter(item => item.channel !== '2')
  }
  if (platform === 'ALI_MINI') {
    return capabilities.filter(item => item.channel !== '1')
  }
  return capabilities
}

export function isAlreadyPaid(result?: PayPrepayResult | null): boolean {
  if (!result) {
    return false
  }
  return result.alreadyPaid === true || result.invokeType === 'ALREADY_PAID'
}

/**
 * 发起支付并等待原生支付回调结束。
 * 调用方应 await 本函数：成功/取消/失败都会在回调后 resolve，
 * 便于页面全程持有支付锁，避免重复唤起收银台。
 */
export function prepay(result: PayPrepayResult, paymentPrice: any, JumpUrl: string): Promise<void> {
  if (paymentPrice === 0) {
    uni.reLaunch({
      url: JumpUrl,
    })
    return Promise.resolve()
  }
  return doPrepay(result, JumpUrl)
}

async function doPrepay(result: PayPrepayResult, JumpUrl: string) {
  // 支付前守卫：绑定/会话租户校验失败时阻断支付（零传输、零收银台唤起）
  await ensureTenantReady()
  const authStore = useAuthStore()
  if (authStore.getToken) {
    await ensureSessionTenant('mall', authStore.getToken)
  }

  if (!result || isAlreadyPaid(result)) {
    uni.reLaunch({ url: JumpUrl })
    return
  }

  const params = result.params || {}
  switch (result.invokeType) {
    case 'WX_MINI':
      await requestWxMini(params, JumpUrl)
      break
    case 'WX_APP':
      if (!(await providerAvailable('wxpay'))) {
        showMessage(translate('labels.payUnavailable'))
        return
      }
      await requestWxApp(params, JumpUrl)
      break
    case 'ALI_APP':
      if (!(await providerAvailable('alipay'))) {
        showMessage(translate('labels.payUnavailable'))
        return
      }
      await requestAliApp(params, JumpUrl)
      break
    case 'ALI_MINI':
      await requestAliMini(params, JumpUrl)
      break
    case 'REDIRECT':
      redirectToChannel(params.redirectUrl)
      break
    case 'NONE':
      uni.reLaunch({ url: JumpUrl })
      break
    default:
      showMessage(result.message || translate('labels.payUnavailable'))
  }
}

function requestWxMini(params: Record<string, any>, JumpUrl: string) {
  return new Promise<void>((resolve) => {
    uni.requestPayment<any>({
      provider: 'wxpay',
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      package: params.package,
      signType: params.signType || 'RSA',
      paySign: params.paySign,
      success() {
        uni.reLaunch({
          url: JumpUrl,
        })
        resolve()
      },
      fail() {
        showCanceled()
        resolve()
      },
    })
  })
}

function requestWxApp(params: Record<string, any>, JumpUrl: string) {
  return new Promise<void>((resolve) => {
    const orderInfo = {
      appid: params.appid,
      partnerid: params.partnerid,
      prepayid: params.prepayid,
      package: params.package,
      noncestr: params.noncestr,
      timestamp: params.timestamp,
      sign: params.sign,
    }
    // HarmonyOS 沿用字符串规则；Android/iOS 必须传对象，不能统一 JSON.stringify
    const osName = uni.getSystemInfoSync().osName
    const paymentOrderInfo = osName === 'harmonyos' ? JSON.stringify(orderInfo) : orderInfo
    uni.requestPayment({
      provider: 'wxpay',
      orderInfo: paymentOrderInfo as any,
      success() {
        uni.reLaunch({
          url: JumpUrl,
        })
        resolve()
      },
      fail() {
        showCanceled()
        resolve()
      },
    })
  })
}

function requestAliApp(params: Record<string, any>, JumpUrl: string) {
  return new Promise<void>((resolve) => {
    uni.requestPayment({
      provider: 'alipay',
      orderInfo: params.orderString,
      success() {
        uni.reLaunch({
          url: JumpUrl,
        })
        resolve()
      },
      fail() {
        showCanceled()
        resolve()
      },
    })
  })
}

function requestAliMini(params: Record<string, any>, JumpUrl: string) {
  return new Promise<void>((resolve) => {
    uni.requestPayment({
      provider: 'alipay',
      orderInfo: params.tradeNo,
      success(res: any) {
        if (res?.resultCode !== '9000') {
          showMessage(res?.memo || translate('labels.payUnavailable'))
          resolve()
          return
        }
        uni.reLaunch({
          url: JumpUrl,
        })
        resolve()
      },
      fail() {
        showCanceled()
        resolve()
      },
    })
  })
}

function redirectToChannel(url?: string) {
  if (!url) {
    showMessage(translate('labels.payUnavailable'))
    return
  }
  // #ifdef H5
  window.location.href = url
  return
  // #endif
  showMessage(translate('labels.payUnavailable'))
}

function providerAvailable(provider: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.getProvider({
      service: 'payment',
      success(res) {
        resolve(((res.provider || []) as string[]).includes(provider))
      },
      fail() {
        resolve(false)
      },
    })
  })
}

function showCanceled() {
  uni.showModal({
    title: translate('labels.notice'),
    content: translate('labels.cancelPayment'),
  })
}

function showMessage(message: string) {
  uni.showModal({
    title: translate('labels.notice'),
    content: message,
  })
}
