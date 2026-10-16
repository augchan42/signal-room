#!/bin/sh
# Export the live stage and all 12 mixes for the app, one Blender process at a time.
set -eu
BLENDER=${BLENDER:-/Applications/Blender.app/Contents/MacOS/Blender}
cd "$(dirname "$0")/../.."
run() {
    "$BLENDER" --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 \
        --python art/blender/export_live_stage.py -- "$@"
}
run stage
for arrangement in sparse driving layered; do
    for member in frontman keyboardist keytar drummer; do
        run mix "$arrangement-$member"
    done
done
