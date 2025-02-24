/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */


import com.android.ddmlib.DdmPreferences
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

//Setting global timeout for apk installation to 10 minutes. We need it for CI
DdmPreferences.setTimeOut(10 * 60 * 1000)

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs.kotlin")
  id("com.starter.easylauncher")
  id("kotlin-parcelize")
  id("com.google.devtools.ksp")
  id("org.ajoberstar.grgit")
}

val keystoreProperties = Properties()
val propertiesFile = project.file("keystore.properties")
if (propertiesFile.exists()) {
  keystoreProperties.load(FileInputStream(propertiesFile))
}

android {
  compileSdk = extra["compileSdkVersion"] as Int
  namespace = "com.flowcrypt.email"

  defaultConfig {
    applicationId = "com.flowcrypt.email"
    minSdk = extra["minSdkVersion"] as Int
    targetSdk = extra["targetSdkVersion"] as Int
    versionCode = extra["appVersionCode"] as Int
    versionName = extra["appVersionName"] as String
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("int", "MIN_SDK_VERSION", "$minSdk")

    /*
     The following argument makes the Android Test Orchestrator run its
     "pm clear" command after each test invocation. This command ensures
     that the app"s state is completely cleared between tests.
     */
    testInstrumentationRunnerArguments += mapOf("clearPackageData" to "true")
    multiDexEnabled = true
  }

  signingConfigs {
    create("release") {
      var keyStoreFile = keystoreProperties["storeFile"]?.toString()
      var keyStorePass = keystoreProperties["storePassword"]?.toString()
      var keySignAlias = keystoreProperties["keyAlias"]?.toString()
      var keyPass = keystoreProperties["keyPassword"]?.toString()

      if (project.hasProperty("runtimeSign")) {
        if (project.hasProperty("storeFile")) {
          keyStoreFile = project.property("storeFile") as? String
        }

        if (project.hasProperty("storePassword")) {
          keyStorePass = project.property("storePassword") as? String
        }

        if (project.hasProperty("keyAlias")) {
          keySignAlias = project.property("keyAlias") as? String
        }

        if (project.hasProperty("keyPassword")) {
          keyPass = project.property("keyPassword") as? String
        }
      }
      storeFile = file(keyStoreFile ?: "Store file is not defined")
      storePassword = keyStorePass
      keyAlias = keySignAlias
      keyPassword = keyPass
    }

    getByName("debug") {
      storeFile = file("debug.keystore")
      storePassword = "android"
      keyAlias = "flowcryptdebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    getByName("release") {
      isShrinkResources = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }

    getByName("debug") {
      isDebuggable = true
      versionNameSuffix = "_" +
          defaultConfig.versionCode +
          "__" + SimpleDateFormat("yyyy_MM_dd").format(Date()) +
          "__" + grgit.head().id.substring(0, 7)
      applicationIdSuffix = ".debug"
      signingConfig = signingConfigs.getByName("debug")

      buildConfigField("boolean", "IS_ACRA_ENABLED", "false")
      buildConfigField("boolean", "IS_HTTP_LOG_ENABLED", "true")
      buildConfigField("String", "HTTP_LOG_LEVEL", "\"BODY\"")
      resValue("string", "gradle_is_acra_enabled", "false")
      resValue("string", "gradle_is_http_log_enabled", "true")
      resValue("string", "gradle_http_log_level", "BODY")
    }

    create("uiTests") {
      initWith(getByName("debug"))
      versionNameSuffix =
        "_test_" + defaultConfig.versionCode + "__" + SimpleDateFormat("yyyy_MM_dd").format(Date())
      buildConfigField("String", "ATTESTER_URL", "\"https://flowcrypt.test/attester/\"")
      buildConfigField(
        "String",
        "SHARED_TENANT_FES_URL",
        "\"https://flowcrypt.test/shared-tenant-fes/\""
      )
      buildConfigField("boolean", "IS_HTTP_LOG_ENABLED", "false")
      buildConfigField("String", "HTTP_LOG_LEVEL", "\"NONE\"")
      resValue("string", "gradle_is_http_log_enabled", "false")
      resValue("string", "gradle_http_log_level", "NONE")
    }
  }

  testBuildType = "uiTests"

  sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }

  flavorDimensions += "standard"

  productFlavors {
    //This is a consumer flavor. It's a base flavor
    create("consumer") {
      dimension = "standard"

      buildConfigField("boolean", "IS_ACRA_ENABLED", "true")
      buildConfigField("boolean", "IS_MAIL_DEBUG_ENABLED", "false")
      buildConfigField("boolean", "IS_HTTP_LOG_ENABLED", "false")
      buildConfigField("String", "HTTP_LOG_LEVEL", "\"NONE\"")
      buildConfigField("String", "ATTESTER_URL", "\"https://flowcrypt.com/attester/\"")
      buildConfigField(
        "String",
        "SHARED_TENANT_FES_URL",
        "\"https://flowcrypt.com/shared-tenant-fes/\""
      )
      resValue("string", "gradle_is_acra_enabled", "true")
      resValue("string", "gradle_is_mail_debug_enabled", "false")
      resValue("string", "gradle_is_http_log_enabled", "false")
      resValue("string", "gradle_http_log_level", "NONE")
    }

    //This is an enterprise flavor
    create("enterprise") {
      initWith(getByName("consumer"))
      dimension = "standard"
      applicationIdSuffix = ".enterprise"

      //https://github.com/FlowCrypt/flowcrypt-android/issues/2174
      buildConfigField("boolean", "IS_ACRA_ENABLED", "false")
      resValue("string", "gradle_is_acra_enabled", "false")
    }

    //This flavor must be used only for a development.
    //It has settings for a fast building (some features are disabled or not included).
    create("dev") {
      initWith(getByName("consumer"))
      dimension = "standard"
      versionNameSuffix = "_dev"
      resourceConfigurations += setOf("en", "xxhdpi")
      buildConfigField("boolean", "IS_MAIL_DEBUG_ENABLED", "true")
      resValue("string", "gradle_is_mail_debug_enabled", "true")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    buildConfig = true
    viewBinding = true
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()
    freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
  }

  packaging {
    resources.excludes += setOf(
      "META-INF/DEPENDENCIES",
      "META-INF/LICENSE.md",
      "META-INF/NOTICE.md",
      "META-INF/INDEX.LIST",
      "META-INF/*.SF",
      "META-INF/*.DSA",
      "META-INF/*.RSA",
      "META-INF/javamail.providers",
      "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
    )
  }

  lint {
    warningsAsErrors = true
    //we have to disable lint for 'GradleDependency' checks as we use dependabot for dependencies
    disable += "GradleDependency"
  }

  testOptions {
    animationsDisabled = true
    execution = "ANDROIDX_TEST_ORCHESTRATOR"

    unitTests.all {
      it.testLogging {
        events = setOf(
          org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
          org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
          org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
          org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
        showCauses = true
        showExceptions = true
        showStackTraces = true
      }

      it.addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {

        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
          if (suite.parent == null) {
            logger.lifecycle("----")
            logger.lifecycle("Test result: ${result.resultType}")
            logger.lifecycle(
              "Test summary: ${result.testCount} tests, " +
                  "${result.successfulTestCount} succeeded, " +
                  "${result.failedTestCount} failed, " +
                  "${result.skippedTestCount} skipped"
            )
          }
        }
      })
    }
  }
}

