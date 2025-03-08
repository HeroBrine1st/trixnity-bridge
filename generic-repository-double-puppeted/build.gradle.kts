plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
    explicitApiWarning()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.bridge)
            implementation(libs.sqldelight.extensions.coroutines)
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("ru.herobrine1st.matrix.bridge.repository.generic.doublepuppeted.database")
            //dialect(libs.sqldelight.dialect.sqlite)
            generateAsync = true
        }
    }
}

mavenPublishing {
    coordinates(project.group.toString(), project.name.toString(), project.version.toString())
}