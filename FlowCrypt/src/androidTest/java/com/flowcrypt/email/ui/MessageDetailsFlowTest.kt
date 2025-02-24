/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import android.text.format.DateUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToHolder
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.web.assertion.WebViewAssertions.webContent
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.matcher.DomMatchers.elementByXPath
import androidx.test.espresso.web.matcher.DomMatchers.withTextContent
import androidx.test.espresso.web.model.Atoms
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.email.model.IncomingMessageInfo
import com.flowcrypt.email.api.retrofit.response.model.DecryptErrorMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.DecryptedAttMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.GenericMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.InlineAttMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.MsgBlock
import com.flowcrypt.email.api.retrofit.response.model.PublicKeyMsgBlock
import com.flowcrypt.email.junit.annotations.NotReadyForCI
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withEmptyRecyclerView
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withMessageHeaderInfo
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withRecyclerViewItemCount
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.security.pgp.PgpKey
import com.flowcrypt.email.ui.activity.CreateMessageActivity
import com.flowcrypt.email.ui.adapter.MessageHeadersListAdapter
import com.flowcrypt.email.ui.adapter.PgpBadgeListAdapter
import com.flowcrypt.email.ui.base.BaseMessageDetailsFlowTest
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
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
class MessageDetailsFlowTest : BaseMessageDetailsFlowTest() {
  private val addPrivateKeyToDatabaseRule = AddPrivateKeyToDatabaseRule()
  private val simpleAttInfo = TestGeneralUtil.getObjectFromJson(
    "messages/attachments/simple_att.json",
    AttachmentInfo::class.java
  )
  private val encryptedAttInfo = TestGeneralUtil.getObjectFromJson(
    "messages/attachments/encrypted_att.json",
    AttachmentInfo::class.java
  )
  private val pubKeyAttInfo = TestGeneralUtil.getObjectFromJson(
    "messages/attachments/pub_key.json",
    AttachmentInfo::class.java
  )

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Before
  fun moveToHomeScreen() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.pressHome()
  }

  @Test
  fun testReplyButton() {
    val incomingMessageInfo = testStandardMsgPlaintextInternal()
    onView(withId(R.id.replyButton))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    intended(hasComponent(CreateMessageActivity::class.java.name))

    checkQuotesFunctionality(incomingMessageInfo)
  }

  @Test
  @Ignore("flaky")
  fun testTopReplyButton() {
    val incomingMessageInfo = testTopReplyAction(getResString(R.string.reply))
    checkQuotesFunctionality(incomingMessageInfo)
  }

  @Test
  @Ignore("flaky 2 3")
  fun testReplyAllButton() {
    val incomingMessageInfo = testStandardMsgPlaintextInternal()
    onView(withId(R.id.replyAllButton))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    intended(hasComponent(CreateMessageActivity::class.java.name))

    checkQuotesFunctionality(incomingMessageInfo)
  }

  @Test
  fun testFwdButton() {
    testStandardMsgPlaintextInternal()
    onView(withId(R.id.forwardButton))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testTopForwardButton() {
    testTopReplyAction(getResString(R.string.forward))
  }

  @Test
  fun testStandardMsgPlaintext() {
    testStandardMsgPlaintextInternal()
  }

  @Test
  fun testStandardMsgPlaintextWithOneAttachment() {
    baseCheckWithAtt(
      incomingMsgInfo = getMsgInfo(
        path = "messages/info/standard_msg_info_plaintext_with_one_att.json",
        mimeMsgPath = "messages/mime/standard_msg_info_plaintext_with_one_att.txt",
        simpleAttInfo,
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      ), att = simpleAttInfo
    )

    onView(withId(R.id.imageButtonPreviewAtt))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testEncryptedMsgPlaintext() {
    baseCheck(
      getMsgInfo(
        path = "messages/info/encrypted_msg_info_text.json",
        mimeMsgPath = "messages/mime/encrypted_msg_info_plain_text.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )
  }

  @Test
  fun testEncryptedBigInlineAtt() {
    IdlingPolicies.setIdlingResourceTimeout(3, TimeUnit.MINUTES)
    baseCheck(
      incomingMsgInfo = getMsgInfo(
        path = "messages/info/encrypted_msg_big_inline_att.json",
        mimeMsgPath = "messages/mime/encrypted_msg_big_inline_att.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      ),
      checkWebContent = false
    ) {
      //we need additional time to decrypt a message
      Thread.sleep(30000)
    }
  }

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testDecryptionError_KEY_MISMATCH_MissingKeyErrorImportKey() {
    testMissingKey(
      getMsgInfo(
        path = "messages/info/encrypted_msg_info_text_with_missing_key.json",
        mimeMsgPath = "messages/mime/encrypted_msg_info_text_with_missing_key.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )

    val privateKey =
      TestGeneralUtil.readFileFromAssetsAsString(TestConstants.DEFAULT_SECOND_KEY_PRV_STRONG)
    addTextToClipboard("private key", privateKey)
    onView(withId(R.id.buttonImportPrivateKey))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.buttonLoadFromClipboard))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.editTextKeyPassword))
      .perform(
        scrollTo(),
        typeText(TestConstants.DEFAULT_STRONG_PASSWORD),
        closeSoftKeyboard()
      )
    onView(withId(R.id.buttonPositiveAction))
      .perform(scrollTo(), click())

    val incomingMsgInfoFixed =
      TestGeneralUtil.getObjectFromJson(
        "messages/info/encrypted_msg_info_text_with_missing_key_fixed.json",
        IncomingMessageInfo::class.java
      )
    incomingMsgInfoFixed?.msgBlocks?.firstOrNull { it.type == MsgBlock.Type.PLAIN_HTML }?.let {
      checkWebViewText(it.content)
    }
  }

  @Test
  fun testDecryptionError_KEY_MISMATCH_MissingPubKey() {
    testMissingKey(
      getMsgInfo(
        path = "messages/info/encrypted_msg_info_text_error_one_pub_key.json",
        mimeMsgPath = "messages/mime/encrypted_msg_info_plain_text_error_one_pub_key.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )
  }

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testDecryptionError_FORMAT_BadlyFormattedMsg() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_text_error_badly_formatted.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_plain_text_error_badly_formatted.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as DecryptErrorMsgBlock
    val decryptError = block.decryptErr
    val formatErrorMsg = (getResString(
      R.string.decrypt_error_message_badly_formatted,
      getResString(R.string.app_name)
    ) + "\n\n" + decryptError?.details?.type + ": " + decryptError?.details?.message)

    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(containsString(formatErrorMsg))))

    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testDecryptionError_NO_MDC() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_error_no_mdc.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_error_no_mdc.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    Thread.sleep(1000)

    val block = msgInfo.msgBlocks?.get(1) as DecryptErrorMsgBlock
    val decryptError = block.decryptErr
    val errorMsg = getResString(
      R.string.could_not_decrypt_message_due_to_error,
      decryptError?.details?.type.toString() + ": " + getResString(R.string.decrypt_error_message_no_mdc)
    )
    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))
    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  @Test
  fun testMissingKeyErrorChooseSinglePubKey() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_text_with_missing_key.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_text_with_missing_key.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    testMissingKey(msgInfo)

    onView(withId(R.id.buttonSendOwnPublicKey))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewMessage)).check(
      matches(
        withText(
          getQuantityString(
            R.plurals
              .tell_sender_to_update_their_settings, 1
          )
        )
      )
    )
    onView(withId(R.id.buttonOk))
      .check(matches(isDisplayed()))
      .perform(click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testMissingKeyErrorChooseFromFewPubKeys() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_text_with_missing_key.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_text_with_missing_key.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    testMissingKey(msgInfo)

    onView(withId(R.id.buttonSendOwnPublicKey))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      addAccountToDatabaseRule
        .account, TestConstants.DEFAULT_SECOND_KEY_PRV_STRONG,
      TestConstants.DEFAULT_STRONG_PASSWORD, KeyImportDetails.SourceType.EMAIL
    )


    val msg = getQuantityString(
      R.plurals
        .tell_sender_to_update_their_settings, 2
    )

    onView(withId(R.id.textViewMessage))
      .check(matches(withText(msg)))
    onData(anything())
      .inAdapterView(withId(R.id.listViewKeys))
      .atPosition(1)
      .perform(click())
    onView(withId(R.id.buttonOk))
      .check(matches(isDisplayed()))
      .perform(click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testEncryptedMsgTextWithOneAttachment() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_text_with_one_att.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_plain_text_with_one_att.txt",
      encryptedAttInfo,
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheckWithAtt(msgInfo, encryptedAttInfo)
  }

  @Test
  fun testEncryptedMsgPlaintextWithPubKey() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_text_with_pub_key.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_text_with_pub_key.txt",
      pubKeyAttInfo,
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheckWithAtt(msgInfo, pubKeyAttInfo)

    val pgpKeyRingDetails =
      PrivateKeysManager.getPgpKeyDetailsFromAssets("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    val email = requireNotNull(pgpKeyRingDetails.getPrimaryInternetAddress()).address
    onView(withId(R.id.textViewKeyOwnerTemplate)).check(
      matches(withText(getResString(R.string.template_message_part_public_key_owner, email)))
    )

    onView(withId(R.id.textViewFingerprintTemplate)).check(
      matches(
        withText(
          getHtmlString(
            getResString(
              R.string.template_message_part_public_key_fingerprint,
              GeneralUtil.doSectionsInText(" ", pgpKeyRingDetails.fingerprint, 4)!!
            )
          )
        )
      )
    )

    onView(withId(R.id.textViewManualImportWarning)).check(
      matches(withText(getResString(R.string.warning_about_manual_import, email)))
    )

    val block = msgInfo?.msgBlocks?.get(1) as PublicKeyMsgBlock

    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.switchShowPublicKey))
      .check(matches(not(isChecked())))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(isDisplayed()))
    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(withText(TestGeneralUtil.replaceVersionInKey(block.keyDetails?.publicKey))))
    onView(withId(R.id.switchShowPublicKey))
      .check(matches(isChecked()))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(not(isDisplayed())))

    onView(withId(R.id.buttonKeyAction))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    onView(withId(R.id.buttonKeyAction))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testSavingPubKeyFromMessagePart() {
    val msgInfo = getMsgInfo(
      path = "messages/info/standard_msg_with_pub_key.json",
      mimeMsgPath = "messages/mime/standard_msg_with_pub_key.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    baseCheck(msgInfo)

    val pgpKeyRingDetails =
      PrivateKeysManager.getPgpKeyDetailsFromAssets("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    val primaryAddress =
      requireNotNull(pgpKeyRingDetails.getPrimaryInternetAddress()).address.lowercase()
    val recipientBeforeSaving = runBlocking {
      roomDatabase.recipientDao().getRecipientWithPubKeysByEmailSuspend(primaryAddress)
    }
    assertArrayEquals(
      emptyArray(),
      recipientBeforeSaving?.publicKeys?.toTypedArray() ?: emptyArray()
    )

    onView(withId(R.id.buttonKeyAction))
      .check(matches(isDisplayed()))
      .check(matches(withText(R.string.import_pub_key)))
      .perform(scrollTo(), click())
    onView(withId(R.id.buttonKeyAction))
      .check(matches(not(isDisplayed())))

    //need to wait database sync
    Thread.sleep(1000)

    val recipientAfterSaving = runBlocking {
      roomDatabase.recipientDao().getRecipientWithPubKeysByEmailSuspend(primaryAddress)
    }
    assertNotNull(recipientAfterSaving)
    val publicKeyByteArray =
      requireNotNull(recipientAfterSaving?.publicKeys?.firstOrNull()?.publicKey)
    val pgpKeyDetailsOfDatabaseEntity =
      PgpKey.parseKeys(publicKeyByteArray).pgpKeyDetailsList.firstOrNull()
    assertEquals(pgpKeyRingDetails.fingerprint, pgpKeyDetailsOfDatabaseEntity?.fingerprint)
  }

  @Test
  fun test8bitEncodingUtf8() {
    baseCheck(
      getMsgInfo(
        path = "messages/info/msg_info_8bit-utf8.json",
        mimeMsgPath = "messages/mime/8bit-utf8.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )
  }

  @Test
  fun testToLabelForTwoRecipients() {
    baseCheck(
      getMsgInfo(
        path = "messages/info/standard_msg_info_plaintext_to_2_recipients.json",
        mimeMsgPath = "messages/mime/standard_msg_info_plaintext_to_2_recipients.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )

    val subText = getResString(R.string.me) + ", User"

    onView(withId(R.id.tVTo))
      .check(matches(withText(getResString(R.string.to_receiver, subText))))
  }

  @Test
  fun testMsgDetailsSingleToReplyToCC() {
    val msgInfo = getMsgInfo(
      path = "messages/info/standard_msg_info_plaintext_single_to_replyto_cc.json",
      mimeMsgPath = "messages/mime/standard_msg_info_plaintext_single_to_replyto_to_cc.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    onView(withId(R.id.rVMsgDetails))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.iBShowDetails))
      .perform(scrollTo(), click())
    onView(withId(R.id.rVMsgDetails))
      .check(matches(isDisplayed()))
      .check(matches(withRecyclerViewItemCount(5)))

    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withMessageHeaderInfo(
            MessageHeadersListAdapter.Header(
              name = getResString(R.string.from),
              value = "Denis Bondarenko <denbond7@flowcrypt.test>"
            )
          )
        )
      )
    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withMessageHeaderInfo(
            MessageHeadersListAdapter.Header(
              name = getResString(R.string.reply_to),
              value = "Denis Bondarenko <denbond7@flowcrypt.test>"
            )
          )
        )
      )
    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withMessageHeaderInfo(
            MessageHeadersListAdapter.Header(
              name = getResString(R.string.to),
              value = "default@flowcrypt.test"
            )
          )
        )
      )
    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withMessageHeaderInfo(
            MessageHeadersListAdapter.Header(
              name = getResString(R.string.cc),
              value = "ccuser@test"
            )
          )
        )
      )

    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
        DateUtils.FORMAT_SHOW_YEAR
    val datetime = DateUtils.formatDateTime(
      getTargetContext(),
      msgInfo?.msgEntity?.receivedDate ?: 0, flags
    )

    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withMessageHeaderInfo(
            MessageHeadersListAdapter.Header(
              name = getResString(R.string.date),
              value = datetime
            )
          )
        )
      )
  }

  @Test
  //more details here https://github.com/FlowCrypt/flowcrypt-android/issues/1475
  fun testEncryptedMsgHiddenAttPGPMimeModifiedByGoogle() {
    val attInfo = TestGeneralUtil.getObjectFromJson(
      "messages/attachments/hidden_att_pgp_mime_modified_by_google.json",
      AttachmentInfo::class.java
    )

    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_hidden_att_pgp_mime_modified_by_google.json",
      mimeMsgPath = "messages/mime/encrypted_msg_hidden_att_pgp_mime_modified_by_google.txt",
      attInfo,
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)
    onView(withId(R.id.rVAttachments))
      .check(matches(withEmptyRecyclerView()))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testEncryptedSymantecEncryptionServerMessageFormat() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_symantec_encryption_server_message_format.json",
      mimeMsgPath = "messages/mime/encrypted_msg_symantec_encryption_server_message_format.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)
  }

  @Test
  fun testShowParsePubKeyError() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_inline_pub_key_parse_error.json",
      mimeMsgPath = "messages/mime/encrypted_msg_inline_pub_key_parse_error.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as PublicKeyMsgBlock
    val errorMsg = getResString(
      R.string.msg_contains_not_valid_pub_key, requireNotNull(block.error?.errorMsg)
    )
    Thread.sleep(1000)//temporary added to complete test. Idling issue
    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))
    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  @Test
  fun testSignedArmoredMsg() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signed_msg_armored.json",
      mimeMsgPath = "messages/mime/signed_msg_armored.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)
  }

  @Test
  fun testSignedMsgClearSign() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signed_msg_clearsign.json",
      mimeMsgPath = "messages/mime/signed_msg_clearsign.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)
  }

  @Test
  @FlakyTest
  @NotReadyForCI
  fun testSignedMsgClearSignBroken() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signed_msg_clearsign_broken.json",
      mimeMsgPath = "messages/mime/signed_msg_clearsign_broken.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as GenericMsgBlock
    val errorMsg = getResString(
      R.string.msg_contains_not_valid_block,
      block.type.toString(),
      requireNotNull(block.error?.errorMsg)
    )
    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))
    matchReplyButtons(details)
  }

  @Test
  fun testMsgWithKeyThatHasNoSuitableEncryptionSubKeys() {
    val msgInfo = getMsgInfo(
      path = "messages/info/standard_msg_with_pub_key_that_has_no_suitable_encryption_subkeys.json",
      mimeMsgPath = "messages/mime/standard_msg_with_pub_key_that_has_no_suitable_encryption_subkeys.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    onView(allOf(withId(R.id.textViewStatus), hasSibling(withId(R.id.switchShowPublicKey))))
      .check(matches(withText(getResString(R.string.cannot_be_used_for_encryption))))
    onView(withId(R.id.buttonKeyAction))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.textViewManualImportWarning))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testSignatureVerificationInbandMissingPubKeyEncryptedAndSigned() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_missing_pub_key_encrypted_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_missing_pub_key_encrypted_signed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.CAN_NOT_VERIFY_SIGNATURE
    )
  }

  @Test
  fun testSignatureVerificationInbandMissingPubKeyOnlySigned() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_missing_pub_key_only_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_missing_pub_key_only_signed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.CAN_NOT_VERIFY_SIGNATURE
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlySignedMixed() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_second.asc")
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_only_signed_mixed.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_only_signed_mixed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.MIXED_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandEncryptedAndSignedMixed() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_second.asc")
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_encrypted_signed_mixed.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_encrypted_signed_mixed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.MIXED_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlySignedPartially() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_only_signed_partially.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_only_signed_partially.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.ONLY_PARTIALLY_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandEncryptedSignedPartially() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_encrypted_signed_partially.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_encrypted_signed_partially.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.ONLY_PARTIALLY_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandEncryptedSigned() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_encrypted_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_encrypted_signed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlySigned() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_only_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_only_signed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlyEncrypted() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_inband_only_encrypted.json",
      mimeMsgPath = "messages/mime/signature_verification_inband_only_encrypted.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationDetachedSignatureJustContent() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_detached_only_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_detached_only_signed.txt",
      useCrLfForMime = true,
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationDetachedSignatureContentAndPublicKey() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_detached_only_signed_public_key.json",
      mimeMsgPath = "messages/mime/signature_verification_detached_only_signed_public_key.txt",
      useCrLfForMime = true,
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationDetachedSignatureContentAndPublicKeyAndAttachment() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_detached_only_signed_public_key_attachment.json",
      mimeMsgPath = "messages/mime/signature_verification_detached_only_signed_public_key_attachment.txt",
      useCrLfForMime = true,
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testMessageWithBrokenBase64() {
    baseCheck(
      getMsgInfo(
        path = "messages/info/message_with_broken_base64.json",
        mimeMsgPath = "messages/mime/message_with_broken_base64.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )
  }

  @Test
  fun testViewsVisibilityOfOutgoingMessages() {
    val incomingMsgInfo = requireNotNull(
      getMsgInfo(
        path = "messages/info/standard_msg_info_plaintext_outbox.json",
        mimeMsgPath = "messages/mime/standard_msg_info_plaintext.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )

    val msgEntity = incomingMsgInfo.msgEntity
    launchActivity(msgEntity)

    onView(withId(R.id.imageButtonReplyAll))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.imageButtonMoreOptions))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.layoutReplyButtons))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testSignatureVerificationCleartextOnlySigned() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_cleartext_only_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_cleartext_only_signed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationCleartextOnlySignedPartially() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_cleartext_only_signed_partially.json",
      mimeMsgPath = "messages/mime/signature_verification_cleartext_only_signed_partially.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.ONLY_PARTIALLY_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationCleartextOnlySignedMixed() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_second.asc")
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_cleartext_only_signed_mixed.json",
      mimeMsgPath = "messages/mime/signature_verification_cleartext_only_signed_mixed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.MIXED_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationCleartextMissingPubKeyOnlySigned() {
    val msgInfo = getMsgInfo(
      path = "messages/info/signature_verification_cleartext_missing_pub_key_only_signed.json",
      mimeMsgPath = "messages/mime/signature_verification_cleartext_missing_pub_key_only_signed.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.CAN_NOT_VERIFY_SIGNATURE
    )
  }

  @Test
  fun testEncryptedMsgMultipartAlternativePGPInTextPlain() {
    val msgInfo = getMsgInfo(
      path = "messages/info/encrypted_msg_info_multipart_alternative_pgp_in_text_plain.json",
      mimeMsgPath = "messages/mime/encrypted_msg_info_multipart_alternative_pgp_in_text_plain.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    baseCheck(msgInfo)

    assertEquals(1, msgInfo?.msgBlocks?.size)
    MatcherAssert.assertThat(msgInfo?.msgBlocks?.first(), instanceOf(GenericMsgBlock::class.java))

    msgInfo?.msgBlocks?.firstOrNull { it.type == MsgBlock.Type.PLAIN_HTML }?.let {
      checkWebViewText(it.content)
    }
  }

  @Test
  fun testDownloadingInlinedAttachmentInOpenPGPMIME() {
    val msgInfo = getMsgInfo(
      path = "messages/info/open_pgp_mime_with_inlined_attachments.json",
      mimeMsgPath = "messages/mime/open_pgp_mime_with_inlined_attachments.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    val attachmentName = "thumb_up.png"
    val downloadCompleteLabel = getResString(R.string.download_complete)
    val uiAutomatorTimeout = 5000L

    baseCheck(msgInfo)
    Thread.sleep(1000)
    onView(
      allOf(
        withId(R.id.imageButtonDownloadAtt), withParent(
          allOf(withId(R.id.actionButtons), hasSibling(withText(attachmentName)))
        )
      )
    )
      .check(matches(isDisplayed()))
      .perform(click())


    //Unfortunately, due to the Scoped Storage,
    //we don't have direct access to the file system and we can't check that a new file was created.
    //That's why we will use UIAutomator to check that we have a notification
    //with text == "Download complete"
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.openNotification()
    //wait until we have a notification in the notification bar
    device.wait(Until.hasObject(By.text(attachmentName)), uiAutomatorTimeout)
    //check that we have a notification with text == "Download complete"
    val attachmentNameUiObject2 = device.findObject(By.text(attachmentName))
    val downloadCompleteLabelUiObject2 = device.findObject(By.text(downloadCompleteLabel))
    assertEquals(attachmentName, attachmentNameUiObject2.text)
    assertEquals(downloadCompleteLabel, downloadCompleteLabelUiObject2.text)
    device.pressHome()
  }

  @Test
  fun testEncryptedSubjectOpenPgpMIMESigned() {
    baseCheck(
      getMsgInfo(
        path = "messages/info/encrypted_subject_openpgp_mime_signed.json",
        mimeMsgPath = "messages/mime/encrypted_subject_openpgp_mime_signed.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )
  }

  @Test
  fun testEncryptedSubjectOpenPgpMIMENotSigned() {
    baseCheck(
      getMsgInfo(
        path = "messages/info/encrypted_subject_openpgp_mime_not_signed.json",
        mimeMsgPath = "messages/mime/encrypted_subject_openpgp_mime_not_signed.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )
  }

  @Test
  fun testDisplayingTinyInlineAttachment() {
    val imgSrc = "data:image/png;%20name=usvT7OBFN5Dir6yr.png;base64,iVBORw0KGgoAAAANSUhEUg" +
        "AAABAAAAAQCAIAAACQkWg2AAAAA3NCSVQICAjb4U/gAAAAm0lEQVQo%0akZVSwRHDIAyTcizVNzN0hWYmW" +
        "CEz8M5YyqNpMIaQq18+SbZlAyUBIFY8hZAAhK/6zXwRmz5Wd1EU%0ahES09GOEmsZ0JmWdgEvjsrSimHqw" +
        "KWD0E1Q8aCyVwaH6FuOltWe+xsdYhugkBgXacw96S2IGwJs3%0asWzdQZw2/smCZ6ROSzu57nCiJEir7kZ" +
        "J7qs6b7a9kPjv9z4ApO9C8yTEUZ4AAAAASUVORK5CYII="
    val xpath = "/html/body/div/div[2]/div/img"

    baseCheck(
      getMsgInfo(
        path = "messages/info/tiny_inline_attachment.json",
        mimeMsgPath = "messages/mime/tiny_inline_attachment.txt",
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      )
    )

    Thread.sleep(2000)

    val imgSrcViaJavaScript = Atoms.script(
      "function getCurrentUrl() {return document.evaluate('$xpath', document, null," +
          " XPathResult.ANY_TYPE, null).iterateNext().getAttribute('src');}",
      Atoms.castOrDie(String::class.java)
    )

    onWebView(withId(R.id.emailWebView)).forceJavascriptEnabled()
    onWebView(withId(R.id.emailWebView))
      .withElement(findElement(Locator.XPATH, xpath))
      .check(webMatches(imgSrcViaJavaScript, equalTo(imgSrc)))
  }

  @Test
  fun testCorrectHandlingOfOpenPGPMIME() {
    val msgInfo = getMsgInfo(
      path = "messages/info/open_pgp_mime_with_inlined_attachments.json",
      mimeMsgPath = "messages/mime/open_pgp_mime_with_inlined_attachments.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    val attachmentMessageBlock = msgInfo?.msgBlocks?.get(2) as DecryptedAttMsgBlock

    assertEquals(4, msgInfo.msgBlocks?.size)
    assertEquals(MsgBlock.Type.PLAIN_HTML, msgInfo.msgBlocks?.get(0)?.type)
    assertEquals(MsgBlock.Type.ENCRYPTED_SUBJECT, msgInfo.msgBlocks?.get(1)?.type)
    assertEquals(MsgBlock.Type.DECRYPTED_ATT, attachmentMessageBlock.type)
    assertEquals(MsgBlock.Type.PUBLIC_KEY, msgInfo.msgBlocks?.get(3)?.type)

    baseCheck(msgInfo)

    onWebView(withId(R.id.emailWebView)).forceJavascriptEnabled()
    onWebView(withId(R.id.emailWebView))
      .check(
        webContent(
          elementByXPath(
            "/html/body",
            withTextContent(not(containsString("Version: 1")))
          )
        )
      )

    Thread.sleep(1000)

    //check that we have only one attachment
    onView(withId(R.id.rVAttachments))
      .check(matches(withRecyclerViewItemCount(1)))

    onView(withId(R.id.rVAttachments))
      .perform(
        RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
          allOf(
            hasDescendant(
              allOf(
                withId(R.id.textViewAttachmentName),
                withText(attachmentMessageBlock.attMeta.name)
              )
            ),
            hasDescendant(withId(R.id.imageButtonPreviewAtt)),
            hasDescendant(withId(R.id.imageButtonDownloadAtt)),
          )
        )
      )
  }

  @Test
  fun testCorrectHandlingOfOpenPGPInlineWithInlineImage() {
    val msgInfo = getMsgInfo(
      path = "messages/info/open_pgp_inline_with_inline_image.json",
      mimeMsgPath = "messages/mime/open_pgp_inline_with_inline_image.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )

    val inlineAttachmentMessageBlock =
      msgInfo?.msgBlocks?.filterIsInstance<InlineAttMsgBlock>()?.first()

    assertEquals(2, msgInfo?.msgBlocks?.size)
    assertEquals(MsgBlock.Type.PLAIN_HTML, msgInfo?.msgBlocks?.get(0)?.type)
    assertEquals(MsgBlock.Type.INLINE_ATT, msgInfo?.msgBlocks?.get(1)?.type)

    baseCheck(msgInfo)

    Thread.sleep(1000)

    //check that we have only one attachment
    onView(withId(R.id.rVAttachments))
      .check(matches(withRecyclerViewItemCount(1)))

    onView(withId(R.id.rVAttachments))
      .perform(
        RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
          allOf(
            hasDescendant(
              allOf(
                withId(R.id.textViewAttachmentName),
                withText(inlineAttachmentMessageBlock?.attMeta?.name)
              )
            ),
            hasDescendant(withId(R.id.imageButtonPreviewAtt)),
            hasDescendant(withId(R.id.imageButtonDownloadAtt)),
          )
        )
      )
  }

  @Test
  fun testHiddenCryptupReplyDivForFESReplyAllCase() {
    val incomingMessageInfo = getMsgInfo(
      path = "messages/info/cryptup_reply_div_should_be_hidden_in_FES_messages.json",
      mimeMsgPath = "messages/mime/cryptup_reply_div_should_be_hidden_in_FES_messages.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(incomingMessageInfo)

    onWebView(withId(R.id.emailWebView))
      .check(
        webContent(
          elementByXPath(
            "/html/body",
            withTextContent(not(containsString("cryptup_reply")))
          )
        )
      )
  }

  @Test
  fun testSPFSoftFail() {
    val incomingMessageInfo = getMsgInfo(
      //we don't care about a separate version for JSON as we will match only a TextView
      path = "messages/info/standard_msg_info_plaintext.json",
      mimeMsgPath = "messages/mime/standard_msg_info_plaintext_spf_soft_fail.txt",
      accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
    )
    baseCheck(incomingMessageInfo)
    onView(withId(R.id.tVTo))
      .check(matches(withText(getResString(R.string.to_receiver, getResString(R.string.me)))))

    onView(withText(R.string.spf_soft_fail_warning))
      .check(matches(isDisplayed()))
  }

  /**
   * https://github.com/FlowCrypt/flowcrypt-android/issues/2135
   */
  @Test
  fun testWarningPromptWhenUserWishesToDownloadPotentiallyDangerousFile() {
    val dangerousAttachmentInfo = simpleAttInfo?.copy(
      name = "android.apk",
      type = "application/vnd.android.package-archive; name=\"android.apk\""
    )
    baseCheckWithAtt(
      incomingMsgInfo = getMsgInfo(
        path = "messages/info/standard_msg_info_plaintext_with_one_att.json",
        mimeMsgPath = "messages/mime/standard_msg_info_plaintext_with_one_dangerous_att.txt",
        dangerousAttachmentInfo,
        accountEntity = addAccountToDatabaseRule.accountEntityWithDecryptedInfo
      ), att = dangerousAttachmentInfo
    )

    onView(withId(R.id.imageButtonDownloadAtt))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    isDialogWithTextDisplayed(
      decorView,
      getResString(R.string.download_dangerous_file_warning)
    )
  }

  private fun checkQuotesFunctionality(incomingMessageInfo: IncomingMessageInfo?) {
    onView(withId(R.id.iBShowQuotedText))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    onView(withId(R.id.editTextEmailMessage))
      .check(matches(isDisplayed()))
      .check(matches(withText(EmailUtil.genReplyContent(incomingMessageInfo))))
  }
}
