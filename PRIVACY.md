# MG4Link privacy

Updated 20 November 2026.

MG4Link has no account, no advertising and no analytics. This page lists every place data goes,
so you can decide what to turn on.

## What leaves the car, and when

| Where to | What | When |
| --- | --- | --- |
| Your phone (Android Auto, Google Maps) | Touch, microphone, steering-wheel keys, the car's GPS position, and the battery figures Maps uses to plan charging stops (charge, range, consumption, pack capacity) | While Android Auto is connected |
| GitHub (`api.github.com`, GitHub's release file hosts) | An ordinary HTTPS request for this repository's release list; an APK download when you accept an update | Update checks (at start and from *Settings → About*) |
| ABRP (`api.iternio.com`) | Charge, power, speed, **position** (latitude, longitude, elevation, heading), charging/parked state, temperatures, range, odometer, state of health, capacity, with your ABRP token | **Off by default.** Only after you turn ABRP on and enter your token |

Nothing else goes out. Maps, music and calls are handled by the phone's apps under their own
policies.

## What stays on the car

- Settings, pairing information and trip data stay on the car.
- The app logs. *Settings → About → Logs → Export* copies recent logs to a mounted USB stick
  when possible. The fallback is a copy of the current log in the car's `Download` folder.
  Copies can include device and network details. Review the files before sharing them.
- Crash reports are stored locally. The crash dialog lets you read a preview and clear the report.

## Permissions

Bluetooth, Wi-Fi and USB run the connection to the phone. Location serves wireless discovery and
the GPS sent to the phone. The microphone is for voice commands and calls. Overlay, notification
and background permissions keep the projection and its service running. The car build runs as the
head unit's system user, to read the car's battery data, take the Android Auto role from the car's
own app and install its updates.

The download site is static and uses no analytics scripts. GitHub handles downloads under its
own policies.

Questions: https://github.com/Skittle6938/MG4Link/issues
