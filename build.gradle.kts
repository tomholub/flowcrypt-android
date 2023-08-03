/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    apply from : './ext.gradle.kts'

  repositories {
    google()
    mavenCentral()
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath 'com.android.tools.build:gradle:8.1.0'
        classpath 'androidx.navigation:navigation-safe-args-gradle-plugin:2.6.0'
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0'
        classpath 'com.project.starter:easylauncher:6.2.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
