/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.api.ClientConfigurationResponse
import com.flowcrypt.email.api.retrofit.response.api.EkmPrivateKeysResponse
import com.flowcrypt.email.api.retrofit.response.api.FesServerResponse
import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.retrofit.response.model.ClientConfiguration
import com.flowcrypt.email.api.retrofit.response.model.Key
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseFesDuringSetupFlowTest
import com.flowcrypt.email.util.TestGeneralUtil
import com.flowcrypt.email.util.exception.ApiException
import com.google.gson.Gson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * @author Denys Bondarenko
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class FesDuringSetupConsumerFlowTest : BaseFesDuringSetupFlowTest() {

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(testNameRule)
    .around(mockWebServerRule)
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  override fun handleAPI(request: RecordedRequest, gson: Gson): MockResponse {
    return when {
      request.path?.startsWith("/ekm") == true -> handleEkmAPI(request, gson)
      else -> MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
    }
  }

  override fun handleCheckIfFesIsAvailableAtCustomerFesUrl(gson: Gson): MockResponse {
    return if ("testFesAvailableRequestTimeOutHasConnection" == testNameRule.methodName) {
      MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeadersDelay(6, TimeUnit.SECONDS)
    } else when (testNameRule.methodName) {

      "testFesAvailableHasConnectionHttpCode404" -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
      }

      "testFesAvailableHasConnectionHttpCodeNot200" -> {
        MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
      }

      "testFesAvailableWrongServiceName" -> {
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

  override fun handleClientConfigurationAPI(gson: Gson): MockResponse {
    val responseCode = when (testNameRule.methodName) {
      "testFesAvailableWrongServiceName" -> HttpURLConnection.HTTP_NOT_ACCEPTABLE
      else -> HttpURLConnection.HTTP_OK
    }

    val body = when (testNameRule.methodName) {
      "testFesAvailableWrongServiceName" -> null
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

  override fun handleClientConfigurationAPIForSharedTenantFes(
    account: String?,
    gson: Gson
  ): MockResponse {
    return when (account) {
      EMAIL_FES_NOT_ALLOWED_SERVER -> MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_NOT_ACCEPTABLE)

      EMAIL_FES_REQUEST_TIME_OUT -> MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)

      EMAIL_FES_HTTP_404 -> MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_FORBIDDEN)

      EMAIL_FES_HTTP_NOT_404_NOT_SUCCESS -> MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_GONE)

      else -> MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
    }
  }

  @Test
  fun testFesAvailableWrongServiceName() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(email = EMAIL_FES_NOT_ALLOWED_SERVER))

    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_NOT_ACCEPTABLE,
          msg = ""
        )
      ).message
    )
  }

  @Test
  fun testFesAvailableRequestTimeOutHasConnection() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_REQUEST_TIME_OUT))
    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_BAD_REQUEST,
          msg = ""
        )
      ).message
    )
  }

  @Test
  fun testFesAvailableHasConnectionHttpCode404() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_HTTP_404))
    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_FORBIDDEN,
          msg = ""
        )
      ).message
    )
  }

  @Test
  fun testFesAvailableHasConnectionHttpCodeNot200() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_HTTP_NOT_404_NOT_SUCCESS))
    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_GONE,
          msg = ""
        )
      ).message
    )
  }

  @Test
  fun testFesAvailableSuccess() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_SUCCESS))
    Thread.sleep(2000)
    onView(withText(R.string.set_pass_phrase))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFesAvailableSSLError() {
    setupAndClickSignInButton(genMockGoogleSignInAccountJson(EMAIL_FES_SSL_ERROR))
    //as our mock server support only flowcrypt.test and flowcrypt.example we will receive
    //HttpURLConnection.HTTP_NOT_FOUND error
    isDialogWithTextDisplayed(
      decorView,
      "ApiException:" + ApiException(
        ApiError(
          code = HttpURLConnection.HTTP_NOT_FOUND,
          msg = ""
        )
      ).message
    )
  }

  private fun handleEkmAPI(request: RecordedRequest, gson: Gson): MockResponse {
    return when {
      request.path.equals("/ekm/v1/keys/private") ->
        MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
          .setBody(gson.toJson(EKM_FES_RESPONSE))

      else -> MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
        .setBody(gson.toJson(EkmPrivateKeysResponse(privateKeys = emptyList())))
    }
  }

  companion object {
    private const val EMAIL_EKM_URL_SUCCESS = "https://flowcrypt.test/ekm/"
    private const val EMAIL_FES_REQUEST_TIME_OUT = "fes_request_timeout@flowcrypt.test"
    private const val EMAIL_FES_HTTP_404 = "fes_404@flowcrypt.test"
    private const val EMAIL_FES_HTTP_NOT_404_NOT_SUCCESS = "fes_not404_not_success@flowcrypt.test"
    private const val EMAIL_FES_NOT_ALLOWED_SERVER = "fes_not_allowed_server@flowcrypt.test"
    private const val EMAIL_FES_SUCCESS = "fes_success@flowcrypt.test"
    private const val EMAIL_FES_SSL_ERROR = "fes_ssl_error@wrongssl.test"

    private val ACCEPTED_FLAGS = listOf(
      ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN,
      ClientConfiguration.ConfigurationProperty.FORBID_STORING_PASS_PHRASE,
      ClientConfiguration.ConfigurationProperty.NO_PRV_CREATE
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
