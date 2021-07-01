/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.retrofit.response.base.ApiResponse
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.api.retrofit.response.model.OrgRules
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.extensions.decrementSafely
import com.flowcrypt.email.extensions.incrementSafely
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.extensions.showInfoDialog
import com.flowcrypt.email.extensions.showTwoWayDialog
import com.flowcrypt.email.extensions.toast
import com.flowcrypt.email.jetpack.viewmodel.DomainOrgRulesViewModel
import com.flowcrypt.email.jetpack.viewmodel.EkmViewModel
import com.flowcrypt.email.jetpack.viewmodel.LoginViewModel
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.security.SecurityUtils
import com.flowcrypt.email.security.model.PgpKeyDetails
import com.flowcrypt.email.service.CheckClipboardToFindKeyService
import com.flowcrypt.email.service.actionqueue.actions.LoadGmailAliasesAction
import com.flowcrypt.email.ui.activity.CheckKeysActivity
import com.flowcrypt.email.ui.activity.CreateOrImportKeyActivity
import com.flowcrypt.email.ui.activity.HtmlViewFromAssetsRawActivity
import com.flowcrypt.email.ui.activity.SignInActivity
import com.flowcrypt.email.ui.activity.fragment.base.BaseSingInFragment
import com.flowcrypt.email.ui.activity.fragment.dialog.TwoWayDialogFragment
import com.flowcrypt.email.ui.activity.settings.FeedbackActivity
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.exception.AccountAlreadyAddedException
import com.flowcrypt.email.util.exception.EkmNotSupportedException
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.flowcrypt.email.util.exception.UnsupportedOrgRulesException
import com.flowcrypt.email.util.google.GoogleApiClientHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.sun.mail.util.MailConnectException
import java.net.SocketTimeoutException
import java.util.*

/**
 * @author Denis Bondarenko
 *         Date: 7/17/20
 *         Time: 12:06 PM
 *         E-mail: DenBond7@gmail.com
 */
class MainSignInFragment : BaseSingInFragment() {
  private lateinit var client: GoogleSignInClient
  private var googleSignInAccount: GoogleSignInAccount? = null
  private var uuid: String = SecurityUtils.generateRandomUUID()
  private var orgRules: OrgRules? = null

  private val loginViewModel: LoginViewModel by viewModels()
  private val domainOrgRulesViewModel: DomainOrgRulesViewModel by viewModels()
  private val ekmViewModel: EkmViewModel by viewModels()

  override val progressView: View?
    get() = view?.findViewById(R.id.progress)
  override val contentView: View?
    get() = view?.findViewById(R.id.layoutContent)
  override val statusView: View?
    get() = view?.findViewById(R.id.status)

  override val contentResourceId: Int = R.layout.fragment_main_sign_in

