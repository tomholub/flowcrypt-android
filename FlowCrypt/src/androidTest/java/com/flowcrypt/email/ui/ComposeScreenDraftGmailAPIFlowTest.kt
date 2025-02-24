/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import android.util.Base64
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.api.retrofit.response.model.ClientConfiguration
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.jetpack.viewmodel.DraftViewModel
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.AddLabelsToDatabaseRule
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseComposeScreenTest
import com.flowcrypt.email.util.AccountDaoManager
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.model.Draft
import com.google.api.services.gmail.model.ListSendAsResponse
import com.google.api.services.gmail.model.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.util.Properties
import java.util.zip.GZIPInputStream
import kotlin.random.Random

/**
 * @author Denys Bondarenko
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ComposeScreenDraftGmailAPIFlowTest : BaseComposeScreenTest() {
  private val accountEntity = AccountDaoManager.getDefaultAccountDao()
    .copy(
      accountType = AccountEntity.ACCOUNT_TYPE_GOOGLE,
      clientConfiguration = ClientConfiguration(
        flags = listOf(
          ClientConfiguration.ConfigurationProperty.NO_PRV_CREATE,
          ClientConfiguration.ConfigurationProperty.NO_PRV_BACKUP,
          ClientConfiguration.ConfigurationProperty.NO_ATTESTER_SUBMIT,
          ClientConfiguration.ConfigurationProperty.PRV_AUTOIMPORT_OR_AUTOGEN,
          ClientConfiguration.ConfigurationProperty.FORBID_STORING_PASS_PHRASE,
          ClientConfiguration.ConfigurationProperty.RESTRICT_ANDROID_ATTACHMENT_HANDLING,
        ),
        keyManagerUrl = "https://flowcrypt.test/",
      ),
      useAPI = true,
      useCustomerFesUrl = true
    )

  override val addAccountToDatabaseRule: AddAccountToDatabaseRule =
    AddAccountToDatabaseRule(accountEntity)

  private val addPrivateKeyToDatabaseRule =
    AddPrivateKeyToDatabaseRule(addAccountToDatabaseRule.account)

  private val mockWebServerRule = FlowCryptMockWebServerRule(
    TestConstants.MOCK_WEB_SERVER_PORT,
    object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        when {
          request.path == "/gmail/v1/users/me/settings/sendAs" -> {
            return MockResponse()
              .setResponseCode(HttpURLConnection.HTTP_OK)
              .setBody(
                ListSendAsResponse().apply {
                  factory = GsonFactory.getDefaultInstance()
                  sendAs = emptyList()
                }.toString()
              )
          }

          request.method == "POST" && request.path == "/gmail/v1/users/me/drafts" -> {
            val gzipInputStream = GZIPInputStream(request.body.inputStream())
            val draft = JsonObjectParser(GsonFactory.getDefaultInstance()).parseAndClose(
              InputStreamReader(gzipInputStream),
              Draft::class.java
            )
            val rawMimeMessageAsByteArray = Base64.decode(
              draft.message.raw,
              Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val mimeMessage = MimeMessage(
              Session.getInstance(Properties()),
              rawMimeMessageAsByteArray.inputStream()
            )

            return if (MESSAGE_SUBJECT == mimeMessage.subject) {
              MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(
                  Draft().apply {
                    factory = GsonFactory.getDefaultInstance()
                    id = DRAFT_ID
                    message = Message().apply {
                      id = MESSAGE_ID
                      threadId = THREAD_ID
                      labelIds = listOf(LABEL_DRAFT)
                    }
                  }.toString()
                )
            } else {
              MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
            }
          }

          request.path == "/gmail/v1/users/me/messages/$MESSAGE_ID?fields=id,threadId,historyId&format=full" -> {
            return MockResponse()
              .setResponseCode(HttpURLConnection.HTTP_OK)
              .setBody(Message().apply {
                factory = GsonFactory.getDefaultInstance()
                id = MESSAGE_ID
                threadId = THREAD_ID
                labelIds = listOf(LABEL_DRAFT)
                historyId = BigInteger.valueOf(Random.nextLong())
              }.toString()).apply {
                isDraftSaved = true
              }
          }

          else -> return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
        }
      }
    })

  private var isDraftSaved = false

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(mockWebServerRule)
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(
      AddLabelsToDatabaseRule(
        account = addAccountToDatabaseRule.account,
        folders = listOf(
          LocalFolder(
            account = addAccountToDatabaseRule.account.email,
            fullName = "Draft",
            folderAlias = "Draft",
            attributes = listOf("\\HasNoChildren", "\\Draft")
          )
        )
      )
    )
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Test
  @Ignore("flaky 8")
  fun testSavingDraftViaGmailAPI() {
    activeActivityRule?.launch(intent)
    registerAllIdlingResources()

    onView(withId(R.id.editTextEmailSubject))
      .check(matches(isDisplayed()))
      .perform(
        scrollTo(),
        click(),
        typeText(MESSAGE_SUBJECT)
      )

    Thread.sleep(DraftViewModel.DELAY_TIMEOUT * 2)
    assertTrue(isDraftSaved)
  }

  companion object {
    const val DRAFT_ID = "r5555555555555555555"
    const val MESSAGE_ID = "5555555555555555"
    const val THREAD_ID = "1111111111111111"
    const val LABEL_DRAFT = "Draft"
    const val MESSAGE_SUBJECT = "Some subject"
  }
}
