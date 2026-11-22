import accountMemberZh from './modules/accountMember.zh-CN'
import accountSettingsZh from './modules/accountSettings.zh-CN'
import buyerOrgZh from './modules/buyerOrg.zh-CN'
import orderZh from './modules/order.zh-CN'
import afterSalesZh from './modules/afterSales.zh-CN'
import salesZh from './modules/sales.zh-CN'
import loginDevicesZh from './modules/loginDevices.zh-CN'
import procurementZh from './modules/procurement.zh-CN'
import productZh from './modules/product.zh-CN'
import checkoutZh from './modules/checkout.zh-CN'
import sharedCartZh from './modules/sharedCart.zh-CN'
import accountMemberEn from './modules/accountMember.en-US'
import accountSettingsEn from './modules/accountSettings.en-US'
import buyerOrgEn from './modules/buyerOrg.en-US'
import orderEn from './modules/order.en-US'
import afterSalesEn from './modules/afterSales.en-US'
import salesEn from './modules/sales.en-US'
import loginDevicesEn from './modules/loginDevices.en-US'
import procurementEn from './modules/procurement.en-US'
import productEn from './modules/product.en-US'
import checkoutEn from './modules/checkout.en-US'
import sharedCartEn from './modules/sharedCart.en-US'
import deliveryScheduleZh from './modules/deliverySchedule.zh-CN'
import deliveryScheduleEn from './modules/deliverySchedule.en-US'

export const subpageMessages = {
  'zh-CN': { ...accountMemberZh, ...accountSettingsZh, ...buyerOrgZh, ...orderZh, ...afterSalesZh, ...salesZh, ...loginDevicesZh, ...procurementZh, ...productZh, ...checkoutZh, ...sharedCartZh, ...deliveryScheduleZh },
  'en-US': { ...accountMemberEn, ...accountSettingsEn, ...buyerOrgEn, ...orderEn, ...afterSalesEn, ...salesEn, ...loginDevicesEn, ...procurementEn, ...productEn, ...checkoutEn, ...sharedCartEn, ...deliveryScheduleEn },
}
