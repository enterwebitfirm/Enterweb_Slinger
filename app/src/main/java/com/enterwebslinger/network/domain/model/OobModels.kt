// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/OobModels.kt ===
package com.enterwebslinger.network.domain.model

enum class ServerPowerState {
    ON,
    OFF,
    POWERING_ON,
    POWERING_OFF,
    PAUSED,
    UNKNOWN
}

enum class PowerAction(val redfishName: String, val ipmiCode: Byte) {
    ON("On", 0x01),
    FORCE_OFF("ForceOff", 0x00),
    GRACEFUL_SHUTDOWN("GracefulShutdown", 0x05),
    FORCE_RESTART("ForceRestart", 0x03),
    POWER_CYCLE("PowerCycle", 0x02)
}

enum class BootDevice(val redfishTarget: String) {
    PXE("Pxe"),
    CD_DVD("Cd"),
    BIOS_SETUP("BiosSetup"),
    HDD("Hdd"),
    NONE("None")
}

data class RedfishServerSummary(
    val id: String = "",
    val bmcHost: String = "",
    val name: String = "",
    val manufacturer: String = "Unknown",
    val model: String = "Unknown",
    val serialNumber: String = "Unknown",
    val biosVersion: String = "Unknown",
    val powerState: ServerPowerState = ServerPowerState.UNKNOWN,
    val healthStatus: String = "Unknown",
    val indicatorLed: String = "Off",
    val thermalSensors: List<ThermalSensor> = emptyList(),
    val fanSensors: List<FanSensor> = emptyList(),
    val powerSupplies: List<PowerSupply> = emptyList(),
    val selEntries: List<SelEntry> = emptyList()
)

data class ThermalSensor(
    val name: String,
    val currentCelsius: Double,
    val warningCelsius: Double = 85.0,
    val criticalCelsius: Double = 95.0,
    val status: String = "Unknown"
)

data class FanSensor(
    val name: String,
    val rpm: Int,
    val status: String = "Unknown"
)

data class PowerSupply(
    val name: String,
    val powerCapacityWatts: Double,
    val lastPowerOutputWatts: Double,
    val status: String = "Unknown"
)

data class SelEntry(
    val id: String,
    val timestamp: String,
    val severity: String,
    val message: String,
    val sensorType: String
)

data class AsfPresenceResult(
    val isAlive: Boolean,
    val ianaEnterpriseId: Long,
    val oemType: String,
    val responseTimeMs: Long,
    val rawHex: String = ""
)

data class IpmiChassisStatus(
    val isPowerOn: Boolean,
    val powerOverload: Boolean,
    val interlock: Boolean,
    val powerFault: Boolean,
    val driveFault: Boolean,
    val coolingFault: Boolean,
    val lastPowerEvent: String,
    val rawStatusByte: Byte
)
