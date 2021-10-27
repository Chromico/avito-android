package com.avito.instrumentation.internal

import com.avito.instrumentation.configuration.InstrumentationConfiguration
import com.avito.instrumentation.configuration.InstrumentationTestsPluginExtension
import com.avito.instrumentation.dumpDirName
import com.avito.logger.LoggerFactory
import com.avito.logger.create
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

internal class OutputDirResolver(
    private val extension: InstrumentationTestsPluginExtension,
    private val reportResolver: ReportResolver,
    private val rootProjectLayout: ProjectLayout,
    private val providers: ProviderFactory,
    loggerFactory: LoggerFactory,
) {

    private val logger = loggerFactory.create<OutputDirResolver>()

    fun resolve(configuration: InstrumentationConfiguration): Provider<Directory> {
        return resolveWithDeprecatedProperty().map {
            it.dir("${reportResolver.getRunId(extension)}/${configuration.name}")
        }
    }

    fun resolveArgsDumpDir(): Provider<Directory> {
        return resolveWithDeprecatedProperty().map { it.dir(dumpDirName) }
    }

    private fun resolveWithDeprecatedProperty(): Provider<Directory> {
        return extension.outputDir.convention(getDeprecatedOutputValueIfSet())
    }

    private fun getDeprecatedOutputValueIfSet(): Provider<Directory> {
        @Suppress("DEPRECATION")
        return providers.provider {
            if (extension.output != extension.defaultOutput.get().asFile.path) {
                logger.warn(
                    "Deprecated 'output' property used in InstrumentationTestsPluginExtension, " +
                        "please migrate to 'outputDir'"
                )

                val rootDirPath = rootProjectLayout.projectDirectory.asFile.path
                if (extension.output.startsWith(rootDirPath)) {
                    val relativePath = extension.output.replace(rootDirPath, "").removePrefix("/")
                    rootProjectLayout.projectDirectory.dir(relativePath)
                } else {
                    logger.warn(
                        "Deprecated 'output' property used in InstrumentationTestsPluginExtension " +
                            "has incorrect input: should be inside project root directory, but was: ${extension.output}"
                    )
                    null
                }
            } else {
                null
            }
        }
    }
}