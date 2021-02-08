package com.avito.instrumentation.configuration

import com.google.common.annotations.VisibleForTesting
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import java.io.File
import java.io.Serializable

public object InstrumentationPluginConfiguration {
    public abstract class GradleInstrumentationPluginConfiguration(
        project: Project
    ) {

        public var applicationApk: String? = null
        public var testApplicationApk: String? = null

        public var reportApiUrl: String = ""
        public var reportApiFallbackUrl: String = ""
        public var reportViewerUrl: String = ""
        public var fileStorageUrl: String = ""

        public var registry: String = ""

        // todo make optional
        public var sentryDsn: String = ""

        // todo extract
        public var slackToken: String = ""

        public var unitToChannelMap: Map<String, String> = emptyMap()

        public var applicationProguardMapping: File? = null
        public var testProguardMapping: File? = null

        public abstract val configurationsContainer: NamedDomainObjectContainer<InstrumentationConfiguration>
        public abstract val filters: NamedDomainObjectContainer<InstrumentationFilter>

        @get:Incubating
        public val testReport: InstrumentationTestReportExtension = InstrumentationTestReportExtension()

        public val configurations: List<InstrumentationConfiguration>
            get() = configurationsContainer.toList()

        public var instrumentationParams: Map<String, String> = emptyMap()

        // https://developer.android.com/studio/command-line/logcat#filteringOutput
        public var logcatTags: Collection<String> = emptyList()

        public var output: String =
            project.rootProject.file("outputs/${project.name}/instrumentation").path

        public fun configurations(closure: Closure<NamedDomainObjectSet<InstrumentationConfiguration>>) {
            configurationsContainer.configure(closure)
        }

        public fun filters(action: Action<NamedDomainObjectContainer<InstrumentationFilter>>) {
            action.execute(filters)
        }

        public fun testReport(action: Action<InstrumentationTestReportExtension>) {
            action.execute(testReport)
        }

        private fun validate() {
            configurations.forEach {
                it.validate()
            }
            require(sentryDsn.isNotEmpty()) {
                "sentryDsn must be initialized"
            }
            require(slackToken.isNotEmpty()) {
                "slackToken must be initialized"
            }
            require(registry.isNotEmpty()) {
                "registry must be initialized"
            }
        }

        private fun createReportViewer(): Data.ReportViewer? {
            val reportViewer = testReport.reportViewer
            return if (reportViewer != null) {
                Data.ReportViewer(
                    reportApiUrl = reportViewer.reportApiUrl,
                    reportApiFallbackUrl = reportViewer.reportApiFallbackUrl,
                    reportViewerUrl = reportViewer.reportViewerUrl,
                    fileStorageUrl = reportViewer.fileStorageUrl
                )
            } else {
                if (reportApiFallbackUrl.isNotEmpty()
                    && reportViewerUrl.isNotEmpty()
                    && reportApiUrl.isNotEmpty()
                    && fileStorageUrl.isNotEmpty()
                ) {
                    Data.ReportViewer(
                        reportApiUrl = reportApiUrl,
                        reportApiFallbackUrl = reportApiFallbackUrl,
                        reportViewerUrl = reportViewerUrl,
                        fileStorageUrl = fileStorageUrl
                    )
                } else {
                    null
                }
            }
        }

        internal fun toData(params: InstrumentationParameters): Data {
            validate()
            val reportViewer = createReportViewer()
            val instrumentationParameters = params
                .applyParameters(
                    mapOf(
                        "sentryDsn" to sentryDsn,
                        "slackToken" to slackToken,
                        "reportApiUrl" to (reportViewer?.reportApiUrl ?: "http://stub"),
                        "reportApiFallbackUrl" to (reportViewer?.reportApiFallbackUrl ?: "http://stub"),
                        "fileStorageUrl" to (reportViewer?.fileStorageUrl ?: "http://stub"),
                        "reportViewerUrl" to (reportViewer?.reportViewerUrl ?: "http://stub")
                    )
                )
                .applyParameters(instrumentationParams)
                .applyParameters(
                    mapOf(
                        // info for InHouseRunner testRunEnvironment creation
                        "inHouse" to "true"
                    )
                )
            return Data(
                configurations = configurations.map { instrumentationConfiguration ->
                    instrumentationConfiguration.data(
                        parentInstrumentationParameters = instrumentationParameters,
                        filters = filters.map { it.toData() }
                    )
                },
                pluginInstrumentationParameters = instrumentationParameters,
                logcatTags = logcatTags,
                output = output,
                applicationApk = applicationApk,
                testApplicationApk = testApplicationApk,
                reportViewer = reportViewer,
                registry = registry,
                slackToken = slackToken,
                applicationProguardMapping = applicationProguardMapping,
                testProguardMapping = testProguardMapping
            )
        }

        public data class Data(
            val configurations: Collection<InstrumentationConfiguration.Data>,
            private val pluginInstrumentationParameters: InstrumentationParameters,
            val logcatTags: Collection<String>,
            val output: String,
            val applicationApk: String?, // TODO file
            val testApplicationApk: String?, // TODO file
            val reportViewer: ReportViewer?,
            val registry: String,
            val slackToken: String,
            val applicationProguardMapping: File?,
            val testProguardMapping: File?
        ) : Serializable {

            public data class ReportViewer(
                val reportApiUrl: String,
                val reportApiFallbackUrl: String,
                val reportViewerUrl: String,
                val fileStorageUrl: String
            ) : Serializable

            /**
             * Из-за того что раньше instrumentationParameters были публичными, их использовали ошибочно,
             * т.к. там еще не учтены override из конфигураций (например часто это новый jobSlug)
             *
             * возможно в дальшейшем пригодится доступ к параметрам на этом уровне,
             * поэтому поле и тест на этот уровень сохранены
             */
            @VisibleForTesting
            internal fun checkPluginLevelInstrumentationParameters(): InstrumentationParameters {
                return pluginInstrumentationParameters
            }
        }
    }
}
