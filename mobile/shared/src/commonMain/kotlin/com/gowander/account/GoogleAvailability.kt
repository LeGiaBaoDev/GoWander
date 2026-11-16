package com.gowander.account

/**
 * Whether the native Google button can run on this platform. Both are false until AUTH-2 part 4 wires Credential
 * Manager (Android) and the GoogleSignIn SDK (iOS): the screens then hide the button instead of offering a dead one.
 */
expect val googleSignInAvailable: Boolean
