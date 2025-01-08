/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.gmailapi

import android.text.format.DateUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToHolder
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.junit.annotations.FlowCryptTestSettings
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withMessageHeaderInfo
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.adapter.GmailApiLabelsListAdapter
import com.flowcrypt.email.ui.adapter.MessageHeadersListAdapter
import com.flowcrypt.email.ui.gmailapi.base.BaseThreadDetailsGmailApiFlowTest
import com.flowcrypt.email.util.DateTimeUtil
import com.flowcrypt.email.viewaction.ClickOnViewInRecyclerViewItem
import org.hamcrest.Matchers.allOf
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
class ThreadDetailsGmailApiFlowTest : BaseThreadDetailsGmailApiFlowTest() {

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
  fun testThreadDetailsWithSingleMessage() {
    //need to wait while the app loads the thread list
    waitForObjectWithText(SUBJECT_EXISTING_STANDARD, TimeUnit.SECONDS.toMillis(20))

    //open a thread with a single message
    onView(withId(R.id.recyclerViewMsgs)).perform(
      RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
        hasDescendant(
          allOf(
            withId(R.id.textViewSubject),
            withText(SUBJECT_SINGLE)
          )
        ),
        click()
      )
    )

    //need to wait while the app loads the messages list and render the last one
    waitForObjectWithResourceName("imageButtonReplyAll", TimeUnit.SECONDS.toMillis(10))

    checkCorrectThreadDetails(
      messagesCount = 1,
      threadSubject = SUBJECT_SINGLE,
      labels = listOf(
        GmailApiLabelsListAdapter.Label("Inbox")
      )
    )

    //check base message details
    onView(allOf(withId(R.id.recyclerViewMessages), isDisplayed()))
      .perform(
        RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
          hasDescendant(
            allOf(
              withId(R.id.textViewSenderAddress),
              withText("From")
            )
          )
        )
      )

    onView(allOf(withId(R.id.recyclerViewMessages), isDisplayed()))
      .perform(
        RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
          hasDescendant(
            allOf(
              withId(R.id.textViewDate),
              withText(DateTimeUtil.formatSameDayTime(getTargetContext(), DATE_EXISTING_STANDARD))
            )
          )
        )
      )

    //open headers details and check them
    onView(allOf(withId(R.id.recyclerViewMessages), isDisplayed())).perform(
      RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
        1,
        ClickOnViewInRecyclerViewItem(R.id.iBShowDetails)
      )
    )

    val messageHeadersList = listOf(
      MessageHeadersListAdapter.Header(
        name = getResString(R.string.from),
        value = DEFAULT_FROM_RECIPIENT
      ),
      MessageHeadersListAdapter.Header(
        name = getResString(R.string.reply_to),
        value = DEFAULT_FROM_RECIPIENT
      ),
      MessageHeadersListAdapter.Header(
        name = getResString(R.string.to),
        value = EXISTING_MESSAGE_TO_RECIPIENT
      ),
      MessageHeadersListAdapter.Header(
        name = getResString(R.string.cc),
        value = EXISTING_MESSAGE_CC_RECIPIENT
      ),
      MessageHeadersListAdapter.Header(
        name = getResString(R.string.date),
        value = DateUtils.formatDateTime(
          getTargetContext(),
          DATE_EXISTING_STANDARD,
          DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
        )
      )
    )

    messageHeadersList.forEach {
      onView(allOf(withId(R.id.rVMsgDetails), isDisplayed()))
        .perform(scrollToHolder(withMessageHeaderInfo(it)))
    }

    //check reply buttons
    checkReplyButtons(isEncryptedMode = false)
  }
}