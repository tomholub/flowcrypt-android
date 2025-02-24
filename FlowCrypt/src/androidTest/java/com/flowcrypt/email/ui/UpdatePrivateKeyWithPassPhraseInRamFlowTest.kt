/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasTextColor
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.entity.AccountSettingsEntity
import com.flowcrypt.email.database.entity.KeyEntity
import com.flowcrypt.email.database.entity.RecipientEntity
import com.flowcrypt.email.database.entity.relation.RecipientWithPubKeys
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.junit.annotations.NotReadyForCI
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withTextInputLayoutError
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.AddRecipientsToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.security.pgp.PgpKey
import com.flowcrypt.email.ui.activity.MainActivity
import com.flowcrypt.email.ui.activity.fragment.PrivateKeyDetailsFragmentArgs
import com.flowcrypt.email.util.DateTimeUtil
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * @author Denys Bondarenko
 */
@FlowCryptTestSettings(useIntents = true, useCommonIdling = false)
@MediumTest
@RunWith(AndroidJUnit4::class)
class UpdatePrivateKeyWithPassPhraseInRamFlowTest : BaseTest() {
  private val addAccountToDatabaseRule = AddAccountToDatabaseRule()
  private val addPrivateKeyToDatabaseRule = AddPrivateKeyToDatabaseRule(
    passphraseType = KeyEntity.PassphraseType.RAM
  )

  override val activityScenarioRule = activityScenarioRule<MainActivity>(
    TestGeneralUtil.genIntentForNavigationComponent(
      destinationId = R.id.privateKeyDetailsFragment,
      extras = PrivateKeyDetailsFragmentArgs(
        fingerprint = addPrivateKeyToDatabaseRule.pgpKeyRingDetails.fingerprint
      ).toBundle()
    )
  )

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(
      AddRecipientsToDatabaseRule(
        listOf(
          RecipientWithPubKeys(
            RecipientEntity(
              email = addAccountToDatabaseRule.account.email,
              name = "Default"
            ),
            listOf(
              addPrivateKeyToDatabaseRule.pgpKeyRingDetails
                .toPublicKeyEntity(addAccountToDatabaseRule.account.email)
                .copy(id = 1)
            )
          )
        )
      )
    )
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testUpdateSuccess() {
    Thread.sleep(1000)
    val dateFormat = DateTimeUtil.getPgpDateFormat(getTargetContext())
    val originalKeyDetails = addPrivateKeyToDatabaseRule.pgpKeyRingDetails
    val updatedKeyDetails = PrivateKeysManager.getPgpKeyDetailsFromAssets(
      "pgp/default@flowcrypt.test_fisrtKey_prv_default_mod_05_22_2023.asc"
    )

    //check a key before update
    onView(
      withText(
        getResString(
          R.string.template_modified,
          dateFormat.format(Date(requireNotNull(originalKeyDetails.lastModified)))
        )
      )
    ).check(matches(isDisplayed()))

    val roomDatabase = FlowCryptRoomDatabase.getDatabase(getTargetContext())
    val recipientDao = roomDatabase.recipientDao()

    val existingRecipientWithPubKeysBeforeUpdate = runBlocking {
      recipientDao.getRecipientWithPubKeysByEmailSuspend(addAccountToDatabaseRule.account.email)
    }

    assertEquals(
      originalKeyDetails.lastModified,
      PgpKey.parseKeys(
        existingRecipientWithPubKeysBeforeUpdate?.publicKeys?.first()?.publicKey ?: byteArrayOf()
      ).pgpKeyDetailsList.first().lastModified
    )

    //check we have a warning about a missing pass phrase. Provide the pass phrase
    onView(withText(R.string.pass_phrase_not_provided))
      .check(matches(isDisplayed()))

    //try to open update key screen. At this stage, a user shouldn't be able to move on
    onView(withId(R.id.btnUpdatePrivateKey))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withId(R.id.editTextNewPrivateKey))
      .check(doesNotExist())

    onView(withId(R.id.eTKeyPassword))
      .perform(
        clearText(),
        typeText(TestConstants.DEFAULT_STRONG_PASSWORD),
        closeSoftKeyboard()
      )
    onView(withId(R.id.btnUpdatePassphrase))
      .perform(click())

