plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
}

kotlin {
    jvmToolchain(17)
    explicitApiWarning()

    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.trixnity.applicationservice)
            api(libs.trixnity.api.client)
            api(libs.trixnity.clientserverapi.client)
        }
    }
}

mavenPublishing {
    coordinates(project.group.toString(), project.name.toString(), project.version.toString())
}