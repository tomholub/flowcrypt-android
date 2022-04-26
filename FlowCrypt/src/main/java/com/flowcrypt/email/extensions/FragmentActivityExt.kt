/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.extensions

import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import com.flowcrypt.email.R
import com.flowcrypt.email.ui.activity.fragment.FeedbackFragment
import com.flowcrypt.email.ui.activity.fragment.FeedbackFragmentArgs
import com.flowcrypt.email.ui.activity.fragment.dialog.InfoDialogFragmentArgs
import com.flowcrypt.email.util.UIUtil

/**
 * This class describes extension function for [FragmentActivity]
 *
 * @author Denis Bondarenko
 *         Date: 11/22/19
 *         Time: 3:37 PM
 *         E-mail: DenBond7@gmail.com
 */

val FragmentActivity.navController: NavController
  get() = (supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
      as NavHostFragment).navController

fun FragmentActivity.showFeedbackFragment() {
  val screenShotByteArray = UIUtil.getScreenShotByteArray(this)
  screenShotByteArray?.let {
    val navDirections = object : NavDirections {
      override fun getActionId() = R.id.feedback_graph
      override fun getArguments() = FeedbackFragmentArgs(
        screenshot = FeedbackFragment.Screenshot(it)
      ).toBundle()
    }
    navController.navigate(navDirections)
  }
}

fun FragmentActivity.showInfoDialog(
  requestCode: Int = 0,
  dialogTitle: String? = null,
  dialogMsg: String? = null,
  buttonTitle: String? = null,
  isCancelable: Boolean = true,
  hasHtml: Boolean = false,
  useLinkify: Boolean = false,
  useWebViewToRender: Boolean = false
) {
  //to show the current dialog we should be sure there is no active dialogs
  if (navController.currentDestination?.navigatorName == "dialog") {
    navController.navigateUp()
  }

  val navDirections = object : NavDirections {
    override fun getActionId() = R.id.info_dialog_graph
    override fun getArguments() = InfoDialogFragmentArgs(
      requestCode = requestCode,
      dialogTitle = dialogTitle,
      dialogMsg = dialogMsg,
      buttonTitle = buttonTitle ?: getString(android.R.string.ok),
      isCancelable = isCancelable,
      hasHtml = hasHtml,
      useLinkify = useLinkify,
      useWebViewToRender = useWebViewToRender
    ).toBundle()
  }

  navController.navigate(navDirections)
}
