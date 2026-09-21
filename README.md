# PRIYA AI — Abhishek Edition V2

## Free personal Android assistant

PRIYA has **no activation system, no subscription, no in-app credits, and no API key requirement for local mode**.

Optional providers can be entered in Settings:
- OpenAI API
- Gemini API
- OpenRouter API
- ElevenLabs API + Voice ID

Third-party APIs can have their own usage limits or pricing. PRIYA itself does not add a paywall.

### Added
- Futuristic red/black animated UI
- PRIYA identity; provider names are not used as the assistant identity
- Boss = Abhishek
- Normal and GF/Romantic modes
- Provider selector and local fallback
- ElevenLabs custom voice support
- Permission center
- Accessibility service declaration for screen understanding/actions
- Voice commands for supported app opening, web search, timer, music, calls, back/home and scrolling
- Vision/OCR foundation
- Smart tools, memory and About sections
- GitHub Actions debug APK build

### Important Android limits
Sensitive permissions require user consent. Accessibility, microphone, camera, contacts and notifications cannot be silently granted by an app. Android also cannot guarantee that a microphone service can never be stopped by the OS/user. Call answering, message automation and arbitrary screen tapping depend on Android version, permissions and the target app.

### GitHub build
Push this project to GitHub and run **Actions → Build PRIYA AI APK**. The workflow builds without API secrets.
