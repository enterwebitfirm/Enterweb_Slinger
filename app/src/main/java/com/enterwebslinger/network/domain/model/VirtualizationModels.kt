// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/VirtualizationModels.kt ===
package com.enterwebslinger.network.domain.model

enum class HypervisorType(val displayName: String, val defaultPort: Int) {
    VMWARE_VCENTER_REST("VMware vCenter (REST)", 443),
    VMWARE_ESXI_SOAP("VMware ESXi (SOAP/SDK)", 443),
    RH_OVIRT("Red Hat RHEV / oVirt", 443),
    MS_HYPERV_WINRM("Microsoft Hyper-V (WinRM)", 5985),
    ORACLE_VBOX("Oracle VirtualBox (vboxwebsrv)", 18083)
}

enum class VmPowerState {
    POWERED_ON,
    POWERED_OFF,
    SUSPENDED,
    UNKNOWN
}

data class VmItem(
    val id: String,
    val name: String,
    val hypervisorType: HypervisorType,
    val powerState: VmPowerState,
    val cpuCount: Int,
    val memoryMb: Long,
    val guestOs: String,
    val ipAddress: String,
    val toolsStatus: String = "Unknown",
    val storageUsedGb: Double = 0.0
)

data class HypervisorHost(
    val id: String,
    val name: String,
    val ipAddress: String,
    val cpuUsageMhz: Long,
    val totalCpuMhz: Long,
    val ramUsageBytes: Long,
    val totalRamBytes: Long,
    val status: String = "UNKNOWN",
    val model: String = "Unknown"
) {
    val cpuPercent: Float
        get() = if (totalCpuMhz > 0) (cpuUsageMhz.toFloat() / totalCpuMhz).coerceIn(0f, 1f) else 0f

    val ramPercent: Float
        get() = if (totalRamBytes > 0) (ramUsageBytes.toFloat() / totalRamBytes).coerceIn(0f, 1f) else 0f
}

data class DatastoreItem(
    val id: String,
    val name: String,
    val type: String,
    val capacityBytes: Long,
    val freeBytes: Long
) {
    val formattedCapacity: String
        get() = String.format("%.1f GB", capacityBytes / 1_073_741_824.0)
    val formattedFree: String
        get() = String.format("%.1f GB", freeBytes / 1_073_741_824.0)
    val usedPercent: Float
        get() = if (capacityBytes > 0) ((capacityBytes - freeBytes).toFloat() / capacityBytes).coerceIn(0f, 1f) else 0f
}

data class WinRmCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val executionTimeMs: Long,
    val isSuccess: Boolean
)
