/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.activity.fragment.base

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.flowcrypt.email.R

/**
 * This interface helps to show a list of some data. It extends [ProgressBehaviour] with one more
 * common view - 'emptyView'.
 *
 * @author Denys Bondarenko
 */
interface ListProgressBehaviour : ProgressBehaviour {
  val emptyView: View?

  override fun showProgress(progressMsg: String?) {
    emptyView?.visibility = View.GONE
    super.showProgress(progressMsg)
  }

  override fun showContent() {
    emptyView?.visibility = View.GONE
    super.showContent()
  }

  override fun showStatus(msg: String?, resourcesId: Int, action: (() -> Unit)?) {
    emptyView?.visibility = View.GONE
    super.showStatus(msg, resourcesId, action)
  }

  fun showEmptyView(msg: String? = null, imageResourcesId: Int = 0) {
    contentView?.visibility = View.GONE
    goneStatusView()
    goneProgressView()

    val tVEmpty = emptyView?.findViewById<TextView>(R.id.tVEmpty)
    msg?.let {
      tVEmpty?.text = it
    }

    if (imageResourcesId > 0) {
      val iVEmptyImg = emptyView?.findViewById<ImageView>(R.id.iVEmptyImg)
      iVEmptyImg?.setImageResource(imageResourcesId)
    }

    emptyView?.visibility = View.VISIBLE
  }

  fun updateEmptyViewText(msg: String?) {
    val tVEmpty = emptyView?.findViewById<TextView>(R.id.tVEmpty)
    msg?.let {
      tVEmpty?.text = it
    }
  }
}