  override fun onAttach(context: Context) {
    super.onAttach(context)
    client = GoogleSignIn.getClient(context, GoogleApiClientHelper.generateGoogleSignInOptions())
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initViews(view)

    subscribeToCheckAccountSettings()
    subscribeToAuthorizeAndSearchBackups()

    initAddNewAccountLiveData()
    initEnterpriseViewModels()
    initSavePrivateKeysLiveData()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_RESOLVE_SIGN_IN_ERROR -> {
        if (resultCode == Activity.RESULT_OK) {
          signInWithGmail()
        }
      }

      REQUEST_CODE_SIGN_IN -> {
        handleSignInResult(resultCode, GoogleSignIn.getSignedInAccountFromIntent(data))
      }

      REQUEST_CODE_CREATE_OR_IMPORT_KEY -> when (resultCode) {
        Activity.RESULT_OK -> if (existedAccounts.isEmpty()) runEmailManagerActivity() else returnResultOk()

        Activity.RESULT_CANCELED, CreateOrImportKeyActivity.RESULT_CODE_USE_ANOTHER_ACCOUNT -> {
          this.googleSignInAccount = null
          showContent()
        }

        CreateOrImportKeyActivity.RESULT_CODE_HANDLE_RESOLVED_KEYS -> {
          handleResultFromCheckKeysActivity(resultCode, data)
        }
      }

      REQUEST_CODE_CHECK_PRIVATE_KEYS_FROM_GMAIL -> {
        handleResultFromCheckKeysActivity(resultCode, data)
      }

      REQUEST_CODE_RETRY_LOGIN -> {
        when (resultCode) {
          TwoWayDialogFragment.RESULT_OK -> {
            val account = googleSignInAccount?.account?.name ?: return
            val idToken = googleSignInAccount?.idToken ?: return
            orgRules = null
            loginViewModel.login(account, uuid, idToken)
          }
        }
      }

      REQUEST_CODE_RETRY_GET_DOMAIN_ORG_RULES -> {
        when (resultCode) {
          TwoWayDialogFragment.RESULT_OK -> {
            val account = googleSignInAccount?.account?.name ?: return
            domainOrgRulesViewModel.fetchOrgRules(account, uuid)
          }
        }
      }

      REQUEST_CODE_RETRY_FETCH_PRV_KEYS_VIA_EKM -> {
        when (resultCode) {
          TwoWayDialogFragment.RESULT_OK -> {
            val idToken = googleSignInAccount?.idToken ?: return
            orgRules?.let { ekmViewModel.fetchPrvKeys(it, idToken) }
          }
        }
      }

      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  override fun getTempAccount(): AccountEntity? {
    return googleSignInAccount?.let { AccountEntity(it, uuid, orgRules?.flags ?: emptyList()) }
  }

  override fun returnResultOk() {
    getTempAccount()?.let {
      roomBasicViewModel.addActionToQueue(LoadGmailAliasesAction(email = it.email))

      val intent = Intent()
      intent.putExtra(SignInActivity.KEY_EXTRA_NEW_ACCOUNT, it)
      activity?.setResult(Activity.RESULT_OK, intent)
      activity?.finish()
    }
  }

  private fun initViews(view: View) {
    view.findViewById<View>(R.id.buttonSignInWithGmail)?.setOnClickListener {
      importCandidates.clear()
      signInWithGmail()
    }

    view.findViewById<View>(R.id.buttonOtherEmailProvider)?.setOnClickListener {
      navController?.navigate(
        MainSignInFragmentDirections.actionMainSignInFragmentToAddOtherAccountFragment()
      )
    }

    view.findViewById<View>(R.id.buttonPrivacy)?.setOnClickListener {
      GeneralUtil.openCustomTab(requireContext(), Constants.FLOWCRYPT_PRIVACY_URL)
    }

    view.findViewById<View>(R.id.buttonTerms)?.setOnClickListener {
      GeneralUtil.openCustomTab(requireContext(), Constants.FLOWCRYPT_TERMS_URL)
    }

    view.findViewById<View>(R.id.buttonSecurity)?.setOnClickListener {
      startActivity(
        HtmlViewFromAssetsRawActivity.newIntent(
          requireContext(), getString(R.string.security),
          "html/security.htm"
        )
      )
    }

    view.findViewById<View>(R.id.buttonHelp)?.setOnClickListener {
      FeedbackActivity.show(requireActivity())
    }
  }

  private fun signInWithGmail() {
    googleSignInAccount = null

    if (GeneralUtil.isConnected(activity)) {
      client.signOut()
      startActivityForResult(client.signInIntent, REQUEST_CODE_SIGN_IN)
    } else {
      showInfoSnackbar(msgText = activity?.getString(R.string.internet_connection_is_not_available))
    }
  }

  private fun handleSignInResult(resultCode: Int, task: Task<GoogleSignInAccount>) {
    try {
      if (task.isSuccessful) {
        googleSignInAccount = task.getResult(ApiException::class.java)

        val account = googleSignInAccount?.account?.name ?: return
        val idToken = googleSignInAccount?.idToken ?: return
        uuid = SecurityUtils.generateRandomUUID()

        val publicEmailDomains = arrayOf(
          JavaEmailConstants.EMAIL_PROVIDER_GMAIL,
          JavaEmailConstants.EMAIL_PROVIDER_GOOGLEMAIL
        )

        if (EmailUtil.getDomain(account).toLowerCase(Locale.US) in publicEmailDomains) {
          onSignSuccess(googleSignInAccount)
        } else {
          orgRules = null
          loginViewModel.login(account, uuid, idToken)
        }
      } else {
        val error = task.exception

        if (error is ApiException) {
          throw error
        }

        showInfoSnackbar(
          msgText = error?.message ?: error?.javaClass?.simpleName
          ?: getString(R.string.unknown_error)
        )
      }
    } catch (e: ApiException) {
      val msg = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
      if (resultCode == Activity.RESULT_OK) {
        showInfoSnackbar(msgText = msg)
      } else {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun onSignSuccess(googleSignInAccount: GoogleSignInAccount?) {
    val existedAccount = existedAccounts.firstOrNull {
      it.email.equals(googleSignInAccount?.email, ignoreCase = true)
    }

    if (existedAccount == null) {
      getTempAccount()?.let {
        if (orgRules?.flags?.firstOrNull { rule -> rule == OrgRules.DomainRule.NO_PRV_BACKUP } != null) {
          requireContext().startService(
            Intent(requireContext(), CheckClipboardToFindKeyService::class.java)
          )
          val intent = CreateOrImportKeyActivity.newIntent(requireContext(), it, true)
          startActivityForResult(intent, REQUEST_CODE_CREATE_OR_IMPORT_KEY)
        } else {
          navController?.navigate(
            MainSignInFragmentDirections.actionMainSignInFragmentToAuthorizeAndSearchBackupsFragment(
              it
            )
          )
        }
      }
    } else {
      showContent()
      showInfoSnackbar(
        msgText = getString(
          R.string.template_email_already_added,
          existedAccount.email
        ), duration = Snackbar.LENGTH_LONG
      )
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun subscribeToCheckAccountSettings() {
    setFragmentResultListener(AuthorizeAndSearchBackupsFragment.REQUEST_KEY_CHECK_ACCOUNT_SETTINGS) { _, bundle ->
      val result: Result<*>? =
        bundle.getSerializable(AuthorizeAndSearchBackupsFragment.KEY_CHECK_ACCOUNT_SETTINGS_RESULT) as? Result<*>

      if (result != null) {
        when (result.status) {
          Result.Status.ERROR, Result.Status.EXCEPTION -> {
            showContent()
            val exception = result.exception ?: return@setFragmentResultListener
            val original = result.exception.cause
            val msg: String? = if (exception.message.isNullOrEmpty()) {
              exception.javaClass.simpleName
            } else exception.message
            var title: String? = null

            if (original != null) {
              if (original is MailConnectException || original is SocketTimeoutException) {
                title = getString(R.string.network_error)
              }
            } else if (exception is AccountAlreadyAddedException) {
              showInfoSnackbar(view, exception.message, Snackbar.LENGTH_LONG)
              return@setFragmentResultListener
            }

            val faqUrl = "https://support.google.com/mail/answer/75725?hl=" + GeneralUtil
              .getLocaleLanguageCode(requireContext())
            val dialogMsg = msg + getString(R.string.provider_faq, faqUrl)

            showInfoDialog(
              dialogTitle = title,
              dialogMsg = dialogMsg,
              useLinkify = true
            )
          }

          else -> {

          }
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun subscribeToAuthorizeAndSearchBackups() {
    setFragmentResultListener(AuthorizeAndSearchBackupsFragment.REQUEST_KEY_SEARCH_BACKUPS) { _, bundle ->
      val result: Result<*>? =
        bundle.getSerializable(AuthorizeAndSearchBackupsFragment.KEY_PRIVATE_KEY_BACKUPS_RESULT) as? Result<*>

      if (result != null) {
        when (result.status) {
          Result.Status.SUCCESS -> {
            onFetchKeysCompleted(result.data as ArrayList<PgpKeyDetails>?)
          }

          Result.Status.ERROR, Result.Status.EXCEPTION -> {
            showContent()

            if (result.exception is UserRecoverableAuthIOException) {
              startActivityForResult(result.exception.intent, REQUEST_CODE_RESOLVE_SIGN_IN_ERROR)
            } else {
              showInfoSnackbar(
                msgText =
                result.exception?.message ?: result.exception?.javaClass?.simpleName
                ?: getString(R.string.unknown_error)
              )
            }
          }

          else -> {

          }
        }
      }
    }
  }

  private fun onFetchKeysCompleted(keyDetailsList: ArrayList<PgpKeyDetails>?) {
    if (keyDetailsList.isNullOrEmpty()) {
      getTempAccount()?.let {
        requireContext().startService(
          Intent(
            requireContext(),
            CheckClipboardToFindKeyService::class.java
          )
        )
        val intent = CreateOrImportKeyActivity.newIntent(requireContext(), it, true)
        startActivityForResult(intent, REQUEST_CODE_CREATE_OR_IMPORT_KEY)
      }
    } else {
      val subTitle = resources.getQuantityString(
        R.plurals.found_backup_of_your_account_key,
        keyDetailsList.size,
        keyDetailsList.size
      )
      val positiveBtnTitle = getString(R.string.continue_)
      val negativeBtnTitle = getString(R.string.use_another_account)
      val intent = CheckKeysActivity.newIntent(
        context = requireContext(),
        privateKeys = keyDetailsList,
        sourceType = KeyImportDetails.SourceType.EMAIL,
        subTitle = subTitle,
        positiveBtnTitle = positiveBtnTitle,
        negativeBtnTitle = negativeBtnTitle
      )
      startActivityForResult(intent, REQUEST_CODE_CHECK_PRIVATE_KEYS_FROM_GMAIL)
    }
  }

  private fun initEnterpriseViewModels() {
    initLoginViewModel()
    initDomainOrgRulesViewModel()
    initEkmViewModel()
  }

  private fun initLoginViewModel() {
    loginViewModel.loginLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          baseActivity.countingIdlingResource.incrementSafely()
          showProgress(progressMsg = it.progressMsg)
        }

        Result.Status.SUCCESS -> {
          if (it.data?.isVerified == true) {
            val account = googleSignInAccount?.account?.name
            if (account != null) {
              domainOrgRulesViewModel.fetchOrgRules(account, uuid)
            } else {
              showContent()
              askUserToReLogin()
            }
          } else {
            showInfoDialog(
              dialogTitle = "",
              dialogMsg = getString(R.string.user_not_verified),
              isCancelable = true
            )
          }

          loginViewModel.loginLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          showContent()
          showDialogWithRetryButton(it, REQUEST_CODE_RETRY_LOGIN)
          loginViewModel.loginLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }
      }
    })
  }

  private fun initDomainOrgRulesViewModel() {
    domainOrgRulesViewModel.domainOrgRulesLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          baseActivity.countingIdlingResource.incrementSafely()
          showProgress(progressMsg = it.progressMsg)
        }

        Result.Status.SUCCESS -> {
          val idToken = googleSignInAccount?.idToken
          if (it.data?.orgRules != null && idToken != null) {
            orgRules = it.data.orgRules
            ekmViewModel.fetchPrvKeys(it.data.orgRules, idToken)
          } else {
            showContent()
            askUserToReLogin()
          }
          domainOrgRulesViewModel.domainOrgRulesLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          showContent()
          showDialogWithRetryButton(it, REQUEST_CODE_RETRY_GET_DOMAIN_ORG_RULES)
          domainOrgRulesViewModel.domainOrgRulesLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }
      }
    })
  }

  private fun initEkmViewModel() {
    ekmViewModel.ekmLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          baseActivity.countingIdlingResource.incrementSafely()
          showProgress(progressMsg = it.progressMsg)
        }

        Result.Status.SUCCESS -> {
          showContent()
          toast("Debug: received = ${it.data?.privateKeys?.size} key(s)")
          //encrypt the received keys with the pass phrase.
          ekmViewModel.ekmLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          showContent()
          when (it.exception) {
            is EkmNotSupportedException -> {
              onSignSuccess(googleSignInAccount)
            }

            is UnsupportedOrgRulesException -> {
              showInfoDialog(
                dialogTitle = "",
                dialogMsg = getString(
                  R.string.combination_of_org_rules_is_not_supported,
                  it.exception.message
                ),
                isCancelable = true
              )
            }

            else -> {
              showDialogWithRetryButton(it, REQUEST_CODE_RETRY_FETCH_PRV_KEYS_VIA_EKM)
            }
          }
          ekmViewModel.ekmLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }
      }
    })
  }

  private fun showDialogWithRetryButton(it: Result<ApiResponse>, resultCode: Int) {
    val errorMsg = it.data?.apiError?.msg
      ?: it.exception?.message
      ?: getString(R.string.unknown_error)
    showTwoWayDialog(
      requestCode = resultCode,
      dialogTitle = "",
      dialogMsg = errorMsg,
      positiveButtonTitle = getString(R.string.retry),
      negativeButtonTitle = getString(R.string.cancel),
      isCancelable = true
    )
  }

  private fun askUserToReLogin() {
    showInfoDialog(
      dialogTitle = "",
      dialogMsg = getString(R.string.please_login_again_to_continue),
      isCancelable = true
    )
  }

  private fun handleResultFromCheckKeysActivity(resultCode: Int, data: Intent?) {
    when (resultCode) {
      Activity.RESULT_OK, CheckKeysActivity.RESULT_SKIP_REMAINING_KEYS -> {
        val keys: List<PgpKeyDetails>? = data?.getParcelableArrayListExtra(
          CheckKeysActivity.KEY_EXTRA_UNLOCKED_PRIVATE_KEYS
        )

        if (keys.isNullOrEmpty()) {
          showInfoSnackbar(msgText = getString(R.string.error_no_keys))
        } else {
          importCandidates.clear()
          importCandidates.addAll(keys)

          if (getTempAccount() == null) {
            ExceptionUtil.handleError(NullPointerException("GoogleSignInAccount is null!"))
            Toast.makeText(
              requireContext(),
              R.string.error_occurred_try_again_later,
              Toast.LENGTH_SHORT
            ).show()
            return
          } else {
            getTempAccount()?.let {
              accountViewModel.addNewAccount(it)
            }
          }
        }
      }

      CheckKeysActivity.RESULT_NO_NEW_KEYS -> {
        Toast.makeText(
          requireContext(),
          getString(R.string.key_already_imported_finishing_setup),
          Toast.LENGTH_SHORT
        ).show()
        if (existedAccounts.isEmpty()) runEmailManagerActivity() else returnResultOk()
      }

      Activity.RESULT_CANCELED, CheckKeysActivity.RESULT_NEGATIVE -> {
        this.googleSignInAccount = null
        showContent()
      }
    }
  }

  companion object {
    private const val REQUEST_CODE_SIGN_IN = 100
    private const val REQUEST_CODE_RESOLVE_SIGN_IN_ERROR = 101
    private const val REQUEST_CODE_CREATE_OR_IMPORT_KEY = 102
    private const val REQUEST_CODE_CHECK_PRIVATE_KEYS_FROM_GMAIL = 103
    private const val REQUEST_CODE_RETRY_LOGIN = 104
    private const val REQUEST_CODE_RETRY_GET_DOMAIN_ORG_RULES = 105
    private const val REQUEST_CODE_RETRY_FETCH_PRV_KEYS_VIA_EKM = 106
  }
}
