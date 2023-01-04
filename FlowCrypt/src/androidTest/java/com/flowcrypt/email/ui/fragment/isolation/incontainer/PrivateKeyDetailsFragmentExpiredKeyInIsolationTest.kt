/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.fragment.isolation.incontainer

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.database.entity.KeyEntity
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.fragment.PrivateKeyDetailsFragment
import com.flowcrypt.email.ui.activity.fragment.PrivateKeyDetailsFragmentArgs
import com.flowcrypt.email.util.DateTimeUtil
import com.flowcrypt.email.util.PrivateKeysManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.Date

/**
 * @author Denis Bondarenko
 *         Date: 1/4/23
 *         Time: 6:49 PM
 *         E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class PrivateKeyDetailsFragmentExpiredKeyInIsolationTest : BaseTest() {
  private val addAccountToDatabaseRule = AddAccountToDatabaseRule()
  private val addPrivateKeyToDatabaseRule = AddPrivateKeyToDatabaseRule(
    accountEntity = addAccountToDatabaseRule.account,
    keyPath = "pgp/expired@flowcrypt.test_prv_default.asc",
    passphrase = TestConstants.DEFAULT_PASSWORD,
    sourceType = KeyImportDetails.SourceType.EMAIL,
    passphraseType = KeyEntity.PassphraseType.RAM
  )
  private val dateFormat = DateTimeUtil.getPgpDateFormat(getTargetContext())
  override val useIntents: Boolean = true

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(ScreenshotTestRule())

  @Before
  fun launchFragmentInContainerWithPredefinedArgs() {
    launchFragmentInContainer<PrivateKeyDetailsFragment>(
      fragmentArgs = PrivateKeyDetailsFragmentArgs(
        fingerprint = PrivateKeysManager
          .getPgpKeyDetailsFromAssets(addPrivateKeyToDatabaseRule.keyPath).fingerprint
      ).toBundle()
    )
  }

  @Test
  fun testShowExpirationDate() {
    val details = addPrivateKeyToDatabaseRule.pgpKeyDetails
    val expectedExpirationDate = "Jan 1, 2011"
    val actualExpirationDate = dateFormat.format(Date(requireNotNull(details.expiration)))
    assertEquals(expectedExpirationDate, actualExpirationDate)

    onView(withId(R.id.textViewExpirationDate))
      .check(
        matches(
          withText(
            getHtmlString(
              details.expiration?.let {
                getResString(R.string.key_expiration, actualExpirationDate)
              } ?: getResString(R.string.key_expiration, getResString(R.string.key_does_not_expire))
            )
          )
        )
      )
  }
}
