$ErrorActionPreference = "Stop"

$repo = "c6823821-sketch/miaoji-paint-android"
$tag = "v2.0.0"
$releaseName = (-join @(
    0x5999, 0x6781, 0x753B, 0x56FE | ForEach-Object { [char]$_ }
)) + " v2.0.0"
$outputFolder = -join @(
    0x5999, 0x6781, 0x624B, 0x673A, 0x753B, 0x56FE | ForEach-Object { [char]$_ }
)
$apkName = -join @(
    0x5999, 0x6781, 0x753B, 0x56FE | ForEach-Object { [char]$_ }
) + "-v2.0.0.apk"
$asset = Join-Path (Join-Path "E:\Codex\outputs" $outputFolder) $apkName
if (-not (Test-Path -LiteralPath $asset)) {
    throw "Versioned APK not found"
}
$notes = [System.IO.File]::ReadAllText(
    "E:\Codex\work\miaoji-paint\release-notes-v2.0.0.md",
    [System.Text.Encoding]::UTF8
)

$credentialInput = "protocol=https`nhost=github.com`n`n"
$credentialLines = $credentialInput | git credential fill
$credential = @{}
foreach ($line in $credentialLines) {
    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) {
        $credential[$parts[0]] = $parts[1]
    }
}
if (-not $credential["password"]) {
    throw "No GitHub credential available"
}

$headers = @{
    Authorization = "Bearer $($credential['password'])"
    Accept = "application/vnd.github+json"
    "User-Agent" = "Codex"
}

$releasePayload = @{
    tag_name = $tag
    name = $releaseName
    body = $notes
    draft = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4

$release = $null
try {
    $release = Invoke-RestMethod `
        -Method Get `
        -Uri "https://api.github.com/repos/$repo/releases/tags/$tag" `
        -Headers $headers
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) {
        throw
    }
}

if ($release) {
    $release = Invoke-RestMethod `
        -Method Patch `
        -Uri "https://api.github.com/repos/$repo/releases/$($release.id)" `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($releasePayload))
} else {
    $release = Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.github.com/repos/$repo/releases" `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($releasePayload))
}

$uploadBase = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets"
$assetName = "miaoji-paint-v2.0.0.apk"
$existingAssets = Invoke-RestMethod `
    -Method Get `
    -Uri "https://api.github.com/repos/$repo/releases/$($release.id)/assets" `
    -Headers $headers
foreach ($existingAsset in @($existingAssets)) {
    if ($existingAsset.name -like "*.apk") {
        Invoke-RestMethod `
            -Method Delete `
            -Uri "https://api.github.com/repos/$repo/releases/assets/$($existingAsset.id)" `
            -Headers $headers | Out-Null
    }
}

$escapedAssetName = [uri]::EscapeDataString($assetName)
$uploadUri = "${uploadBase}?name=${escapedAssetName}"
Write-Output $uploadUri
$uploaded = Invoke-RestMethod `
    -Method Post `
    -Uri $uploadUri `
    -Headers $headers `
    -ContentType "application/vnd.android.package-archive" `
    -Body ([System.IO.File]::ReadAllBytes($asset))

[pscustomobject]@{
    release = $release.html_url
    tag = $release.tag_name
    asset = $uploaded.browser_download_url
    size = $uploaded.size
} | ConvertTo-Json
