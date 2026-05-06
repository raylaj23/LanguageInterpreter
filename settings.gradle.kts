// Auto-provisions a matching JDK if one isn't installed locally,
// so reviewers don't need to install Java 21 manually.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "language-interpreter"
