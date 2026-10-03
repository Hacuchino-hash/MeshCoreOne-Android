# AndroidOnly: WP-002 Portable, credential-stripped, bounded candidate Gradle invocation.
param(
    [switch] $ConstrainedMemory,
    [switch] $BuildLogic,
    [ValidatePattern('^[1-9][0-9]*[mMgG]$')]
    [string] $BuildHeap = '1024m',
    [ValidatePattern('^[1-9][0-9]*[mMgG]$')]
    [string] $BuildMetaspace = '512m',
    [ValidatePattern('^[1-9][0-9]*[mMgG]$')]
    [string] $BuildCodeCache = '96m',
    [ValidatePattern('^[1-9][0-9]*[mMgG]$')]
    [string] $TestHeap = '256m',
    [ValidatePattern('^[1-9][0-9]*[mMgG]$')]
    [string] $TestMetaspace = '256m',
    [Parameter(Mandatory = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
$android = Split-Path -Parent $PSScriptRoot
$declaration = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'environment-allowlist.json') | ConvertFrom-Json
if ($declaration.schema_version -ne 1 -or $declaration.allowed_names.Count -eq 0) {
    throw 'Malformed candidate environment allowlist'
}
Get-ChildItem Env: | Where-Object { $_.Name -notin $declaration.allowed_names } | ForEach-Object {
    Remove-Item -LiteralPath ('Env:' + $_.Name)
}
$env:PYTHONDONTWRITEBYTECODE = '1'
python (Join-Path $PSScriptRoot 'check_environment.py')
if ($LASTEXITCODE -ne 0) {
    throw 'Candidate build environment is not ready; Gradle was not started'
}

$localOptions = @()
if ($ConstrainedMemory) {
    $env:JAVA_OPTS = '-Xms32m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m'
    $env:GRADLE_OPTS = "-Dorg.gradle.jvmargs=`"-Xms64m -Xmx$BuildHeap -XX:MaxMetaspaceSize=$BuildMetaspace -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=$BuildCodeCache -Dfile.encoding=UTF-8`""
    $localOptions = @(
        '--max-workers=1',
        '-Pkotlin.compiler.execution.strategy=in-process',
        "-PscaffoldTestHeap=$TestHeap",
        "-PscaffoldTestJvmArgs=-Xms32m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=$TestMetaspace"
    )
}
$projectDirectory = if ($BuildLogic) { Join-Path $android 'build-logic' } else { $android }
Push-Location -LiteralPath $projectDirectory
try {
    & (Join-Path $android 'gradlew.bat') -p $projectDirectory --no-daemon --console=plain @localOptions @GradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Candidate Gradle failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
