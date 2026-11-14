// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/CellularTelemetryRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.enterwebslinger.network.domain.model.CellRadioType
import com.enterwebslinger.network.domain.model.CellularNeighborEntry
import com.enterwebslinger.network.domain.model.CellularTelemetryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Pure-Kotlin Cellular 5G NR / 4G LTE / 3G WCDMA / Neighboring Cell Telemetry Repository.
 * Uses Android TelephonyManager CellInfo & ServiceState APIs with runtime permission gating.
 */
class CellularTelemetryRepository(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    fun hasCellularPermissions(): Boolean {
        val phone = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return phone && fine
    }

    @SuppressLint("MissingPermission")
    suspend fun inspectCellularTelemetry(): CellularTelemetryState = withContext(Dispatchers.IO) {
        val tm = telephonyManager ?: return@withContext CellularTelemetryState(
            errorMessage = "TelephonyManager unavailable on this hardware."
        )
        if (!hasCellularPermissions()) {
            return@withContext CellularTelemetryState(
                errorMessage = "READ_PHONE_STATE and ACCESS_FINE_LOCATION permissions are required to inspect cellular RF towers."
            )
        }

        val operatorName = runCatching { tm.networkOperatorName.ifBlank { tm.simOperatorName } }
            .getOrDefault("Unavailable").ifBlank { "Unavailable" }
        val opNumeric = runCatching { tm.networkOperator }.getOrDefault("")
        val mcc = if (opNumeric.length >= 3) opNumeric.substring(0, 3) else "--"
        val mnc = if (opNumeric.length >= 5) opNumeric.substring(3) else "--"

        val dataNetType = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm.dataNetworkType else tm.networkType
        }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        val networkTypeLabel = mapDataNetworkType(dataNetType)

        val voiceStateLabel = runCatching {
            when (tm.serviceState?.state) {
                ServiceState.STATE_IN_SERVICE -> "In Service"
                ServiceState.STATE_OUT_OF_SERVICE -> "Out of Service"
                ServiceState.STATE_EMERGENCY_ONLY -> "Emergency Only"
                ServiceState.STATE_POWER_OFF -> "Radio Off / Airplane Mode"
                else -> "Unknown"
            }
        }.getOrDefault("Unknown")

        val cellInfos: List<CellInfo> = fetchFreshCellInfo(tm)
        if (cellInfos.isEmpty()) {
            return@withContext CellularTelemetryState(
                operatorName = operatorName,
                mcc = mcc,
                mnc = mnc,
                networkTypeLabel = networkTypeLabel,
                voiceServiceState = voiceStateLabel,
                lastUpdatedMs = System.currentTimeMillis(),
                errorMessage = "No cellular radio cells reported (no SIM, radio off, or Wi-Fi only device)."
            )
        }

        var primaryType = CellRadioType.UNKNOWN
        var primaryCellId = -1L
        var primaryPci = -1
        var primaryTac = -1
        var primaryArfcn = -1
        var primaryBand = "--"
        var primaryRsrp: Int? = null
        var primaryRsrq: Int? = null
        var primarySinr: Int? = null
        var primaryTa: Int? = null
        var primaryMcc = mcc
        var primaryMnc = mnc

        val neighbors = mutableListOf<CellularNeighborEntry>()
        val registeredFirst = cellInfos.sortedByDescending { it.isRegistered }

        for (info in registeredFirst) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
                val id = info.cellIdentity as? CellIdentityNr
                val sig = info.cellSignalStrength as? CellSignalStrengthNr
                val pci = sanitizeVal(id?.pci)
                val arfcn = sanitizeVal(id?.nrarfcn)
                val rsrp = sanitizeVal(sig?.ssRsrp)
                val rsrq = sanitizeVal(sig?.ssRsrq)
                val sinr = sanitizeVal(sig?.ssSinr)
                val band = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && id?.bands?.isNotEmpty() == true) {
                    id.bands.joinToString(",") { "n$it" }
                } else {
                    if (arfcn > 0) "NR-ARFCN $arfcn" else "5G NR"
                }

                if (info.isRegistered && primaryType == CellRadioType.UNKNOWN) {
                    primaryType = CellRadioType.NR_5G
                    primaryCellId = id?.nci?.takeIf { it != CellInfo.UNAVAILABLE_LONG } ?: -1L
                    primaryPci = pci
                    primaryTac = sanitizeVal(id?.tac)
                    primaryArfcn = arfcn
                    primaryBand = band
                    primaryRsrp = rsrp.takeIf { it != -1 }
                    primaryRsrq = rsrq.takeIf { it != -1 }
                    primarySinr = sinr.takeIf { it != -1 }
                    id?.mccString?.takeIf { it.isNotBlank() }?.let { primaryMcc = it }
                    id?.mncString?.takeIf { it.isNotBlank() }?.let { primaryMnc = it }
                } else {
                    neighbors.add(
                        CellularNeighborEntry(
                            radioType = CellRadioType.NR_5G,
                            pciOrPsc = pci,
                            arfcnOrEarfcn = arfcn,
                            bandLabel = band,
                            rsrpOrRscpDbm = if (rsrp != -1) rsrp else -140,
                            rsrqOrEcNoDb = rsrq.takeIf { it != -1 },
                            isRegistered = info.isRegistered
                        )
                    )
                }
            } else if (info is CellInfoLte) {
                val id: CellIdentityLte = info.cellIdentity
                val sig: CellSignalStrengthLte = info.cellSignalStrength
                val pci = sanitizeVal(id.pci)
                val earfcn = sanitizeVal(id.earfcn)
                val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sanitizeVal(sig.rsrp) else sig.dbm
                val rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sanitizeVal(sig.rsrq) else -1
                val rssnr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sanitizeVal(sig.rssnr) else -1
                val ta = sanitizeVal(sig.timingAdvance)
                val band = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && id.bands.isNotEmpty()) {
                    id.bands.joinToString(",") { "B$it" }
                } else {
                    earfcnToLteBandLabel(earfcn)
                }

                if (info.isRegistered && primaryType == CellRadioType.UNKNOWN) {
                    primaryType = CellRadioType.LTE_4G
                    primaryCellId = sanitizeVal(id.ci).toLong()
                    primaryPci = pci
                    primaryTac = sanitizeVal(id.tac)
                    primaryArfcn = earfcn
                    primaryBand = band
                    primaryRsrp = rsrp.takeIf { it != -1 }
                    primaryRsrq = rsrq.takeIf { it != -1 }
                    primarySinr = rssnr.takeIf { it != -1 }
                    primaryTa = ta.takeIf { it != -1 }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        id.mccString?.takeIf { it.isNotBlank() }?.let { primaryMcc = it }
                        id.mncString?.takeIf { it.isNotBlank() }?.let { primaryMnc = it }
                    }
                } else {
                    neighbors.add(
                        CellularNeighborEntry(
                            radioType = CellRadioType.LTE_4G,
                            pciOrPsc = pci,
                            arfcnOrEarfcn = earfcn,
                            bandLabel = band,
                            rsrpOrRscpDbm = if (rsrp != -1) rsrp else sig.dbm,
                            rsrqOrEcNoDb = rsrq.takeIf { it != -1 },
                            isRegistered = info.isRegistered
                        )
                    )
                }
            } else if (info is CellInfoWcdma) {
                val id: CellIdentityWcdma = info.cellIdentity
                val sig: CellSignalStrengthWcdma = info.cellSignalStrength
                val psc = sanitizeVal(id.psc)
                val uarfcn = sanitizeVal(id.uarfcn)
                val rscp = sig.dbm
                val ecNo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sanitizeVal(sig.ecNo) else -1

                if (info.isRegistered && primaryType == CellRadioType.UNKNOWN) {
                    primaryType = CellRadioType.WCDMA_3G
                    primaryCellId = sanitizeVal(id.cid).toLong()
                    primaryPci = psc
                    primaryTac = sanitizeVal(id.lac)
                    primaryArfcn = uarfcn
                    primaryBand = if (uarfcn > 0) "UMTS UARFCN $uarfcn" else "3G UMTS"
                    primaryRsrp = rscp
                    primaryRsrq = ecNo.takeIf { it != -1 }
                } else {
                    neighbors.add(
                        CellularNeighborEntry(
                            radioType = CellRadioType.WCDMA_3G,
                            pciOrPsc = psc,
                            arfcnOrEarfcn = uarfcn,
                            bandLabel = "3G UMTS",
                            rsrpOrRscpDbm = rscp,
                            rsrqOrEcNoDb = ecNo.takeIf { it != -1 },
                            isRegistered = info.isRegistered
                        )
                    )
                }
            } else if (info is CellInfoGsm) {
                val id: CellIdentityGsm = info.cellIdentity
                val sig: CellSignalStrengthGsm = info.cellSignalStrength
                if (info.isRegistered && primaryType == CellRadioType.UNKNOWN) {
                    primaryType = CellRadioType.GSM_2G
                    primaryCellId = sanitizeVal(id.cid).toLong()
                    primaryTac = sanitizeVal(id.lac)
                    primaryArfcn = sanitizeVal(id.arfcn)
                    primaryBand = "2G GSM"
                    primaryRsrp = sig.dbm
                } else {
                    neighbors.add(
                        CellularNeighborEntry(
                            radioType = CellRadioType.GSM_2G,
                            pciOrPsc = sanitizeVal(id.bsic),
                            arfcnOrEarfcn = sanitizeVal(id.arfcn),
                            bandLabel = "2G GSM",
                            rsrpOrRscpDbm = sig.dbm,
                            rsrqOrEcNoDb = null,
                            isRegistered = info.isRegistered
                        )
                    )
                }
            }
        }

        CellularTelemetryState(
            operatorName = operatorName,
            mcc = primaryMcc,
            mnc = primaryMnc,
            networkTypeLabel = networkTypeLabel,
            radioType = primaryType,
            voiceServiceState = voiceStateLabel,
            cellIdentity = primaryCellId,
            pci = primaryPci,
            tacOrLac = primaryTac,
            arfcn = primaryArfcn,
            bandLabel = primaryBand,
            rsrpDbm = primaryRsrp,
            rsrqDb = primaryRsrq,
            sinrDb = primarySinr,
            timingAdvance = primaryTa,
            neighbors = neighbors,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFreshCellInfo(tm: TelephonyManager): List<CellInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fresh = withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine<List<CellInfo>> { cont ->
                    try {
                        tm.requestCellInfoUpdate(callbackExecutor, object : TelephonyManager.CellInfoCallback() {
                            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                if (cont.isActive) cont.resume(cellInfo.toList())
                            }

                            override fun onError(errorCode: Int, detail: Throwable?) {
                                if (cont.isActive) cont.resume(runCatching { tm.allCellInfo.orEmpty() }.getOrDefault(emptyList()))
                            }
                        })
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(runCatching { tm.allCellInfo.orEmpty() }.getOrDefault(emptyList()))
                    }
                }
            }
            if (!fresh.isNullOrEmpty()) return fresh
        }
        return runCatching { tm.allCellInfo.orEmpty() }.getOrDefault(emptyList())
    }

    private fun sanitizeVal(v: Int?): Int {
        if (v == null || v == CellInfo.UNAVAILABLE || v == Int.MAX_VALUE) return -1
        return v
    }

    private fun earfcnToLteBandLabel(earfcn: Int): String {
        return when (earfcn) {
            in 0..599 -> "B1 (2100)"
            in 600..1199 -> "B2 (1900)"
            in 1200..1949 -> "B3 (1800)"
            in 1950..2399 -> "B4 (AWS)"
            in 2400..2649 -> "B5 (850)"
            in 2750..3449 -> "B7 (2600)"
            in 3450..3799 -> "B8 (900)"
            in 5010..5179 -> "B12 (700)"
            in 5180..5279 -> "B13 (700)"
            in 6150..6449 -> "B20 (800)"
            in 9210..9659 -> "B28 (700)"
            in 39650..41589 -> "B41 (2500)"
            in 66436..67335 -> "B66 (AWS-3)"
            in 68586..68935 -> "B71 (600)"
            else -> if (earfcn > 0) "EARFCN $earfcn" else "4G LTE"
        }
    }

    private fun mapDataNetworkType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi Calling (IWLAN)"
            else -> "Unknown"
        }
    }
}
