/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: Ivan Pizhenko
 */

package com.flowcrypt.email.security.pgp

import org.bouncycastle.bcpg.SignaturePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpArmorTest {
  private val message = """-----BEGIN PGP SIGNED MESSAGE-----
Hash: SHA256

Standard message

signed inline

should easily verify
This is email footer
-----BEGIN PGP SIGNATURE-----
Version: FlowCrypt 5.0.4 Gmail Encryption flowcrypt.com
Comment: Seamlessly send, receive and search encrypted email

wsFcBAEBCAAQBQJZ+74YCRAGylU+wkVdcAAAfAkQAKYwTCQUX4K26jwzKPG0
ue6+jSygpkNlsHqfo7ZU0SYbvao0xEo1QQPy9zVW7zP39UAJZkN5EpIARBzF
671AA3s0KtknLt0AYfiTJdkqTihRjJZHBHQcxkkajws+3Br8oBieB4zi19GJ
oOqjyi2uxl7By5CSP238B6CXBTgaYkh/7TpYJDgFzuhtXtx0aWBP9h7TgEYN
AYNmtGItT6W2Q/JoB29cVsxyugVsQhdfM8DA5MpEZY2Zk/+UHXN0L45rEJFj
8HJkR83voiwAe6DdkLQHbYfVytSDZN+K80xN/VCQfdd7+HKpKbftIig0cXmr
+OsoDMGvPWkGEqJRh57bezWfz6jnkSSJSX9mXFG6KSJ2xuj30nPXsl1Wn1Xv
wR5T3L2kDusluFERiq0NnKDwAveHZIzh7xtjmYRlGVNujta0qTQXTyajxDpu
gZIqZKjDVZp7CjKYYPzvgUsihPzlgyqAodkMpl/IhYidPMB135lV4BBKHrF2
Urbb2tXMHa6rEZoj6jbS0uw/O1fSBJASYflrJ1M8YLsFCwBHpMWWL38ojbmK
i1EHYIU8A/y0qELPpKorgnLNKh8t05a01nrUWd/eXDKS1bbGlLeR6R/YvOM5
ADjvgywpiGmrwdehioKtS0SrHRvExYx8ory0iLo0cLGERArZ3jycF8F+S2Xp
5BnI
=F2om
-----END PGP SIGNATURE----- 
"""

  private val messageText =
      "Standard message\n\nsigned inline\n\nshould easily verify\nThis is email footer\n"

  @Test
  fun testReadClearTextSignedMessage() {
    val result = PgpArmor.readSignedClearTextMessage(message.toByteArray())
    val s = String(result.content)
    assertEquals(messageText, s)
    assertEquals(1, result.signature.packets.size)
    assertTrue(
        "packet is not a SignaturePacketWrapper",
        result.signature.packets[0] is PgpMessage.SignaturePacketWrapper
    )
  }
}
