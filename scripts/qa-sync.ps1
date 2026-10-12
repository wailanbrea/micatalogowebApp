#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('all','local','tenant','ui','live')][string]$Profile = 'all',
    [string]$Device = 'emulator-5554',
    [string]$Sdk = 'C:\Users\waila\AppData\Local\Android\Sdk',
    [switch]$Live
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if ($Device -notmatch '^emulator-\d+$') { throw 'QA is emulator-only.' }
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$signer = Join-Path $Sdk 'build-tools\36.0.0\apksigner.bat'
$aapt = Join-Path $Sdk 'build-tools\36.0.0\aapt.exe'
$base = 'http://127.0.0.1:18767/'
if ($Live) {
    $identity = Invoke-RestMethod ($base + '__qa/identity')
    if ($identity.environment -ne 'testing' -or $identity.driver -ne 'sqlite' -or
        $identity.database -ne 'C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-sync-20261012.sqlite') {
        throw 'The effective QA database is not the explicitly isolated SQLite snapshot.'
    }
    $identity | ConvertTo-Json -Compress
}
$model = (& $adb -s $Device shell getprop ro.product.model) -join ''
if ($LASTEXITCODE -ne 0 -or $model -notmatch 'sdk_gphone|Emulator') { throw 'Verified emulator not available.' }
if (-not (Test-Path -LiteralPath (Join-Path $project 'app'))) { throw 'Android project directory is missing.' }
& (Join-Path $project 'gradlew.bat') -p $project :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -PmiCatalogoOfflineCheck=true "-PmiCatalogoQaProfile=$Profile" "-PmiCatalogoApiBaseUrl=$base" --console=plain
if ($LASTEXITCODE -ne 0) { throw 'QA compilation or unit tests failed.' }
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $project 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$badging = (& $aapt dump badging $apk) -join "`n"
if ($badging -notmatch "package: name='com.bsolutions.micatalogo.offlinecheck.syncqa' versionCode='(\d+)'" -or
    $badging -notmatch 'application-debuggable') { throw 'QA requires the isolated debug APK.' }
# Read version explicitly: unrelated regex matches must not overwrite capture groups.
$version = [int]([regex]::Match($badging, "versionCode='(\d+)'").Groups[1].Value)
$certificate = (& $signer verify --print-certs $apk) -join "`n"
if ($LASTEXITCODE -ne 0 -or $certificate -notmatch 'e266fd48b028dc66dc4e530c0bc8af06b9be820545600c6c4b19c81b4b1ff775') { throw 'Unexpected debug certificate.' }
$installed = (& $adb -s $Device shell dumpsys package com.bsolutions.micatalogo.offlinecheck.syncqa) -join "`n"
if ($installed -match 'versionCode=(\d+)' -and [int]$Matches[1] -gt $version) { throw 'A downgrade is not permitted.' }
$installedPath = ((& $adb -s $Device shell pm path com.bsolutions.micatalogo.offlinecheck.syncqa) -join '').Trim() -replace '^package:', ''
if ($installedPath) {
    if ($installedPath -notmatch '^/data/app/[^;\r\n]+/base\.apk$') { throw 'Unexpected installed APK path.' }
    $temporary = 'C:\Users\waila\AppData\Local\Temp\opencode'
    if (-not (Test-Path -LiteralPath $temporary)) { throw 'Approved temporary directory is missing.' }
    $reference = Join-Path $temporary 'qa-sync-installed-package.apk'
    & $adb -s $Device pull $installedPath $reference
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify installed APK.' }
    $referenceCertificate = (& $signer verify --print-certs $reference) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $referenceCertificate -notmatch 'e266fd48b028dc66dc4e530c0bc8af06b9be820545600c6c4b19c81b4b1ff775') {
        throw 'Installed signature differs; replacement requires explicit authorization and a verified data backup.'
    }
}
& $adb -s $Device reverse tcp:18767 tcp:18767
if ($LASTEXITCODE -ne 0) { throw 'QA reverse tunnel failed.' }
& $adb -s $Device install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Compatible update failed; data and signature were preserved.' }
& $adb -s $Device install -r -t $testApk
if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
$runner = 'com.bsolutions.micatalogo.offlinecheck.syncqa.test/com.example.bspos.qa.IsolatedQaRunner'
if ($Live) { & $adb -s $Device shell am instrument -w -r -e syncQaLive true $runner }
else { & $adb -s $Device shell am instrument -w -r $runner }
if ($LASTEXITCODE -ne 0) { throw 'Instrumentation did not complete.' }
$reportText = (& $adb -s $Device shell cat /sdcard/Android/data/com.bsolutions.micatalogo.offlinecheck.syncqa/files/qa-results.json) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'QA result report is missing.' }
$report = $reportText | ConvertFrom-Json
[pscustomobject]@{ profile=$Profile; declared=$report.declared; executed=$report.executed; counts=$report.counts; gate=$report.gate } | ConvertTo-Json -Depth 5
if ($report.gate -ne 'PASS') { throw 'QA gate failed: failures, missing tests, skips or assumptions are never a certification.' }
