/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.gmailapi

import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.rules.AddLabelsToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.adapter.GmailApiLabelsListAdapter
import com.flowcrypt.email.ui.base.BaseGmailApiTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.Matchers.allOf
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
@FlowCryptTestSettings(useCommonIdling = false)
class ThreadsListGmailApiFlowTest : BaseGmailApiTest(
  BASE_ACCOUNT_ENTITY.copy(useConversationMode = true)
) {
  override val mockWebServerRule =
    FlowCryptMockWebServerRule(TestConstants.MOCK_WEB_SERVER_PORT, object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return when {
          else -> handleCommonAPICalls(request)
        }
      }
    })

  private val customLabelsRule = AddLabelsToDatabaseRule(
    account = accountEntity, folders = CUSTOM_LABELS.map {
      LocalFolder(
        account = addAccountToDatabaseRule.account.email,
        fullName = it.name,
        folderAlias = it.name,
        isCustom = true,
        labelColor = it.backgroundColor,
        textColor = it.textColor,
        attributes = listOf("\\HasNoChildren")
      )
    }
  )

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(mockWebServerRule)
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(addLabelsToDatabaseRule)
    .around(customLabelsRule)
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  @Test
  @Ignore("flaky")
  fun testShowCorrectThreadsDetailsInList() {
    //need to wait while the app loads the thread list
    waitForObjectWithText(SUBJECT_EXISTING_STANDARD, TimeUnit.SECONDS.toMillis(10))

    //test thread with 2 standard messages with attachments
    checkThreadRowDetails(
      subject = SUBJECT_EXISTING_STANDARD,
      senderPattern = "From (2)",
      hasAttachments = true,
      hasPgp = false
    )

    //test thread with 2 encrypted messages with attachments
    checkThreadRowDetails(
      subject = SUBJECT_EXISTING_ENCRYPTED,
      senderPattern = "From (2)",
      hasAttachments = true,
      hasPgp = true
    )

    //test thread with 2 standard messages without attachments
    checkThreadRowDetails(
      subject = SUBJECT_NO_ATTACHMENTS,
      senderPattern = "From (2)",
      hasAttachments = false,
      hasPgp = false
    )

    //test thread with 1 message in thread
    checkThreadRowDetails(
      subject = SUBJECT_SINGLE_STANDARD,
      senderPattern = "From",
      hasAttachments = true,
      hasPgp = false
    )

    //test thread with a few messages in thread + 1 draft
    checkThreadRowDetails(
      subject = SUBJECT_FEW_MESSAGES_WITH_SINGLE_DRAFT,
      senderPattern = "From, me, Draft (3)",
      hasAttachments = true,
      hasPgp = false
    )

    //test thread with a few messages in thread + few draft
    checkThreadRowDetails(
      subject = SUBJECT_FEW_MESSAGES_WITH_FEW_DRAFTS,
      senderPattern = "From, Drafts(2) (4)",
      hasAttachments = true,
      hasPgp = false
    )

    //test thread with one message in thread + few draft
    checkThreadRowDetails(
      subject = SUBJECT_ONE_MESSAGE_WITH_FEW_DRAFTS,
      senderPattern = "From, Drafts(2) (3)",
      hasAttachments = true,
      hasPgp = false
    )
  }

  private fun checkThreadRowDetails(
    subject: String,
    senderPattern: String,
    hasAttachments: Boolean,
    hasPgp: Boolean,
  ) {
    onView(withId(R.id.recyclerViewMsgs))
      .perform(
        RecyclerViewActions.scrollTo<ViewHolder>(
          allOf(
            hasDescendant(
              allOf(
                withId(R.id.textViewSubject),
                withText(subject)
              ),
            ),
            hasDescendant(
              allOf(
                withId(R.id.textViewSenderAddress),
                withText(senderPattern)
              ),
            ),
            hasDescendant(
              allOf(
                withId(R.id.imageViewAtts),
                withEffectiveVisibility(
                  if (hasAttachments) {
                    ViewMatchers.Visibility.VISIBLE
                  } else {
                    ViewMatchers.Visibility.GONE
                  }
                )
              ),
            ),
            hasDescendant(
              allOf(
                withId(R.id.viewHasPgp),
                withEffectiveVisibility(
                  if (hasPgp) {
                    ViewMatchers.Visibility.VISIBLE
                  } else {
                    ViewMatchers.Visibility.GONE
                  }
                )
              ),
            ),
          )
        )
      )
  }

  companion object {
    private val CUSTOM_LABELS = listOf(
      GmailApiLabelsListAdapter.Label("LABEL_1", "#ff2323ff", "#FFFFFF"),
      GmailApiLabelsListAdapter.Label("LABEL_2", "#FFD14836", "#FFFFFF"),
      GmailApiLabelsListAdapter.Label("LABEL_3", "#FFFFEB3B", "#FFFFFF"),
      GmailApiLabelsListAdapter.Label("LABEL_4", "#FF00EB3B", "#FFFFFF"),
    )
  }
}