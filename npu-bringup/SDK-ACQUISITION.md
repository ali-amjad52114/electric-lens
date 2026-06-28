# QNN SDK (QAIRT) Acquisition

The on-device run needs the **Qualcomm AI Engine Direct SDK** — recently
rebranded **QAIRT** (Qualcomm AI Runtime). This is the SDK that provides the HTP
backend host tooling and the matching device/DSP libraries. `QNN_SDK_ROOT` points
at it.

> The **exact version to install is in `VERSION-MATCH.md`** (it must line up with
> the QNN backend baked into our AAR and the device's Hexagon v79 skel). Do not
> grab "latest" blindly — install the version that doc specifies.

## What to download

- **Product:** Qualcomm AI Engine Direct SDK / **QAIRT** (Qualcomm AI Runtime SDK).
- **Where:** Qualcomm's developer site —
  the Qualcomm Package Manager (QPM3) or the "Qualcomm AI Engine Direct SDK"
  download page under Qualcomm AI Stack tooling.
- **Account:** requires a **free Qualcomm developer account** (sign-in / EULA
  acceptance is mandatory; there is no anonymous download).
- **Form:** a `.zip` (or QPM-managed install). We want the Linux x86_64 host
  package so it runs in WSL2.

## Where to put it (WSL)

Unzip / install into your WSL home so the path matches the rest of this kit:

```bash
mkdir -p ~/qcom/qairt
# unzip the downloaded SDK so you end up with: ~/qcom/qairt/<version>/
unzip ~/Downloads/qairt-<version>-linux.zip -d ~/qcom/qairt/
ls ~/qcom/qairt/                      # should show the <version> dir
```

Then set the env (also add to `~/.bashrc` — see RUNBOOK Phase 2):

```bash
export QNN_SDK_ROOT="$HOME/qcom/qairt/<version>"
```

## Quick validity check

After install, these should exist (names vary slightly by version):

```bash
ls "$QNN_SDK_ROOT/bin"                              # host tools (qnn-* / qairt-*)
ls "$QNN_SDK_ROOT/lib/x86_64-linux-clang"          # host libs (WSL build)
ls "$QNN_SDK_ROOT/lib/aarch64-android"             # device libs
ls "$QNN_SDK_ROOT/lib/hexagon-v79/unsigned"        # DSP skels (incl. libQnnHtpV79Skel.so)
```

The `hexagon-v79/unsigned/libQnnHtpV79Skel.so` here is the same skel we try to pull
from the phone in `03-pull-qnn-libs.ps1` — either source works as long as the
version matches `VERSION-MATCH.md`.
