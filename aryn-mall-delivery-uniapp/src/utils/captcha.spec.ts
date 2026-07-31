import CryptoJS from 'crypto-js'
import { describe, expect, it } from 'vitest'
import { buildCaptchaVerification, encryptCaptchaText } from './captcha'

describe('配送端滑块验证码协议', () => {
  it('使用服务端约定的 AES ECB PKCS7 格式加密坐标', () => {
    const secretKey = '1234567890abcdef'
    const encrypted = encryptCaptchaText(JSON.stringify({ x: 123, y: 5 }), secretKey)
    const decrypted = CryptoJS.AES.decrypt(encrypted, CryptoJS.enc.Utf8.parse(secretKey), {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7,
    }).toString(CryptoJS.enc.Utf8)

    expect(decrypted).toBe('{"x":123,"y":5}')
  })

  it('登录校验值包含验证码 token 和已校验坐标', () => {
    const secretKey = '1234567890abcdef'
    const encrypted = buildCaptchaVerification('captcha-token', { x: 88, y: 5 }, secretKey)
    const decrypted = CryptoJS.AES.decrypt(encrypted, CryptoJS.enc.Utf8.parse(secretKey), {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7,
    }).toString(CryptoJS.enc.Utf8)

    expect(decrypted).toBe('captcha-token---{"x":88,"y":5}')
  })
})
