/**
 * English messages for cross-page shared components (privacy popup, SKU popup, delivery progress, category fallbacks).
 * Keys must mirror shared.zh-CN.ts.
 */
export default {
  shared: {
    privacy: {
      title: 'Privacy Protection Notice',
      desc: 'Thank you for using this app. Before using our services, please read and agree to',
      subDesc: '. By tapping Agree and starting to use the services, you confirm that you have read and agree to this policy, which is legally binding. If you decline, related services will be unavailable.',
      protocol: 'Privacy Protection Guidelines',
      disagree: 'Decline',
      agree: 'Agree',
    },
    sku: {
      requesting: 'Loading...',
      invalidActionFunction: 'custom-action must be a function',
      invalidActionPromise: 'custom-action must return a Promise',
    },
    deliveryProgress: {
      noPhone: 'No phone number',
      callFailed: 'Failed to place the call',
      staffRole: 'Mall delivery staff',
      tripNo: 'Trip No.',
      progressTitle: 'Delivery Progress',
      evidence: 'Proof of Delivery',
      empty: 'No delivery progress yet',
    },
    categoryIcon: {
      fallbackText: 'A',
    },
    deliveryPick: {
      uncategorized: 'Uncategorized',
    },
  },
}
