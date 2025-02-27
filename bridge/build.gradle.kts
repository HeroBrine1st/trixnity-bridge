import org.gradle.internal.impldep.org.bouncycastle.cms.RecipientId.password

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

//publishing.repositories {
//    maven {
//        name = "forgejo"
//        url = uri("https://git.herobrine1st.ru/api/packages/HeroBrine1st/maven")
//
//        credentials(PasswordCredentials::class)
//    }
//}

mavenPublishing {
    coordinates("ru.herobrine1st.matrix.bridge", "bridge", "1.0-SNAPSHOT")
}