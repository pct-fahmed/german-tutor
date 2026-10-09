# Deutsch Tutor

A desktop app for practising spoken German. You talk, a local Whisper model writes down exactly what you said (mistakes included), Claude answers as a tutor and corrects you, and a local Piper voice reads the answer aloud.

Speech recognition and speech output run offline on your machine; only the text goes to the Claude API.

## Requirements

- Linux x86_64, Java 21+, Maven
- `git`, `curl`, `g++`, `python3` (for the one-time Whisper build)
- A microphone and speakers
- A Claude API key

## Setup

```bash
./scripts/setup-whisper.sh   # builds whisper.cpp, downloads the "small" model (~470 MB)
./scripts/setup-piper.sh     # downloads Piper and the German "thorsten" voice (~60 MB)
export ANTHROPIC_API_KEY=sk-ant-...
mvn javafx:run
```

`MODEL=medium ./scripts/setup-whisper.sh` downloads a larger model (~1.5 GB) that understands accented German better but is slower. Set `whisper.model` in the config to use it.

## Using it

- **🎤 Sprechen** starts recording, **⏹ Stopp** ends it. Your sentence appears exactly as Whisper heard it.
- Click a tutor message to show the English translation, **🔊** to hear it again.
- **Korrekturen** on the right lists your mistakes with a short explanation.
- **Situation** switches to a role-play (Bäcker, Arzt, Restaurant, …), where the tutor speaks first.
- **📒 Meine Fehler** shows the mistakes you make most often; **Mit dem Tutor üben** starts a conversation that practises them.

## Configuration

Optional, in `~/.german-tutor/config.properties`:

```properties
# claude-haiku-5-5 is faster and cheaper
model=claude-opus-5
# A1 .. C1
level=A2
# higher = slower speech
piper.lengthScale=1.15
whisper.model=/home/me/.german-tutor/models/ggml-medium.bin
```

## Your data

Everything stays in `~/.german-tutor/`:

| Path | Content |
|---|---|
| `mistakes.jsonl` | every correction, used by "Meine Fehler" |
| `sessions/*.md` | a transcript of each conversation |
| `bin/`, `models/` | Whisper and Piper |
