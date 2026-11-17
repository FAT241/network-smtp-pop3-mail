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
    $libDir = Join-Path $buildDir "lib"
    New-Item -ItemType Directory -Force -Path $libDir | Out-Null
    $artifacts = @(
        "org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar",
        "org/eclipse/angus/jakarta.mail/2.0.3/jakarta.mail-2.0.3.jar",
        "jakarta/activation/jakarta.activation-api/2.1.3/jakarta.activation-api-2.1.3.jar",
        "org/eclipse/angus/angus-activation/2.0.2/angus-activation-2.0.2.jar"
    )
    foreach ($artifact in $artifacts) {
        $destination = Join-Path $libDir ($artifact.Split('/')[-1])
        if (!(Test-Path -LiteralPath $destination)) {
            $cached = Join-Path $env:USERPROFILE ".m2/repository/$artifact"
            if (Test-Path -LiteralPath $cached) {
                Copy-Item -LiteralPath $cached -Destination $destination
            } else {
                Invoke-WebRequest "https://repo.maven.apache.org/maven2/$artifact" -OutFile "$destination.partial"
                Move-Item -LiteralPath "$destination.partial" -Destination $destination
            }
        }
    }
    javac -encoding UTF-8 -cp "$libDir/*" -d $buildDir MailStore.java MailMime.java GmailRelay.java LANMailServer.java SMTPClient.java POP3Client.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $classpath = "$buildDir;$libDir/*"

    switch ($Mode) {
        "server" {
            $credentialFile = Join-Path $env:LOCALAPPDATA "Lab3Mail/postgres.credential.xml"
            if (!$env:LAB3_DB_URL -and !$env:LAB3_DB_USER -and !$env:LAB3_DB_PASSWORD -and
                    (Test-Path -LiteralPath $credentialFile)) {
                $credential = Import-Clixml -LiteralPath $credentialFile
                $env:LAB3_DB_USER = $credential.UserName
                $env:LAB3_DB_PASSWORD = $credential.GetNetworkCredential().Password
                try {
                    java -cp $classpath LANMailServer
                } finally {
                    Remove-Item Env:LAB3_DB_USER, Env:LAB3_DB_PASSWORD -ErrorAction SilentlyContinue
                }
            } else {
                java -cp $classpath LANMailServer
            }
        }
        "smtp" { java -cp $classpath SMTPClient $Server }
        "pop3" { java -cp $classpath POP3Client $Server }
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
