# Seamless DJ – Intelligent AutoMix for Local Music Libraries
 
A mobile DJ app that automatically reorders and seamlessly mixes your local music library with beat-matched transitions, harmonic compatibility analysis, and intelligent energy flow. Turn any playlist into a continuous, non-jarring mix that sounds like a real DJ is behind the decks.
 
**Status:** Active Development | Android Native (Kotlin)
 
---
 
## Features
 
### Core Automix Engine
- **Intelligent Playlist Reordering:** Uses a greedy algorithm with weighted compatibility scoring to find the smoothest possible song order based on harmonic key, tempo, and energy.
- **Real-Time DSP Analysis:** On-device audio feature extraction using TarsosDSP:
  - **BPM Detection:** Spectral flux + beat detection (40–220 BPM range)
  - **Musical Key Detection:** Chromagram-based pitch estimation, mapped to Camelot Wheel notation
  - **Energy Analysis:** RMS loudness + spectral flux for vibe continuity
  - **LUFS Loudness Normalization:** ITU-R BS.1770-4 standard for consistent perceived volume across transitions
### Smooth Transitions
- **Multiple Transition Types** (selected based on track compatibility):
  - `CROSSFADE`: Simple volume blend (perfect key/BPM match)
  - `EQ_FADE` (Bass Swap): Dynamic low-shelf EQ fade to prevent muddy overlaps
  - `FILTER_SWEEP`: Resonant high-pass filter sweep for less compatible tracks
  - `ECHO_OUT`: Reverb tail on outgoing track to mask clashes
- **Beat-Locked Alignment:** Transitions snap to beat grid boundaries, not arbitrary sample positions
- **Tempo Sync:** Optional Phase Vocoder time-stretching for seamless BPM alignment (experimental, can be disabled)
### Playlist Management
- **Local Music Library:** Load MP3s from your phone's storage or upload ZIP archives
- **Smart Starting Point:** Choose a song to begin with; the automix builds the rest of the set from there
- **Manual Queue Reordering:** Skip, shuffle, or manually swap tracks before playback
- **Compatibility Visualization:** See compatibility scores and predicted transition types for upcoming songs
### User Experience
- **Dark & Light Modes:** Minimal, spacious UI inspired by Google Home design
- **Party-Friendly Controls:** Large touch targets (48dp+), legible in low light
- **Real-Time Metadata:** BPM, key, energy, and compatibility indicators on every screen
- **Offline-First:** All analysis happens on-device; no internet required for playback
### Optional Features
- **Gemini API Integration:** Low-confidence BPM/key lookups can be validated via Gemini for edge cases (disabled by default)
- **Debug Mode:** View detailed compatibility scores, transition selections, and performance logs
---
 
## How It Works
 
### 1. Audio Analysis Pipeline
When you load a playlist, the app analyzes each track:
1. Extracts the first 30–60 seconds of audio
2. Applies STFT to compute BPM via spectral flux peaks
3. Builds a chromagram to detect musical key
4. Calculates RMS + spectral flux for energy score
5. Computes LUFS for loudness normalization
6. **Caches all results** so subsequent loads are instant
Confidence scoring determines if Gemini validation is needed; most tracks are purely on-device.
 
### 2. Playlist Reordering (Greedy Optimization)
Starting from your chosen song, the app:
1. Scores every remaining song against the current last song in the queue
2. Selects the song with the highest compatibility score
3. Repeats until all songs are queued
**Compatibility Score:**
```
score = 0.5 * camelot_compatibility
      + 0.3 * bpm_similarity
      + 0.2 * energy_continuity
```
 
### 3. Smart Transitions
As playback approaches the transition point (~20 seconds before the next song):
1. Calculates compatibility between current and next track
2. Selects a transition type:
   - `compatibilityScore >= 0.85` → `CROSSFADE`
   - `compatibilityScore >= 0.70` → `EQ_FADE`
   - `compatibilityScore >= 0.50` → `FILTER_SWEEP`
   - `compatibilityScore < 0.50` → `ECHO_OUT`
3. Applies the transition (dynamic EQ, filter sweep, or reverb tail)
4. Normalizes loudness via LUFS compensation
5. Moves to the next track seamlessly
### 4. Beat-Grid Alignment
Transitions snap to downbeats (bar boundaries), not random sample positions. This ensures the "one" beat of the next song lands exactly when expected, sounding natural to the listener.
 
