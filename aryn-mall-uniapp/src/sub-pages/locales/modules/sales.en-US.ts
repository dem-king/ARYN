/**
 * Sales workspace copy.
 *
 * Sales commission is paid with salary; the mini program is read-only and
 * offers no withdrawal. When the member is not a registered sales person
 * (`isSales=false`) the page only shows the "not a sales account" notice.
 */
export default {
  sales: {
    workbench: {
      navTitle: 'Sales Workspace',
      nonSalesTitle: 'Not a sales account',
      nonSalesHint: 'This account is not registered as a sales person, so the sales workspace is unavailable',
      nonSalesBack: 'Back',
      monthCommission: 'Est. Commission (CNY)',
      monthTradeAmount: 'Monthly Sales (CNY)',
      monthOrderCount: 'Monthly Orders',
      totalCommission: 'Total Commission (CNY)',
      customerCount: 'Customers',
      qrcodeTitle: 'My Referral Code',
      qrcodeHint: 'Customers who scan it are bound to you in the mini program',
      qrcodeLoading: 'Generating referral code...',
      qrcodeUnavailable: 'Referral code unavailable',
      qrcodeRetry: 'Retry',
      saveToAlbum: 'Save to Album',
      saveSuccess: 'Saved to album',
      saveFailed: 'Save failed. Long-press the image to save it.',
      longPressHint: 'You can also long-press the image to save',
      customersTitle: 'My Customers',
      customersEmpty: 'No customers yet',
      bindSourceLabel: 'Source: {source}',
      bindTimeLabel: 'Bound: {time}',
      ordersTitle: 'Performance',
      ordersEmpty: 'No performance records yet',
      orderCommissionBase: 'Base {amount}',
      orderCommissionAmount: 'Commission {amount}',
      orderPayTime: 'Paid: {time}',
      loadMore: 'Load more',
      status: {
        pending: 'Pending',
        payable: 'Payable',
        paid: 'Paid',
        refunded: 'Fully Refunded',
        unknown: 'Unknown',
      },
      bindSource: {
        share: 'Share Link',
        qrcode: 'Referral Code',
        admin: 'Admin Assigned',
        transfer: 'Resignation Transfer',
        claim: 'First-Order Transfer',
        unknown: 'Other',
      },
    },
  },
}
