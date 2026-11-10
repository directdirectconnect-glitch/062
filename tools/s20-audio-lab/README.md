# English Words: S20 Audio Probe (pre-release)

This is an **additional local Windows test module**, not a separate Android
app. It tests *actual* Vosk microphone recognition on the S20 FE, only after
a physical speaker and known WAV samples have been installed.

## What has been implemented

- Debug-only Android VoiceProbeActivity in the isolated S20 test APK, accepting
  language `en`/`ru` and Base64-encoded UTF-8 expected words over ADB.
- The fixture uses **the same OfflineVoskEngine** as English Words, not an
  Android cloud speech service.
- `S20AudioProbe.ps1` waits until the recognizer is ready, plays a real local
  WAV via a computer speaker, reads PASS/FAIL from Android UI accessibility,
  and exits nonzero for errors.
- Tests are limited to `com.example.english1000.s20test`; they never erase
  or launch the production app. Only fixed PASS/FAIL codes can be uploaded.

## What has not happened yet

- Windows AutoLab v6 still runs its existing 20 offline UI tests.
- This PowerShell module is committed to GitHub but **is not installed on the
  Windows PC and is not yet part of the scheduled task**. GitHub access alone
  cannot install files or start a task on the user's Windows PC.
- The PC still needs a connected speaker, and we need verified English/Russian
  PCM WAV word samples. A PC microphone is unnecessary.
- Until those are connected and the module is deployed, neither an acoustic
  PASS nor a correctly credited spoken answer has been established.
- This is a one-word-at-a-time probe, not full voice UI automation.

## Requirements for future lab integration

Windows 10/11, PowerShell 5.1, authorized ADB device, isolated S20 test APK,
a working speaker and real PCM WAV files containing a word that matches the
expected vocabulary. Launch in the logged-in interactive Windows session to
avoid audio service/session issues.

Example after setup:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\S20AudioProbe.ps1 -Language en -Expected hello -WavPath .\samples\hello.wav
```

The test must be executed for RU and EN, including known positive/negative
cases. A negative recognition is not a successful recognition test. Main app
installation, online speech engines, microphone emulation and synthetic PASS
reports are prohibited.

## Security

No remote shell, code downloaded and executed from GitHub issues, or forwarding
of raw audio, recognized transcript, device serial or UI XML to public GitHub.
Any future issue #3 report must contain only the test case ID and PASS/FAIL.
