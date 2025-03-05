plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
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

mavenPublishing {
    coordinates(project.group.toString(), project.name.toString(), project.version.toString())
}