/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.extensions.decrementSafely
import com.flowcrypt.email.extensions.incrementSafely
import com.flowcrypt.email.extensions.showInfoDialogFragment
import com.flowcrypt.email.jetpack.viewmodel.CheckPrivateKeysViewModel
import com.flowcrypt.email.model.KeyDetails
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.ui.activity.fragment.dialog.InfoDialogFragment
import com.flowcrypt.email.ui.activity.fragment.dialog.WebViewInfoDialogFragment
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.UIUtil
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * This class describes checking the incoming private keys. As a result will be returned encrypted
 * keys.
 *
 * @author Denis Bondarenko
 * Date: 21.07.2017
 * Time: 9:59
 * E-mail: DenBond7@gmail.com
 */
class CheckKeysActivity : BaseNodeActivity(), View.OnClickListener, InfoDialogFragment.OnInfoDialogButtonClickListener {
  private var originalKeys: MutableList<NodeKeyDetails> = mutableListOf()
  private val unlockedKeys: ArrayList<NodeKeyDetails> = ArrayList()
  private val remainingKeys: ArrayList<NodeKeyDetails> = ArrayList()
  private var keyDetailsAndFingerprintsMap: MutableMap<NodeKeyDetails, String>? = null
  private lateinit var checkPrivateKeysViewModel: CheckPrivateKeysViewModel

  private var editTextKeyPassword: EditText? = null
  private var textViewSubTitle: TextView? = null
  private var progressBar: View? = null

  private var subTitle: String? = null
  private var positiveBtnTitle: String? = null
  private var negativeBtnTitle: String? = null
  private var uniqueKeysCount: Int = 0
  private var type: KeyDetails.Type? = null

  override val isDisplayHomeAsUpEnabled: Boolean
    get() = false

  override val contentViewResourceId: Int
    get() = R.layout.activity_check_keys

