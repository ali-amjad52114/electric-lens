# =============================================================================
# assemble-runtime-libs.ps1  -  Lay out a complete arm64-v8a QNN runtime lib set.
#
# 1. Extracts libexecutorch.so + libqnn_executorch_backend.so from the QNN AAR.
# 2. Copies the 3 device libs we already pulled (relaysight jniLibs).
# 3. Gathers everything into npu-bringup\runtime-libs\arm64-v8a\.
# 4. Prints a present / MISSING table (skel is expected MISSING until
#    03-pull-qnn-libs.ps1 runs, or until copied from the QNN SDK).
#
# Non-destructive: only reads AAR + copies files. Run from Windows PowerShell:
#   powershell -ExecutionPolicy Bypass -File .\assemble-runtime-libs.ps1
# =============================================================================
$ErrorActionPreference = 'Stop'

# ---- Config (real ground-truth paths) --------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Aar       = 'C:\AI\Projects\ElectricSafe\_qnn_aar\executorch-qnn.aar'
$JniLibs   = 'C:\AI\Projects\ElectricSafe\relaysight-android\app\src\main\jniLibs\arm64-v8a'
$StageDir  = Join-Path $ScriptDir 'runtime-libs\arm64-v8a'
$Abi       = 'arm64-v8a'

# Libs we expect from each source.
$AarLibs    = @('libexecutorch.so', 'libqnn_executorch_backend.so')
$DeviceLibs = @('libQnnHtp.so', 'libQnnHtpV79Stub.so', 'libQnnSystem.so')
$SkelName   = 'libQnnHtpV79Skel.so'   # supplied by 03-pull-qnn-libs.ps1 / QNN SDK

function Say  ($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok   ($m) { Write-Host "    OK: $m"   -ForegroundColor Green }
function Warn ($m) { Write-Host "    WARN: $m" -ForegroundColor Yellow }
function Die  ($m) { Write-Host "ERROR: $m" -ForegroundColor Red; exit 1 }

# ---- Preflight -------------------------------------------------------------
Say 'Preflight'
if (-not (Test-Path $Aar))     { Die "QNN AAR not found at $Aar" }
if (-not (Test-Path $JniLibs)) { Warn "Device jniLibs dir not found at $JniLibs (will mark those MISSING)" }
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
Ok "Staging dir: $StageDir"

# ---- 1. Extract from the AAR (a zip) ---------------------------------------
Say 'Extracting ExecuTorch + QNN backend .so from the AAR'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("aar_" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($Aar, $tmp)
    foreach ($lib in $AarLibs) {
        # AAR layout is typically jni/<abi>/<lib>.so ; search to be robust.
        $src = Get-ChildItem -Path $tmp -Recurse -Filter $lib -File |
               Where-Object { $_.FullName -match [regex]::Escape($Abi) } |
               Select-Object -First 1
        if (-not $src) {
            $src = Get-ChildItem -Path $tmp -Recurse -Filter $lib -File | Select-Object -First 1
        }
        if ($src) {
            Copy-Item $src.FullName (Join-Path $StageDir $lib) -Force
            Ok "extracted $lib ($($src.Length) bytes) from AAR"
        } else {
            Warn "AAR did not contain $lib"
        }
    }
}
finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

# ---- 2. Copy the 3 device libs we already have -----------------------------
Say "Copying device libs from $JniLibs"
foreach ($lib in $DeviceLibs) {
    $src = Join-Path $JniLibs $lib
    if (Test-Path $src) {
        Copy-Item $src (Join-Path $StageDir $lib) -Force
        Ok "copied $lib ($((Get-Item $src).Length) bytes)"
    } else {
        Warn "missing source: $src"
    }
}

# ---- 2b. Pick up an already-staged skel if 03-pull put one there ------------
$skelStaged = Test-Path (Join-Path $StageDir $SkelName)

# ---- 3. Present / MISSING table --------------------------------------------
Say "Runtime lib set in $StageDir"
$all = $AarLibs + $DeviceLibs + @($SkelName)
$missing = @()
foreach ($lib in $all) {
    $p = Join-Path $StageDir $lib
    if (Test-Path $p) {
        Write-Host ("    [ OK     ] {0,-30} {1,10} bytes" -f $lib, (Get-Item $p).Length)
    } else {
        Write-Host ("    [ MISSING] {0}" -f $lib) -ForegroundColor Yellow
        $missing += $lib
    }
}

Say 'Summary'
if ($missing.Count -eq 0) {
    Ok 'Complete arm64-v8a runtime lib set assembled (6/6).'
} else {
    Warn ("Missing: {0}" -f ($missing -join ', '))
    if ($missing -contains $SkelName) {
        Write-Host "    -> $SkelName is expected missing until you run 03-pull-qnn-libs.ps1" -ForegroundColor Yellow
        Write-Host "       (or copy it from the QNN SDK: \$QNN_SDK_ROOT/lib/hexagon-v79/unsigned/$SkelName)." -ForegroundColor Yellow
    }
}

Write-Host "`n    Required full set for an on-device run:"
Write-Host "      libexecutorch.so, libqnn_executorch_backend.so (AAR)"
Write-Host "      libQnnHtp.so, libQnnHtpV79Stub.so, libQnnSystem.so (device/SDK)"
Write-Host "      libQnnHtpV79Skel.so (DSP skel - device/SDK)"
