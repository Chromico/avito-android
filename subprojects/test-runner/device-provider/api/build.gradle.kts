plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-kotlin-library")
}

dependencies {
    api(project(":test-runner:device-provider:model"))
}

kotlin {
    explicitApi()
}