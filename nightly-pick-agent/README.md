# nightly-pick-agent

Python agent service for `夜拾`.

## Run

1. Create a virtualenv
2. Install dependencies from `pyproject.toml`
3. Copy `.env.example` to `.env`
4. Start the service:

```bash
uvicorn app.main:app --reload --port 8000
```

Or use:

```bash
./scripts/run-dev.sh
```

Health check:

```bash
curl http://localhost:8000/health
```

## Provider Modes

- `TEXT_PROVIDER=mock|minimax`
- `SPEECH_TRANSCRIBE_PROVIDER=mock|minimax|tencent`
- `SPEECH_SYNTHESIZE_PROVIDER=mock|minimax`

Recommended MVP setup:

```env
TEXT_PROVIDER=mock
SPEECH_TRANSCRIBE_PROVIDER=mock
SPEECH_SYNTHESIZE_PROVIDER=mock
```

When you are ready to switch to MiniMax:

```env
TEXT_PROVIDER=minimax
SPEECH_TRANSCRIBE_PROVIDER=tencent
SPEECH_SYNTHESIZE_PROVIDER=minimax
MINIMAX_API_KEY=your_key
TEXT_MODEL=MiniMax-M2.5
MINIMAX_TTS_MODEL=speech-2.8-turbo
MINIMAX_TTS_VOICE_ID=Chinese (Mandarin)_Warm_Bestie
```

You can start from `.env.minimax.example`.

For a MiniMax-backed local run:

```bash
cp .env.minimax.example .env
# fill in your key
./scripts/run-minimax.sh
```

## Notes

- Text generation is wired for MiniMax text chat.
- TTS is wired for MiniMax T2A.
- ASR currently supports a safe fallback mock path unless you provide a concrete ASR endpoint in `MINIMAX_ASR_ENDPOINT`.
- The app validates selected providers on startup, so bad MiniMax config will fail fast instead of surfacing only on the first request.
- For system TTS voices, use a valid official `voice_id`. A safe Chinese default is `Chinese (Mandarin)_Warm_Bestie`.
