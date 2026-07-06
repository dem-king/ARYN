export function prepay(data: any, paymentPrice: any, JumpUrl: string, paymentType: string) {
  if (paymentPrice === 0) {
    uni.reLaunch({
      url: JumpUrl,
    })
  }
  else {
    // #ifdef H5
    if (paymentType === '2') {
      // 支付宝支付
      window.location.href = data.body
    }
    else {
      // 微信支付
      window.location.href = data
    }
    // #endif

    // #ifdef MP-WEIXIN
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
      },
      fail() {
        uni.showModal({
          title: '提示',
          content: '取消支付',
        })
      },
    })
    // #endif

    // #ifdef MP-ALIPAY
    uni.requestPayment({
      provider: 'alipay',
      orderInfo: data.tradeNo,
      success(res: any) {
        if (res.resultCode !== '9000') {
          uni.showModal({
            title: '提示',
            content: res.memo,
          })
        }
        else {
          uni.reLaunch({
            url: JumpUrl,
          })
        }
      },
      fail() {
        uni.showModal({
          title: '提示',
          content: '取消支付',
        })
      },
    })
    // #endif

    // #ifdef MP-TOUTIAO
    // 抖音小程序支付：通过 tt.pay 调起，UniApp 封装为 uni.requestPayment
    uni.requestPayment({
      provider: 'wxpay',
      orderInfo: {
        order_id: data.orderId,
        order_token: data.orderToken,
      },
      success(res: any) {
        if (res.code === 0) {
          uni.reLaunch({
            url: JumpUrl,
          })
        }
        else {
          uni.showModal({
            title: '提示',
            content: res.msg || '支付失败',
          })
        }
      },
      fail() {
        uni.showModal({
          title: '提示',
          content: '取消支付',
        })
      },
    })
    // #endif

    // #ifdef APP-PLUS
    const orderInfo = {
      appid: data.appid,
      noncestr: data.noncestr,
      package: data.packageValue,
      partnerid: data.partnerId,
      prepayid: data.prepayId,
      timestamp: data.timestamp,
      sign: data.sign,
    }
    if (paymentType === '1') {
      uni.requestPayment({
        provider: 'wxpay',
        orderInfo: JSON.stringify(orderInfo),
        success() {
          uni.reLaunch({
            url: JumpUrl,
          })
        },
        fail() {
          uni.showModal({
            title: '提示',
            content: '取消支付',
          })
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
        },
        fail() {
          uni.showModal({
            title: '提示',
            content: '取消支付',
          })
        },
      })
    }

    // #endif
  }
}
