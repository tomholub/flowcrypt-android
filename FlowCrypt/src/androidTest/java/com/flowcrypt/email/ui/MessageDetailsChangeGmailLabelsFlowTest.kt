/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui

import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.base.BaseGmailLabelsFlowTest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.model.BatchModifyMessagesRequest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.GzipSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.net.HttpURLConnection

/**
 * @author Denys Bondarenko
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class MessageDetailsChangeGmailLabelsFlowTest : BaseGmailLabelsFlowTest() {
  private var lastLabelIds = mutableListOf<String>()

  private val mockWebServerRule = FlowCryptMockWebServerRule(
    TestConstants.MOCK_WEB_SERVER_PORT,
    object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        when {
          request.method == "POST" && request.path == "/gmail/v1/users/me/messages/batchModify" -> {
            val source = GzipSource(request.body)
            val batchModifyMessagesRequest = GsonFactory.getDefaultInstance().fromInputStream(
              source.buffer().inputStream(),
              BatchModifyMessagesRequest::class.java
            )

            batchModifyMessagesRequest.addLabelIds?.let {
              lastLabelIds.addAll(it)
            }
            batchModifyMessagesRequest.removeLabelIds?.let {
              lastLabelIds.removeAll(it)
            }

            assertEquals(
              LABELS.take(3).map { it.name },
              batchModifyMessagesRequest.removeLabelIds
            )
            assertEquals(
              LABELS.filter { it.name == "Test9" }.map { it.name },
              batchModifyMessagesRequest.addLabelIds
            )
            return MockResponse()
              .setResponseCode(HttpURLConnection.HTTP_OK)
          }

          else -> return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
        }
      }
    })

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(RetryRule.DEFAULT)
    .around(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))
    .around(mockWebServerRule)
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(addLabelsToDatabaseRule)
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Test
  @Ignore("flaky 1")
  fun testLabelsManagement() {
    val allLabels = initLabelIds()
    lastLabelIds = (allLabels.take(4) + allLabels.takeLast(1)).toMutableList()
    val details = genIncomingMessageInfo(labelsIds = lastLabelIds)?.msgEntity
    requireNotNull(details)
    launchActivity(details)
    Thread.sleep(1000)

    //open dialog by clicking on one of the labels
    onView(withId(R.id.recyclerViewLabels))
      .perform(RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(0, click()))

    //close dialog
    pressBack()

    //open dialog from the Action Bar menu
    openActionBarOverflowOrOptionsMenu(getTargetContext())
    onView(withText(R.string.change_labels))
      .check(matches(isDisplayed()))
      .perform(click())

    //deselect the first 3 labels
    onView(withId(R.id.recyclerViewLabels))
      .perform(RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(0, click()))
    onView(withId(R.id.recyclerViewLabels))
      .perform(RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(1, click()))
    onView(withId(R.id.recyclerViewLabels))
      .perform(RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(2, click()))

    //scroll to the last label
    onView(withId(R.id.recyclerViewLabels))
      .perform(
        RecyclerViewActions.scrollToPosition<ViewHolder>(
          allLabels.size - 2 //as we don't show INBOX need to make offset == 2)
        )
      )

    //scroll to the first label
    onView(withId(R.id.recyclerViewLabels))
      .perform(
        RecyclerViewActions.scrollToPosition<ViewHolder>(0)
      )

    //select the last label. In our case it will be Test9
    onView(withId(R.id.recyclerViewLabels))
      .perform(
        RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
          allLabels.size - 2, //as we don't show INBOX need to make offset == 2
          click()
        )
      )

    onView(withText(R.string.change_labels))
      .inRoot(isDialog())
      .perform(click())

    Thread.sleep(3000)

    assertEquals(
      allLabels.take(1) +
          allLabels.takeLast(1) +
          allLabels.filter { it == "Test9" },
      lastLabelIds
    )
  }
}
