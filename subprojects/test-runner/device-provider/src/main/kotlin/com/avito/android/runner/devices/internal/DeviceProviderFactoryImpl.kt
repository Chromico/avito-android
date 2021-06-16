package com.avito.android.runner.devices.internal

import com.avito.android.runner.devices.DevicesProvider
import com.avito.android.runner.devices.DevicesProviderFactory
import com.avito.android.runner.devices.internal.kubernetes.KubernetesReservationClientProvider
import com.avito.android.runner.devices.model.DeviceType
import com.avito.logger.LoggerFactory
import com.avito.runner.service.worker.device.adb.Adb
import com.avito.runner.service.worker.device.adb.AdbDeviceFactory
import com.avito.runner.service.worker.device.adb.AdbDevicesManager
import com.avito.runner.service.worker.device.adb.listener.RunnerMetricsConfig
import com.avito.time.TimeProvider
import java.io.File

internal class DeviceProviderFactoryImpl(
    private val loggerFactory: LoggerFactory,
    private val timeProvider: TimeProvider,
    private val deviceType: DeviceType,
    private val kubernetesReservationClientProvider: KubernetesReservationClientProvider,
    private val androidDebugBridgeProvider: AndroidDebugBridgeProvider,
    private val emulatorsLogsReporterProvider: EmulatorsLogsReporterProvider,
    private val metricsConfig: RunnerMetricsConfig
) : DevicesProviderFactory {

    override fun create(tempLogcatDir: File): DevicesProvider {
        return when (deviceType) {
            DeviceType.MOCK ->
                StubDevicesProvider(loggerFactory)

            DeviceType.LOCAL ->
                LocalDevicesProvider(
                    androidDebugBridge = androidDebugBridgeProvider.provide(),
                    emulatorsLogsReporter = emulatorsLogsReporterProvider.provide(tempLogcatDir),
                    devicesManager = AdbDevicesManager(
                        loggerFactory = loggerFactory,
                        adb = Adb()
                    ),
                    adbDeviceFactory = AdbDeviceFactory(
                        loggerFactory = loggerFactory,
                        adb = Adb(),
                        timeProvider = timeProvider,
                        metricsConfig = null
                    ),
                    loggerFactory = loggerFactory,
                )

            DeviceType.CLOUD ->
                KubernetesDevicesProvider(
                    client = kubernetesReservationClientProvider.provide(tempLogcatDir),
                    adbDeviceFactory = AdbDeviceFactory(
                        loggerFactory = loggerFactory,
                        adb = Adb(),
                        timeProvider = timeProvider,
                        metricsConfig = metricsConfig
                    ),
                    devicesManager = AdbDevicesManager(
                        loggerFactory = loggerFactory,
                        adb = Adb()
                    )
                )
        }
    }
}
