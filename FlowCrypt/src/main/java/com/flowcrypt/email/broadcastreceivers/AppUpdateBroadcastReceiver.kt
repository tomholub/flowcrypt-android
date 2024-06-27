/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowcrypt.email.util.PreferencesKeys
import com.flowcrypt.email.util.SharedPreferencesHelper

/**
 * This [BroadcastReceiver] can be used to run some actions when the app will be updated.
 *
 * @author Denys Bondarenko
 */
class AppUpdateBroadcastReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent != null && Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {
      SharedPreferencesHelper.setValue(context, PreferencesKeys.KEY_IS_CHECK_KEYS_NEEDED, true)
    }
  }
}
