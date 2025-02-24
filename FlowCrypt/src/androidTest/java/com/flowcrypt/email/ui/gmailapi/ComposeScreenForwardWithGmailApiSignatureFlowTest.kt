/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.gmailapi

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.junit.annotations.OutgoingMessageConfiguration
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseComposeGmailApiSignatureFlowTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.CoreMatchers.startsWith
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * @author Denys Bondarenko
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@FlowCryptTestSettings(useCommonIdling = false)
@OutgoingMessageConfiguration(
  message = "",
  subject = "",
  isNew = false
)
class ComposeScreenForwardWithGmailApiSignatureFlowTest :
  BaseComposeGmailApiSignatureFlowTest() {
  override val mockWebServerRule =
    FlowCryptMockWebServerRule(TestConstants.MOCK_WEB_SERVER_PORT, object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return handleCommonAPICalls(request)
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
  @Ignore("flaky")
  fun testAddingSignatureAfterStart() {
    //need to wait while the app loads the messages list
    Thread.sleep(2000)

    //click on a message
    onView(withId(R.id.recyclerViewMsgs))
      .perform(
        actionOnItemAtPosition<RecyclerView.ViewHolder>(
          POSITION_EXISTING_ENCRYPTED, click()
        )
      )

    //wait the message details rendering
    waitForObjectWithText(getResString(R.string.reply_all_encrypted), TimeUnit.SECONDS.toMillis(10))

    //click on forward button
    openReplyScreen(R.id.forwardButton, SUBJECT_EXISTING_ENCRYPTED)

    //need to wait while all action for reply case will be applied
    Thread.sleep(1000)

    onView(withId(R.id.editTextEmailMessage))
      .perform(scrollTo())
      .check(matches(withText(startsWith("\n\n$SIGNATURE_FOR_MAIN"))))

    //switch to standard mode
    openActionBarOverflowOrOptionsMenu(getTargetContext())
    onView(withText(R.string.switch_to_standard_email))
      .check(matches(isDisplayed()))
      .perform(click())

    closeSoftKeyboard()

    onView(withId(R.id.editTextEmailMessage))
      .perform(scrollTo())
      .check(matches(withText(startsWith("\n\n$SIGNATURE_FOR_MAIN"))))

    onView(withId(R.id.imageButtonAliases))
      .perform(scrollTo())
      .perform(click())

    onView(withText(ALIAS_EMAIL))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withId(R.id.editTextEmailMessage))
      .perform(scrollTo())
      .check(matches(withText(startsWith("\n\n$SIGNATURE_FOR_ALIS"))))
  }
}