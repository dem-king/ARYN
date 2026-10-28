import { ensureSessionTenant, ensureTenantReady } from '@/api/core/tenant-identity'
import { translate } from '@/locales'

/**
 * 发起支付并等待原生支付回调结束。
 *
 * 调用方应 await 本函数：成功/取消/失败都会在回调后 resolve，
 * 便于页面全程持有支付锁，避免重复唤起收银台。
 */
export function prepay(data: any, paymentPrice: any, JumpUrl: string, paymentType: string): Promise<void> {
  if (paymentPrice === 0) {
    uni.reLaunch({
      url: JumpUrl,
    })
    return Promise.resolve()
  }
  return doPrepay(data, JumpUrl, paymentType)
}

async function doPrepay(data: any, JumpUrl: string, paymentType: string) {
  // 支付前守卫：绑定/会话租户校验失败时阻断支付（零传输、零收银台唤起）
  await ensureTenantReady()
  const authStore = useAuthStore()
  if (authStore.getToken) {
    await ensureSessionTenant('mall', authStore.getToken)
  }

  // #ifdef H5
  if (paymentType === '2') {
    // 支付宝支付
    window.location.href = data.body
  }
  else {
    // 微信支付
    window.location.href = data
  }
  return
  // #endif

  // #ifdef MP-WEIXIN
  await requestWxPayment(data, JumpUrl)
  // #endif

  // #ifdef MP-ALIPAY
  await requestAliPayment(data, JumpUrl)
  // #endif

  // #ifdef APP-PLUS
  await requestAppPayment(data, JumpUrl, paymentType)
  // #endif
}

function requestWxPayment(data: any, JumpUrl: string) {
  return new Promise<void>((resolve) => {
    uni.requestPayment<any>({
      provider: 'wxpay',
      timeStamp: data.timeStamp,
      nonceStr: data.nonceStr,
      package: data.packageValue,
      signType: data.signType,
      paySign: data.paySign,
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

function requestAliPayment(data: any, JumpUrl: string) {
  return new Promise<void>((resolve) => {
    uni.requestPayment({
      provider: 'alipay',
      orderInfo: data.tradeNo,
      success(res) {
        if (res.resultCode !== '9000') {
          uni.showModal({
            title: translate('labels.notice'),
            content: res.memo,
          })
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

function requestAppPayment(data: any, JumpUrl: string, paymentType: string) {
  return new Promise<void>((resolve) => {
    if (paymentType === '1') {
      const orderInfo = {
        appid: data.appid,
        noncestr: data.noncestr,
        package: data.packageValue,
        partnerid: data.partnerId,
        prepayid: data.prepayId,
        timestamp: data.timestamp,
        sign: data.sign,
      }
      uni.requestPayment({
        provider: 'wxpay',
        orderInfo: JSON.stringify(orderInfo),
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
    }
    else {
      uni.requestPayment({
        provider: 'alipay',
        orderInfo: data.body,
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
    }
  })
}

function showCanceled() {
  uni.showModal({
    title: translate('labels.notice'),
    content: translate('labels.cancelPayment'),
  })
}
