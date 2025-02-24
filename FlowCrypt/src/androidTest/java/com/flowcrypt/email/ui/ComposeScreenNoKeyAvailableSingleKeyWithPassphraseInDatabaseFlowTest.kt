/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.junit.annotations.NotReadyForCI
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseComposeScreenNoKeyAvailableTest
import com.flowcrypt.email.util.PrivateKeysManager
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
@FlowCryptTestSettings(useCommonIdling = false)
class ComposeScreenNoKeyAvailableSingleKeyWithPassphraseInDatabaseFlowTest : BaseComposeScreenNoKeyAvailableTest() {
  private val addPrivateKeyToDatabaseRule = AddPrivateKeyToDatabaseRule(
    keyPath = "pgp/denbond7@flowcrypt.test_prv_strong_primary.asc"
  )

  private val pgpKeyDetails =
    PrivateKeysManager.getPgpKeyDetailsFromAssets("pgp/default@flowcrypt.test_fisrtKey_prv_strong.asc")

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testImportKey() {
    doBaseActions {
      addTextToClipboard("private key", requireNotNull(pgpKeyDetails.privateKey))
      onView(withText(R.string.import_private_key))
        .check(matches(isDisplayed()))
        .perform(click())
      Thread.sleep(1000)
      onView(withText(R.string.load_from_clipboard))
        .check(matches(isDisplayed()))
        .perform(click())

      onView(withId(R.id.editTextKeyPassword))
        .perform(
          replaceText(TestConstants.DEFAULT_STRONG_PASSWORD),
          closeSoftKeyboard()
        )
      onView(withId(R.id.buttonPositiveAction))
        .perform(scrollTo(), click())

      waitForObjectWithText(addAccountToDatabaseRule.account.email, 5000)
    }
  }

  @Test
  @Ignore("flaky")
  fun testAddEmailToExistingSingleKeyPassphraseInDatabase() {
    doTestAddEmailToExistingKey {
      //no more additional actions
    }
  }
}
