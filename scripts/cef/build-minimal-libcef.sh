#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# build-minimal-libcef.sh
#
# Automated pipeline to build a minimal libcef distribution from source.
#
# Strategy:
#   1. Prepare the environment (Python 3, Git, depot_tools on PATH).
#   2. Download CEF + Chromium sources with automate-git.py using
#      --minimal-distrib and --client-distrib (release build only).
#   3. Pass GN flags via GN_DEFINES that strip unnecessary Chromium
#      subsystems (WebRTC, printing, NaCl, extensions, WebGL, PGO) and
#      remove debug symbols (symbol_level=0).
#   4. Re-run automate-git.py with --force-build to produce the distributable.
#   5. Post-process the output binaries with `strip` for maximum size savings.
#
# Usage:
#   ./build-minimal-libcef.sh [CEF_BRANCH] [DOWNLOAD_DIR]
#
# Examples:
#   ./build-minimal-libcef.sh                 # defaults: branch 6834, ~/chromium_git
#   ./build-minimal-libcef.sh 6834 /ssd/cef   # explicit branch and download dir
#
# Requirements:
#   - Linux x64, SSD with >= 200 GB free space.
#   - Python 3, Git, curl. On Debian/Ubuntu also install the Chromium
#     build dependencies (see docs/cef-minimal-build.md and the CEF wiki:
#     https://bitbucket.org/chromiumembedded/cef/wiki/AutomatedBuildSetup)
#
# All code, comments and documentation in this project are written in English.
# ---------------------------------------------------------------------------
set -euo pipefail

# --------------------------- Configuration ---------------------------------
# CEF milestone branch to build (e.g. 6834 -> CEF 132). Override via $1.
CEF_BRANCH="${1:-6834}"

# Workspace where Chromium/CEF sources will live. Override via $2.
DOWNLOAD_DIR="${2:-${HOME}/chromium_git}"

# Location of depot_tools checkout.
DEPOT_TOOLS_DIR="${DEPOT_TOOLS_DIR:-${HOME}/depot_tools}"

# Minimum free disk space (GB) required on the download volume.
MIN_FREE_GB=200

# --------------------------- GN flags --------------------------------------
# These flags are consumed by automate-git.py / CEF's build scripts and
# forwarded to the GN toolchain. They disable entire Chromium subsystems
# that a minimal desktop embedder does not need.
#
#   is_official_build=true  : production optimizations (LTCG, etc.)
#   symbol_level=0          : drop all debug symbols (huge binary-size win)
#   enable_webrtc=false     : no WebRTC (video calls, data channels)
#   enable_printing=false   : remove the printing subsystem
#   enable_nacl=false       : remove deprecated Native Client support
#   enable_extensions=false : remove browser-extension support
#   disable_webgl=true      : remove the WebGL stack
#   chrome_pgo_phase=0      : skip profile-guided optimization data steps
export GN_DEFINES="is_official_build=true symbol_level=0 enable_webrtc=false enable_printing=false enable_nacl=false enable_extensions=false disable_webgl=true chrome_pgo_phase=0"

# Keep depot_tools from auto-updating mid-build; force the system python3.
export DEPOT_TOOLS_UPDATE=0

# --------------------------- Helpers ---------------------------------------
log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

check_disk_space() {
    local path="$1"
    mkdir -p "$path"
    local free_gb
    free_gb=$(df -BG --output=avail "$path" | tail -n1 | tr -dc '0-9')
    log "Available space on volume for ${path}: ~${free_gb} GB"
    if [ "${free_gb}" -lt "${MIN_FREE_GB}" ]; then
        die "At least ${MIN_FREE_GB} GB of free space are required (found ${free_gb} GB)."
    fi
}

# --------------------------- Step 1: Environment ----------------------------
log "Step 1/5: Preparing build environment"
require_cmd git
require_cmd python3
require_cmd curl
check_disk_space "${DOWNLOAD_DIR}"

if [ ! -d "${DEPOT_TOOLS_DIR}" ]; then
    log "Cloning depot_tools into ${DEPOT_TOOLS_DIR}"
    git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "${DEPOT_TOOLS_DIR}"
fi
export PATH="${DEPOT_TOOLS_DIR}:${PATH}"
log "depot_tools added to PATH: ${DEPOT_TOOLS_DIR}"

# --------------------------- Step 2: Source download ------------------------
log "Step 2/5: Downloading CEF/Chromium sources (branch ${CEF_BRANCH})"
AUTOMATE_GIT="${DOWNLOAD_DIR}/cef/tools/automate-git.py"

if [ ! -f "${AUTOMATE_GIT}" ]; then
    mkdir -p "${DOWNLOAD_DIR}"
    # Bootstrap: clone the CEF repository itself first so automate-git.py
    # is available at its canonical location.
    if [ ! -d "${DOWNLOAD_DIR}/cef" ]; then
        git clone https://bitbucket.org/chromiumembedded/cef.git "${DOWNLOAD_DIR}/cef"
        ( cd "${DOWNLOAD_DIR}/cef" && git checkout "origin/${CEF_BRANCH}" )
    fi
    AUTOMATE_GIT="${DOWNLOAD_DIR}/cef/tools/automate-git.py"
    [ -f "${AUTOMATE_GIT}" ] || die "automate-git.py not found at ${AUTOMATE_GIT}"

    # First pass: download + patch only (--no-build). The actual build runs
    # in step 4 so that GN_DEFINES are guaranteed to be applied consistently.
    python3 "${AUTOMATE_GIT}" \
        --download-dir="${DOWNLOAD_DIR}" \
        --depot-tools-dir="${DEPOT_TOOLS_DIR}" \
        --branch="${CEF_BRANCH}" \
        --no-debug-build \
        --no-build \
        --x64-build
else
    log "Sources already present, skipping initial download"
fi

# --------------------------- Step 3: Verify GN flags ------------------------
log "Step 3/5: GN flags exported:"
log "  GN_DEFINES=${GN_DEFINES}"

# --------------------------- Step 4: Build + distribute ---------------------
log "Step 4/5: Building release + minimal/client distributions"
python3 "${AUTOMATE_GIT}" \
    --download-dir="${DOWNLOAD_DIR}" \
    --depot-tools-dir="${DEPOT_TOOLS_DIR}" \
    --branch="${CEF_BRANCH}" \
    --minimal-distrib \
    --client-distrib \
    --no-debug-build \
    --force-build \
    --x64-build

# --------------------------- Step 5: Strip binaries --------------------------
log "Step 5/5: Stripping debug symbols from output binaries"
# automate-git.py places results under <download-dir>/chromium/src/cef/binary_distrib
BINARY_DISTRIB="${DOWNLOAD_DIR}/chromium/src/cef/binary_distrib"
if [ -d "${BINARY_DISTRIB}" ]; then
    find "${BINARY_DISTRIB}" -type f \( -name 'libcef.so' -o -name 'libEGL.so' \
        -o -name 'libGLESv2.so' \) -print -exec strip --strip-all {} \; \
        || log "WARNING: strip failed for some artifacts (they may already be stripped)"
else
    log "WARNING: binary_distrib directory not found at ${BINARY_DISTRIB}"
fi

log "Done. Minimal CEF distributions available under:"
log "  ${BINARY_DISTRIB}"
