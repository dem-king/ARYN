/**
 * Login-devices page copy. Keys mirror `loginDevices.zh-CN.ts`.
 *
 * The single-session policy must be described truthfully (one session per account);
 * do not imply multi-device coexistence is supported.
 */
export default {
  loginDevices: {
    title: 'Login devices',
    entry: 'Login devices',
    currentDevice: 'Current device',
    currentTag: 'Current',
    currentDeviceCannotKick: 'The current device cannot be signed out here. Use "Sign Out" at the bottom.',
    otherDevices: 'Other sessions',
    noOthers: 'No other sessions',
    empty: 'No device information',
    emptyHint: 'Sign in to view the devices using your account in personal settings',
    loadFailed: 'Failed to load login devices. Please try again later.',
    retry: 'Retry',
    kick: 'Sign out',
    kickTitle: 'Sign out device',
    kickConfirm: 'Sign out this device? It will need to sign in again.',
    kickSuccess: 'Device signed out',
    kickFailed: 'Failed to sign out the device. Please try again later.',
    unknownDevice: 'Unknown device',
    location: 'Location: {location}',
    loginTime: 'Signed in at {time}',
    policyTip: 'Only one session is allowed per account. Signing in on another device will sign this device out automatically.',
  },
}
