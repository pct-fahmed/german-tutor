#!/usr/bin/env bash
# Downloads the Piper TTS binary and a German voice into ~/.german-tutor
set -euo pipefail

PIPER_RELEASE="${PIPER_RELEASE:-2023.11.14-2}"
VOICE="${VOICE:-de_DE-thorsten-medium}"
HOME_DIR="$HOME/.german-tutor"

mkdir -p "$HOME_DIR/bin" "$HOME_DIR/models"

if [[ ! -x "$HOME_DIR/bin/piper/piper" ]]; then
    echo ">> downloading piper $PIPER_RELEASE"
    curl -L --fail --progress-bar \
        "https://github.com/rhasspy/piper/releases/download/$PIPER_RELEASE/piper_linux_$(uname -m).tar.gz" \
        | tar -xz -C "$HOME_DIR/bin"
fi

IFS=_- read -r LANG_CODE REGION SPEAKER QUALITY <<< "$VOICE"
VOICE_URL="https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/$LANG_CODE/${LANG_CODE}_$REGION/$SPEAKER/$QUALITY/$VOICE"
for ext in onnx onnx.json; do
    if [[ ! -f "$HOME_DIR/models/$VOICE.$ext" ]]; then
        echo ">> downloading $VOICE.$ext"
        curl -L --fail --progress-bar -o "$HOME_DIR/models/$VOICE.$ext.part" "$VOICE_URL.$ext"
        mv "$HOME_DIR/models/$VOICE.$ext.part" "$HOME_DIR/models/$VOICE.$ext"
    fi
done

echo ">> done: $HOME_DIR/bin/piper/piper + $HOME_DIR/models/$VOICE.onnx"
