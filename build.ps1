param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } elseif (Test-Path 'C:/Program Files/Java/jdk-27') { 'C:/Program Files/Java/jdk-27' } else { $null }
$javac = if ($jdk) { Join-Path $jdk 'bin/javac.exe' } else { 'javac' }
$java = if ($jdk) { Join-Path $jdk 'bin/java.exe' } else { 'java' }
New-Item -ItemType Directory -Path lib,out,test-out -Force | Out-Null
if (!(Test-Path 'lib/pdfbox-app-3.0.8.jar')) {
    Invoke-WebRequest 'https://downloads.apache.org/pdfbox/3.0.8/pdfbox-app-3.0.8.jar' -OutFile 'lib/pdfbox-app-3.0.8.jar'
}
if (!(Test-Path 'lib/pdfbox-app-3.0.8.jar.sha512')) {
    Invoke-WebRequest 'https://downloads.apache.org/pdfbox/3.0.8/pdfbox-app-3.0.8.jar.sha512' -OutFile 'lib/pdfbox-app-3.0.8.jar.sha512'
}
$checksum = (Get-Content 'lib/pdfbox-app-3.0.8.jar.sha512' -Raw).Trim()
$hash = (Get-FileHash 'lib/pdfbox-app-3.0.8.jar' -Algorithm SHA512).Hash
if ($checksum -notmatch $hash) { throw 'PDFBox checksum mismatch' }
if (!(Test-Path 'lib/postgresql-42.7.14.jar')) {
    Invoke-WebRequest 'https://jdbc.postgresql.org/download/postgresql-42.7.14.jar' -OutFile 'lib/postgresql-42.7.14.jar'
}
$sources = Get-ChildItem src -Recurse -Filter '*.java' | ForEach-Object FullName
& $javac --release 17 -cp 'lib/*' -d out $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
if ($Test) {
    $tests = Get-ChildItem tests -Filter '*.java' | ForEach-Object FullName
    & $javac --release 17 -cp 'out;lib/*' -d test-out $tests
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & $java -cp 'out;test-out;lib/*' RecruitmentIntegrationTest
    if ($LASTEXITCODE -ne 0) { throw 'Recruitment integration test failed' }
    & $java -cp 'out;test-out;lib/*' EditDetailsIntegrationTest
    if ($LASTEXITCODE -ne 0) { throw 'Desktop integration test failed' }
}
Write-Output 'Build complete. Start: java -cp "out;lib/*" main.WebMain'
