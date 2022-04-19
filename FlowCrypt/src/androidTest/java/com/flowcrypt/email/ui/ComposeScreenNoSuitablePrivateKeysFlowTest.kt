/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.database.entity.KeyEntity
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.base.BaseCreateMessageActivityTest
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.net.HttpURLConnection

/**
 * @author Denis Bondarenko
 *         Date: 4/19/22
 *         Time: 12:23 PM
 *         E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ComposeScreenNoSuitablePrivateKeysFlowTest : BaseCreateMessageActivityTest() {
  private val addPrivateKeyToDatabaseRule =
    AddPrivateKeyToDatabaseRule("pgp/default@flowcrypt.test_secondKey_prv_strong_revoked.asc")

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Test
  fun testShowNoPrivateKeysSuitableForEncryptionWarning() {
    activeActivityRule?.launch(intent)
    registerAllIdlingResources()
    fillInAllFields(TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER)

    onView(withId(R.id.menuActionSend))
      .check(matches(isDisplayed()))
      .perform(click())

    isDialogWithTextDisplayed(
      decorView,
      getResString(R.string.no_private_keys_suitable_for_encryption)
    )
  }

  @Test
  fun testDoNotShowNoPrivateKeysSuitableForEncryptionWarningIfAtLeastOneKeyAvailable() {
    val details = PrivateKeysManager.getPgpKeyDetailsFromAssets(
      "pgp/default@flowcrypt.test_fisrtKey_prv_strong.asc"
    )
    PrivateKeysManager.saveKeyToDatabase(
      accountEntity = addAccountToDatabaseRule.account,
      pgpKeyDetails = details,
      passphrase = null,
      sourceType = KeyImportDetails.SourceType.EMAIL,
      passphraseType = KeyEntity.PassphraseType.RAM
    )

    activeActivityRule?.launch(intent)
    registerAllIdlingResources()
    fillInAllFields(TestConstants.RECIPIENT_WITH_PUBLIC_KEY_ON_ATTESTER)

    onView(withId(R.id.menuActionSend))
      .check(matches(isDisplayed()))
      .perform(click())

    isDialogWithTextDisplayed(
      decorView,
      getQuantityString(R.plurals.please_provide_passphrase_for_following_keys, 1)
    )
  }

  companion object {
    @get:ClassRule
    @JvmStatic
    val mockWebServerRule = FlowCryptMockWebServerRule(TestConstants.MOCK_WEB_SERVER_PORT,
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
          if (request.path?.startsWith("/pub", ignoreCase = true) == true) {
            val lastSegment = request.requestUrl?.pathSegments?.lastOrNull()

            when {
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
  }
}
