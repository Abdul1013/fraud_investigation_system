#!/bin/sh
# ============================================================================
# FILE: infra/container/ollama-entrypoint.sh
# PURPOSE: Keep model weights/catalogue immutable while allocating private ephemeral metadata.
# OWNER: Platform & Operations
# SINCE: week-34
# RELATED: Dockerfile.ollama
# NOTES: No model download or alias mutation. The metadata link resolves only into this container's scratch volume.
# ============================================================================
set -eu
umask 077
mkdir -p /tmp/model-metadata
exec /bin/ollama "$@"