---
 
## Installation & Setup
 
### Requirements
- **Android 8.0+ (API 26+)** (target API 34+)
- **Storage Permission:** Read access to local music files
- **Optional:** Gemini API key (for low-confidence metadata validation)
### Build from Source
 
```bash
# Clone the repository
git clone https://github.com/solo6760/Seamless-DJ.git
cd Seamless-DJ
 
# Build the release APK
./gradlew assembleRelease
 
# Or, build an Android App Bundle (for Play Store)
./gradlew bundleRelease
```
 
### First-Time Setup
 
1. **Launch the app**
2. **Grant storage permissions** when prompted
3. (Optional) **Add Gemini API key** on the Settings screen if you want AI validation for edge cases
4. **Load a playlist:** Tap "Open Playlist" → choose a ZIP file or select songs from your library
5. **Pick a starting track** and tap "Reorder for Smooth Transitions"
6. **Tap "Start Mix"** and enjoy
---
 
## Usage
 
### Loading a Playlist
- **From ZIP:** Tap "Upload Playlist" and select a `.zip` file containing MP3s
- **From Device:** Tap "Local Library" to browse and select songs manually
### Playback Controls
- **Play/Pause:** Large center button
- **Skip:** Forward/backward buttons (triggered transitions use the same smart mixing)
- **Queue View:** Swipe up to see upcoming songs, tap to jump to any track
- **Compatibility Info:** Each upcoming song shows a small badge indicating transition type and smoothness
### Settings
- **Dark Mode Toggle:** Top of Settings screen
- **Gemini API Key:** Paste your key (encrypted storage)
- **Transition Settings:** Adjust fade duration, EQ depth, filter sweep speed
- **Clear Cache:** Resets all cached BPM/key/energy data (useful if analysis was wrong)
- **Debug Mode:** View detailed logs and performance metrics
### Tips for Best Results
- **Start with a high-energy song** if you want an upbeat set; low-energy songs create a mellow vibe
- **Mixed genres work best** — folk → electronic → hip-hop creates interesting juxtapositions with smart transitions
- **Reorder multiple times** to experiment — the algorithm is deterministic, so different starting points yield different mixes
- **Listen to the transitions** — they're the star of the show; pay attention to bass swaps and filter sweeps
---
 
## Architecture
 
### Tech Stack
- **Language:** Kotlin
- **Audio Playback:** ExoPlayer (dual-deck crossfading)
- **Audio Analysis:** TarsosDSP (beat detection, pitch estimation)
- **Data Storage:** Room (metadata caching)
- **Encryption:** EncryptedSharedPreferences (API key storage)
- **Optional DSP:** Phase Vocoder (time-stretching, experimental)
- **Optional AI:** Gemini 3.1 Flash-Lite API (low-confidence fallback)
### Key Components
- **`AudioAnalysisService`:** On-device DSP (BPM, key, energy, LUFS extraction)
- **`CompatibilityScorer`:** Weighted scoring function (Camelot + BPM + energy)
- **`PlaylistOptimizer`:** Greedy playlist reordering
- **`AutomixScheduler`:** Real-time transition engine (crossfade, EQ fade, filter sweep, echo)
- **`MetadataCache`:** Room database for persistent results
- **`GeminiValidator`:** Optional low-confidence fallback (disabled by default)
### Data Flow
```
[Load Playlist]
       ↓
[Analyze Each Track: BPM, Key, Energy, LUFS]
       ↓
[Cache Results]
       ↓
[Reorder Playlist: Greedy Compatibility Scoring]
       ↓
[Display Queue with Compatibility Scores]
       ↓
[Start Playback]
       ↓
[Scheduler: ~20s Before Transition]
       ├→ [Calculate Compatibility]
       ├→ [Select Transition Type]
       ├→ [Apply DSP: EQ Fade / Filter Sweep / Echo]
       ├→ [Normalize LUFS]
       └→ [Crossfade to Next Track]
```
 
---
 
## Performance
 
