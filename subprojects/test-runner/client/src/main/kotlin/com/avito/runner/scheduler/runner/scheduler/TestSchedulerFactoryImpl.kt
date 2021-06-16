package com.avito.runner.scheduler.runner.scheduler

import com.avito.android.TestSuiteLoader
import com.avito.android.runner.devices.DevicesProvider
import com.avito.android.runner.devices.DevicesProviderFactory
import com.avito.android.runner.report.Report
import com.avito.android.runner.report.ReportFactory
import com.avito.android.stats.StatsDSender
import com.avito.filestorage.RemoteStorageFactory
import com.avito.http.HttpClientProvider
import com.avito.report.model.TestStaticData
import com.avito.report.serialize.ReportSerializer
import com.avito.retrace.ProguardRetracer
import com.avito.runner.config.InstrumentationTestsActionParams
import com.avito.runner.scheduler.TestRunnerFactory
import com.avito.runner.scheduler.args.TestRunnerFactoryConfig
import com.avito.runner.scheduler.listener.ArtifactsTestListener
import com.avito.runner.scheduler.listener.AvitoFileStorageUploader
import com.avito.runner.scheduler.listener.LegacyTestArtifactsProcessor
import com.avito.runner.scheduler.listener.LogListener
import com.avito.runner.scheduler.listener.LogcatProcessor
import com.avito.runner.scheduler.listener.LogcatTestLifecycleListener
import com.avito.runner.scheduler.listener.ReportProcessor
import com.avito.runner.scheduler.listener.ReportProcessorImpl
import com.avito.runner.scheduler.listener.TestArtifactsProcessor
import com.avito.runner.scheduler.listener.TestArtifactsProcessorImpl
import com.avito.runner.scheduler.listener.TestArtifactsUploader
import com.avito.runner.scheduler.listener.TestLifecycleListener
import com.avito.runner.scheduler.metrics.InstrumentationMetricsSender
import com.avito.runner.scheduler.metrics.TestMetricsListenerImpl
import com.avito.runner.scheduler.metrics.TestMetricsSender
import com.avito.runner.scheduler.suite.TestSuiteProvider
import com.avito.runner.scheduler.suite.filter.FilterFactory
import com.avito.runner.scheduler.suite.filter.FilterInfoWriter
import com.avito.runner.service.DeviceWorkerPoolProvider
import com.avito.runner.service.listener.CompositeListener
import com.avito.runner.service.listener.TestListener
import com.avito.runner.service.model.TestCase
import com.avito.runner.service.worker.device.adb.listener.RunnerMetricsConfig
import com.avito.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.nio.file.Files

