<#
.SYNOPSIS
    Собирает CrimTube.UWP (прототип для Windows 10 Mobile).

.DESCRIPTION
    Требуется Visual Studio 2019 Build Tools с нагрузкой
    "Universal Windows Platform Build Tools" (она же ставит Windows 10 SDK).
    Результат: CrimTube.UWP\AppPackages\...\*.appx (подписан временным сертификатом).

.EXAMPLE
    .\build-uwp.ps1
    .\build-uwp.ps1 -Configuration Release -Platform ARM
#>
param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Debug',

    [ValidateSet('ARM', 'x86', 'x64')]
    [string]$Platform = 'ARM'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$candidates = @(
    'C:\BuildTools2019\MSBuild\Current\Bin\MSBuild.exe',
    'C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\MSBuild\Current\Bin\MSBuild.exe',
    'C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\MSBuild\Current\Bin\MSBuild.exe',
    'C:\Program Files (x86)\Microsoft Visual Studio\2019\Professional\MSBuild\Current\Bin\MSBuild.exe'
)

$msbuild = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $msbuild) {
    throw 'MSBuild не найден. Установите VS 2019 Build Tools с нагрузкой Universal Windows Platform Build Tools.'
}

Write-Host "MSBuild: $msbuild"
Write-Host "Сборка $Configuration|$Platform ..."

& $msbuild "$root\CrimTube.UWP.sln" /restore /t:Build /v:minimal `
    "/p:Configuration=$Configuration" "/p:Platform=$Platform" "/p:AppxBundle=Never"

if ($LASTEXITCODE -ne 0) {
    throw "Сборка не удалась, код $LASTEXITCODE"
}

$packages = Get-ChildItem "$root\CrimTube.UWP\AppPackages" -Recurse -Filter *.appx -ErrorAction SilentlyContinue
if (-not $packages) {
    Write-Warning 'Сборка прошла, но .appx не найден.'
    exit 0
}

$packages | ForEach-Object {
    Write-Host ("Пакет: {0} ({1:N0} байт)" -f $_.FullName, $_.Length)
}
