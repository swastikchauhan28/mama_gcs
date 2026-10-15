package com.mamadrones.gcs.data.transport.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BleReceiveSelectionTest {
    private val characteristic = BleNotifyCharacteristic("service", "characteristic", false, 2, 3)
    private val selection = BleReceiveSelection(characteristic, 4)

    @Test fun exactCurrentSelectionIsAccepted() {
        assertTrue(selection.isCurrent(4, true, false, listOf(characteristic)))
    }

    @Test fun reconnectRejectsOldSelectionEvenForSameCharacteristic() {
        assertFalse(selection.isCurrent(5, true, false, listOf(characteristic)))
        assertFalse(selection.isCurrent(3, true, false, listOf(characteristic)))
    }

    @Test fun missingOrInvalidConnectionNumberIsRejected() {
        listOf(0L, -1L).forEach { id ->
            assertFalse(BleReceiveSelection(characteristic, id).isCurrent(id, true, false, listOf(characteristic)))
        }
    }

    @Test fun closedAndBusyConnectionsCannotStartReceive() {
        assertFalse(selection.isCurrent(4, false, false, listOf(characteristic)))
        assertFalse(selection.isCurrent(4, true, true, listOf(characteristic)))
    }

    @Test fun removedOrDifferentCharacteristicIsRejected() {
        assertFalse(selection.isCurrent(4, true, false, emptyList()))
        listOf(characteristic.copy(serviceUuid = "other"), characteristic.copy(characteristicUuid = "other"),
            characteristic.copy(serviceInstanceId = 4), characteristic.copy(characteristicInstanceId = 4),
            characteristic.copy(supportsIndication = true)).forEach {
            assertFalse(selection.isCurrent(4, true, false, listOf(it)))
        }
    }

    @Test fun identicalUuidsOnDifferentInstancesCannotBeSubstituted() {
        val second = characteristic.copy(serviceInstanceId = 8, characteristicInstanceId = 9)
        assertTrue(BleReceiveSelection(second, 4).isCurrent(4, true, false, listOf(characteristic, second)))
        assertFalse(BleReceiveSelection(second, 4).isCurrent(4, true, false, listOf(characteristic)))
    }
}
