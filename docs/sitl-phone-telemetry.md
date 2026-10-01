# Rover SITL to Android phone telemetry

This guide connects ArduPilot Rover SITL running under WSL2 to Mama GCS on a physical Android phone. It exercises the current receive-only MAVLink path. It does not enable vehicle commands.

## Network layout

```text
ArduRover SITL → MAVProxy → Windows WSL virtual adapter UDP port 14550 → Windows UDP relay port 14551 → Mama GCS
```

The Windows relay is required because the app's UDP transport accepts packets only from one configured host and source port. WSL2 network translation can change MAVProxy's outbound source port before packets reach the phone. The relay provides a stable Windows Wi-Fi address and source port.

## Prerequisites

- WSL2 with Ubuntu and the ArduPilot build environment installed.
- A physical Android phone with Mama GCS installed.
- The phone and Windows PC connected to the same Wi-Fi network.
- The phone Wi-Fi IP address and the PC Wi-Fi IPv4 address. On Windows, use `ipconfig` and read the `Wireless LAN adapter Wi-Fi` IPv4 address.

## Start Rover SITL

In Ubuntu:

```bash
cd ~/ardupilot/Rover
../Tools/autotest/sim_vehicle.py --console --map
```

Wait for MAVProxy to show a `MAV>` or `MANUAL>` prompt and for the simulator to report a GPS fix. At that prompt, run:

```text
output
```

It should list a WSL host gateway output ending in `:14550`. In the validated setup this was `172.29.144.1:14550`; `sim_vehicle.py` created it automatically. This output sends the stream to the Windows relay. Enter MAVProxy commands in the MAVProxy terminal, not the separate Rover process window.

If no output ends in `:14550`, find the WSL gateway in a second Ubuntu terminal:

```bash
ip route | awk '/default/ {print $3}'
```

Then add the printed gateway address at the MAVProxy prompt:

```text
output add <wsl-gateway-ip>:14550
```

## Start the Windows relay

Open a separate, normal Windows PowerShell window. Replace the example phone IP address and run:

```powershell
$listen = [System.Net.Sockets.UdpClient]::new(14550)
$send = [System.Net.Sockets.UdpClient]::new(14551)
$phone = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Parse("192.168.1.12"), 14550)
$from = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)

while ($true) {
    $data = $listen.Receive([ref]$from)
    [void]$send.Send($data, $data.Length, $phone)
}
```

Leave this PowerShell window running for the entire test. If Windows Firewall asks for access, allow it on the private Wi-Fi network.

## Configure Mama GCS

In Mama GCS, open **More → Settings** and set:

| Field | Example | Meaning |
| --- | --- | --- |
| Remote host | `192.168.1.14` | Windows PC Wi-Fi IPv4 address |
| Remote port | `14551` | Relay's fixed source port |
| Local port | `14550` | Phone listening port |

Save the endpoint, then tap **Open UDP socket**.

Within a few seconds, the received-packet count should increase. The session then selects the first valid autopilot HEARTBEAT and the dashboard can show the MAVLink data that SITL streams: connection/liveness, GPS, global position, attitude, system status, battery readings, and status text.

## Verify and troubleshoot

In another Ubuntu terminal, this command confirms that MAVProxy is sending datagrams to the Windows relay:

```bash
sudo tcpdump -n -i any 'udp and dst port 14550'
```

Expected output includes lines shaped like:

```text
IP 172.29.x.x.<dynamic-port> > 172.29.x.x.14550: UDP, length <n>
```

The `172.29.x.x` address and dynamic source port are WSL-internal values. Do not use them as Mama GCS's remote peer. The Windows relay addresses the source-port instability.

If packet counts remain at zero, confirm the relay is still running, check both devices use the same Wi-Fi network, recheck the two IP addresses, and allow the relay through the private Windows Firewall profile. If packets increase but the dashboard remains disconnected, verify that MAVProxy is forwarding an unsigned MAVLink 2 autopilot HEARTBEAT. Signed MAVLink packets are currently rejected because the app has no signing-key verifier.

Stop the test by closing the UDP socket in Mama GCS, pressing `Ctrl+C` in the relay PowerShell window, and stopping SITL with `Ctrl+C`.

## Successful SITL verification

On 2026-09-30, Rover SITL on WSL2 was connected to Mama GCS on a physical Android phone with this arrangement. The app showed `CONNECTED` and fresh telemetry receive ages. It decoded an `RTK FIXED` GPS sample with 10 satellites and HDOP 1.2; latitude `-35.3632621`; longitude `149.1652374`; filtered global position near the same coordinates at 584.1 m MSL; battery pack 0 at 12.6 V and 100%; and attitude near roll -0.1°, pitch -0.1°, yaw -7.8°.

The Map screen shows the SITL coordinate, heading, and bounded track over the configured online MapTiler vector basemap. Center and Follow are display-only camera controls. Offline regions remain unavailable, and the Control page sends no commands.
