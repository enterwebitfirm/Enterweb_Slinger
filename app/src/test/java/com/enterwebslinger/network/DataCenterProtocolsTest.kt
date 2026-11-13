// === FILE: app/src/test/java/com/enterwebslinger/network/DataCenterProtocolsTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.data.repository.OobHardwareRepository
import com.enterwebslinger.network.domain.model.CallQualityMetric
import com.enterwebslinger.network.domain.model.PowerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCenterProtocolsTest {

    @Test
    fun testIpmiGetChannelAuthCapsPacketEncoding() {
        val repo = OobHardwareRepository()
        val packet = repo.buildGetChannelAuthCapsPacket()

        assertTrue("Packet should have bytes", packet.isNotEmpty())
        assertEquals("RMCP Version must be 0x06", 0x06, packet[0].toInt() and 0xFF)
        assertEquals("RMCP Class must be IPMI (0x07)", 0x07, packet[3].toInt() and 0xFF)
        assertEquals("Auth Type must be None (0x00)", 0x00, packet[4].toInt() and 0xFF)
    }

    @Test
    fun testIpmiChassisStatusParsing() {
        val repo = OobHardwareRepository()

        // 0x01: Bit 0 = 1 (Power ON), all other fault bits 0
        val statusOn = repo.parseIpmiChassisStatus(0x01.toByte())
        assertTrue("Power should be ON", statusOn.isPowerOn)
        assertFalse("No overload", statusOn.powerOverload)
        assertFalse("No cooling fault", statusOn.coolingFault)

        // 0x29: Bit 0 = 1 (Power ON), Bit 3 = 1 (Fault), Bit 5 = 1 (Cooling Fault)
        val statusFault = repo.parseIpmiChassisStatus(0x29.toByte())
        assertTrue("Power should be ON", statusFault.isPowerOn)
        assertTrue("Power fault flag", statusFault.powerFault)
        assertTrue("Cooling fault flag", statusFault.coolingFault)

        // 0x00: Bit 0 = 0 (Power OFF)
        val statusOff = repo.parseIpmiChassisStatus(0x00.toByte())
        assertFalse("Power should be OFF", statusOff.isPowerOn)
    }

    @Test
    fun testRedfishPowerActionNames() {
        assertEquals("On", PowerAction.ON.redfishName)
        assertEquals("ForceOff", PowerAction.FORCE_OFF.redfishName)
        assertEquals("GracefulShutdown", PowerAction.GRACEFUL_SHUTDOWN.redfishName)
        assertEquals("ForceRestart", PowerAction.FORCE_RESTART.redfishName)
        assertEquals("PowerCycle", PowerAction.POWER_CYCLE.redfishName)
    }

    @Test
    fun testCallQualityMosEvaluation() {
        val goodCall = CallQualityMetric(
            callId = "CALL-101",
            caller = "sip:ext1001",
            callee = "sip:ext1002",
            mosScore = 4.35,
            jitterMs = 4.2,
            latencyMs = 22.0,
            packetLossPercent = 0.0
        )
        assertTrue("Should be rated good quality", goodCall.isGoodQuality)

        val degradedCall = CallQualityMetric(
            callId = "CALL-102",
            caller = "sip:ext1003",
            callee = "sip:ext1004",
            mosScore = 3.12,
            jitterMs = 55.0,
            latencyMs = 180.0,
            packetLossPercent = 3.5
        )
        assertFalse("Should be rated degraded quality", degradedCall.isGoodQuality)
    }

    @Test
    fun testIpmiChannelAuthCapsResponseParsing() {
        val repo = OobHardwareRepository()

        // Valid 28-byte RMCP+IPMI GetChannelAuthCaps response
        val validResp = ByteArray(28)
        validResp[0] = 0x06 // RMCP version
        validResp[3] = 0x07 // RMCP class IPMI
        validResp[20] = 0x00 // Completion Code OK
        validResp[21] = 0x01 // Channel 1
        validResp[22] = (0x04 or 0x10 or 0x80).toByte() // MD5 + Password + RMCP+
        validResp[23] = (0x20 or 0x04).toByte() // KgKey=true, NonNullUsers=true
        validResp[25] = 0xA2.toByte() // Enterprise IANA = 674 (0x0002A2 Dell LE)
        validResp[26] = 0x02

        val parsed = repo.parseChannelAuthCapsResponse(validResp, validResp.size)
        assertTrue(parsed.isSuccess)
        val summary = parsed.getOrThrow()
        assertTrue(summary.contains("Channel 1"))
        assertTrue(summary.contains("MD5"))
        assertTrue(summary.contains("RMCP+ (v2.0)"))
        assertTrue(summary.contains("KgKey=true"))
        assertTrue(summary.contains("OEM IANA=674"))

        // Truncated packet must return Result.failure
        val truncated = ByteArray(10)
        val failed = repo.parseChannelAuthCapsResponse(truncated, truncated.size)
        assertTrue(failed.isFailure)
    }
}
