param(
    [ValidateSet("server", "smtp", "pop3", "build")]
    [string]$Mode = "build",
    [string]$Server = "localhost"
)

$ErrorActionPreference = "Stop"
Push-Location -LiteralPath $PSScriptRoot
try {
    $buildDir = Join-Path $PSScriptRoot "build"
    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    javac -encoding UTF-8 -d $buildDir MailStore.java LANMailServer.java SMTPClient.java POP3Client.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    switch ($Mode) {
        "server" {
            $version = "42.7.14"
            $jarName = "postgresql-$version.jar"
            $artifact = "org/postgresql/postgresql/$version/$jarName"
            $driver = Join-Path $env:USERPROFILE ".m2/repository/$artifact"
            if (!(Test-Path -LiteralPath $driver)) {
                $driver = Join-Path $buildDir $jarName
                if (!(Test-Path -LiteralPath $driver)) {
                    Invoke-WebRequest "https://repo.maven.apache.org/maven2/$artifact" -OutFile "$driver.partial"
                    Move-Item -LiteralPath "$driver.partial" -Destination $driver
                }
            }
            $credentialFile = Join-Path $env:LOCALAPPDATA "Lab3Mail/postgres.credential.xml"
            if (!$env:LAB3_DB_URL -and !$env:LAB3_DB_USER -and !$env:LAB3_DB_PASSWORD -and
                    (Test-Path -LiteralPath $credentialFile)) {
                $credential = Import-Clixml -LiteralPath $credentialFile
                $env:LAB3_DB_USER = $credential.UserName
                $env:LAB3_DB_PASSWORD = $credential.GetNetworkCredential().Password
                try {
                    java -cp "$buildDir;$driver" LANMailServer
                } finally {
                    Remove-Item Env:LAB3_DB_USER, Env:LAB3_DB_PASSWORD -ErrorAction SilentlyContinue
                }
            } else {
                java -cp "$buildDir;$driver" LANMailServer
            }
        }
        "smtp" { java -cp $buildDir SMTPClient $Server }
        "pop3" { java -cp $buildDir POP3Client $Server }
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
