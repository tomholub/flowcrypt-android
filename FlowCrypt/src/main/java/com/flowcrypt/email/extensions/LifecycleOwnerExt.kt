/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.extensions

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import com.flowcrypt.email.R
import com.flowcrypt.email.model.DialogItem
import com.flowcrypt.email.model.Screenshot
import com.flowcrypt.email.ui.activity.fragment.FeedbackFragmentArgs
import com.flowcrypt.email.ui.activity.fragment.dialog.ActionsDialogFragmentArgs
import com.flowcrypt.email.ui.activity.fragment.dialog.FixNeedPassphraseIssueDialogFragment
import com.flowcrypt.email.ui.activity.fragment.dialog.FixNeedPassphraseIssueDialogFragmentArgs
import com.flowcrypt.email.ui.activity.fragment.dialog.InfoDialogFragmentArgs
import com.flowcrypt.email.ui.activity.fragment.dialog.TwoWayDialogFragmentArgs
import com.flowcrypt.email.util.UIUtil

/**
 * @author Denys Bondarenko
 */
fun LifecycleOwner.showDialogFragment(
  navController: NavController?,
  action: () -> NavDirections
) {
  //to show the current dialog we should be sure there is no active dialogs
  if (navController?.currentDestination?.navigatorName == "dialog") {
    navController.navigateUp()
  }

  val navDirections = action.invoke()
  navController?.navigate(navDirections)
}

fun LifecycleOwner.showInfoDialog(
  context: Context,
  navController: NavController?,
  requestKey: String? = null,
  requestCode: Int = 0,
  dialogTitle: String? = null,
  dialogMsg: String? = null,
  buttonTitle: String? = null,
  isCancelable: Boolean = true,
  hasHtml: Boolean = false,
  useLinkify: Boolean = false,
  useWebViewToRender: Boolean = false,
  bundle: Bundle? = null
) {
  showDialogFragment(navController) {
    return@showDialogFragment object : NavDirections {
      override val actionId = R.id.info_dialog_graph
      override val arguments = InfoDialogFragmentArgs(
        requestKey = requestKey,
        requestCode = requestCode,
        dialogTitle = dialogTitle,
        dialogMsg = dialogMsg,
        buttonTitle = buttonTitle ?: context.getString(android.R.string.ok),
        isCancelable = isCancelable,
        hasHtml = hasHtml,
        useLinkify = useLinkify,
        useWebViewToRender = useWebViewToRender,
        bundle = bundle
      ).toBundle()
    }
  }
}

fun LifecycleOwner.showTwoWayDialog(
  context: Context,
  navController: NavController?,
  requestKey: String? = null,
  requestCode: Int = 0,
  dialogTitle: String? = null,
  dialogMsg: String? = null,
  positiveButtonTitle: String? = null,
  negativeButtonTitle: String? = null,
  isCancelable: Boolean = true,
  hasHtml: Boolean = false,
  useLinkify: Boolean = false,
  bundle: Bundle? = null
) {
  showDialogFragment(navController) {
    return@showDialogFragment object : NavDirections {
      override val actionId = R.id.two_way_dialog_graph
      override val arguments = TwoWayDialogFragmentArgs(
        requestKey = requestKey,
        requestCode = requestCode,
        dialogTitle = dialogTitle,
        dialogMsg = dialogMsg,
        positiveButtonTitle = positiveButtonTitle ?: context.getString(android.R.string.ok),
        negativeButtonTitle = negativeButtonTitle ?: context.getString(android.R.string.cancel),
        isCancelable = isCancelable,
        hasHtml = hasHtml,
        useLinkify = useLinkify,
        bundle = bundle
      ).toBundle()
    }
  }
}

fun LifecycleOwner.showNeedPassphraseDialog(
  requestKey: String,
  navController: NavController?,
  fingerprints: List<String>,
  logicType: Long = FixNeedPassphraseIssueDialogFragment.LogicType.AT_LEAST_ONE,
  requestCode: Int = 0,
  customTitle: String? = null,
  showKeys: Boolean = true,
  bundle: Bundle? = null
) {
  if (navController?.currentDestination?.id == R.id.fixNeedPassphraseIssueDialogFragment) {
    //it prevents blinking
    return
  }

  showDialogFragment(navController) {
    return@showDialogFragment object : NavDirections {
      override val actionId = R.id.fix_need_pass_phrase_dialog_graph
      override val arguments = FixNeedPassphraseIssueDialogFragmentArgs(
        requestKey = requestKey,
        fingerprints = fingerprints.toTypedArray(),
        logicType = logicType,
        requestCode = requestCode,
        customTitle = customTitle,
        showKeys = showKeys,
        bundle = bundle
      ).toBundle()
    }
  }
}

fun LifecycleOwner.showInfoDialogWithExceptionDetails(
  context: Context,
  navController: NavController?,
  throwable: Throwable?,
  msgDetails: String? = null
) {
  val msg =
    throwable?.message ?: throwable?.javaClass?.simpleName ?: msgDetails
    ?: context.getString(R.string.unknown_error)

  showInfoDialog(
    navController = navController,
    context = context,
    dialogTitle = "",
    dialogMsg = msg
  )
}

fun LifecycleOwner.showFeedbackFragment(
  activity: Activity,
  navController: NavController?
) {
  val screenShotByteArray = UIUtil.getScreenShotByteArray(activity)
  screenShotByteArray?.let {
    val navDirections = object : NavDirections {
      override val actionId: Int = R.id.feedback_graph
      override val arguments: Bundle = FeedbackFragmentArgs(
        screenshot = Screenshot(it)
      ).toBundle()
    }
    navController?.navigate(navDirections)
  }
}

fun LifecycleOwner.showActionDialogFragment(
  navController: NavController?,
  requestKey: String,
  dialogTitle: String? = null,
  isCancelable: Boolean = true,
  items: List<DialogItem>,
  bundle: Bundle? = null
) {
  showDialogFragment(navController) {
    return@showDialogFragment object : NavDirections {
      override val actionId = R.id.actions_dialog_graph
      override val arguments = ActionsDialogFragmentArgs(
        requestKey = requestKey,
        dialogTitle = dialogTitle,
        isCancelable = isCancelable,
        items = items.toTypedArray(),
        bundle = bundle
      ).toBundle()
    }
  }
}
