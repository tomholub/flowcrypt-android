/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors:
 *     Ivan Pizhenko
 */

package com.flowcrypt.email.security.pgp

import com.flowcrypt.email.api.retrofit.response.model.node.MsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.SignedMsgBlock
import com.flowcrypt.email.core.msg.MimeUtils
import com.flowcrypt.email.extensions.kotlin.toEscapedHtml
import com.flowcrypt.email.extensions.kotlin.normalizeEol
import com.flowcrypt.email.extensions.kotlin.removeUtf8Bom
import com.google.gson.JsonParser
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pgpainless.PGPainless
import org.pgpainless.util.Passphrase
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class PgpMsgTest {

  private data class MessageInfo(
    val key: String,
    val content: List<String>,
    val quoted: Boolean? = null,
    val charset: String = "UTF-8"
  ) {
    val armored: String = loadResourceAsString("messages/$key.txt")
  }

  companion object {
    private val privateKeys = listOf(
      PgpMsg.KeyWithPassPhrase(
        passphrase = Passphrase.fromPassword("flowcrypt compatibility tests"),
        keyRing = loadSecretKey("key0.txt"),
      ),

      PgpMsg.KeyWithPassPhrase(
        passphrase = Passphrase.fromPassword("flowcrypt compatibility tests 2"),
        keyRing = loadSecretKey("key1.txt")
      )
    )

    private val messages = listOf(
      MessageInfo(
        key = "decrypt - without a subject",
        content = listOf("This is a compatibility test email")
      ),

      MessageInfo(
        key = "decrypt - [security] mdc - missing - error",
        content = listOf("Security threat!", "MDC", "Display the message at your own risk."),
      ),

      MessageInfo(
        key = "decrypt - [security] mdc - modification detected - error",
        content = listOf(
          "Security threat - opening this message is dangerous because it was modified" +
              " in transit."
        ),
      ),

      MessageInfo(
        key = "decrypt - [everdesk] message encrypted for sub but claims encryptedFor-primary,sub",
        content = listOf("this is a sample for FlowCrypt compatibility")
      ),

      MessageInfo(
        key = "decrypt - [gpg] signed fully armored message",
        content = listOf(
          "this was encrypted with gpg",
          "gpg --sign --armor -r flowcrypt.compatibility@gmail.com ./text.txt"
        ),
        quoted = false
      ),

      MessageInfo(
        key = "decrypt - encrypted missing checksum",
        content = listOf("400 library systems in 177 countries worldwide")
      ),

      MessageInfo(
        key = "decrypt - [enigmail] encrypted iso-2022-jp pgp-mime",
        content = listOf("=E3=82=BE=E3=81=97=E9=80=B8=E7=8F=BE"), // part of "ゾし逸現飲"
        charset = "ISO-2022-JP",
      ),

      MessageInfo(
        key = "decrypt - [enigmail] encrypted iso-2022-jp, plain text",
        content = listOf(
          // complete string "ゾし逸現飲"
          decodeString("=E3=82=BE=E3=81=97=E9=80=B8=E7=8F=BE=E9=A3=B2", "UTF-8")
        ),
        charset = "ISO-2022-JP",
      )
    )

    @Suppress("SameParameterValue")
    private fun decodeString(s: String, charsetName: String): String {
      val bytes = s.substring(1).split('=').map { Integer.parseInt(it, 16).toByte() }.toByteArray()
      return String(bytes, Charset.forName(charsetName))
    }

    private fun loadResource(path: String): ByteArray {
      return PgpMsgTest::class.java.classLoader!!
        .getResourceAsStream("${PgpMsgTest::class.simpleName}/$path")
        .readBytes()
    }

    private fun loadResourceAsString(path: String): String {
      return String(loadResource(path), StandardCharsets.UTF_8)
    }

    private fun loadSecretKey(file: String): PGPSecretKeyRing {
      return PGPainless.readKeyRing().secretKeyRing(loadResourceAsString("keys/$file"))
    }

    private fun findMessage(key: String): MessageInfo {
      return messages.firstOrNull { it.key == key }
        ?: throw IllegalArgumentException("Message '$key' not found")
    }
  }

  @Test
  fun multipleDecryptionTest() {
    val keys = listOf(
      "decrypt - without a subject",
      "decrypt - [enigmail] encrypted iso-2022-jp pgp-mime",
      "decrypt - [enigmail] encrypted iso-2022-jp, plain text",
      "decrypt - [gpg] signed fully armored message"
    )
    for (key in keys) {
      println("Decrypt: '$key'")
      val r = processMessage(key)
      assertTrue("Message not returned", r.content != null)
      val messageInfo = findMessage(key)
      checkContent(
        expected = messageInfo.content,
        actual = r.content!!.toByteArray(),
        charset = messageInfo.charset
      )
    }
  }

  @Test
  fun missingMdcTest() {
    val r = processMessage("decrypt - [security] mdc - missing - error")
    assertTrue("Message is returned when should not", r.content == null)
    assertTrue("Error not returned", r.error != null)
    assertTrue("Missing MDC not detected", r.error!!.type == PgpDecrypt.DecryptionErrorType.NO_MDC)
  }

  @Test
  fun badMdcTest() {
    val r = processMessage("decrypt - [security] mdc - modification detected - error")
    assertTrue("Message is returned when should not", r.content == null)
    assertTrue("Error not returned", r.error != null)
    assertTrue("Bad MDC not detected", r.error!!.type == PgpDecrypt.DecryptionErrorType.BAD_MDC)
  }

  // TODO: Should there be any error?
  // https://github.com/FlowCrypt/flowcrypt-android/issues/1214
  @Test
  fun decryptionTest3() {
    val r = processMessage(
      "decrypt - [everdesk] message encrypted for sub but claims encryptedFor-primary,sub"
    )
    assertTrue("Message not returned", r.content != null)
    assertTrue("Error returned", r.error == null)
  }

  @Test
  fun missingArmorChecksumTest() {
    // This is a test for the message with missing armor checksum - different from MDC.
    // Usually the four digits at the and like p3Fc=.
    // Such messages are still valid if this is missing,
    // and should decrypt correctly - so it's good as is.
    val r = processMessage("decrypt - encrypted missing checksum")
    assertTrue("Message not returned", r.content != null)
    assertTrue("Error returned", r.error == null)
  }

  @Test
  fun wrongPassphraseTest() {
    val messageInfo = findMessage("decrypt - without a subject")
    val wrongPassphrase = Passphrase.fromPassword("this is wrong passphrase for sure")
    val privateKeysWithWrongPassPhrases = privateKeys.map {
      PgpMsg.KeyWithPassPhrase(keyRing = it.keyRing, passphrase = wrongPassphrase)
    }
    val r = PgpMsg.decrypt(
      messageInfo.armored.toByteArray(), privateKeysWithWrongPassPhrases, null
    )
    assertTrue("Message returned", r.content == null)
    assertTrue("Error not returned", r.error != null)
    assertTrue(
      "Wrong passphrase not detected",
      r.error!!.type == PgpDecrypt.DecryptionErrorType.WRONG_PASSPHRASE
    )
  }

  @Test
  fun missingPassphraseTest() {
    val messageInfo = findMessage("decrypt - without a subject")
    val privateKeysWithMissingPassphrases = privateKeys.map {
      PgpMsg.KeyWithPassPhrase(keyRing = it.keyRing, passphrase = null)
    }
    val r = PgpMsg.decrypt(
      messageInfo.armored.toByteArray(), privateKeysWithMissingPassphrases, null
    )
    assertTrue("Message returned", r.content == null)
    assertTrue("Error not returned", r.error != null)
    assertTrue(
      "Missing passphrase not detected",
      r.error!!.type == PgpDecrypt.DecryptionErrorType.NEED_PASSPHRASE
    )
  }

  @Test
  fun wrongKeyTest() {
    val messageInfo = findMessage("decrypt - without a subject")
    val wrongKey = listOf(privateKeys[1])
    val r = PgpMsg.decrypt(
      messageInfo.armored.toByteArray(), wrongKey, null
    )
    assertTrue("Message returned", r.content == null)
    assertTrue("Error not returned", r.error != null)
    assertTrue(
      "Key mismatch not detected",
      r.error!!.type == PgpDecrypt.DecryptionErrorType.KEY_MISMATCH
    )
  }

  // -------------------------------------------------------------------------------------------

  // Use this one for debugging
  @Test
  fun singleDecryptionTest() {
    val key = "decrypt - [enigmail] encrypted iso-2022-jp, plain text"
    val r = processMessage(key)
    assertTrue("Message not returned", r.content != null)
    val messageInfo = findMessage(key)
    checkContent(
      expected = messageInfo.content,
      actual = r.content!!.toByteArray(),
      charset = messageInfo.charset
    )
  }

  private fun processMessage(messageKey: String): PgpMsg.DecryptionResult {
    val messageInfo = findMessage(messageKey)
    val result = PgpMsg.decrypt(messageInfo.armored.toByteArray(), privateKeys, null)
    if (result.content != null) {
      val s = String(result.content!!.toByteArray(), Charset.forName(messageInfo.charset))
      println("=========\n$s\n=========")
    }
    if (result.error != null && result.error!!.cause != null) {
      println("CAUSE:")
      result.error!!.cause!!.printStackTrace(System.out)
    }
    return result
  }

  private fun checkContent(expected: List<String>, actual: ByteArray, charset: String) {
    val z = String(actual, Charset.forName(charset))
    for (s in expected) {
      assertTrue("Text '$s' not found", z.indexOf(s) != -1)
    }
  }

  // -------------------------------------------------------------------------------------------

  @Test
  fun multipleComplexMessagesTest() {
    val testFiles = listOf(
      "decrypt - [enigmail] basic html-0.json",
      "decrypt - [gnupg v2] thai text-0.json",
      "decrypt - [gnupg v2] thai text in html-0.json",
      "decrypt - [gpgmail] signed message will get parsed and rendered " +
          "(though verification fails, enigmail does the same)-0.json",
      "decrypt - protonmail - auto TOFU load matching pubkey first time-0.json",
      "decrypt - protonmail - load pubkey into contact + verify detached msg-0.json",
      "decrypt - protonmail - load pubkey into contact + verify detached msg-1.json",
      "decrypt - protonmail - load pubkey into contact + verify detached msg-2.json",
      "decrypt - [symantec] base64 german umlauts-0.json",
      "decrypt - [thunderbird] unicode chinese-0.json",
      "decrypt - verify encrypted+signed message-0.json",
      "verify - Kraken - urldecode signature-0.json"
    )

    for (testFile in testFiles) {
      checkComplexMessage(testFile)
    }
  }

  // Use this one for debugging
  @Test
  fun singleComplexMessageTest() {
    val testFile = "verify - Kraken - urldecode signature-0.json"
    checkComplexMessage(testFile)
  }

  private fun checkComplexMessage(fileName: String) {
    println("\n*** Processing '$fileName'")
    val json = loadResourceAsString("complex_messages/$fileName")
    val rootObject = JsonParser.parseString(json).asJsonObject
    val inputMsg = Base64.getDecoder().decode(rootObject["in"].asJsonObject["mimeMsg"].asString)
    val out = rootObject["out"].asJsonObject
    val expectedBlocks = out["blocks"].asJsonArray
    val from = InternetAddress(out["from"].asString)
    val to = out["to"].asJsonArray.map { InternetAddress(it.asString) }.toTypedArray()
    val session = Session.getInstance(Properties())
    val mimeMessage = MimeMessage(session, inputMsg.inputStream())
    val mimeContent = PgpMsg.decodeMimeMessage(mimeMessage)
    val processed = PgpMsg.processDecodedMimeMessage(mimeContent)

    assertEquals(1, processed.from?.size ?: 0)
    assertEquals(from, processed.from!![0])
    assertArrayEquals(to, processed.to)
    assertEquals(expectedBlocks.size(), processed.blocks.size)

    for (i in processed.blocks.indices) {
      println("Checking block #$i of the '$fileName'")

      val expectedBlock = expectedBlocks[i].asJsonObject
      val expectedBlockType = MsgBlock.Type.ofSerializedName(expectedBlock["type"].asString)
      val expectedContent = expectedBlock["content"].asString.normalizeEol()
      val expectedComplete = expectedBlock["complete"].asBoolean
      val actualBlock = processed.blocks[i]
      val actualContent = (actualBlock.content ?: "").normalizeEol().removeUtf8Bom()

      assertEquals(expectedBlockType, actualBlock.type)
      assertEquals(expectedComplete, actualBlock.complete)
      assertEquals(expectedContent, actualContent)

      if (actualBlock.type in MsgBlock.Type.signedBlocks) {
        val expectedSignature = expectedBlock["signature"].asString.normalizeEol()
        val actualSignature = ((actualBlock as SignedMsgBlock).signature ?: "").normalizeEol()
        assertEquals(expectedSignature, actualSignature)
      }
    }
  }

  // -------------------------------------------------------------------------------------------

  @Test
  fun testParseDecryptMsgUnescapedSpecialCharactersInTextOriginallyTextPlain() {
    val mimeText = "MIME-Version: 1.0\n" +
        "Date: Fri, 6 Sep 2019 10:48:25 +0000\n" +
        "Message-ID: <some@mail.gmail.com>\n" +
        "Subject: plain text with special chars\n" +
        "From: Human at FlowCrypt <human@flowcrypt.com>\n" +
        "To: FlowCrypt Compatibility <flowcrypt.compatibility@gmail.com>\n" +
        "Content-Type: text/plain; charset=\"UTF-8\"\n" +
        "\n" + textSpecialChars
    val keys = TestKeys.KEYS["rsa1"]!!.listOfKeysWithPassPhrase
    val result = PgpMsg.parseDecryptMsg(MimeUtils.mimeTextToMimeMessage(mimeText), keys)
    assertEquals(textSpecialChars, result.text)
    assertEquals(false, result.isReplyEncrypted)
    assertEquals("plain text with special chars", result.subject)
    assertEquals(1, result.blocks.size)
    val block = result.blocks[0]
    assertEquals(MsgBlock.Type.PLAIN_HTML, block.type)
    checkRenderedBlock(block, listOf(RenderedBlock.normal(true, "PLAIN", textSpecialChars)))
  }

  @Test
  fun testParseDecryptMsgUnescapedSpecialCharactersInTextOriginallyTextHtml() {
    val mimeText = "MIME-Version: 1.0\n" +
        "Date: Fri, 6 Sep 2019 10:48:25 +0000\n" +
        "Message-ID: <some@mail.gmail.com>\n" +
        "Subject: plain text with special chars\n" +
        "From: Human at FlowCrypt <human@flowcrypt.com>\n" +
        "To: FlowCrypt Compatibility <flowcrypt.compatibility@gmail.com>\n" +
        "Content-Type: text/html; charset=\"UTF-8\"\n" +
        "\n" + htmlSpecialChars
    val keys = TestKeys.KEYS["rsa1"]!!.listOfKeysWithPassPhrase
    val result = PgpMsg.parseDecryptMsg(MimeUtils.mimeTextToMimeMessage(mimeText), keys)
    assertEquals(textSpecialChars, result.text)
    assertEquals(false, result.isReplyEncrypted)
    assertEquals("plain text with special chars", result.subject)
    assertEquals(1, result.blocks.size)
    val block = result.blocks[0]
    assertEquals(MsgBlock.Type.PLAIN_HTML, block.type)
    checkRenderedBlock(block, listOf(RenderedBlock.normal(true, "PLAIN", htmlSpecialChars)))
  }

  @Test
  fun testParseDecryptMsgUnescapedSpecialCharactersInEncryptedPgpMime() {
    val text = loadResourceAsString("compat/direct-encrypted-pgpmime-special-chars.txt")
    val keys = TestKeys.KEYS["rsa1"]!!.listOfKeysWithPassPhrase
    val result = PgpMsg.parseDecryptMsg(text, false, keys)
    assertEquals(textSpecialChars, result.text)
    assertEquals(true, result.isReplyEncrypted)
    assertEquals("direct encrypted pgpmime special chars", result.subject)
    assertEquals(1, result.blocks.size)
    val block = result.blocks[0]
    assertEquals(MsgBlock.Type.PLAIN_HTML, block.type)
    checkRenderedBlock(block, listOf(RenderedBlock.normal(true, "GREEN", htmlSpecialChars)))
  }

  @Test
  fun testParseDecryptMsgUnescapedSpecialCharactersInEncryptedText() {
    val text = loadResourceAsString("compat/direct-encrypted-text-special-chars.txt")
    val keys = TestKeys.KEYS["rsa1"]!!.listOfKeysWithPassPhrase
    val result = PgpMsg.parseDecryptMsg(text, false, keys)
    assertEquals(textSpecialChars, result.text)
    assertEquals(true, result.isReplyEncrypted)
    assertTrue(result.subject == null)
    assertEquals(1, result.blocks.size)
    val block = result.blocks[0]
    assertEquals(MsgBlock.Type.PLAIN_HTML, block.type)
    checkRenderedBlock(block, listOf(RenderedBlock.normal(true, "GREEN", htmlSpecialChars)))
  }

  @Test
  fun testParseDecryptMsgPlainInlineImage() {
    val text = loadResourceAsString("other/plain-inline-image.txt")
    val keys = TestKeys.KEYS["rsa1"]!!.listOfKeysWithPassPhrase
    val result = PgpMsg.parseDecryptMsg(text, true, keys)
    assertEquals("Below\n[image: image.png]\nAbove", result.text)
    assertEquals(false, result.isReplyEncrypted)
    assertEquals("tiny inline img plain", result.subject)
    assertEquals(1, result.blocks.size)
    val block = result.blocks[0]
    assertEquals(MsgBlock.Type.PLAIN_HTML, block.type)
    val htmlContent = loadResourceAsString("other/plain-inline-image-html-content.txt")
    checkRenderedBlock(block, listOf(RenderedBlock.normal(true, "PLAIN", htmlContent)))
  }

  @Test
  fun testParseDecryptMsgSignedMessagePreserveNewlines() {
    val text = loadResourceAsString("other/signed-message-preserve-newlines.txt")
    val keys = TestKeys.KEYS["rsa1"]!!.listOfKeysWithPassPhrase
    val result = PgpMsg.parseDecryptMsg(text, false, keys)
    assertEquals(
      "Standard message\n\nsigned inline\n\nshould easily verify\nThis is email footer",
      result.text
    )
    assertEquals(false, result.isReplyEncrypted)
    assertTrue(result.subject == null)
    assertEquals(1, result.blocks.size)
    val block = result.blocks[0]
    assertEquals(MsgBlock.Type.PLAIN_HTML, block.type)
    checkRenderedBlock(
      block,
      listOf(
        RenderedBlock.normal(
          true,
          "PLAIN",
          "Standard message<br /><br />signed inline<br /><br />should easily verify<br />" +
              "This is email footer"
        )
      )
    )
  }

  private data class RenderedBlock(
    val rendered: Boolean,
    val frameColor: String?,
    val htmlContent: String?,
    val content: String?,
    val error: String?
  ) {
    companion object{
      fun normal(rendered: Boolean, frameColor: String?, htmlContent: String?): RenderedBlock {
        return RenderedBlock(
          rendered = rendered,
          frameColor = frameColor,
          htmlContent = htmlContent,
          content = null,
          error = null
        )
      }

      fun error(error: String, content: String): RenderedBlock {
        return RenderedBlock(
          rendered = false,
          frameColor = null,
          htmlContent = null,
          content = content,
          error = error
        )
      }
    }
  }

  private fun checkRenderedBlock(block: MsgBlock, expectedRenderedBlocks: List<RenderedBlock>) {
    val parts = block.content!!.split(bodySplitRegex, 3)
    val head = parts[0]
    assertTrue(head.contains("<!DOCTYPE html><html>"))
    assertTrue(head.contains("<style>"))
    assertTrue(head.contains("<meta name=\"viewport\" content=\"width=device-width\" />"))
    val foot = parts[2]
    assertTrue(foot.contains("</html>"))
    val body = parts[1]
    if (body.contains(nextMsgBlockDelimiter)) {
      val renderedContentBlocks = body.split(nextMsgBlockDelimiter)
      val lastEmpty = renderedContentBlocks.last()
      assertEquals("", lastEmpty)
      val actualRenderedBlocks = renderedContentBlocks.subList(0, renderedContentBlocks.size - 1)
        .map {
          val match = renderedContentBlockRegex.find(it)
          if (match == null)
            RenderedBlock.error("TEST VALIDATION ERROR - MISMATCHING CONTENT BLOCK FORMAT", it)
          else
            RenderedBlock.normal(true, match.groups[1]?.value, match.groups[2]?.value)
        }.toList()
      assertEquals(expectedRenderedBlocks, actualRenderedBlocks)
    }
  }

  private val nextMsgBlockDelimiter = "<!-- next MsgBlock -->\n"
  private val bodySplitRegex = Regex("</?body>")
  private val renderedContentBlockRegex = Regex(
    "<div class=\"MsgBlock ([a-z]+)\" style=\"[^\"]+\">(.*)</div>"
  )
  private val textSpecialChars = "> special <tag> & other\n> second line"
  private val htmlSpecialChars = textSpecialChars.toEscapedHtml()
}
