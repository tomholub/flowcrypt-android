/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.fragment.isolation.incontainer

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.model.IncomingMessageInfo
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.api.retrofit.response.model.VerificationResult
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.model.MessageEncryptionType
import com.flowcrypt.email.model.MessageType
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.fragment.CreateMessageFragment
import com.flowcrypt.email.ui.activity.fragment.CreateMessageFragmentArgs
import com.flowcrypt.email.ui.base.BaseComposeScreenTest
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 *         Date: 11/17/22
 *         Time: 1:58 PM
 *         E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class CreateMessageFragmentRecipientsDuringReplyAllInIsolationTest : BaseComposeScreenTest() {
  private val addPrivateKeyToDatabaseRule = AddPrivateKeyToDatabaseRule()

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(ScreenshotTestRule())

  @Test
  fun testRecipientsForSentAndOutboxFolder() {
    val toRecipient = "to@flowcrypt.test"
    val ccRecipient = "cc@flowcrypt.test"

    val localFolder = LocalFolder(
      addAccountToDatabaseRule.account.email,
      fullName = "SENT",
      folderAlias = "SENT",
      msgCount = 1,
      attributes = listOf("\\HasNoChildren")
    )

    val incomingMessageInfo = IncomingMessageInfo(
      localFolder = localFolder,
      msgEntity = MessageEntity(
        email = addAccountToDatabaseRule.account.email,
        folder = localFolder.fullName,
        uid = 123,
        toAddress = toRecipient,
        ccAddress = ccRecipient
      ),
      encryptionType = MessageEncryptionType.STANDARD,
      verificationResult = VERIFICATION_RESULT
    )

    launchFragmentInContainer<CreateMessageFragment>(
      fragmentArgs = CreateMessageFragmentArgs(
        messageType = MessageType.REPLY_ALL,
        incomingMessageInfo = incomingMessageInfo
      ).toBundle()
    )

    onView(withId(R.id.recyclerViewAutocompleteTo))
      .perform(scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(toRecipient))))
    onView(withId(R.id.recyclerViewAutocompleteCc))
      .perform(scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(ccRecipient))))
  }

  companion object {
    private val VERIFICATION_RESULT = VerificationResult(
      hasEncryptedParts = false,
      hasSignedParts = false,
      hasMixedSignatures = false,
      isPartialSigned = false,
      keyIdOfSigningKeys = emptyList(),
      hasBadSignatures = false
    )
  }
}
