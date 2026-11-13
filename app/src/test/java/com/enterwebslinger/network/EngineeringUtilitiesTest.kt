// === FILE: app/src/test/java/com/enterwebslinger/network/EngineeringUtilitiesTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.data.repository.EngineeringUtilitiesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngineeringUtilitiesTest {

    private lateinit var repo: EngineeringUtilitiesRepository

    @Before
    fun setup() {
        repo = EngineeringUtilitiesRepository(null)
    }

    @Test
    fun testMacVendorAndRandomizedMacDetection() {
        // Cisco Systems (00:00:0C) -> bit 1 is 0 (Globally Unique)
        val ciscoMac = "00:00:0C:12:34:56"
        assertFalse(repo.isRandomizedMac(ciscoMac))
        assertEquals("Cisco Systems", repo.resolveVendor(ciscoMac))

        // Raspberry Pi (B8:C2:53) -> B8 = 10111000, bit 1 is 0
        val rpiMac = "B8:C2:53:AA:BB:CC"
        assertFalse(repo.isRandomizedMac(rpiMac))
        assertEquals("Raspberry Pi Trading Ltd", repo.resolveVendor(rpiMac))

        // Locally administered randomized MAC (e.g. DA:XX... -> DA = 11011010, bit 1 is 1)
        val randomizedMac = "DA:A1:19:22:33:44"
        assertTrue(repo.isRandomizedMac(randomizedMac))
        assertTrue(repo.resolveVendor(randomizedMac).contains("Randomized"))
    }

    @Test
    fun testIpv4SubnetCalculation() {
        val calc24 = repo.calculateIpv4Subnet("192.168.1.100", 24)
        assertNotNull(calc24)
        calc24!!
        assertEquals("192.168.1.0", calc24.networkAddress)
        assertEquals("192.168.1.255", calc24.broadcastAddress)
        assertEquals("255.255.255.0", calc24.netmask)
        assertEquals("0.0.0.255", calc24.wildcardMask)
        assertEquals("192.168.1.1", calc24.firstUsableIp)
        assertEquals("192.168.1.254", calc24.lastUsableIp)
        assertEquals(254L, calc24.usableHosts)
        assertTrue(calc24.isPrivateRfc1918)
        assertEquals("Class C", calc24.ipClass)

        // Point-to-point /30 subnet
        val calc30 = repo.calculateIpv4Subnet("10.0.0.5", 30)
        assertNotNull(calc30)
        calc30!!
        assertEquals("10.0.0.4", calc30.networkAddress)
        assertEquals("10.0.0.7", calc30.broadcastAddress)
        assertEquals("255.255.255.252", calc30.netmask)
        assertEquals("10.0.0.5", calc30.firstUsableIp)
        assertEquals("10.0.0.6", calc30.lastUsableIp)
        assertEquals(2L, calc30.usableHosts)
    }

    @Test
    fun testVlsmAllocation() {
        val allocations = repo.calculateVlsm(
            parentCidr = "192.168.1.0/24",
            subnetHostRequirements = listOf(
                "Accounting" to 60,
                "Sales" to 25,
                "IT" to 10
            )
        )

        assertEquals(3, allocations.size)
        // 60 hosts requires /26 (capacity 62)
        assertEquals(26, allocations[0].cidrPrefix)
        assertEquals("192.168.1.0", allocations[0].networkAddress)

        // 25 hosts requires /27 (capacity 30)
        assertEquals(27, allocations[1].cidrPrefix)
        assertEquals("192.168.1.64", allocations[1].networkAddress)

        // 10 hosts requires /28 (capacity 14)
        assertEquals(28, allocations[2].cidrPrefix)
        assertEquals("192.168.1.96", allocations[2].networkAddress)
    }

    @Test
    fun testIpv6ScopeDetection() {
        val linkLocal = repo.calculateIpv6("fe80::1ff:fe23:4567")
        assertNotNull(linkLocal)
        assertTrue(linkLocal!!.addressScope.contains("Link-Local"))

        val ula = repo.calculateIpv6("fd12:3456:789a::1")
        assertNotNull(ula)
        assertTrue(ula!!.addressScope.contains("Unique Local"))

        val global = repo.calculateIpv6("2001:db8:85a3::8a2e:370:7334")
        assertNotNull(global)
        assertTrue(global!!.addressScope.contains("Global Unicast"))
    }
}
