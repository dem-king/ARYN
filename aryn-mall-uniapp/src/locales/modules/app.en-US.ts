/**
 * English messages for the app module (App-level messages: request errors, tenant checks, auth session and navigation fallbacks).
 * Keys must mirror app.zh-CN.ts.
 */
export default {
  app: {
    request: {
      featureUnavailable: 'This feature is temporarily unavailable. Please try again later.',
      forbidden: 'You do not have permission to access this feature.',
    },
    auth: {
      loggingIn: 'Signing in. Please wait...',
      wxCredentialFailed: 'Failed to get the WeChat login credential',
      tokenMissing: 'The login response has no valid credential',
      unsupportedScene: 'Unsupported purchase scenario: {scene}',
    },
    nav: {
      noPermission: 'No access permission',
    },
    deliveryNav: {
      noAddress: 'No delivery address',
      openMapFailed: 'Failed to open the map',
      addressCopiedTitle: 'Address Copied',
      addressCopiedContent: 'Open a navigation app on your phone, paste the address into the search box, and start navigating.',
      gotIt: 'Got It',
      copyFailed: 'Failed to copy the address',
    },
    countdown: {
      finished: 'Ended',
      days: '{count}d',
    },
    tenantBlocked: {
      title: 'App configuration unavailable',
      desc: 'Please contact an administrator to verify the Mini Program and tenant configuration, then try again later.',
      retry: 'Retry',
    },
    tenantGuard: {
      moduleMissing: 'Tenant identity module missing',
      tenantIdMismatch: 'Tenant ID in the bundle does not match the injected environment value',
      appIdMismatch: 'WeChat AppID in the bundle does not match the injected environment value',
      openBootMismatch: 'Deployment mode in the bundle does not match the injected environment value',
      apiBaseUrlMismatch: 'API base URL in the bundle does not match the injected environment value',
      deviceMismatch: 'Runtime container AppID does not match the expected bundle AppID (real={real})',
      bindingMismatch: 'Server tenant mapping does not match the bundle identity',
      bindingCheckFailed: 'Tenant binding check failed',
      bindingChanged: 'The server tenant mapping has changed. Reopen the app.',
      sessionMismatch: 'Session tenant does not match the bundle identity',
    },
    tenantPreflight: {
      httpFailed: 'Tenant preflight request failed (HTTP {status})',
      abnormalResponse: 'Tenant preflight returned an error code={code}',
      missingData: 'Tenant preflight response is missing data',
      networkFailed: 'Tenant preflight network failure',
    },
    file: {
      deliveryIdentityFailed: 'Delivery identity verification failed',
      uploadFailed: 'Credential upload failed',
      uploadResponseInvalid: 'Invalid credential upload response',
    },
  },
}
