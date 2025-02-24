/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.gmailapi.passwordprotected

import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.openLinkWithText
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.junit.annotations.OutgoingMessageConfiguration
import com.flowcrypt.email.rules.AddRecipientsToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import org.hamcrest.Matchers.allOf
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
@FlowCryptTestSettings(useCommonIdling = false, useIntents = true)
@OutgoingMessageConfiguration(
  to = [],
  cc = [],
  bcc = [],
  message = "",
  subject = "",
  isNew = true
)
class ComposeScreenPasswordProtectedDisallowedTermsFlowTest :
  BaseComposeScreenPasswordProtectedDisallowedTermsTest(
    ACCOUNT_ENTITY_WITH_EXISTING_OPTIONAL_PARAMETERS
  ) {

  @get:Rule
  var ruleChain: TestRule =
    RuleChain.outerRule(RetryRule.DEFAULT)
      .around(ClearAppSettingsRule())
      .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
      .around(mockWebServerRule)
      .around(addAccountToDatabaseRule)
      .around(addPrivateKeyToDatabaseRule)
      .around(AddRecipientsToDatabaseRule(prepareRecipientsForTest()))
      .around(addLabelsToDatabaseRule)
      .around(activityScenarioRule)
      .around(ScreenshotTestRule())

  @Test
  @Ignore("flaky")
  fun testDialogWithErrorText() {
    intentsRelease()

    MATCHING_SUBJECTS.forEach { subject ->
      onView(withId(R.id.editTextEmailSubject))
        .perform(scrollTo(), click(), replaceText(subject))

      waitForObjectWithText(subject, TimeUnit.SECONDS.toMillis(5))

      onView(withId(R.id.menuActionSend))
        .check(matches(isDisplayed()))
        .perform(click())

      isDialogWithTextDisplayed(
        decorView,
        ERROR_TEXT
      )

      Intents.init()
      //test that URL is openable
      val expectingIntent = allOf(hasAction(Intent.ACTION_VIEW), hasData(URL))
      //mocking intent to prevent actual navigation during test
      Intents.intending(expectingIntent).respondWith(Instrumentation.ActivityResult(0, null))
      //performing action
      onView(withText(ERROR_TEXT))
        .perform(openLinkWithText(URL))
      //asserting our expected intent was recorded
      Intents.intended(expectingIntent)
      Intents.release()

      onView(withId(android.R.id.button1))
        .check(matches(isDisplayed()))
        .perform(click())
    }
  }

  @Test
  fun testDialogWithoutErrorText() {
    NON_MATCHING_SUBJECTS.forEach { subject ->
      onView(withId(R.id.editTextEmailSubject))
        .perform(scrollTo(), click(), replaceText(subject))
      Thread.sleep(1000)

      onView(withId(R.id.menuActionSend))
        .check(matches(isDisplayed()))
        .perform(click())

      onView(
        withText(getResString(R.string.sending_message_must_not_be_empty))
      ).check(matches(isDisplayed()))
    }
  }

  companion object {
    val ACCOUNT_ENTITY_WITH_EXISTING_OPTIONAL_PARAMETERS =
      BASE_ACCOUNT_ENTITY.copy(
        clientConfiguration = BASE_ACCOUNT_ENTITY.clientConfiguration?.copy(
          disallowPasswordMessagesErrorText = ERROR_TEXT,
          disallowPasswordMessagesForTerms = TERMS
        )
      )
  }
}
