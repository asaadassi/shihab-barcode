# PC link: phone to the Windows receiver

Barcode (Android) sends each scan to **Barcode Receiver** (Windows, `windows/`), which types it into the active
window, followed by Enter, as a keyboard would. Three ways, chosen on the phone (Settings, Connection mode):

| Phone mode | Transport | Needs |
|---|---|---|
| HID keyboard (default) | Bluetooth HID | nothing on the PC: the phone *is* a keyboard |
| **PC (Wi-Fi or cable)** | TCP + TLS, this document | the receiver, paired once by scanning its QR code |
| RFCOMM (SPP) | Bluetooth serial | the receiver, phone paired in Windows Bluetooth settings |

## Ports

TCP **5870** (busy: 5871 to 5879; the QR code carries the real one). Never 5757/5758/8080 (Shihab Cam) or
5860 to 5869 (Shihab Remote).

## Pairing

The receiver shows a QR code; the phone reads it with its own scanner (any mode: reading it switches the phone to
PC mode). Content:

```
shihabbarcode://pair?v=1&n=<PC name>&h=<ip1>,<ip2>&p=<port>&fp=<sha256>&k=<key>
```

- `fp`: lowercase hex SHA-256 of the receiver's certificate (DER). The phone pins it: any other certificate is
  refused, so nobody on the network can pose as the PC.
- `k`: the pairing key, 16 random bytes, base64url. The phone sends it after TLS; a wrong key is refused. "Unpair
  all phones" on the PC makes a new key (and QR code).
- `h`: the PC's IPv4 addresses on its active networks (Wi-Fi, Ethernet, USB tethering).
- The certificate is a self-signed ECDSA P-256 one made on first run, kept with DPAPI (current user) with the key.

## Connecting

The phone tries, in order: `127.0.0.1:<port>` (the **cable**: the receiver runs `adb reverse tcp:<port>
tcp:<port>` for every phone with USB debugging allowed), then each `h` address (**Wi-Fi**). TLS 1.2 or 1.3,
certificate pinned to `fp`. It reconnects on its own (1, 2, 4 ... 15 s), trying the cable first each time.

## Messages

UTF-8 lines ending in `\n`, both directions. Free text is base64url (no padding) so it never contains a newline.

| From | Line | Meaning |
|---|---|---|
| phone | `HELLO 1 <key> <phone id> <phone name b64>` | first line; `phone id` is random per install (hex, at most 64 characters); the receiver answers `OK <PC name b64>`, or `ERR AUTH` and closes |
| phone | `SCAN <id> <text b64>` | a scan, already with its suffix (Enter is `\n`, Tab `\t`); `id` is a number |
| PC | `ACK <id>` | typed; the phone counts it as delivered |
| PC | `ERR <id> <code>` | not typed (`BUSY`: another app holds the input; `TOO_LONG`: over 4096 characters) |
| phone | `PING` | every 15 s; the receiver answers `PONG`, and closes a link silent for 45 s |

A scan that gets no `ACK` within 8 s is sent again after reconnecting (same `id`: the receiver ignores an `id` it
has already typed from that phone).

## Bluetooth (RFCOMM)

The phone is the SPP server (UUID `00001101-0000-1000-8000-00805F9B34FB`), the receiver connects to a phone the
user picked among the paired ones. The phone writes each scan as UTF-8 text ending with its suffix (`\n` for Enter);
the receiver types it as received. No key: Bluetooth pairing in Windows is the trust.

## Typing

One queue for all links, in arrival order. Each character becomes the key the active keyboard layout uses for it
(`VkKeyScanEx`, Shift when needed), or a Unicode key event when the layout has no key for it (Arabic text on an
English layout). `\n` is Enter, `\t` is Tab. 8 ms between keys by default: well under the 100 ms the Awqaf sabr
site allows between the digits of a scan.
