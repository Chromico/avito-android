plugins {
    `kotlin-dsl`
    id("convention.libraries")
}

group = "com.avito.android.buildlogic"

dependencies {
    implementation("com.avito.android.buildlogic:libraries")
    implementation("com.avito.android.buildlogic:kotlin-convention")
    implementation(platform("com.avito.android:infra-bom"))
    implementation(libs.kotlinPlugin)
    implementation(libs.androidGradlePlugin)
}

repositories {
    mavenCentral()
    google() // agp
    jcenter() // org jetbrains trove4j (agp depends on it)
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
