plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "ru.herobrine1st.matrix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    explicitApiWarning()

    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(projects.compat)
            api(libs.trixnity.applicationservice)

            implementation(libs.trixnity.api.client)
            implementation(libs.trixnity.clientserverapi.client)
            implementation(libs.kotlinLogging)
        }
    }
}