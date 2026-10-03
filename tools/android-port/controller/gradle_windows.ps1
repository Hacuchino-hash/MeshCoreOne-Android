# AndroidOnly: WP-003 Preserve native batch argument boundaries without cmd.exe double quoting.
param(
    [Parameter(Mandatory = $true)]
    [string] $InvocationFile
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandArgumentPassing = 'Legacy'
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$allowlist = Get-Content -Raw (Join-Path $repo 'android\scaffold\environment-allowlist.json') | ConvertFrom-Json
if ($allowlist.schema_version -ne 1 -or $allowlist.allowed_names.Count -eq 0) {
    throw 'Malformed unchanged candidate environment allowlist'
}
Get-ChildItem Env: | Where-Object { $_.Name -notin $allowlist.allowed_names } | ForEach-Object {
    Remove-Item -LiteralPath ('Env:' + $_.Name)
}
python (Join-Path $repo 'android\scaffold\check_environment.py')
if ($LASTEXITCODE -ne 0) {
    throw 'PowerShell child environment was not ready; Gradle was not started'
}
$invocation = Get-Content -Raw -LiteralPath $InvocationFile | ConvertFrom-Json
if (-not $invocation.wrapper -or -not $invocation.project -or $invocation.arguments.Count -eq 0) {
    throw 'Malformed declared Gradle invocation'
}
$arguments = @('-p', [string] $invocation.project) + @($invocation.arguments | ForEach-Object { [string] $_ })
& ([string] $invocation.wrapper) @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Declared Gradle invocation failed with exit $LASTEXITCODE"
}
