import com.avito.android.publish.CreateStagingRepositoryTask

plugins {
    id("convention.publish-base")
    signing
}

val ossrhUsername: Provider<String> = providers.gradleProperty("avito.ossrh.user")
    .forUseAtConfigurationTime()

val ossrhPassword: Provider<String> = providers.gradleProperty("avito.ossrh.password")
    .forUseAtConfigurationTime()

val ossrhStagingProfileId: Provider<String> = providers.gradleProperty("avito.ossrh.stagingProfileId")
    .forUseAtConfigurationTime()

val sonatypeRepoName = "SonatypeReleases"

val repositoryUrlOutputFilePath: Provider<RegularFile> = rootProject.layout.buildDirectory.file("sonatype-repo.id")

val buildId: Provider<String> = providers.gradleProperty("teamcityBuildId").forUseAtConfigurationTime()

val createStagingRepositoryTask: TaskProvider<CreateStagingRepositoryTask> = with(rootProject.tasks) {
    val createStagingTaskName = "createSonatypeStagingRepository"

    try {
        @Suppress("UNCHECKED_CAST")
        named(createStagingTaskName) as TaskProvider<CreateStagingRepositoryTask>
    } catch (e: UnknownTaskException) {
        register<CreateStagingRepositoryTask>(createStagingTaskName) {
            group = "publication"

            stagingProfileId.set(ossrhStagingProfileId)
            user.set(ossrhUsername)
            password.set(ossrhPassword)
            repositoryDescription.set("Release v.$version; build ${buildId.get()}")
            repositoryIdFile.set(repositoryUrlOutputFilePath)
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {

    // https://docs.gradle.org/current/userguide/publishing_customization.html#sec:configuring_publishing_tasks
    if (name.contains(sonatypeRepoName)) {
        doFirst {

            // no direct task access, because "cannot be cast to class CreateStagingRepositoryTask" for some reason
            val repositoryUrl = repositoryUrlOutputFilePath.get().asFile.readText()
            repository = repository.apply { setUrl(repositoryUrl) }
        }
        dependsOn(createStagingRepositoryTask)
    }
}

val publishTask = tasks.register<Task>("publishToSonatype") {
    group = "publication"

    dependsOn(tasks.named("publishAllPublicationsTo${sonatypeRepoName}Repository"))
}

publishing {
    repositories {
        maven {
            name = sonatypeRepoName
            credentials {
                username = ossrhUsername.orNull
                password = ossrhPassword.orNull
            }
        }
    }
}

signing {
    sign(publishing.publications)

    val signingKeyId = providers.gradleProperty("avito.pgp.keyid")
        .forUseAtConfigurationTime()
        .orNull
    val signingKey = providers.gradleProperty("avito.pgp.key")
        .forUseAtConfigurationTime()
        .orNull
    val signingPassword = providers.gradleProperty("avito.pgp.password")
        .forUseAtConfigurationTime()
        .orNull

    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
}

tasks.withType<Sign>().configureEach {
    onlyIf {
        gradle.taskGraph.hasTask(publishTask.get())
    }
}
