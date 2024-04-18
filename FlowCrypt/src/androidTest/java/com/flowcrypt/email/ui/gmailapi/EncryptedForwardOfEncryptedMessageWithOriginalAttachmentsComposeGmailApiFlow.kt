/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.gmailapi

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.api.email.model.IncomingMessageInfo
import com.flowcrypt.email.api.retrofit.response.model.VerificationResult
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.junit.annotations.OutgoingMessageConfiguration
import com.flowcrypt.email.model.MessageEncryptionType
import com.flowcrypt.email.model.MessageType
import com.flowcrypt.email.rules.AddRecipientsToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseComposeGmailFlow
import com.flowcrypt.email.ui.base.BaseComposeScreenTest
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMultipart
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
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
@OutgoingMessageConfiguration(
  to = [BaseComposeGmailFlow.DEFAULT_TO_RECIPIENT],
  cc = [BaseComposeGmailFlow.DEFAULT_CC_RECIPIENT],
  bcc = [BaseComposeGmailFlow.DEFAULT_BCC_RECIPIENT],
  message = BaseComposeScreenTest.MESSAGE,
  subject = "",
  isNew = false
)
class EncryptedForwardOfEncryptedMessageWithOriginalAttachmentsComposeGmailApiFlow : BaseComposeGmailFlow() {
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
      .around(AddRecipientsToDatabaseRule(prepareRecipientsForTest()))
      .around(addLabelsToDatabaseRule)
      .around(activityScenarioRule)
      .around(ScreenshotTestRule())

  @Test
  @Ignore("Should be fixed before the next release")
  fun testSending() {
    //need to wait while the app loads the messages list
    Thread.sleep(2000)

    //click on the encrypted message
    onView(withId(R.id.recyclerViewMsgs))
      .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

    //wait the message details rendering
    Thread.sleep(1000)

    //click on forward
    onView(
      allOf(
        withId(R.id.layoutFwdButton),
        withParent(
          withParent(
            withParent(
              hasSibling(
                allOf(
                  withId(R.id.layoutHeader),
                  withChild(allOf(withId(R.id.textViewSubject), withText(SUBJECT_EXISTING_ENCRYPTED)))
                )
              )
            )
          )
        )
      )
    ).check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    val outgoingMessageConfiguration =
      requireNotNull(outgoingMessageConfigurationRule.outgoingMessageConfiguration)

    //need to wait while all action for forward case will be applied
    Thread.sleep(1000)

    fillData(outgoingMessageConfiguration)

    //enqueue outgoing message
    onView(withId(R.id.menuActionSend))
      .check(matches(isDisplayed()))
      .perform(click())

    //back to the message details screen
    pressBack()

    doAfterSendingChecks { _, rawMime, mimeMessage ->
      //check forward subject
      assertEquals(rawMime, "Fwd: $SUBJECT_EXISTING_ENCRYPTED", mimeMessage.subject)
      val multipart = mimeMessage.content as MimeMultipart
      assertEquals(3, multipart.count)

      //check forward text
      val encryptedMessagePart = multipart.getBodyPart(0)
      val expectedText = outgoingMessageConfiguration.message + IncomingMessageInfo(
        msgEntity = MessageEntity(
          email = "",
          folder = "",
          uid = 0,
          fromAddress = DEFAULT_FROM_RECIPIENT,
          subject = SUBJECT_EXISTING_ENCRYPTED,
          receivedDate = DATE_EXISTING_ENCRYPTED,
          toAddress = InternetAddress.toString(
            arrayOf(
              InternetAddress(
                EXISTING_MESSAGE_TO_RECIPIENT
              )
            )
          ),
          ccAddress = InternetAddress.toString(
            arrayOf(
              InternetAddress(
                EXISTING_MESSAGE_CC_RECIPIENT
              )
            )
          )
        ),
        encryptionType = MessageEncryptionType.ENCRYPTED,
        msgBlocks = emptyList(),
        subject = SUBJECT_EXISTING_ENCRYPTED,
        text = MESSAGE_EXISTING_ENCRYPTED,
        verificationResult = VerificationResult(
          hasEncryptedParts = false,
          hasSignedParts = false,
          hasMixedSignatures = false,
          isPartialSigned = false,
          keyIdOfSigningKeys = emptyList(),
          hasBadSignatures = false
        )
      ).toInitializationData(
        context = getTargetContext(),
        messageType = MessageType.FORWARD,
        accountEmail = addAccountToDatabaseRule.account.email,
        aliases = emptyList()
      ).body
      checkEncryptedMessagePart(
        bodyPart = encryptedMessagePart, expectedText = expectedText
      )

      //check forwarded attachments
      checkEncryptedAttachment(multipart.getBodyPart(1), ATTACHMENT_NAME_1, attachmentsDataCache[0])
      checkEncryptedAttachment(multipart.getBodyPart(2), ATTACHMENT_NAME_3, attachmentsDataCache[2])
    }
  }
}