  override val rootView: View
    get() = findViewById(R.id.layoutContent)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent == null) {
      finish()
      return
    }

    getExtras()

    if (originalKeys.isNotEmpty()) {
      this.keyDetailsAndFingerprintsMap = prepareMapFromKeyDetailsList(originalKeys)
      this.uniqueKeysCount = getUniqueFingerprintsCount(keyDetailsAndFingerprintsMap)

      if (!intent.getBooleanExtra(KEY_EXTRA_IS_EXTRA_IMPORT_OPTION, false)) {
        if (intent.getBooleanExtra(KEY_EXTRA_SKIP_IMPORTED_KEYS, false)) {
          removeAlreadyImportedKeys()
        }
        this.uniqueKeysCount = getUniqueFingerprintsCount(keyDetailsAndFingerprintsMap)
        this.originalKeys = ArrayList(keyDetailsAndFingerprintsMap?.keys ?: emptyList())

        when (uniqueKeysCount) {
          0 -> {
            setResult(RESULT_NO_NEW_KEYS)
            finish()
          }

          1 -> {
            this.subTitle = resources.getQuantityString(
                R.plurals.found_backup_of_your_account_key, uniqueKeysCount, uniqueKeysCount)
          }

          else -> {
            if (originalKeys.size != keyDetailsAndFingerprintsMap?.size) {
              val map = prepareMapFromKeyDetailsList(originalKeys)
              val remainingKeyCount = getUniqueFingerprintsCount(map)

              this.subTitle = resources.getQuantityString(R.plurals.not_recovered_all_keys, remainingKeyCount,
                  uniqueKeysCount - remainingKeyCount, uniqueKeysCount, remainingKeyCount)
            } else {
              this.subTitle = resources.getQuantityString(
                  R.plurals.found_backup_of_your_account_key, uniqueKeysCount, uniqueKeysCount)
            }
          }
        }
      }

      remainingKeys.addAll(originalKeys)

      if (originalKeys.isNotEmpty()) {
        initViews()
        setupCheckPrivateKeysViewModel()
      }

      checkExistingOfPartiallyEncryptedPrivateKeys()
    } else {
      setResult(Activity.RESULT_CANCELED)
      finish()
    }
  }

  override fun onClick(v: View) {
    when (v.id) {
      R.id.buttonPositiveAction -> {
        UIUtil.hideSoftInput(this, editTextKeyPassword)
        val passphrase = editTextKeyPassword?.text?.toString()
        if (passphrase.isNullOrEmpty()) {
          showInfoSnackbar(editTextKeyPassword, getString(R.string.passphrase_must_be_non_empty))
        } else {
          snackBar?.dismiss()
          checkPrivateKeysViewModel.checkKeys(remainingKeys, passphrase)
        }
      }

      R.id.buttonSkipRemainingBackups -> {
        returnUnlockedKeys(RESULT_SKIP_REMAINING_KEYS)
      }

      R.id.buttonNegativeAction -> {
        setResult(RESULT_NEGATIVE)
        finish()
      }

      R.id.imageButtonHint -> {
        val infoDialogFragment = InfoDialogFragment.newInstance(dialogMsg =
        getString(R.string.hint_when_found_keys_in_email))
        infoDialogFragment.show(supportFragmentManager, InfoDialogFragment::class.java.simpleName)
      }

      R.id.imageButtonPasswordHint -> try {
        val webViewInfoDialogFragment = WebViewInfoDialogFragment.newInstance("",
            IOUtils.toString(assets.open("html/forgotten_pass_phrase_hint.htm"), StandardCharsets.UTF_8))
        webViewInfoDialogFragment.show(supportFragmentManager, WebViewInfoDialogFragment::class.java.simpleName)
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
  }

  override fun onInfoDialogButtonClick(requestCode: Int) {
    setResult(RESULT_CANCELED)
    finish()
  }

  private fun getExtras() {
    val keys: List<NodeKeyDetails>? = intent?.getParcelableArrayListExtra(KEY_EXTRA_PRIVATE_KEYS)
    keys?.let { originalKeys.addAll(it) }
    this.type = intent?.getParcelableExtra(KEY_EXTRA_TYPE)
    this.subTitle = intent?.getStringExtra(KEY_EXTRA_SUB_TITLE)
    this.positiveBtnTitle = intent?.getStringExtra(KEY_EXTRA_POSITIVE_BUTTON_TITLE)
    this.negativeBtnTitle = intent?.getStringExtra(KEY_EXTRA_NEGATIVE_BUTTON_TITLE)
  }

  private fun initViews() {
    initButton(R.id.buttonPositiveAction, text = positiveBtnTitle)
    initButton(R.id.buttonNegativeAction, text = negativeBtnTitle)

    val imageButtonHint = findViewById<View>(R.id.imageButtonHint)
    if (originalKeys.isNotEmpty() && type === KeyDetails.Type.EMAIL) {
      imageButtonHint?.visibility = View.VISIBLE
      imageButtonHint?.setOnClickListener(this)
    } else {
      imageButtonHint?.visibility = View.GONE
    }

    findViewById<View>(R.id.imageButtonPasswordHint)?.setOnClickListener(this)

    textViewSubTitle = findViewById(R.id.textViewSubTitle)
    textViewSubTitle?.text = subTitle

    editTextKeyPassword = findViewById(R.id.editTextKeyPassword)
    progressBar = findViewById(R.id.progressBar)

    if (intent.getBooleanExtra(KEY_EXTRA_IS_EXTRA_IMPORT_OPTION, false)) {
      val textViewTitle = findViewById<TextView>(R.id.textViewTitle)
      textViewTitle.setText(R.string.import_private_key)
    }
  }

  private fun initButton(buttonViewId: Int, visibility: Int = View.VISIBLE, text: String?) {
    val button = findViewById<Button>(buttonViewId)
    button?.visibility = visibility
    button?.text = text
    button?.setOnClickListener(this)
  }

  private fun setupCheckPrivateKeysViewModel() {
    checkPrivateKeysViewModel = ViewModelProvider(this).get(CheckPrivateKeysViewModel::class.java)
    val observer = Observer<Result<List<CheckPrivateKeysViewModel.CheckResult>>> {
      it?.let {
        when (it.status) {
          Result.Status.LOADING -> {
            countingIdlingResource.incrementSafely()
            progressBar?.visibility = View.VISIBLE
          }

          else -> {
            progressBar?.visibility = View.GONE
            when (it.status) {
              Result.Status.SUCCESS -> {
                val resultKeys = it.data ?: emptyList()
                val sessionUnlockedKeys = resultKeys
                    .filter { checkResult ->
                      checkResult.nodeKeyDetails.passphrase?.isNotEmpty() == true
                    }.map { checkResult -> checkResult.nodeKeyDetails }
                if (sessionUnlockedKeys.isNotEmpty()) {
                  unlockedKeys.addAll(sessionUnlockedKeys)

                  for (key in sessionUnlockedKeys) {
                    remainingKeys.removeAll(remainingKeys.filter { details ->
                      (details.fingerprint == key.fingerprint)
                    })
                  }

                  if (remainingKeys.isNotEmpty()) {
                    initButton(R.id.buttonSkipRemainingBackups, text = getString(R.string.skip_remaining_backups))
                    editTextKeyPassword?.text = null
                    val mapOfRemainingBackups = prepareMapFromKeyDetailsList(remainingKeys)
                    val remainingKeyCount = getUniqueFingerprintsCount(mapOfRemainingBackups)

                    textViewSubTitle?.text = resources.getQuantityString(
                        R.plurals.not_recovered_all_keys, remainingKeyCount,
                        uniqueKeysCount - remainingKeyCount, uniqueKeysCount, remainingKeyCount)
                  } else {
                    returnUnlockedKeys(Activity.RESULT_OK)
                  }

                } else {
                  if (resultKeys.size == 1) {
                    showInfoSnackbar(rootView, resultKeys.first().e?.message)
                  } else {
                    showInfoSnackbar(rootView, getString(R.string.password_is_incorrect))
                  }
                }
              }

              else -> {
              }
            }
            countingIdlingResource.decrementSafely()
          }
        }
      }
    }

    checkPrivateKeysViewModel.checkPrvKeysLiveData.observe(this, observer)
  }

  private fun checkExistingOfPartiallyEncryptedPrivateKeys() {
    val partiallyEncryptedPrivateKes = originalKeys.filter { it.isPartiallyEncrypted }

    if (partiallyEncryptedPrivateKes.isNotEmpty()) {
      showInfoDialogFragment(dialogMsg = getString(R.string.partially_encrypted_private_key_error_msg))
    }
  }

  private fun returnUnlockedKeys(resultCode: Int) {
    val intent = Intent()
    intent.putExtra(KEY_EXTRA_UNLOCKED_PRIVATE_KEYS, unlockedKeys)
    setResult(resultCode, intent)
    finish()
  }

  /**
   * Remove the already imported keys from the list of found backups.
   */
  private fun removeAlreadyImportedKeys() {
    val fingerprints = getUniqueKeysFingerprints(keyDetailsAndFingerprintsMap!!)
    val keysStorage = KeysStorageImpl.getInstance(this)

    for (fingerprint in fingerprints) {
      if (keysStorage.getPgpPrivateKey(fingerprint) != null) {
        val iterator = keyDetailsAndFingerprintsMap!!.entries.iterator()
        while (iterator.hasNext()) {
          val entry = iterator.next()
          if (fingerprint == entry.value) {
            iterator.remove()
          }
        }
      }
    }
  }

  /**
   * Get a count of unique fingerprints.
   *
   * @param mapOfKeyDetailsAndFingerprints An input map of [NodeKeyDetails].
   * @return A count of unique fingerprints.
   */
  private fun getUniqueFingerprintsCount(mapOfKeyDetailsAndFingerprints: Map<NodeKeyDetails, String>?): Int {
    return HashSet(mapOfKeyDetailsAndFingerprints?.values ?: emptyList()).size
  }

  /**
   * Get a set of unique fingerprints.
   *
   * @param mapOfKeyDetailsAndFingerprints An input map of [NodeKeyDetails].
   * @return A list of unique fingerprints.
   */
  private fun getUniqueKeysFingerprints(mapOfKeyDetailsAndFingerprints: Map<NodeKeyDetails, String>): Set<String> {
    return HashSet(mapOfKeyDetailsAndFingerprints.values)
  }

  /**
   * Generate a map of incoming list of [NodeKeyDetails] objects where values will be a [NodeKeyDetails]
   * fingerprint.
   *
   * @param keys An incoming list of [NodeKeyDetails] objects.
   * @return A generated map.
   */
  private fun prepareMapFromKeyDetailsList(keys: List<NodeKeyDetails>?): MutableMap<NodeKeyDetails, String> {
    val map = HashMap<NodeKeyDetails, String>()

    keys?.let {
      for (keyDetails in it) {
        map[keyDetails] = keyDetails.fingerprint ?: ""
      }
    }
    return map
  }

  companion object {

    const val RESULT_NEGATIVE = 10
    const val RESULT_SKIP_REMAINING_KEYS = 11
    const val RESULT_NO_NEW_KEYS = 12

    val KEY_EXTRA_PRIVATE_KEYS = GeneralUtil.generateUniqueExtraKey(
        "KEY_EXTRA_PRIVATE_KEYS", CheckKeysActivity::class.java)
    val KEY_EXTRA_TYPE = GeneralUtil.generateUniqueExtraKey(
        "KEY_EXTRA_TYPE", CheckKeysActivity::class.java)
    val KEY_EXTRA_SUB_TITLE = GeneralUtil.generateUniqueExtraKey(
        "KEY_EXTRA_SUB_TITLE", CheckKeysActivity::class.java)
    val KEY_EXTRA_POSITIVE_BUTTON_TITLE = GeneralUtil.generateUniqueExtraKey(
        "KEY_EXTRA_POSITIVE_BUTTON_TITLE", CheckKeysActivity::class.java)
    val KEY_EXTRA_NEGATIVE_BUTTON_TITLE =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_NEGATIVE_BUTTON_TITLE", CheckKeysActivity::class.java)
    val KEY_EXTRA_IS_EXTRA_IMPORT_OPTION =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_IS_EXTRA_IMPORT_OPTION", CheckKeysActivity::class.java)
    val KEY_EXTRA_UNLOCKED_PRIVATE_KEYS = GeneralUtil.generateUniqueExtraKey(
        "KEY_EXTRA_UNLOCKED_PRIVATE_KEYS", CheckKeysActivity::class.java)
    val KEY_EXTRA_SKIP_IMPORTED_KEYS = GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_SKIP_IMPORTED_KEYS", CheckKeysActivity::class.java)

    fun newIntent(context: Context, privateKeys: ArrayList<NodeKeyDetails>,
                  type: KeyDetails.Type? = null, subTitle: String? = null, positiveBtnTitle:
                  String? = null, negativeBtnTitle: String? = null,
                  isExtraImportOpt: Boolean = false,
                  skipImportedKeys: Boolean = false): Intent {
      val intent = Intent(context, CheckKeysActivity::class.java)
      intent.putExtra(KEY_EXTRA_PRIVATE_KEYS, privateKeys)
      intent.putExtra(KEY_EXTRA_TYPE, type as Parcelable)
      intent.putExtra(KEY_EXTRA_SUB_TITLE, subTitle)
      intent.putExtra(KEY_EXTRA_POSITIVE_BUTTON_TITLE, positiveBtnTitle)
      intent.putExtra(KEY_EXTRA_NEGATIVE_BUTTON_TITLE, negativeBtnTitle)
      intent.putExtra(KEY_EXTRA_IS_EXTRA_IMPORT_OPTION, isExtraImportOpt)
      intent.putExtra(KEY_EXTRA_SKIP_IMPORTED_KEYS, skipImportedKeys)
      return intent
    }
  }
}
