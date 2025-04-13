plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    // the whole vanniktech plugin is not loaded because it tries to publish root project too
    // however, it depends on this plugin, and that's the only thing we need to configure
    `maven-publish`
}

allprojects {
    group = "ru.herobrine1st.matrix"
    version = "0.1.4"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        configure<PublishingExtension> {
            publishing.repositories {
                maven {
                    name = "forgejo"
                    url = uri("https://git.herobrine1st.ru/api/packages/HeroBrine1st/maven")

                    credentials(PasswordCredentials::class)
                }
            }
        }
    }
}