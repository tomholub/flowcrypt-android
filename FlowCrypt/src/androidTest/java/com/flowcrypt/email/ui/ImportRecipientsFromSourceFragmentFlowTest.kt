/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.MainActivity
import com.flowcrypt.email.util.TestGeneralUtil
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection

/**
 * @author Denys Bondarenko
 */
@FlowCryptTestSettings(useIntents = true)
@MediumTest
@RunWith(AndroidJUnit4::class)
class ImportRecipientsFromSourceFragmentFlowTest : BaseTest() {
  private val addAccountToDatabaseRule = AddAccountToDatabaseRule()

  override val activityScenarioRule = activityScenarioRule<MainActivity>(
    TestGeneralUtil.genIntentForNavigationComponent(
      destinationId = R.id.importRecipientsFromSourceFragment
    )
  )

  private lateinit var fileWithPublicKey: File
  private lateinit var publicKey: String

  private val mockWebServerRule = FlowCryptMockWebServerRule(
    TestConstants.MOCK_WEB_SERVER_PORT,
    object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.path?.startsWith("/attester/pub", ignoreCase = true) == true) {
          val lastSegment = request.requestUrl?.pathSegments?.lastOrNull()

          when {
            TestConstants.RECIPIENT_WITHOUT_PUBLIC_KEY_ON_ATTESTER.equals(
              lastSegment, true
            ) -> {
              return MockResponse()
                .setStatus("HTTP/1.1 404 Not Found")
                .setBody(TestGeneralUtil.readResourceAsString("2.txt"))
            }

            TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER.equals(
              lastSegment, true
            ) -> {
              return MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(TestGeneralUtil.readResourceAsString("3.txt"))
            }
          }
        }

        return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
      }
    })

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(mockWebServerRule)
    .around(addAccountToDatabaseRule)
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  @Before
  fun createResources() {
    publicKey = TestGeneralUtil.readFileFromAssetsAsString(
      "pgp/" + TestConstants.RECIPIENT_WITHOUT_PUBLIC_KEY_ON_ATTESTER + "-pub.asc"
    )
    fileWithPublicKey = TestGeneralUtil.createFileWithTextContent(
      fileName = TestConstants.RECIPIENT_WITHOUT_PUBLIC_KEY_ON_ATTESTER + "_pub.asc",
      fileText = publicKey
    )
  }

  @After
  fun cleanResources() {
    TestGeneralUtil.deleteFiles(listOf(fileWithPublicKey))
  }

  @Test
  fun testFetchKeyFromAttesterForExistedUser() {
    onView(withId(R.id.eTKeyIdOrEmail))
      .perform(
        clearText(),
        typeText(TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER),
        closeSoftKeyboard()
      )
    onView(withId(R.id.iBSearchKey))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(containsString(TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER)))
      .check(matches(isDisplayed()))
  }

  @Test
  @Ignore("flaky 4")
  fun testFetchKeyFromAttesterForExistedUserImeAction() {
    onView(withId(R.id.eTKeyIdOrEmail))
      .perform(
        clearText(),
        typeText(TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER),
        closeSoftKeyboard(),
        pressImeActionButton()
      )

    onView(withText(containsString(TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFetchKeyFromAttesterForNotExistedUser() {
    onView(withId(R.id.eTKeyIdOrEmail))
      .perform(
        clearText(),
        typeText(TestConstants.RECIPIENT_WITHOUT_PUBLIC_KEY_ON_ATTESTER),
        closeSoftKeyboard()
      )
    onView(withId(R.id.iBSearchKey))
      .check(matches(isDisplayed()))
      .perform(click())

    //due to realization of MockWebServer I can't produce the same response.
    isDialogWithTextDisplayed(decorView, "API error: code = 404, message = Public key not found")
  }

  @Test
  fun testImportKeyFromFile() {
    val resultData = TestGeneralUtil.genIntentWithPersistedReadPermissionForFile(fileWithPublicKey)
    intendingActivityResultContractsGetContent(resultData = resultData)
    onView(withId(R.id.btLoadFromFile))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(containsString(TestConstants.RECIPIENT_WITHOUT_PUBLIC_KEY_ON_ATTESTER)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testImportKeyFromClipboard() {
    addTextToClipboard("public key", publicKey)
    onView(withId(R.id.btLoadFromClipboard))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(containsString(TestConstants.RECIPIENT_WITHOUT_PUBLIC_KEY_ON_ATTESTER)))
      .check(matches(isDisplayed()))
  }
}
