/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.os.Bundle
import com.flowcrypt.email.Constants

import com.flowcrypt.email.R
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.dao.source.AccountDaoSource
import com.flowcrypt.email.ui.activity.fragment.base.BasePreferenceFragment

/**
 * The main settings fragment.
 *
 * @author DenBond7
 * Date: 26.05.2017
 * Time: 10:13
 * E-mail: DenBond7@gmail.com
 */

class MainSettingsFragment : BasePreferenceFragment() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.preferences_main_settings)

    val account = AccountDaoSource().getActiveAccountInformation(context)

    val preferenceBackups = findPreference(Constants.PREF_KEY_BACKUPS)
    preferenceBackups?.isVisible =
        !(account?.isRuleExist(AccountDao.DomainRule.NO_PRV_BACKUP) ?: false)
  }
}
