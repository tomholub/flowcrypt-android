/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.flowcrypt.email.junit.annotations.DebugTestAnnotation
import com.flowcrypt.email.junit.annotations.NotReadyForCI
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 *         Date: 9/16/20
 *         Time: 1:59 PM
 *         E-mail: DenBond7@gmail.com
 */
@SmallTest
@DebugTestAnnotation
@RunWith(AndroidJUnit4::class)
class DebugTestingTest {
  @Test
  @NotReadyForCI
  fun alwaysSuccessTest() {
  }

  @Test
  @NotReadyForCI
  fun alwaysSuccessTestSecond() {
  }
}