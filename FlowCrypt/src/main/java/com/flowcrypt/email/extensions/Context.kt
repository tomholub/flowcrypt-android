/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import com.flowcrypt.email.FlowCryptApplication

/**
 * @author Denys Bondarenko
 */
fun Context.toast(text: String?, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, text ?: "", duration).show()
}

fun Context.toast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, resId, duration).show()
}

fun Context?.hasActiveConnection(): Boolean {
  return this?.let {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val cap = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  } ?: false
}

/**
 * Check if the app is in the foreground or visible..
 *
 * @return true if the app is foregrounded or visible.
 */
fun Context?.isAppForegrounded(): Boolean {
  return (this as? FlowCryptApplication)?.appForegroundedObserver?.isAppForegrounded ?: false
}
