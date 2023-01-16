/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.api.retrofit.ApiHelper
import com.flowcrypt.email.api.retrofit.response.api.ClientConfigurationResponse
import com.flowcrypt.email.api.retrofit.response.api.EkmPrivateKeysResponse
import com.flowcrypt.email.api.retrofit.response.api.FesServerResponse
import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.retrofit.response.model.ClientConfiguration
import com.flowcrypt.email.api.retrofit.response.model.Key
import com.flowcrypt.email.junit.annotations.NotReadyForCI
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.MainActivity
import com.flowcrypt.email.ui.base.BaseSignTest
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import com.flowcrypt.email.util.exception.ApiException
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer
import com.google.api.client.json.Json
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.gson.Gson
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.net.HttpURLConnection

/**
 * @author Denis Bondarenko
 *         Date: 10/31/19
 *         Time: 3:08 PM
 *         E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class MainSignInFragmentEnterpriseFlowTest : BaseSignTest() {
  override val useIntents: Boolean = true
  override val activityScenarioRule = activityScenarioRule<MainActivity>(
    TestGeneralUtil.genIntentForNavigationComponent(
      destinationId = R.id.mainSignInFragment
    )
  )

  private val testNameRule = TestName()
  private val mockWebServerRule =
    FlowCryptMockWebServerRule(TestConstants.MOCK_WEB_SERVER_PORT, object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val gson = ApiHelper.getInstance(getTargetContext()).gson

        when {
          request.path.equals("/api/") -> {
            return handleFesAvailabilityAPI(gson)
          }

          request.path.equals("/api/v1/client-configuration?domain=localhost:1212") -> {
            return handleClientConfigurationAPI(gson)
          }

          request.path?.startsWith("/ekm") == true -> {
            handleEkmAPI(request, gson)?.let { return it }
          }

          request.path.equals("/account/get") -> {
            val account = extractEmailFromRecordedRequest(request)
            return handleGetDomainRulesAPI(account, gson)
          }

          request.requestUrl?.encodedPath == "/gmail/v1/users/me/messages"
              && request.requestUrl?.queryParameterNames?.contains("q") == true -> {
            val q = requireNotNull(request.requestUrl?.queryParameter("q"))
            return when {
              q.startsWith("from:${EMAIL_FES_ENFORCE_ATTESTER_SUBMIT}") -> MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(
                  ListMessagesResponse().apply {
                    factory = GsonFactory.getDefaultInstance()
                    messages = emptyList()
                    resultSizeEstimate = 0
                  }.toString()
                )

              q.startsWith("from:${EMAIL_GMAIL}") ->
                prepareMockResponseForPublicDomains(EMAIL_GMAIL)

              q.startsWith("from:${EMAIL_GOOGLEMAIL}") ->
                prepareMockResponseForPublicDomains(EMAIL_GOOGLEMAIL)

              else -> MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
            }
          }
        }

        return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
      }
    })

  private fun prepareMockResponseForPublicDomains(string: String) =
    MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
      .setHeader("Content-Type", Json.MEDIA_TYPE)
      .setBody(GoogleJsonErrorContainer().apply {
        factory = GsonFactory.getDefaultInstance()
        error = GoogleJsonError().apply {
          code = HttpURLConnection.HTTP_NOT_FOUND
          message = string
          errors = listOf(GoogleJsonError.ErrorInfo().apply {
            message = string
            domain = "local"
            reason = "notFound"
          })
        }
      }.toString())

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(testNameRule)
    .around(mockWebServerRule)
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  @Before
  fun waitWhileToastWillBeDismissed() {
    Thread.sleep(1000)
  }

  @Test
  fun testErrorGetDomainRules() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_DOMAIN_CLIENT_CONFIGURATION_ERROR))
    isDialogWithTextDisplayed(decorView, CLIENT_CONFIGURATION_ERROR_RESPONSE.apiError?.msg!!)
    onView(withText(R.string.retry))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testClientConfigurationCombinationNotSupportedForMustAutogenPassPhraseQuietlyExisted() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(
        EMAIL_MUST_AUTOGEN_PASS_PHRASE_QUIETLY_EXISTED
      )
    )
    isDialogWithTextDisplayed(
      decorView = decorView,
      message = getResString(
        R.string.combination_of_client_configuration_is_not_supported,
        ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN.name +
            " + " + ClientConfiguration.ConfigurationProperty.PASS_PHRASE_QUIET_AUTOGEN
      )
    )
  }

  @Test
  fun testClientConfigurationCombinationNotSupportedForForbidStoringPassPhraseMissing() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(
        EMAIL_FORBID_STORING_PASS_PHRASE_MISSING
      )
    )
    isDialogWithTextDisplayed(
      decorView = decorView,
      message = getResString(
        R.string.combination_of_client_configuration_is_not_supported,
        ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN.name + " + missing " +
            ClientConfiguration.ConfigurationProperty.FORBID_STORING_PASS_PHRASE
      )
    )
  }

  @Test
  fun testClientConfigurationCombinationNotSupportedForMustSubmitToAttesterExisted() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(
        EMAIL_MUST_SUBMIT_TO_ATTESTER_EXISTED
      )
    )
    isDialogWithTextDisplayed(
      decorView = decorView,
      message = getResString(
        R.string.combination_of_client_configuration_is_not_supported,
        ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN.name +
            " + " + ClientConfiguration.ConfigurationProperty.ENFORCE_ATTESTER_SUBMIT
      )
    )
  }

  @Test
  fun testClientConfigurationCombinationNotSupportedForForbidCreatingPrivateKeyMissing() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(
        EMAIL_FORBID_CREATING_PRIVATE_KEY_MISSING
      )
    )
    isDialogWithTextDisplayed(
      decorView = decorView,
      message = getResString(
        R.string.combination_of_client_configuration_is_not_supported,
        ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN.name + " + missing "
            + ClientConfiguration.ConfigurationProperty.NO_PRV_CREATE
      )
    )
  }

  @Test
  fun testErrorGetPrvKeysViaEkm() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_GET_KEYS_VIA_EKM_ERROR))
    isDialogWithTextDisplayed(decorView, EKM_ERROR)
    onView(withText(R.string.retry))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testErrorGetPrvKeysViaEkmEmptyList() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_GET_KEYS_VIA_EKM_EMPTY_LIST))
    isDialogWithTextDisplayed(
      decorView,
      "IllegalStateException:" + getResString(R.string.no_prv_keys_ask_admin)
    )
    onView(withText(R.string.retry))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testErrorGetPrvKeysViaEkmNotFullyDecryptedKey() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(
        EMAIL_GET_KEYS_VIA_EKM_NOT_FULLY_DECRYPTED
      )
    )
    val pgpKeyDetails = PrivateKeysManager.getPgpKeyDetailsFromAssets(
      "pgp/keys/user_with_not_fully_decrypted_prv_key@flowcrypt.test_prv_default.asc"
    )
    isDialogWithTextDisplayed(
      decorView, "IllegalStateException:" + getResString(
        R.string.found_not_fully_decrypted_key_ask_admin,
        pgpKeyDetails.fingerprint
      )
    )
    onView(withText(R.string.retry))
      .check(matches(isDisplayed()))
  }

  @Test
  @NotReadyForCI
  fun testNoPrvCreateRule() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_WITH_NO_PRV_CREATE_RULE))
    onView(withId(R.id.buttonCreateNewKey))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testFesAvailabilityServerDownNoConnection() {
    try {
      changeConnectionState(false)
      setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_NO_CONNECTION))
      isDialogWithTextDisplayed(
        decorView = decorView,
        message = getResString(R.string.no_connection_or_server_is_not_reachable)
      )
    } finally {
      changeConnectionState(true)
    }
  }

  @Test
  fun testFesAvailabilityServerUpRequestTimeOut() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_REQUEST_TIME_OUT))
    onView(withText(R.string.set_pass_phrase))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFesServerUpHasConnectionHttpCode404() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_HTTP_404))
    onView(withText(R.string.set_pass_phrase))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFesServerUpHasConnectionHttpCodeNotSuccess() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_HTTP_NOT_404_NOT_SUCCESS))
    onView(withText(R.string.set_pass_phrase))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFesServerNotEnterpriseServer() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(email = EMAIL_FES_NOT_ALLOWED_SERVER))

    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_NOT_FOUND,
          msg = ""
        )
      ).message!!
    )
  }

  @Test
  fun testFesServerExternalServiceAlias() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_SERVER_EXTERNAL_SERVICE))
    //we simulate error for https://fes.$domain/api/v1/client-configuration?domain=$domain
    //to check that external-service was accepted and we called getClientConfigurationFromFes()

    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_NOT_ACCEPTABLE,
          msg = ""
        )
      ).message!!
    )
  }

  @Test
  fun testFesServerEnterpriseServerAlias() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_SERVER_ENTERPRISE_SERVER))
    //we simulate error for https://fes.$domain/api/v1/client-configuration?domain=$domain
    //to check that external-service was accepted and we called getClientConfigurationFromFes()
    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_CONFLICT,
          msg = ""
        )
      ).message!!
    )
  }

  @Test
  fun testFesServerUpGetClientConfigurationSuccess() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(EMAIL_FES_CLIENT_CONFIGURATION_SUCCESS)
    )
    onView(withText(R.string.set_pass_phrase))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFesServerUpGetClientConfigurationFailed() {
    setupAndClickSignInButton(
      genMockGoogleSignInAccountJson(EMAIL_FES_CLIENT_CONFIGURATION_FAILED)
    )
    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_FORBIDDEN,
          msg = ""
        )
      ).message!!
    )
  }

  @Test
  fun testFailAttesterSubmit() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_ENFORCE_ATTESTER_SUBMIT))
    val passphrase = "unconventional blueberry unlike any other"
    onView(withId(R.id.buttonCreateNewKey))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.editTextKeyPassword))
      .check(matches(isDisplayed()))
      .perform(replaceText(passphrase), closeSoftKeyboard())
    onView(withId(R.id.buttonSetPassPhrase))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.editTextKeyPasswordSecond))
      .check(matches(isDisplayed()))
      .perform(replaceText(passphrase), closeSoftKeyboard())

    onView(withId(R.id.buttonConfirmPassPhrases))
      .check(matches(isDisplayed()))
      .perform(click())

    isDialogWithTextDisplayed(
      decorView,
      ApiException(ApiError(code = HttpURLConnection.HTTP_NOT_FOUND, msg = "")).message!!
    )
  }

  @Test
  fun testFlowForPublicEmailDomainsGmail() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_GMAIL))
    checkIsSnackBarDisplayed(EMAIL_GMAIL)
  }

  @Test
  fun testFlowForPublicEmailDomainsGoogleMail() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_GOOGLEMAIL))
    checkIsSnackBarDisplayed(EMAIL_GOOGLEMAIL)
  }

  private fun handleFesAvailabilityAPI(gson: Gson): MockResponse {
    return if (testNameRule.methodName == "testFesAvailabilityServerUpRequestTimeOut") {
      val delayInMilliseconds = 6000
      val initialTimeMillis = System.currentTimeMillis()
      while (System.currentTimeMillis() - initialTimeMillis <= delayInMilliseconds) {
        Thread.sleep(100)
      }
      MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
    } else when (testNameRule.methodName) {
      "testFesServerUpHasConnectionHttpCode404", "testFailAttesterSubmit" -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
      }

      "testFesServerUpHasConnectionHttpCodeNotSuccess" -> {
        MockResponse().setResponseCode(500)
      }

      "testFesServerNotEnterpriseServer" -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
          .setBody(gson.toJson(FES_SUCCESS_RESPONSE.copy(service = "hello")))
      }

      "testFesServerExternalServiceAlias" -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
          .setBody(gson.toJson(FES_SUCCESS_RESPONSE.copy(service = "external-service")))
      }

      "testFesServerEnterpriseServerAlias" -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
          .setBody(gson.toJson(FES_SUCCESS_RESPONSE.copy(service = "enterprise-server")))
      }

      else -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
          .setBody(gson.toJson(FES_SUCCESS_RESPONSE))
      }
    }
  }

  private fun handleClientConfigurationAPI(gson: Gson): MockResponse {
    val responseCode = when (testNameRule.methodName) {
      "testFesServerUpGetClientConfigurationFailed" -> HttpURLConnection.HTTP_FORBIDDEN
      "testFesServerExternalServiceAlias" -> HttpURLConnection.HTTP_NOT_ACCEPTABLE
      "testFesServerEnterpriseServerAlias" -> HttpURLConnection.HTTP_CONFLICT
      else -> HttpURLConnection.HTTP_OK
    }

    val body = when (testNameRule.methodName) {
      "testFesServerUpGetClientConfigurationFailed",
      "testFesServerExternalServiceAlias",
      "testFesServerEnterpriseServerAlias" -> null
      else -> gson.toJson(
        ClientConfigurationResponse(
          clientConfiguration = ClientConfiguration(
            flags = ACCEPTED_FLAGS,
            keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
          )
        )
      )
    }

    return MockResponse().setResponseCode(responseCode).apply {
      body?.let { setBody(it) }
    }
  }

  private fun handleEkmAPI(request: RecordedRequest, gson: Gson): MockResponse? {
    if (request.path.equals("/ekm/error/v1/keys/private")) {
      return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
        .setBody(gson.toJson(EKM_ERROR_RESPONSE))
    }

    if (request.path.equals("/ekm/empty/v1/keys/private")) {
      return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
        .setBody(gson.toJson(EkmPrivateKeysResponse(privateKeys = emptyList())))
    }

    if (request.path.equals("/ekm/v1/keys/private")) {
      return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
        .setBody(gson.toJson(EKM_FES_RESPONSE))
    }

    if (request.path.equals("/ekm/not_fully_decrypted_key/v1/keys/private")) {
      return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
        .setBody(
          gson.toJson(
            EkmPrivateKeysResponse(
              privateKeys = listOf(
                Key(
                  TestGeneralUtil.readFileFromAssetsAsString(
                    "pgp/keys/user_with_not_fully_decrypted_prv_key@flowcrypt.test_prv_default.asc"
                  )
                )
              )
            )
          )
        )
    }

    return null
  }

  private fun handleGetDomainRulesAPI(account: String?, gson: Gson): MockResponse {
    when (account) {
      EMAIL_DOMAIN_CLIENT_CONFIGURATION_ERROR -> return MockResponse().setResponseCode(
        HttpURLConnection.HTTP_OK
      )
        .setBody(gson.toJson(CLIENT_CONFIGURATION_ERROR_RESPONSE))

      EMAIL_WITH_NO_PRV_CREATE_RULE -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = listOf(
            ClientConfiguration.ConfigurationProperty.NO_PRV_CREATE,
            ClientConfiguration.ConfigurationProperty.NO_PRV_BACKUP
          )
        )
      )

      EMAIL_MUST_AUTOGEN_PASS_PHRASE_QUIETLY_EXISTED -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = listOf(
            ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN,
            ClientConfiguration.ConfigurationProperty.PASS_PHRASE_QUIET_AUTOGEN
          ),
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
        )
      )

      EMAIL_FORBID_STORING_PASS_PHRASE_MISSING -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = listOf(
            ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN
          ),
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
        )
      )

      EMAIL_MUST_SUBMIT_TO_ATTESTER_EXISTED -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = listOf(
            ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN,
            ClientConfiguration.ConfigurationProperty.FORBID_STORING_PASS_PHRASE,
            ClientConfiguration.ConfigurationProperty.ENFORCE_ATTESTER_SUBMIT
          ),
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
        )
      )

      EMAIL_FORBID_CREATING_PRIVATE_KEY_MISSING -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = listOf(
            ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN,
            ClientConfiguration.ConfigurationProperty.FORBID_STORING_PASS_PHRASE
          ),
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
        )
      )

      EMAIL_GET_KEYS_VIA_EKM_ERROR -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = ACCEPTED_FLAGS,
          keyManagerUrl = EMAIL_EKM_URL_ERROR,
        )
      )

      EMAIL_GET_KEYS_VIA_EKM_EMPTY_LIST -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = ACCEPTED_FLAGS,
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS_EMPTY_LIST,
        )
      )

      EMAIL_GET_KEYS_VIA_EKM_NOT_FULLY_DECRYPTED -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = ACCEPTED_FLAGS,
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS_NOT_FULLY_DECRYPTED_KEY,
        )
      )

      EMAIL_FES_REQUEST_TIME_OUT,
      EMAIL_FES_HTTP_404,
      EMAIL_FES_HTTP_NOT_404_NOT_SUCCESS -> return successMockResponseForClientConfiguration(
        gson = gson,
        clientConfiguration = ClientConfiguration(
          flags = ACCEPTED_FLAGS,
          keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
        )
      )

      EMAIL_FES_NOT_ALLOWED_SERVER -> return MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)

      EMAIL_FES_ENFORCE_ATTESTER_SUBMIT -> {
        return successMockResponseForClientConfiguration(
          gson = gson,
          clientConfiguration = ClientConfiguration(
            flags = listOf(
              ClientConfiguration.ConfigurationProperty.ENFORCE_ATTESTER_SUBMIT
            ),
            keyManagerUrl = EMAIL_EKM_URL_SUCCESS,
          )
        )
      }

      else -> return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
    }
  }

  private fun successMockResponseForClientConfiguration(
    gson: Gson,
    clientConfiguration: ClientConfiguration
  ) =
    MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(
        gson.toJson(
          ClientConfigurationResponse(
            clientConfiguration = clientConfiguration
          )
        )
      )

  companion object {
    private const val EMAIL_EKM_URL_SUCCESS = "https://localhost:1212/ekm/"
    private const val EMAIL_EKM_URL_SUCCESS_EMPTY_LIST = "https://localhost:1212/ekm/empty/"
    private const val EMAIL_EKM_URL_SUCCESS_NOT_FULLY_DECRYPTED_KEY =
      "https://localhost:1212/ekm/not_fully_decrypted_key/"
    private const val EMAIL_EKM_URL_ERROR = "https://localhost:1212/ekm/error/"
    private const val EMAIL_WITH_NO_PRV_CREATE_RULE = "no_prv_create@flowcrypt.test"
    private const val EMAIL_DOMAIN_CLIENT_CONFIGURATION_ERROR =
      "client_configuration_error@flowcrypt.test"
    private const val EMAIL_MUST_AUTOGEN_PASS_PHRASE_QUIETLY_EXISTED =
      "must_autogen_pass_phrase_quietly_existed@flowcrypt.test"
    private const val EMAIL_FORBID_STORING_PASS_PHRASE_MISSING =
      "forbid_storing_pass_phrase_missing@flowcrypt.test"
    private const val EMAIL_MUST_SUBMIT_TO_ATTESTER_EXISTED =
      "must_submit_to_attester_existed@flowcrypt.test"
    private const val EMAIL_FORBID_CREATING_PRIVATE_KEY_MISSING =
      "forbid_creating_private_key_missing@flowcrypt.test"
    private const val EMAIL_GET_KEYS_VIA_EKM_ERROR = "keys_via_ekm_error@flowcrypt.test"
    private const val EMAIL_GET_KEYS_VIA_EKM_EMPTY_LIST = "keys_via_ekm_empty_list@flowcrypt.test"
    private const val EMAIL_GET_KEYS_VIA_EKM_NOT_FULLY_DECRYPTED =
      "user_with_not_fully_decrypted_prv_key@flowcrypt.test"
    private const val EMAIL_FES_NO_CONNECTION = "fes_request_timeout@flowcrypt.test"
    private const val EMAIL_FES_REQUEST_TIME_OUT = "fes_request_timeout@localhost:1212"
    private const val EMAIL_FES_HTTP_404 = "fes_404@localhost:1212"
    private const val EMAIL_FES_HTTP_NOT_404_NOT_SUCCESS = "fes_not404_not_success@localhost:1212"
    private const val EMAIL_FES_NOT_ALLOWED_SERVER = "fes_not_allowed_server@localhost:1212"
    private const val EMAIL_FES_SERVER_EXTERNAL_SERVICE =
      "fes_server_external_service@localhost:1212"
    private const val EMAIL_FES_SERVER_ENTERPRISE_SERVER =
      "fes_server_enterprise_server@localhost:1212"
    private const val EMAIL_FES_CLIENT_CONFIGURATION_SUCCESS =
      "fes_client_configuration_success@localhost:1212"
    private const val EMAIL_FES_CLIENT_CONFIGURATION_FAILED =
      "fes_client_configuration_failed@localhost:1212"
    private const val EMAIL_FES_ENFORCE_ATTESTER_SUBMIT =
      "enforce_attester_submit@localhost:1212"

    private const val EMAIL_GMAIL = "gmail@gmail.com"
    private const val EMAIL_GOOGLEMAIL = "googlemail@googlemail.com"

    private val ACCEPTED_FLAGS = listOf(
      ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN,
      ClientConfiguration.ConfigurationProperty.FORBID_STORING_PASS_PHRASE,
      ClientConfiguration.ConfigurationProperty.NO_PRV_CREATE
    )

    private val CLIENT_CONFIGURATION_ERROR_RESPONSE = ClientConfigurationResponse(
      ApiError(
        HttpURLConnection.HTTP_UNAUTHORIZED,
        "Not logged in or unknown account", "auth"
      ), null
    )

    private const val EKM_ERROR = "some error"
    private val EKM_ERROR_RESPONSE = EkmPrivateKeysResponse(
      code = HttpURLConnection.HTTP_BAD_REQUEST,
      message = EKM_ERROR
    )

    private val EKM_FES_RESPONSE = EkmPrivateKeysResponse(
      privateKeys = listOf(
        Key(
          TestGeneralUtil.readFileFromAssetsAsString("pgp/fes@flowcrypt.test_prv_decrypted.asc")
        )
      )
    )

    private val FES_SUCCESS_RESPONSE = FesServerResponse(
      apiError = null,
      vendor = "FlowCrypt",
      service = "enterprise-server",
      orgId = "localhost",
      version = "2021",
      endUserApiVersion = "v1",
      adminApiVersion = "v1"
    )
  }
}
