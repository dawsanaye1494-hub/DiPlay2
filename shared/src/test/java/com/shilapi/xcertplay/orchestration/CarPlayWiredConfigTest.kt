package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CarPlayWiredConfigTest {

    @Test
    fun `wired transport succeeds even if wireless hotspot mode is manual without ssid`() {
        val identification = Iap2IdentificationConfig(
            name = "DiPlay",
            modelIdentifier = "AndroidAuto",
            manufacturer = "Generic",
            serialNumber = "DIPLAY-001122334455",
            firmwareVersion = "0.1.0",
            hardwareVersion = "1.0",
            carPlayUsbInterfaceNumber = 3,
        )

        val config = CarPlayRuntimeConfig(
            mfiTarget = MfiTarget.LOCAL,
            identification = identification,
            transport = CarPlayTransport.WIRED,
            wirelessHotspotMode = WirelessHotspotMode.MANUAL,
            manualHotspotSsid = null,
            manualHotspotPassphrase = null,
        )

        assertNotNull(config)
        assertEquals(CarPlayTransport.WIRED, config.transport)
    }
}
