plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "ru.herobrine1st.matrix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
