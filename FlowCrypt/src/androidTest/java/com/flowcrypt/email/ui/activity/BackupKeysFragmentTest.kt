/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.junit.annotations.DependsOnMailServer
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.settings.SettingsActivity
import com.flowcrypt.email.util.AccountDaoManager
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File

/**
 * @author Denis Bondarenko
 * Date: 17.08.2018
 * Time: 16:28
 * E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class BackupKeysFragmentTest : BaseTest() {
  override val useIntents: Boolean = true
  override val activityScenarioRule = activityScenarioRule<SettingsActivity>()

  val addAccountToDatabaseRule = AddAccountToDatabaseRule()

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(ClearAppSettingsRule())
    .around(addAccountToDatabaseRule)
    .around(RetryRule.DEFAULT)
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  @Before
  fun goToBackupKeysFragment() {
    onView(withText(getResString(R.string.backups)))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
  }

  @Test
  fun testEmailOptionHint() {
    onView(withId(R.id.rBEmailOption))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(getResString(R.string.backup_as_email_hint)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testDownloadOptionHint() {
    onView(withId(R.id.rBDownloadOption))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(getResString(R.string.backup_as_download_hint)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testNoKeysEmailOption() {
    onView(withId(R.id.rBEmailOption))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(
      withText(
        getResString(
          R.string.there_are_no_private_keys,
          AccountDaoManager.getDefaultAccountDao().email
        )
      )
    )
      .check(matches(isDisplayed()))
  }

  @Test
  fun testNoKeysDownloadOption() {
    onView(withId(R.id.rBDownloadOption))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(
      withText(
        getResString(
          R.string.there_are_no_private_keys,
          AccountDaoManager.getDefaultAccountDao().email
        )
      )
    )
      .check(matches(isDisplayed()))
  }

  @Test
  @DependsOnMailServer
  fun testSuccessEmailOption() {
    addFirstKeyWithStrongPassword()
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(getResString(R.string.title_activity_settings)))
      .check(matches(isDisplayed()))
      .perform(click())
  }

  @Test
  @DependsOnMailServer
  fun testSuccessWithTwoKeysEmailOption() {
    addSecondKeyWithStrongPassword()
    testSuccessEmailOption()
  }

  @Test
  fun testSuccessDownloadOption() {
    addFirstKeyWithStrongPassword()
    onView(withId(R.id.rBDownloadOption))
      .check(matches(isDisplayed()))
      .perform(click())

    val file = TestGeneralUtil.createFileAndFillWithContent("key.asc", "")

    intendingFileChoose(file)
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())

    TestGeneralUtil.deleteFiles(listOf(file))

    onView(withText(getResString(R.string.title_activity_settings)))
      .check(matches(isDisplayed()))
      .perform(click())
  }

  @Test
  fun testSuccessWithTwoKeysDownloadOption() {
    addSecondKeyWithStrongPassword()
    testSuccessDownloadOption()
  }

  @Test
  fun testShowWeakPasswordHintForDownloadOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.rBDownloadOption))
      .check(matches(isDisplayed()))
      .perform(click())
    intendingFileChoose(File(""))
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(getResString(R.string.pass_phrase_is_too_weak)))
      .check(matches(isDisplayed()))
  }

  @Test
  @DependsOnMailServer
  fun testShowWeakPasswordHintForEmailOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withText(getResString(R.string.pass_phrase_is_too_weak)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testFixWeakPasswordForDownloadOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.rBDownloadOption))
      .check(matches(isDisplayed()))
      .perform(click())
    intendingFileChoose(File(""))
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.pass_phrase_is_too_weak))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  @Test
  @DependsOnMailServer
  fun testFixWeakPasswordForEmailOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.pass_phrase_is_too_weak))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  @Test
  @DependsOnMailServer
  fun testDiffPassphrasesForEmailOption() {
    addFirstKeyWithStrongPassword()
    addSecondKeyWithStrongSecondPassword()
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.different_pass_phrases))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  @Test
  fun testDiffPassphrasesForDownloadOption() {
    addFirstKeyWithStrongPassword()
    addSecondKeyWithStrongSecondPassword()
    onView(withId(R.id.rBDownloadOption))
      .check(matches(isDisplayed()))
      .perform(click())
    intendingFileChoose(File(""))
    onView(withId(R.id.btBackup))
      .check(matches(isDisplayed()))
      .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.different_pass_phrases))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  private fun intendingFileChoose(file: File) {
    val resultData = Intent()
    resultData.data = Uri.fromFile(file)
    intending(
      allOf(
        hasAction(Intent.ACTION_CREATE_DOCUMENT),
        hasCategories(hasItem(equalTo(Intent.CATEGORY_OPENABLE))),
        hasType(Constants.MIME_TYPE_PGP_KEY)
      )
    )
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, resultData))
  }

  private fun addFirstKeyWithDefaultPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      accountEntity = addAccountToDatabaseRule.account,
      keyPath = "pgp/default@flowcrypt.test_fisrtKey_prv_default.asc",
      passphrase = TestConstants.DEFAULT_PASSWORD,
      sourceType = KeyImportDetails.SourceType.EMAIL
    )
  }

  private fun addFirstKeyWithStrongPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      accountEntity = addAccountToDatabaseRule.account,
      keyPath = "pgp/default@flowcrypt.test_fisrtKey_prv_strong.asc",
      passphrase = TestConstants.DEFAULT_STRONG_PASSWORD,
      sourceType = KeyImportDetails.SourceType.EMAIL
    )
  }

  private fun addSecondKeyWithStrongPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      accountEntity = addAccountToDatabaseRule.account,
      keyPath = TestConstants.DEFAULT_SECOND_KEY_PRV_STRONG,
      passphrase = TestConstants.DEFAULT_STRONG_PASSWORD,
      sourceType = KeyImportDetails.SourceType.EMAIL
    )
  }

  private fun addSecondKeyWithStrongSecondPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      accountEntity = addAccountToDatabaseRule.account,
      keyPath = "pgp/default@flowcrypt.test_secondKey_prv_strong_second.asc",
      passphrase = TestConstants.DEFAULT_SECOND_STRONG_PASSWORD,
      sourceType = KeyImportDetails.SourceType.EMAIL
    )
  }
}
