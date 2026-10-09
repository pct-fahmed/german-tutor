#!/usr/bin/env bash
# Builds whisper.cpp and downloads a German-capable model into ~/.german-tutor
set -euo pipefail

WHISPER_VERSION="${WHISPER_VERSION:-v1.7.6}"
MODEL="${MODEL:-small}"
HOME_DIR="$HOME/.german-tutor"
SRC_DIR="$HOME_DIR/src/whisper.cpp"

mkdir -p "$HOME_DIR/bin" "$HOME_DIR/models" "$HOME_DIR/src"

CMAKE="$(command -v cmake || true)"
if [[ -z "$CMAKE" ]]; then
    echo ">> cmake not found, installing it into $HOME_DIR/tools"
    python3 -m venv "$HOME_DIR/tools"
    "$HOME_DIR/tools/bin/pip" install --quiet cmake
    CMAKE="$HOME_DIR/tools/bin/cmake"
fi

if [[ ! -d "$SRC_DIR" ]]; then
    git clone --quiet --depth 1 --branch "$WHISPER_VERSION" https://github.com/ggml-org/whisper.cpp.git "$SRC_DIR"
fi

echo ">> building whisper.cpp $WHISPER_VERSION"
"$CMAKE" -S "$SRC_DIR" -B "$SRC_DIR/build" -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF > /dev/null
"$CMAKE" --build "$SRC_DIR/build" --config Release -j "$(nproc)" --target whisper-cli > /dev/null
cp "$SRC_DIR/build/bin/whisper-cli" "$HOME_DIR/bin/whisper-cli"

MODEL_FILE="$HOME_DIR/models/ggml-$MODEL.bin"
if [[ ! -f "$MODEL_FILE" ]]; then
    echo ">> downloading ggml-$MODEL.bin"
    curl -L --fail --progress-bar -o "$MODEL_FILE.part" \
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$MODEL.bin"
    mv "$MODEL_FILE.part" "$MODEL_FILE"
fi

echo ">> done: $HOME_DIR/bin/whisper-cli + $MODEL_FILE"
