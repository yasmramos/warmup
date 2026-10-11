# Building a Minimal libcef for Desktop Applications

This guide describes how to compile a size-optimized `libcef` from source,
intended for embedding in desktop applications (such as JavaFX apps that need
an embedded web view). The strategy relies on build-time configuration rather
than source patching, which keeps the result maintainable and officially
supported.

## 1. Environment Preparation

Required tooling:

- **Python 3** and **Git**
- **depot_tools** (Chromium's toolchain: `gclient`, `gn`, `ninja`)
- An **SSD with at least 200 GB free** — the Chromium checkout plus build
  artifacts are very large.

```bash
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git ~/depot_tools
export PATH="$HOME/depot_tools:$PATH"   # add to ~/.bashrc for persistence
```

On Debian/Ubuntu, install the Chromium build dependencies listed in the
[CEF Automated Build Setup wiki](https://bitbucket.org/chromiumembedded/cef/wiki/AutomatedBuildSetup).

## 2. Obtaining the Source Code

The canonical automation entry point is `automate-git.py`, shipped inside the
CEF repository. It clones Chromium, applies CEF patches, syncs dependencies
and (optionally) builds and packages distributions.

```bash
python3 ~/chromium_git/cef/tools/automate-git.py \
  --download-dir=$HOME/chromium_git \
  --depot-tools-dir=$HOME/depot_tools \
  --branch=6834 \
  --minimal-distrib \
  --client-distrib \
  --no-debug-build
```

| Flag | Purpose |
|------|---------|
| `--minimal-distrib` | Produces a distribution containing only the files essential to run an app (release binaries, resources), excluding samples and tests. |
| `--client-distrib` | Adds the headers and import libraries needed to compile your own application against CEF. |
| `--no-debug-build` | Skips the debug build entirely, saving hours of compile time and hundreds of GB. |

> Branch numbers correspond to CEF milestones (e.g. `6834` → CEF 132). Pick
> the branch matching the Chromium version you need; see the release list at
> https://github.com/chromiumembedded/cef/wiki/GeneralReleasePlanning.

## 3. Build Flags (`GN_DEFINES`)

Export these flags before invoking the build so they are forwarded to GN.
They disable entire Chromium subsystems a minimal desktop embedder does not
need:

```bash
export GN_DEFINES="is_official_build=true symbol_level=0 enable_webrtc=false enable_printing=false enable_nacl=false enable_extensions=false disable_webgl=true chrome_pgo_phase=0"
```

| Flag | Effect |
|------|--------|
| `is_official_build=true` | Enables production optimizations (LTCG, thin-LTO behavior). |
| `symbol_level=0` | **Critical.** Removes all debug symbols; drastically shrinks binaries. |
| `enable_webrtc=false` | Disables WebRTC (video calls, data channels). |
| `enable_printing=false` | Removes the printing subsystem. |
| `enable_nacl=false` | Removes deprecated Native Client support. |
| `enable_extensions=false` | Removes browser-extension support. |
| `disable_webgl=true` | Removes the WebGL stack. |
| `chrome_pgo_phase=0` | Skips profile-guided-optimization steps. |

Notes:

- Use `false` (not `0`) for boolean GN args; both parse, but `false` is the
  idiomatic form.
- Some flags interact: `is_official_build=true` already implies
  `symbol_level=0` and `dcheck_always_on=false` on most platforms, but setting
  them explicitly documents intent.

## 4. Compiling and Final Optimization

Run the full pipeline (the script below wraps steps 1–5):

```bash
./scripts/cef/build-minimal-libcef.sh 6834 $HOME/chromium_git
```

Which internally executes:

```bash
python3 automate-git.py \
  --download-dir=$HOME/chromium_git \
  --depot-tools-dir=$HOME/depot_tools \
  --branch=6834 \
  --minimal-distrib \
  --client-distrib \
  --no-debug-build \
  --force-build \
  --x64-build
```

After the build, strip any remaining symbols from the Linux output (defense in
depth — usually redundant when `symbol_level=0` was applied):

```bash
strip --strip-all $HOME/chromium_git/chromium/src/cef/binary_distrib/*/cef_linux_x64/libcef.so
```

Outputs land in `<download-dir>/chromium/src/cef/binary_distrib/`.

## 5. Strategy Summary & Warnings

- **Core strategy**: combine `--minimal-distrib` with the `GN_DEFINES` above.
  This is the most effective reduction path without touching Chromium sources.
- **Patching risks**: disabling features by patching code (PDF viewer,
  DevTools internals) is possible but unsupported and may break internal
  dependencies at runtime. Try GN flags first.
- **Runtime alternatives**: several features can be disabled without
  recompiling, via command-line switches passed to the CEF app, e.g.
  `--disable-dev-tools`, `--no-sandbox` (dev only), or CEF settings such as
  `external_message_pump`.
- **CI**: `.github/workflows/cef-minimal-build.yml` is a template for running
  this pipeline on a self-hosted runner with sufficient disk/CPU. Standard
  GitHub-hosted runners cannot fit a Chromium build.

### Expected size impact (indicative)

A stock release `libcef.so` is typically ~150–200 MB. With `symbol_level=0`,
official-build optimizations and the feature-disabling flags above, results
in the ~70–120 MB range are common depending on milestone. Exact numbers vary
per CEF version; measure with `ls -lh` / `bloaty` after each build.
