/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.util

import android.content.Context
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.util.gson.GsonHelper
import com.google.gson.Gson
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * @author Denis Bondarenko
 * Date: 18.01.2018
 * Time: 13:02
 * E-mail: DenBond7@gmail.com
 */

fun <T> readObjectFromResources(path: String, aClass: Class<T>): T? {
  try {
    val json = IOUtils.toString(aClass.classLoader!!.getResourceAsStream(path), StandardCharsets.UTF_8)
    return Gson().fromJson(json, aClass)
  } catch (e: IOException) {
    e.printStackTrace()
  }

  return null
}

fun readFileFromAssetsAsString(context: Context, filePath: String): String {
  return IOUtils.toString(context.assets.open(filePath), "UTF-8")
}

fun deleteFiles(files: List<File>) {
  files.forEach { file ->
    if (!file.delete()) {
      println("Can't delete a file $file")
    }
  }
}

fun createFile(fileName: String, fileText: String): File {
  val file = File(InstrumentationRegistry.getInstrumentation().targetContext
      .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
  try {
    FileOutputStream(file).use { outputStream -> outputStream.write(fileText.toByteArray()) }
  } catch (e: IOException) {
    e.printStackTrace()
  }

  return file
}

fun <T> getObjectFromJson(jsonPathInAssets: String?, classOfT: Class<T>): T? {
  try {
    if (jsonPathInAssets != null) {
      val gson = GsonHelper.gson
      val json = readFileFromAssetsAsString(BaseTest.getContext(), jsonPathInAssets)
      return gson.fromJson(json, classOfT)
    }
  } catch (e: IOException) {
    e.printStackTrace()
  }

  return null
}

fun replaceVersionInKey(key: String): String {
  val regex = "Version: FlowCrypt \\d*.\\d*.\\d* Gmail".toRegex()
  val version = BuildConfig.VERSION_NAME.split("_").first()
  val replacement = "Version: FlowCrypt " + version + " Gmail"

  return key.replaceFirst(regex, replacement)
}