public class TestSchedulerFactoryImpl(
    private val params: InstrumentationTestsActionParams,
    private val report: Report,
    private val timeProvider: TimeProvider,
    private val httpClientProvider: HttpClientProvider,
    private val metricsConfig: RunnerMetricsConfig,
    private val testSuiteLoader: TestSuiteLoader,
    private val reportFactory: ReportFactory,
    private val devicesProviderFactory: DevicesProviderFactory
) : TestSchedulerFactory {

    override fun create(): TestScheduler {
        val tempDir = Files.createTempDirectory(null).toFile()
        val testSuiteProvider: TestSuiteProvider = createTestSuiteProvider()

        val statsDSender: StatsDSender = StatsDSender.Impl(
            config = metricsConfig.statsDConfig,
            loggerFactory = params.loggerFactory
        )

        val metricsSender = InstrumentationMetricsSender(
            statsDSender = statsDSender,
            runnerPrefix = metricsConfig.runnerPrefix
        )

        val devicesProvider = devicesProviderFactory.create(tempLogcatDir = tempDir)

        return TestSchedulerImpl(
            params = params,
            report = report,
            testSuiteProvider = testSuiteProvider,
            testSuiteLoader = testSuiteLoader,
            filterInfoWriter = FilterInfoWriter.Impl(
                outputDir = params.outputDir,
            ),
            loggerFactory = params.loggerFactory,
            executionParameters = params.executionParameters,
            devicesProvider = devicesProvider,
            testRunnerFactoryFactory = { testStaticDataByTestCase ->
                createTestRunnerFactory(
                    testStaticDataByTestCase = testStaticDataByTestCase,
                    devicesProvider = devicesProvider,
                    tempLogcatDir = tempDir,
                    metricsSender = metricsSender
                )
            },
        )
    }

    private fun createTestRunnerFactory(
        testStaticDataByTestCase: Map<TestCase, TestStaticData>,
        devicesProvider: DevicesProvider,
        tempLogcatDir: File,
        metricsSender: InstrumentationMetricsSender
    ): TestRunnerFactory {
        val statsDSender: StatsDSender = StatsDSender.Impl(
            config = metricsConfig.statsDConfig,
            loggerFactory = params.loggerFactory
        )
        val testMetricsSender = TestMetricsListenerImpl(
            testMetricsSender = TestMetricsSender(
                statsDSender = statsDSender,
                prefix = metricsConfig.runnerPrefix
            ),
            timeProvider = timeProvider,
            loggerFactory = params.loggerFactory
        )

        return TestRunnerFactory(
            outputDir = params.outputDir,
            config = TestRunnerFactoryConfig(
                loggerFactory = params.loggerFactory,
                reservation = devicesProvider,
            ),
            deviceWorkerPoolProvider = DeviceWorkerPoolProvider(
                timeProvider = timeProvider,
                loggerFactory = params.loggerFactory,
                testListenerProvider = { testRunnerOutputDir ->
                    CompositeListener(
                        listeners = mutableListOf<TestListener>().apply {
                            add(LogListener())
                            add(
                                ArtifactsTestListener(
                                    lifecycleListener = createTestReporter(
                                        testStaticDataByTestCase = testStaticDataByTestCase,
                                        tempLogcatDir = tempLogcatDir,
                                        metricsSender = metricsSender
                                    ),
                                    outputDirectory = testRunnerOutputDir,
                                    loggerFactory = params.loggerFactory,
                                    saveTestArtifactsToOutputs = params.saveTestArtifactsToOutputs,
                                    fetchLogcatForIncompleteTests = params.fetchLogcatForIncompleteTests,
                                )
                            )
                        }
                    )
                },
                deviceListener = testMetricsSender
            ),
            testMetricsListener = testMetricsSender
        )
    }

    private fun createTestReporter(
        testStaticDataByTestCase: Map<TestCase, TestStaticData>,
        tempLogcatDir: File,
        metricsSender: InstrumentationMetricsSender
    ): TestLifecycleListener {
        return LogcatTestLifecycleListener(
            logcatDir = tempLogcatDir,
            reportProcessor = createReportProcessor(testStaticDataByTestCase, metricsSender),
            report = report,
        )
    }

    private fun createTestSuiteProvider(): TestSuiteProvider = TestSuiteProvider.Impl(
        report = report,
        targets = params.instrumentationConfiguration.targets,
        reportSkippedTests = params.instrumentationConfiguration.reportSkippedTests,
        filterFactory = FilterFactory.create(
            filterData = params.instrumentationConfiguration.filter,
            impactAnalysisResult = params.impactAnalysisResult,
            reportFactory = reportFactory,
            loggerFactory = params.loggerFactory
        )
    )

    private fun createReportProcessor(
        testSuite: Map<TestCase, TestStaticData>,
        metricsSender: InstrumentationMetricsSender
    ): ReportProcessor {

        val dispatcher = Dispatchers.IO

        val retracer: ProguardRetracer = ProguardRetracer.Impl(params.proguardMappings)

        val artifactsUploader: TestArtifactsUploader = AvitoFileStorageUploader(
            RemoteStorageFactory.create(
                endpoint = params.fileStorageUrl.toHttpUrl(),
                httpClientProvider = httpClientProvider,
                isAndroidRuntime = false
            )
        )

        val logcatUploader = LogcatProcessor.Impl(
            testArtifactsUploader = artifactsUploader,
            retracer = retracer
        )

        return ReportProcessorImpl(
            loggerFactory = params.loggerFactory,
            testSuite = testSuite,
            metricsSender = metricsSender,
            testArtifactsProcessor = createTestArtifactsProcessor(
                uploadTestArtifacts = params.uploadTestArtifacts,
                reportSerializer = ReportSerializer(),
                dispatcher = dispatcher,
                logcatProcessor = logcatUploader,
                testArtifactsUploader = artifactsUploader
            ),
            logcatProcessor = logcatUploader,
            timeProvider = timeProvider,
            dispatcher = dispatcher
        )
    }

    private fun createTestArtifactsProcessor(
        uploadTestArtifacts: Boolean,
        reportSerializer: ReportSerializer,
        dispatcher: CoroutineDispatcher,
        logcatProcessor: LogcatProcessor,
        testArtifactsUploader: TestArtifactsUploader
    ): TestArtifactsProcessor {

        return if (uploadTestArtifacts) {
            TestArtifactsProcessorImpl(
                reportSerializer = reportSerializer,
                testArtifactsUploader = testArtifactsUploader,
                dispatcher = dispatcher,
                logcatProcessor = logcatProcessor
            )
        } else {
            LegacyTestArtifactsProcessor(
                reportSerializer = reportSerializer,
                logcatProcessor = logcatProcessor,
                dispatcher = dispatcher
            )
        }
    }
}