ksp {
  //used by Room, to test migrations
  arg("room.schemaLocation", "$projectDir/schemas")
}

androidComponents {
  beforeVariants { variantBuilder ->
    if (variantBuilder.name in listOf("devRelease", "devUiTests")) {
      // Gradle ignores any variants that satisfy the conditions above.
      println("INFO: Excluded \"${variantBuilder.name}\" from build variant list as unused")
      variantBuilder.enable = false
    }
  }

  onVariants { variant ->
    //we share applicationId as a res value
    variant.resValues.put(
      variant.makeResValueKey("string", "application_id"),
      com.android.build.api.variant.ResValue(variant.applicationId.get())
    )
  }
}

easylauncher {
  buildTypes {
    register("debug") {
      filters(
        chromeLike(
          ribbonColor = "#6600CC",
          labelColor = "#FFFFFF",
          gravity = com.project.starter.easylauncher.filter.ChromeLikeFilter.Gravity.BOTTOM,
          overlayHeight = 0.25f,
          textSizeRatio = 0.2f,
        )
      )
    }

    register("uiTests") {
      filters(
        chromeLike(
          label = "test",
          ribbonColor = "#E91E63",
          labelColor = "#FFFFFF",
          gravity = com.project.starter.easylauncher.filter.ChromeLikeFilter.Gravity.BOTTOM,
          overlayHeight = 0.25f,
          textSizeRatio = 0.2f,
        )
      )
    }
  }

  variants {
    register("devDebug") {
      filters(
        chromeLike(
          label = "dev",
          ribbonColor = "#CC5F00",
          labelColor = "#FFFFFF",
          gravity = com.project.starter.easylauncher.filter.ChromeLikeFilter.Gravity.BOTTOM,
          overlayHeight = 0.25f,
          textSizeRatio = 0.2f,
        )
      )
    }
  }
}

tasks.register("checkCorrectBranch") {
  if (!grgit.branch.current().name.equals("master")) {
    throw GradleException("Please use 'master' branch to generate a release build")
  }
}

tasks.register("renameReleaseBuilds") {
  doLast {
    android.applicationVariants.forEach { applicationVariant ->
      if (applicationVariant.buildType.name == "release") {
        applicationVariant.outputs.forEach { variantOutput ->
          val file = variantOutput.outputFile
          val newName = file.name.replace(
            ".apk", "_" + android.defaultConfig.versionCode +
                "_" + android.defaultConfig.versionName + "_"
                + SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date()) + ".apk"
          )
          variantOutput.outputFile.renameTo(File(file.parent, newName))
        }
      }
    }
  }
}

