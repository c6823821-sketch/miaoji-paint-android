$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$SourceRoot = Join-Path $ProjectRoot "app\src\main"
$BuildRoot = Join-Path $ProjectRoot "build"
$AndroidSdk = "E:\Android\sdk"
$BuildTools = Join-Path $AndroidSdk "build-tools\36.0.0"
$AndroidJar = Join-Path $AndroidSdk "platforms\android-36\android.jar"
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$ZipAlign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"
$JavaHome = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar = Join-Path $JavaHome "bin\jar.exe"
$KeyTool = Join-Path $JavaHome "bin\keytool.exe"
$Keystore = Join-Path $ProjectRoot "miaojipaint.keystore"
$KeystorePassword = "miaojipaint2026"
$Alias = "miaojipaint"

foreach ($required in @($Aapt2, $D8, $ZipAlign, $ApkSigner, $AndroidJar, $Javac, $Jar, $KeyTool)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required build tool missing: $required"
    }
}

$resolvedBuild = [System.IO.Path]::GetFullPath($BuildRoot)
$resolvedProject = [System.IO.Path]::GetFullPath($ProjectRoot)
if (-not $resolvedBuild.StartsWith($resolvedProject, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean build directory outside project root"
}
if (Test-Path -LiteralPath $BuildRoot) {
    Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $BuildRoot, (Join-Path $BuildRoot "gen"), (Join-Path $BuildRoot "classes"), (Join-Path $BuildRoot "dex") | Out-Null

$CompiledResources = Join-Path $BuildRoot "resources.zip"
& $Aapt2 compile --dir (Join-Path $SourceRoot "res") -o $CompiledResources
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

$UnsignedApk = Join-Path $BuildRoot "app-unsigned.apk"
& $Aapt2 link `
    -o $UnsignedApk `
    -I $AndroidJar `
    --manifest (Join-Path $SourceRoot "AndroidManifest.xml") `
    -A (Join-Path $SourceRoot "assets") `
    --java (Join-Path $BuildRoot "gen") `
    $CompiledResources
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$JavaSources = @(Get-ChildItem -LiteralPath (Join-Path $SourceRoot "java") -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName })
$GeneratedSources = @(Get-ChildItem -LiteralPath (Join-Path $BuildRoot "gen") -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName })
$JavacArgs = @(
    "-encoding", "UTF-8",
    "-source", "8",
    "-target", "8",
    "-classpath", $AndroidJar,
    "-d", (Join-Path $BuildRoot "classes")
) + $JavaSources + $GeneratedSources
& $Javac @JavacArgs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$ClassFiles = @(Get-ChildItem -LiteralPath (Join-Path $BuildRoot "classes") -Recurse -Filter "*.class" |
    ForEach-Object { $_.FullName })
$D8Args = @(
    "--lib", $AndroidJar,
    "--min-api", "26",
    "--output", (Join-Path $BuildRoot "dex")
) + $ClassFiles
& $D8 @D8Args
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

$WithDexApk = Join-Path $BuildRoot "app-with-dex.apk"
Copy-Item -LiteralPath $UnsignedApk -Destination $WithDexApk
Push-Location (Join-Path $BuildRoot "dex")
try {
    & $Jar uf $WithDexApk "classes.dex"
    if ($LASTEXITCODE -ne 0) { throw "Adding classes.dex failed" }
} finally {
    Pop-Location
}

$AlignedApk = Join-Path $BuildRoot "app-aligned.apk"
& $ZipAlign -f 4 $WithDexApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

if (-not (Test-Path -LiteralPath $Keystore)) {
    & $KeyTool -genkeypair `
        -keystore $Keystore `
        -alias $Alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -storepass $KeystorePassword `
        -keypass $KeystorePassword `
        -dname "CN=MiaoJi Paint, OU=Personal, O=Codex, L=Shanghai, ST=Shanghai, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "Keystore creation failed" }
}

$SignedApk = Join-Path $BuildRoot "miaoji-paint-v1.7.2.apk"
& $ApkSigner sign `
    --ks $Keystore `
    --ks-key-alias $Alias `
    --ks-pass "pass:$KeystorePassword" `
    --key-pass "pass:$KeystorePassword" `
    --min-sdk-version 26 `
    --out $SignedApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& $ApkSigner verify --verbose --print-certs $SignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

$FinalApk = $SignedApk
$hash = (Get-FileHash -LiteralPath $FinalApk -Algorithm SHA256).Hash
$apkInfo = Get-Item -LiteralPath $FinalApk
"APK=$($apkInfo.FullName)"
"SIZE=$($apkInfo.Length)"
"SHA256=$hash"
"SIGNED=verified"
