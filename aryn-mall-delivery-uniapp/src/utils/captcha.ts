import CryptoJS from 'crypto-js'

export interface CaptchaPoint {
  x: number
  y: number
}

export function encryptCaptchaText(value: string, secretKey: string) {
  const key = CryptoJS.enc.Utf8.parse(secretKey)
  const source = CryptoJS.enc.Utf8.parse(value)
  return CryptoJS.AES.encrypt(source, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  }).toString()
}

export function buildCaptchaVerification(token: string, point: CaptchaPoint, secretKey: string) {
  return encryptCaptchaText(`${token}---${JSON.stringify(point)}`, secretKey)
}
