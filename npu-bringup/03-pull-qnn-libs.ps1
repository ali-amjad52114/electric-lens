# =============================================================================
# 03-pull-qnn-libs.ps1  -  Pull Qualcomm QNN runtime libs from the phone.
#
# Pulls the still-missing DSP skel (libQnnHtpV79Skel.so) by searching /vendor,
# and re-pulls the other 3 device libs, into the staging dir
# npu-bringup\runtime-libs\arm64-v8a\. Then verifies all 4 are present.
#
# Requires BLOCKER A cleared: phone authorized for adb (serial R3CXC0804NZ).
# Run from Windows PowerShell:
#   powershell -ExecutionPolicy Bypass -File .\03-pull-qnn-libs.ps1
# =============================================================================
$ErrorActionPreference = 'Stop'

# ---- Config (real ground-truth values) -------------------------------------
$Serial   = 'R3CXC0804NZ'
$Adb      = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$StageDir = Join-Path $ScriptDir 'runtime-libs\arm64-v8a'

# The device libs we re-pull from their known location (relaysight pulled from here).
$KnownDir = '/vendor/lib64/snap'
$Known    = @('libQnnHtp.so', 'libQnnHtpV79Stub.so', 'libQnnSystem.so')
$SkelName = 'libQnnHtpV79Skel.so'
# Candidate dirs for the DSP skel (Hexagon v79).
$SkelDirs = @(
    '/vendor/lib/rfsa/adsp',
    '/vendor/dsp',
    '/vendor/lib64/snap',
    '/vendor/lib/rfsa/dsp',
    '/dsp'
)

function Say  ($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok   ($m) { Write-Host "    OK: $m"   -ForegroundColor Green }
function Warn ($m) { Write-Host "    WARN: $m" -ForegroundColor Yellow }
function Die  ($m) { Write-Host "ERROR: $m" -ForegroundColor Red; exit 1 }

# ---- Preflight -------------------------------------------------------------
Say 'Preflight'
if (-not (Test-Path $Adb)) { Die "adb.exe not found at $Adb" }
Ok "adb: $Adb"
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
Ok "Staging dir: $StageDir"

# adb authorization check (BLOCKER A).
$state = (& $Adb -s $Serial get-state) 2>$null
if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne 'device') {
    $devs = (& $Adb devices) 2>$null
    Warn "adb did not report '$Serial' as 'device'. Current 'adb devices':"
    Write-Host $devs
    Die @"
BLOCKER A: device not authorized/online.
On the phone: re-plug USB, tap 'Allow' on the 'Allow USB debugging?' prompt
(check 'Always allow from this computer'), then re-run this script.
If it still shows 'unauthorized': & '$Adb' kill-server; & '$Adb' start-server
"@
}
Ok "Device $Serial online"

# ---- Helper: try to pull a remote file -------------------------------------
function Try-Pull([string]$remote, [string]$destName) {
    $dest = Join-Path $StageDir $destName
    & $Adb -s $Serial pull $remote $dest 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0 -and (Test-Path $dest)) {
        Ok "pulled $remote -> $destName ($((Get-Item $dest).Length) bytes)"
        return $true
    }
    return $false
}

# ---- 1. Re-pull the 3 known libs -------------------------------------------
Say "Re-pulling known device libs from $KnownDir"
foreach ($lib in $Known) {
    if (-not (Try-Pull "$KnownDir/$lib" $lib)) {
        Warn "Could not pull $KnownDir/$lib (will rely on staged copy if present)."
    }
}

# ---- 2. Locate + pull the DSP skel -----------------------------------------
Say "Locating $SkelName on device"
$found = $null
foreach ($d in $SkelDirs) {
    $remote = "$d/$SkelName"
    $ls = (& $Adb -s $Serial shell "ls $remote 2>/dev/null") 2>$null
    if ($LASTEXITCODE -eq 0 -and $ls -and $ls.Trim() -eq $remote) {
        $found = $remote
        Ok "Found at $remote"
        break
    }
}

if (-not $found) {
    Warn "Not in known dirs; doing a broad 'find' under /vendor and /dsp (may need root)."
    $hit = (& $Adb -s $Serial shell "find /vendor /dsp /odm -name $SkelName 2>/dev/null | head -n 1") 2>$null
    if ($hit) { $hit = $hit.Trim() }
    if ($hit) { $found = $hit; Ok "find located: $found" }
}

if ($found) {
    if (-not (Try-Pull $found $SkelName)) {
        Warn "Found $found but pull failed (permission?). Try 'adb root' (may be denied on retail S25),"
        Warn "or take the skel from the QNN SDK: \$QNN_SDK_ROOT/lib/hexagon-v79/unsigned/$SkelName"
    }
} else {
    Warn "$SkelName not found via shell user. Likely needs vendor/root read access."
    Warn "Fallback: copy it from the QNN SDK after install:"
    Warn "   ~/qcom/qairt/<version>/lib/hexagon-v79/unsigned/$SkelName  (version per VERSION-MATCH.md)"
}

# ---- 3. Verify the full required set ---------------------------------------
Say 'Verify staged Qualcomm libs'
$required = @('libQnnHtp.so', 'libQnnHtpV79Stub.so', 'libQnnSystem.so', $SkelName)
$missing = @()
foreach ($lib in $required) {
    $p = Join-Path $StageDir $lib
    if (Test-Path $p) {
        Write-Host ("    [ OK     ] {0,-26} {1,10} bytes" -f $lib, (Get-Item $p).Length)
    } else {
        Write-Host ("    [ MISSING] {0}" -f $lib) -ForegroundColor Yellow
        $missing += $lib
    }
}

if ($missing.Count -eq 0) {
    Ok "All 4 Qualcomm libs present in $StageDir"
    Write-Host "    Next: assemble-runtime-libs.ps1 (adds the 2 AAR libs), then run Phase 5."
} else {
    Warn ("Still missing: {0}" -f ($missing -join ', '))
    Warn "See fallbacks above. The skel can also come from the QNN SDK."
    exit 2
}
