/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.MainActivity
import com.flowcrypt.email.ui.base.AddOtherAccountBaseTest
import com.flowcrypt.email.util.AuthCredentialsManager
import com.flowcrypt.email.util.TestGeneralUtil
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.startsWith
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * @author Denys Bondarenko
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class AddOtherAccountFlowTest : AddOtherAccountBaseTest() {

  override val activeActivityRule = activityScenarioRule<MainActivity>(
    TestGeneralUtil.genIntentForNavigationComponent(
      destinationId = R.id.addOtherAccountFragment
    )
  )

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Test
  @Ignore("flaky 8")
  fun testShowWarningIfAuthFail() {
    enableAdvancedMode()
    val credentials = AuthCredentialsManager.getAuthCredentials("user_with_not_existed_server.json")
    fillAllFields(credentials)
    val someFailTextToChangeRightValue = "123"

    val fieldIdentifiersWithIncorrectData = intArrayOf(
      R.id.editTextEmail,
      R.id.editTextUserName,
      R.id.editTextImapServer,
      R.id.editTextImapPort,
      R.id.editTextSmtpServer,
      R.id.editTextSmtpPort,
      R.id.editTextSmtpUsername,
      R.id.editTextSmtpPassword
    )

    val correctData = arrayOf(
      credentials.email,
      credentials.username,
      credentials.password,
      credentials.imapServer,
      credentials.imapPort.toString(),
      credentials.smtpServer,
      credentials.smtpPort.toString(),
      credentials.smtpSigInUsername,
      credentials.smtpSignInPassword
    )

    val numberOfChecks = if (credentials.hasCustomSignInForSmtp) {
      fieldIdentifiersWithIncorrectData.size
    } else {
      fieldIdentifiersWithIncorrectData.size - 2
    }

    for (i in 0 until numberOfChecks) {
      onView(withId(fieldIdentifiersWithIncorrectData[i]))
        .perform(
          scrollTo(),
          typeText(someFailTextToChangeRightValue),
          closeSoftKeyboard()
        )
      onView(withId(R.id.buttonTryToConnect))
        .perform(scrollTo(), click())

      onView(
        anyOf(
          withText(startsWith(TestConstants.IMAP)),
          withText(startsWith(TestConstants.SMTP))
        )
      ).check(matches(isDisplayed()))

      if (i in intArrayOf(
          R.id.editTextImapServer,
          R.id.editTextImapPort,
          R.id.editTextSmtpServer,
          R.id.editTextSmtpPort
        )
      ) {
        onView(withText(getResString(R.string.network_error)))
          .check(matches(isDisplayed()))
      }

      onView(withText(getResString(R.string.cancel)))
        .inRoot(isDialog())
        .check(matches(isDisplayed()))
        .perform(click())

      onView(withId(fieldIdentifiersWithIncorrectData[i]))
        .perform(
          scrollTo(),
          clearText(),
          typeText(correctData[i]),
          closeSoftKeyboard()
        )
    }
  }
}