tasks.register<Copy>("copyReleaseApks") {
  from("$buildDir") {
    include("**/*release*.apk")
  }

  includeEmptyDirs = false
  into("${rootProject.rootDir}/release")

  eachFile {
    //replace path to copy only apk file to the destination folder(without subdirectories)
    path = file.name
  }
}

val devDebugImplementation: Configuration by configurations.creating
val uiTestsImplementation by configurations.named("uiTestsImplementation")

dependencies {
  ksp("com.github.bumptech.glide:ksp:4.16.0")
  ksp("androidx.annotation:annotation:1.9.1")
  ksp("androidx.room:room-compiler:2.6.1")
  //ACRA needs the following dependency to use a custom report sender

  ksp("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")
  ksp("com.google.auto.service:auto-service:1.1.1")

  devDebugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
  //uiTests is the build type for testing.
  //noinspection FragmentGradleConfiguration
  uiTestsImplementation("androidx.fragment:fragment-testing:1.8.6")
  uiTestsImplementation("androidx.test:core-ktx:1.6.1")

  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
  androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
  androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
  androidTestImplementation("androidx.test.espresso:espresso-web:3.6.1")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test:rules:1.6.1")
  androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
  androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
  androidTestImplementation("androidx.room:room-testing:2.6.1")
  androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
  androidTestImplementation("androidx.work:work-testing:2.10.0")
  androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
  androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
  androidTestImplementation("com.athaydes.rawhttp:rawhttp-core:2.6.0")
  androidTestUtil("androidx.test:orchestrator:1.5.1")

  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
  testImplementation("com.flextrade.jfixture:jfixture:2.7.2")
  testImplementation("com.shazam:shazamcrest:0.11")
  testImplementation("org.robolectric:robolectric:4.14.1")
  //we need it to test Parcelable implementation
  testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.room:room-testing:2.6.1")
  testImplementation("io.github.classgraph:classgraph:4.8.179")

  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

  implementation("androidx.legacy:legacy-support-v4:1.0.0")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.legacy:legacy-preference-v14:1.0.0")
  implementation("androidx.cardview:cardview:1.0.0")
  implementation("androidx.browser:browser:1.8.0")
  implementation("androidx.recyclerview:recyclerview:1.4.0")
  implementation("androidx.recyclerview:recyclerview-selection:1.1.0")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation("androidx.test.espresso:espresso-idling-resource:3.6.1")
  implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-process:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-service:2.8.7")
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  //we disabled warnings about paging-runtime-ktx because a newer version doesn't fit our needs
  //noinspection GradleDependency
  implementation("androidx.paging:paging-runtime-ktx:2.1.2")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.core:core-splashscreen:1.0.1")
  implementation("androidx.activity:activity-ktx:1.10.0")
  implementation("androidx.fragment:fragment-ktx:1.8.6")
  implementation("androidx.work:work-runtime-ktx:2.10.0")
  implementation("androidx.navigation:navigation-fragment-ktx:2.8.7")
  implementation("androidx.navigation:navigation-ui-ktx:2.8.7")
  implementation("androidx.navigation:navigation-runtime-ktx:2.8.7")
  implementation("androidx.webkit:webkit:1.12.1")

  implementation("com.google.android.gms:play-services-base:18.5.0")
  implementation("com.google.android.gms:play-services-auth:21.3.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.google.android.flexbox:flexbox:3.0.0")
  implementation("com.google.code.gson:gson:2.12.1")
  implementation("com.google.api-client:google-api-client-android:2.7.2")
  implementation("com.google.apis:google-api-services-gmail:v1-rev20240520-2.0.0")
  //ACRA needs the following dependency to use a custom report sender
  implementation("com.google.auto.service:auto-service-annotations:1.1.1")

  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
  implementation("com.squareup.okio:okio:3.10.2")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

  implementation("com.github.bumptech.glide:glide:4.16.0")
  implementation("com.nulab-inc:zxcvbn:1.9.0")
  implementation("com.burhanrashid52:photoeditor:3.0.2")
  implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")
  implementation("com.sandinh:zbase32-commons-codec_2.12:1.0.0")
  implementation("org.bitbucket.b_c:jose4j:0.9.6")
  implementation("org.jsoup:jsoup:1.18.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
  implementation("org.pgpainless:pgpainless-core:1.7.2")
  implementation("org.eclipse.angus:angus-mail:2.0.3")
  implementation("org.eclipse.angus:gimap:2.0.3")
  implementation("commons-io:commons-io:2.18.0")
  implementation("net.openid:appauth:0.11.1")
  implementation("ch.acra:acra-http:5.12.0")
  implementation("io.github.everythingme:overscroll-decor-android:1.1.1")

  constraints {
    //due to https://github.com/FlowCrypt/flowcrypt-security/issues/199
    implementation("commons-codec:commons-codec:1.18.0") {
      because("version 1.11 has VULNERABILITY DESCRIPTION CWE-200")
    }
  }
}
