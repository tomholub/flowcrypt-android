/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors:
 * Ivan Pizhenko
 * DenBond7
 */

package com.flowcrypt.email

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.flowcrypt.email.api.retrofit.ApiHelper
import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.wkd.WkdClient
import com.flowcrypt.email.extensions.org.bouncycastle.openpgp.armor
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.FlowCryptMockWebServerRule
import com.flowcrypt.email.rules.GrantPermissionRuleChooser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.apache.commons.codec.binary.ZBase32
import org.apache.commons.codec.digest.DigestUtils
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.pgpainless.PGPainless
import java.net.HttpURLConnection

@SmallTest
@RunWith(AndroidJUnit4::class)
@Ignore(
  "Need to think about this test. " +
      "It uses real API calls and sometimes it fails due to a server not available issue."
)
class WkdClientTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(ClearAppSettingsRule())
    .around(GrantPermissionRuleChooser.grant(android.Manifest.permission.POST_NOTIFICATIONS))

  @Test
  fun existingEmailTest() = runBlocking {
    val keys = WkdClient.lookupEmail(context, EXISTING_EMAIL)
    assertTrue("Key not found", keys != null)
    assertTrue("There are no keys in the key collection", keys!!.keyRings.hasNext())
  }

  @Test
  fun nonExistingEmailTest1() = runBlocking {
    val keys = WkdClient.lookupEmail(context, NOT_EXISTING_EMAIL)
    assertTrue("Key found for non-existing email", keys == null)
  }

  @Test
  fun nonExistingEmailTest2() = runBlocking {
    val keys = WkdClient.lookupEmail(context, "doesnotexist@google.com")
    assertTrue("Key found for non-existing email", keys == null)
  }

  @Test
  fun nonExistingDomainTest() = runBlocking {
    val keys = WkdClient.lookupEmail(
      context,
      "doesnotexist@thisdomaindoesnotexist.test"
    )
    assertTrue("Key found for non-existing email", keys == null)
  }

  companion object {

    const val EXISTING_EMAIL = "existing@flowcrypt.test"
    const val NOT_EXISTING_EMAIL = "not_existing@flowcrypt.test"

    @get:ClassRule
    @JvmStatic
    val mockWebServerRule = FlowCryptMockWebServerRule(TestConstants.MOCK_WEB_SERVER_PORT,
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
          val gson = ApiHelper.getInstance(ApplicationProvider.getApplicationContext()).gson

          when (request.path) {
            "/.well-known/openpgpkey/policy" -> {
              return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
            }

            genLookupUrlPath(EXISTING_EMAIL) -> {
              return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(
                  PGPainless.generateKeyRing().simpleEcKeyRing(EXISTING_EMAIL).publicKey.armor()
                )
            }

            genLookupUrlPath(NOT_EXISTING_EMAIL) -> {
              return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
                .setBody(
                  gson.toJson(
                    ApiError(
                      code = HttpURLConnection.HTTP_NOT_FOUND,
                      message = "Public key not found"
                    )
                  )
                )
            }
          }

          return MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
        }
      })

    private fun genLookupUrlPath(email: String): String {
      val user = email.substringBefore("@")
      val hu = ZBase32().encodeAsString(DigestUtils.sha1(user.toByteArray()))
      return "/.well-known/openpgpkey/hu/$hu?l=$user"
    }
  }
}

