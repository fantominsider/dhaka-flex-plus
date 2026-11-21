# First-device release qualification checklist

**Status: not yet completed.** Tests must be executed on supported devices and authorized private-network servers. Tests are not claims of current compatibility.

- [ ] Android phone, portrait and landscape, touch navigation.
- [ ] Android TV emulator or physical TV, D-pad focus, library browse, embedded LibVLC playback.
- [ ] PICO 4 Ultra, standard 2D and browser-based WebXR immersive mode.
- [ ] Meta Quest, standard 2D and WebXR immersive mode.
- [ ] Android host connected to XREAL One, normal 2D and user-configured SBS 3D output.
- [ ] Authorized HTTP media directory: add, browse, play, edit host/IP, preserve content-root path.
- [ ] Factory IP entries: Restore Default IP (no implicit save), Save IP, Make Default.
- [ ] Confirm media playback/seek for MP4, MKV, HLS, IPTV M3U and RTSP where supported.
- [ ] Embedded audio/subtitle selection, resume, sleep timer, background/foreground behavior.
- [ ] 2D, SBS and VR180/VR360 playback: monitor device heat, dropped frames and decoding limits.
- [ ] Network denial, malformed URL, unreachable server, URL redirection, network timeout safety.
- [ ] Import/export backup; no credentials, server exports or private M3U included in GitHub.
- [ ] Verify app icon, launcher, Android accessibility labels and no TV-only orientation.
- [ ] Confirm Debug APK installed only as an untrusted TEST release. Signing approach defined for production.
