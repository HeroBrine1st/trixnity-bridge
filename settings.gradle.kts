plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "trixnity-bridge"

include("bridge")
include("compat")
include("generic-repository-doublepuppeted")
