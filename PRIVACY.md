# MG4Link privacy

Updated: 18 November 2026.

MG4Link has no account, no advertising and no analytics. This page lists every place data goes,
so you can decide what to turn on.

## What leaves the car, and when

| Where to | What | When |
| --- | --- | --- |
| Your phone (Android Auto, Google Maps) | Touch, microphone, steering-wheel keys, the car's GPS position, and the battery figures Maps uses to plan charging stops (charge, range, consumption, pack capacity) | While Android Auto is connected |
| GitHub (`api.github.com`, GitHub's release file hosts) | An ordinary HTTPS request for this repository's release list; an APK download when you accept an update | Update checks (at start and from *Settings → About*) |
| ABRP (`api.iternio.com`) | Charge, power, speed, **position** (latitude, longitude, elevation, heading), charging/parked state, temperatures, range, odometer, state of health, capacity, with your ABRP token | **Off by default.** Only after you turn ABRP on and enter your token |
| The maintainer's Telegram chat, through Telegram's bot API | The crash report shown in the crash window: the error and the app's recent log lines | **Only when you tap Send** in that window |

Nothing else goes out. Maps, music and calls are handled by the phone's apps under their own
policies.

## What stays on the car

- Settings, paired phones and trip data, in the app's private storage.
- The app log. *Settings → About → Logs → Export* copies it to the car's `Download` folder, where
  other apps with storage access can read it.
- An exported log, or a crash report, can include device and network details (Bluetooth and
  Wi-Fi state, addresses) and, on the car build, lines from the car's own Android Auto and call
  apps, in which numbers and call details are masked. Read a report before you send or share it.

## Permissions

Bluetooth, Wi-Fi and USB run the connection to the phone. Location serves wireless discovery and
the GPS sent to the phone. The microphone is for voice commands and calls. Overlay, notification
and background permissions keep the projection and its service running. The car build runs as the
head unit's system user, to read the car's battery data, take the Android Auto role from the car's
own app and install its updates.

The download site is static and uses no analytics scripts. GitHub and Telegram handle visits and
downloads under their own policies.

Questions: https://github.com/Skittle6938/MG4Link/issues
