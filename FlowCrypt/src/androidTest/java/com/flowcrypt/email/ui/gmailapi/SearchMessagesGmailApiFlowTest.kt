/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.gmailapi

import android.view.KeyEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseGmailApiTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.net.HttpURLConnection

/**
 * @author Denys Bondarenko
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@FlowCryptTestSettings(useCommonIdling = false)
class SearchMessagesGmailApiFlowTest : BaseGmailApiTest() {

  override val mockWebServerRule =
    FlowCryptMockWebServerRule(TestConstants.MOCK_WEB_SERVER_PORT, object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when {
          request.path == "/gmail/v1/users/me/messages?maxResults=45&q=android" -> {
            MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(
              LIST_MESSAGES_RESPONSE_ALL_MESSAGE.toString()
            )
          }

          else -> handleCommonAPICalls(request)
        }
      }
    })

  @get:Rule
  var ruleChain: TestRule =
    RuleChain.outerRule(RetryRule.DEFAULT)
      .around(ClearAppSettingsRule())
      .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
      .around(mockWebServerRule)
      .around(addAccountToDatabaseRule)
      .around(addPrivateKeyToDatabaseRule)
      .around(addLabelsToDatabaseRule)
      .around(activityScenarioRule)
      .around(ScreenshotTestRule())

  @Test
  fun testSearchMessages() {
    //need to wait while the app loads the messages list
    Thread.sleep(2000)

    onView(withId(R.id.menuSearch))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(com.google.android.material.R.id.search_src_text))
      .perform(clearText(), replaceText(SEARCH_TEXT))
      .perform(pressKey(KeyEvent.KEYCODE_ENTER))

    //need to wait while the app loads the messages list
    Thread.sleep(2000)

    //click on a message
    onView(withId(R.id.recyclerViewMsgs))
      .perform(
        RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
          POSITION_EXISTING_ENCRYPTED, click()
        )
      )

    //wait the message details rendering
    Thread.sleep(1000)

    //check that correct message is displayed
    onView(withText(SUBJECT_EXISTING_ENCRYPTED))
      .check(matches(isDisplayed()))

    Thread.sleep(5000)

    //check that correct message is still displayed
    onView(withText(SUBJECT_EXISTING_ENCRYPTED))
      .check(matches(isDisplayed()))
  }

  companion object {
    private const val SEARCH_TEXT = "android"
  }
}