    Thread.sleep(1000)
    onView(withId(R.id.tVPassPhraseVerification))
      .check(matches(withText(getResString(R.string.stored_pass_phrase_matched))))
      .check(matches(hasTextColor(R.color.colorPrimaryLight)))

    // move on to update key screen
    onView(withId(R.id.btnUpdatePrivateKey))
      .check(matches(isDisplayed()))
      .perform(click())

    //type key
    onView(withId(R.id.editTextNewPrivateKey))
      .check(matches(isDisplayed()))
      .perform(replaceText(updatedKeyDetails.privateKey?:""), closeSoftKeyboard())

    onView(withId(R.id.buttonCheck))
      .check(matches(isDisplayed()))
      .perform(click())

    Thread.sleep(1000)
    //click on 'use this key'
    onView(withId(android.R.id.button1))
      .perform(click())

    //type pass phrase
    onView(withId(R.id.editTextKeyPassword))
      .perform(scrollTo(), replaceText(TestConstants.DEFAULT_PASSWORD), closeSoftKeyboard())
    onView(withId(R.id.buttonPositiveAction))
      .perform(scrollTo(), click())

    waitForObjectWithText(getResString(R.string.key_details), TimeUnit.SECONDS.toMillis(60))

    //do checks after update
    onView(
      withText(
        getResString(
          R.string.template_modified,
          dateFormat.format(Date(requireNotNull(updatedKeyDetails.lastModified)))
        )
      )
    ).check(matches(isDisplayed()))

    val existingRecipientWithPubKeysAfterUpdate = runBlocking {
      recipientDao.getRecipientWithPubKeysByEmailSuspend(addAccountToDatabaseRule.account.email)
    }
    assertEquals(
      updatedKeyDetails.lastModified,
      PgpKey.parseKeys(
        existingRecipientWithPubKeysAfterUpdate?.publicKeys?.first()?.publicKey ?: byteArrayOf()
      ).pgpKeyDetailsList.first().lastModified
    )
  }

  @Test
  @Ignore("flaky 2 2")
  fun testPrivateKeyPassphraseAntiBruteforceProtection() {
    onView(withText(R.string.pass_phrase_not_provided))
      .check(matches(isDisplayed()))

    onView(withId(R.id.btnProvidePassphrase))
      .check(matches(isDisplayed()))
      .perform(click())

    val wrongPassphrase = "wrong pass phrase"

    for (i in 0 until AccountSettingsEntity.ANTI_BRUTE_FORCE_PROTECTION_ATTEMPTS_MAX_VALUE) {
      onView(withId(R.id.eTKeyPassword))
        .perform(
          clearText(),
          replaceText(wrongPassphrase),
          closeSoftKeyboard()
        )
      onView(withId(R.id.btnUpdatePassphrase))
        .perform(click())
      Thread.sleep(2000)
      if (i == AccountSettingsEntity.ANTI_BRUTE_FORCE_PROTECTION_ATTEMPTS_MAX_VALUE - 1) {
        onView(withId(R.id.tILKeyPassword))
          .check(
            matches(
              withTextInputLayoutError(
                getResString(
                  R.string.private_key_passphrase_anti_bruteforce_protection_hint
                )
              )
            )
          )
      } else {
        val attemptsLeft =
          AccountSettingsEntity.ANTI_BRUTE_FORCE_PROTECTION_ATTEMPTS_MAX_VALUE - i - 1

        onView(withId(R.id.tILKeyPassword))
          .check(
            matches(
              withTextInputLayoutError(
                getResString(R.string.password_is_incorrect) +
                    "\n\n" +
                    getQuantityString(
                      R.plurals.next_attempt_warning_about_wrong_pass_phrase,
                      attemptsLeft,
                      attemptsLeft
                    )
              )
            )
          )
      }

      checkPassPhraseAttemptsCount(i + 1)
      Thread.sleep(1000)
    }
  }

  private fun checkPassPhraseAttemptsCount(expectedValue: Int) {
    val accountSettings = runBlocking {
      roomDatabase.accountSettingsDao().getAccountSettings(
        addAccountToDatabaseRule.account.email,
        addAccountToDatabaseRule.account.accountType
      )
    }

    assertNotNull(accountSettings)
    assertEquals(expectedValue, accountSettings?.checkPassPhraseAttemptsCount)
  }
}
