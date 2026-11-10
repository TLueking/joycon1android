# Switch 1 controllers

Switch 1 Joy-Cons and Pro Controllers use Bluetooth Classic HID, not BLE. Android's Bluetooth stack owns
the link, so the app can't talk to them directly. Instead they pair in Android's Bluetooth settings,
the kernel's `hid-nintendo` driver binds them, and the app reads the driver's evdev nodes as the shell
user through Shizuku. Everything downstream (assignment, the virtual gamepad, mapping) is shared with
Switch 2 controllers through `CombinedControllers`.

| Class | Module | Role |
|---|---|---|
| `evdev_relay.c` | `app/src/main/cpp` | Finds, grabs and streams the controllers |
| `ShellEvdevRelayLauncher` | `:app` | Starts the relay through `PrivilegedAccess` |
| `Joycon1Manager` | `:feature:connection:data` | `ControllerRepository` over the relay's output |
| `EvdevControllerState` | `:feature:connection:data` | Events between SYN_REPORTs → `JoyconInput` |
| `CombinedControllers` | `:feature:connection:data` | Switch 1 and Switch 2 sources as one repository |

## Requirements

- A kernel with `hid-nintendo`. Without it Android's generic HID mapping gives an 8-way stick and
  none of this applies. Check with `adb shell getevent -il`: the devices are named
  "Nintendo Switch Left Joy-Con" / "Right Joy-Con" with ±32767 axes.
- Shizuku. The shell user is in the `input` group, so it can open and `EVIOCGRAB` `/dev/input/event*`.

## evdev relay

Runs until stdin closes; closing releases every grab. Watches `/dev/input` with inotify and rescans
every 2 s. Matches vendor `057e`, products `2006` (left), `2007` (right), `2009` (Pro); skips `IMU`
nodes. Grabbing stops Android's `InputReader` seeing the controllers, so games see only the virtual pad.

One line per message on stdout, space-separated:

| Line | Meaning |
|---|---|
| `OK` | Started |
| `A <slot> <product hex> <address or -> <name, spaces as _>` | Controller grabbed; its current state follows |
| `E <slot> <type> <code> <value>` | `EV_KEY` or `EV_ABS` event |
| `S <slot>` | `SYN_REPORT`: the frame is complete |
| `R <slot>` | Controller gone |

`slot` is the `eventN` number. On `SYN_DROPPED` the relay re-sends the full state.

**Address.** `EVIOCGUNIQ`, then sysfs `uniq` / `HID_UNIQ`. On a Pixel 3a (Android 12, kernel 4.9,
measured 2026-11-08) all are empty or SELinux-denied to shell, so `Joycon1Manager` falls back to
`switch1-<product>` (`-2`, `-3` for duplicates), stable across reconnects for type overrides.

## Buttons

Measured on a Pixel 3a's backported `hid-nintendo`, 2026-11-08, on the app's controller card, both Joy-Cons. X and Y
are `BTN_X` / `BTN_Y` (0x133 / 0x134); `getevent` prints those codes by their aliases `BTN_NORTH` /
`BTN_WEST`:

| Button | Code | Button | Code |
|---|---|---|---|
| A | `BTN_EAST` | L / ZL | `BTN_TL` / `BTN_TL2` |
| B | `BTN_SOUTH` | R / ZR | `BTN_TR` / `BTN_TR2` |
| X | `BTN_NORTH` (`BTN_X`) | − / + | `BTN_SELECT` / `BTN_START` |
| Y | `BTN_WEST` (`BTN_Y`) | Home / Capture | `BTN_MODE` / `BTN_Z` |
| Stick clicks | `BTN_THUMBL` / `BTN_THUMBR` | D-pad | `BTN_DPAD_*` (or `ABS_HAT0X/Y`) |

This driver reports no SL/SR, so a lone sideways Joy-Con has no shoulder buttons and the SL + SR
assignment combo doesn't fire. L + R across a pair does.

## Sticks

The driver applies factory calibration and reports ±32767, up negative. `EvdevStick` maps to the app's
0–4095 with up high, through a 12% radial deadzone: one Joy-Con rested at 3302 (10%) after release.

## Turning off

The ⏻ button on a player card, **Disconnect** on an unassigned controller and **Disconnect all**
turn Switch 1 controllers off. A button press wakes one and it reconnects to the phone without
re-pairing (Pixel 3a: Android 12 with the sleep command, 2026-11-08; LineageOS 22 with the Shizuku
disconnect, 2026-11-09).

| Class | Module | Role |
|---|---|---|
| `EvdevDisconnector` | `:feature:connection:data` | What `Joycon1Manager` calls per product |
| `HidHostDisconnector` | `:app` | Disconnects through Shizuku; falls back to the sleep command |

**Disconnect through Shizuku.** The HID host's `disconnect` (hidden, by reflection) is called through
`ShizukuBinderWrapper` with a `com.android.shell` `AttributionSource`. It needs `BLUETOOTH_PRIVILEGED`:
shell has it on Android 15, and the Joy-Cons go to sleep when the host drops the link (Pixel 3a,
LineageOS 22, 2026-11-09). Shell lacks it on Android 12 (`Neither user 2000 nor current process has
android.permission.BLUETOOTH_PRIVILEGED`), so the call is refused there. `app_process` as shell can't
bind the service at all (`Unable to find app for caller`).

**Sleep command fallback, Android 14 and older.** `BluetoothHidHost.sendData` (hidden, reflection) with
the app's own `BLUETOOTH_CONNECT` sends output report `01 00 00014040 00014040 06 00`: packet 0,
neutral rumble, subcommand `0x06` (set HCI state) `0x00` (disconnect). The controller drops the link
itself. Worked on a Pixel 3a (Android 12), both Joy-Cons, 2026-11-08.

Not on Android 15: with the Bluetooth flag `allow_switching_hid_and_hogp` on, `sendData` posts the
`BluetoothDevice` but `handleMessageSendData` casts it to `byte[]`, and the `ClassCastException` restarts
the Bluetooth process. `setReport` doesn't crash, but a Joy-Con ignores subcommands sent as a
control-channel SET_REPORT (LineageOS 22, 2026-11-09).

evdev doesn't expose the Bluetooth address, so the device is matched by its advertised name
(`EvdevProduct.bluetoothName`). With two of the same type connected, a single turn-off refuses and
says so; **Disconnect all** sends to both.

## Auto-sleep

A Switch 1 controller that stays unassigned for 2 minutes gets the sleep command again, so one woken
by a bump in a bag turns itself back off. `UnassignedTimeout` (`:feature:connection:domain`) tracks
how long each address has been unassigned; `AppContainer` checks it every 15 s against the session's
unassigned controllers that `Joycon1Manager` owns. Switch 2 controllers are left alone: they can't
be woken by a button.

**Disconnect All** keeps the relay running, so a controller that wakes afterwards is still grabbed:
it shows up unassigned and moves nothing on screen. Only the app's process ending stops the relay,
and then Android gets the controllers' input again.

## Not available

No motion (this driver version creates no IMU node), rumble (no `FF_RUMBLE`) or battery. Player LEDs
are sysfs LEDs that shell can't write. `setPlayerLed`, `vibrate` and `rumble` are no-ops.