### On-Device Analysis
- **BPM/Key/Energy extraction:** ~2–5 seconds per 3-minute track (first 30–60 seconds analyzed)
- **Cached after first load:** Subsequent app launches are instant
- **Memory footprint:** ~50MB base app + ~100–200MB cached audio buffers during playback
### Playback & Transitions
- **Real-time DSP:** EQ fade, filter sweep, echo all run at low CPU overhead (<10% per transition)
- **Phase Vocoder (if enabled):** ~50–100ms per buffer (can be disabled if sluggish)
- **Typical latency:** <20ms from transition trigger to audible change
### Device Compatibility
- **Tested:** Pixel 6a (Android 13), OnePlus 8 (Android 12), Samsung Galaxy A52 (Android 11)
- **Minimum:** Snapdragon 600-series or equivalent (released ~2012+)
- **Recommended:** Snapdragon 700-series or newer for Phase Vocoder (experimental)
---
 
## Known Limitations & Roadmap
 
### Current Limitations
1. **No Stem Separation:** Can't separate vocals/drums/bass for independent mixing (too heavy for mobile)
2. **No File Export:** Outputs to playback only, not file export (by design; focus is real-time DJing)
3. **Local Audio Only:** No Spotify/YouTube streaming integration (privacy-first, but limits catalog)
4. **Single Deck Queuing:** Plays songs sequentially; true multi-deck setup not supported
5. **Phase Vocoder is Experimental:** Time-stretching can sound robotic at extreme ratios; disabled by default
### Future Enhancements (Tier 2+)
- [ ] Markov chain playlist optimization (global vs. greedy)
- [ ] Pitch shifting beyond Camelot (±6 semitone harmonic forcing)
- [ ] True-peak limiter on output
- [ ] Guest song request voting (multi-user feature)
- [ ] Bluetooth speaker sync (multiple devices)
- [ ] Audio visualization (waveforms, energy peaks)
- [ ] Spotify/Apple Music metadata (browsing only, local playback)
---
 
## Troubleshooting
 
### App Won't Play Audio
- Ensure storage permissions are granted
- Check that your MP3s aren't corrupted (try playing in another app)
- If Phase Vocoder is causing issues: **Settings → Disable "Use Advanced Time-Stretching"**
### BPM/Key Detection is Wrong
- Some tracks (ambient, acapella, experimental) are genuinely hard to analyze
- If confidence is low (<50%), the app flags it; you can:
  - Use Gemini validation (Settings → Add Gemini API key)
  - Manually edit the metadata (coming soon)
### Playback is Stuttering During Transitions
- This is usually Phase Vocoder overhead on lower-end phones
- **Fix:** Settings → Disable "Use Advanced Time-Stretching"
- Or reduce your library size (load <50 songs at a time)
### High Battery Drain
- On-device DSP analysis is the culprit (one-time cost per file)
- Playback itself has minimal overhead
- **Fix:** Analyze playlists while charging; results are cached forever
### Gemini API Errors
- Check your API key is valid (go to Google AI Studio)
- Ensure internet connectivity
- If disabled, the app still works offline with on-device analysis only
---
 
## Privacy & Security
 
- **All audio analysis happens on-device.** No audio data leaves your phone.
- **Gemini API key** (if used) is encrypted using Android's `EncryptedSharedPreferences`.
- **Playlist metadata** (BPM, key, energy) is cached locally in a Room database; never uploaded.
- **No telemetry or analytics.** This app doesn't collect usage data.
- **Open source:** Review the code yourself at [solo6760/Seamless-DJ](https://github.com/solo6760/Seamless-DJ)
---
 
## Contributing
 
Contributions are welcome! If you find a bug or have a feature request:
 
1. **Open an issue** on GitHub describing the problem or idea
2. **Fork the repo** and create a feature branch
3. **Submit a pull request** with clear commit messages
4. **Test on a real device** before submitting
For major changes, please open an issue first to discuss.
 
---
 
## License
 
This project is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) for details.
 
---
 
## Acknowledgments
 
- **TarsosDSP:** Beat detection and pitch estimation
- **ExoPlayer:** Robust audio playback and effects
- **Google AI Studio:** Gemini API for metadata validation
- **Camelot Wheel:** Harmonic mixing foundation
- Inspired by Apple Music Automix, Spotify DJ, and professional DJ mixing techniques
