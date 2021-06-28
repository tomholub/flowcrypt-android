/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment.preferences

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.extensions.showNeedPassphraseDialog
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.ui.activity.fragment.base.BasePreferenceFragment
import com.flowcrypt.email.ui.activity.fragment.dialog.FixNeedPassphraseIssueDialogFragment
import com.flowcrypt.email.util.UIUtil

/**
 * This fragment contains actions which related to Security options.
 *
 * @author DenBond7
 * Date: 08.08.2018.
 * Time: 10:47.
 * E-mail: DenBond7@gmail.com
 */
class SecuritySettingsFragment : BasePreferenceFragment(), Preference.OnPreferenceClickListener {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as AppCompatActivity?)?.supportActionBar?.title =
      getString(R.string.security_and_privacy)
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_security_settings, rootKey)
    findPreference<Preference>(Constants.PREF_KEY_SECURITY_CHANGE_PASS_PHRASE)?.onPreferenceClickListener =
      this
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_SHOW_FIX_EMPTY_PASSPHRASE_DIALOG -> when (resultCode) {
        FixNeedPassphraseIssueDialogFragment.RESULT_OK -> {
          navController?.navigate(
            SecuritySettingsFragmentDirections
              .actionSecuritySettingsFragmentToChangePassphraseOfImportedKeysFragment(
                title = getString(R.string.change_pass_phrase)
              )
          )
        }
      }

      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    return when (preference.key) {
      Constants.PREF_KEY_SECURITY_CHANGE_PASS_PHRASE -> {
        val keysStorage = KeysStorageImpl.getInstance(requireContext())
        if (keysStorage.getRawKeys().isEmpty()) {
          UIUtil.showInfoSnackbar(
            requireView(), getString(
              R.string.account_has_no_associated_keys,
              getString(R.string.support_email)
            )
          )
        } else {
          val fingerprints = keysStorage.getFingerprintsWithEmptyPassphrase()
          if (fingerprints.isNotEmpty()) {
            showNeedPassphraseDialog(
              fingerprints = fingerprints,
              requestCode = REQUEST_CODE_SHOW_FIX_EMPTY_PASSPHRASE_DIALOG,
              logicType = FixNeedPassphraseIssueDialogFragment.LogicType.ALL
            )
          } else {
            navController?.navigate(
              SecuritySettingsFragmentDirections
                .actionSecuritySettingsFragmentToChangePassphraseOfImportedKeysFragment(
                  title = getString(R.string.change_pass_phrase)
                )
            )
          }
        }
        true
      }

      else -> false
    }
  }

  companion object {
    private const val REQUEST_CODE_SHOW_FIX_EMPTY_PASSPHRASE_DIALOG = 100
  }
}
