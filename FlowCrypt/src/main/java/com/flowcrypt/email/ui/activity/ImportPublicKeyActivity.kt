/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.flowcrypt.email.R
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.relation.RecipientWithPubKeys
import com.flowcrypt.email.jetpack.viewmodel.RecipientsViewModel
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.security.model.PgpKeyDetails
import com.flowcrypt.email.ui.activity.base.BaseImportKeyActivity
import com.flowcrypt.email.util.GeneralUtil
import com.google.android.material.snackbar.Snackbar

/**
 * This activity describes a logic of import public keys.
 *
 * @author Denis Bondarenko
 * Date: 03.08.2017
 * Time: 12:35
 * E-mail: DenBond7@gmail.com
 */
class ImportPublicKeyActivity : BaseImportKeyActivity() {

  private var recipientWithPubKeys: RecipientWithPubKeys? = null
  private val recipientsViewModel: RecipientsViewModel by viewModels()

  override val contentViewResourceId: Int
    get() = R.layout.activity_import_public_key_for_pgp_contact

  override val isPrivateKeyMode: Boolean
    get() = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent != null && intent.hasExtra(KEY_EXTRA_PGP_CONTACT)) {
      this.recipientWithPubKeys = intent.getParcelableExtra(KEY_EXTRA_PGP_CONTACT)
    } else {
      finish()
    }
  }

  override fun onKeyFound(
    sourceType: KeyImportDetails.SourceType,
    keyDetailsList: List<PgpKeyDetails>
  ) {
    if (keyDetailsList.isNotEmpty()) {
      if (keyDetailsList.size == 1) {
        val key = keyDetailsList.first()

        if (!key.usableForEncryption) {
          showInfoSnackbar(
            view = rootView,
            messageText = getString(R.string.cannot_be_used_for_encryption)
          )
          return
        }

        if (key.isPrivate) {
          showInfoSnackbar(
            view = rootView,
            messageText = getString(
              R.string.file_has_wrong_pgp_structure, getString(R.string.public_)
            ),
            duration = Snackbar.LENGTH_LONG
          )
          return
        }

        updateInformationAboutRecipientWithPubKeys(key)
        setResult(Activity.RESULT_OK)
        finish()
      } else {
        showInfoSnackbar(rootView, getString(R.string.select_only_one_key))
      }
    } else {
      showInfoSnackbar(rootView, getString(R.string.error_no_keys))
    }
  }

  private fun updateInformationAboutRecipientWithPubKeys(keyDetails: PgpKeyDetails) {
    recipientWithPubKeys?.recipient?.let {
      recipientsViewModel.copyPubKeysToRecipient(it, keyDetails)
    }
  }

  companion object {
    val KEY_EXTRA_PGP_CONTACT = GeneralUtil.generateUniqueExtraKey(
      "KEY_EXTRA_PGP_CONTACT",
      ImportPublicKeyActivity::class.java
    )

    fun newIntent(
      context: Context?,
      accountEntity: AccountEntity?,
      title: String,
      recipientWithPubKeys: RecipientWithPubKeys
    ): Intent {
      val intent = newIntent(
        context = context, accountEntity = accountEntity, title = title,
        throwErrorIfDuplicateFoundEnabled = false, cls = ImportPublicKeyActivity::class.java
      )
      intent.putExtra(KEY_EXTRA_PGP_CONTACT, recipientWithPubKeys)
      return intent
    }
  }
}
