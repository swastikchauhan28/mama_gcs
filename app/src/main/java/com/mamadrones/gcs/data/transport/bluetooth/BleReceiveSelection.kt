package com.mamadrones.gcs.data.transport.bluetooth

/** An explicit selection for one successful GATT connection in this process; never persisted. */
data class BleReceiveSelection(
    val characteristic: BleNotifyCharacteristic,
    val connectionNumber: Long,
) {
    fun isCurrent(currentConnectionNumber: Long, connected: Boolean, receiveBusy: Boolean,
        characteristics: List<BleNotifyCharacteristic>,
    ): Boolean = connectionNumber > 0 && connectionNumber == currentConnectionNumber &&
        connected && !receiveBusy && characteristic in characteristics
}
