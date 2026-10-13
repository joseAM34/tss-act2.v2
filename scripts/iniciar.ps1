param(
    [ValidateSet('dev', 'postgres')]
    [string]$Perfil = 'dev'
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$previousPassword = $env:ADMIN_PASSWORD
$previousUsername = $env:ADMIN_USERNAME
$previousMavenOptions = $env:MAVEN_OPTS
$locationBeforeStart = Get-Location
$passwordPointer = [IntPtr]::Zero

try {
    Set-Location -LiteralPath $projectDirectory
    if (-not $env:ADMIN_USERNAME) { $env:ADMIN_USERNAME = 'admin' }
    if (-not $env:ADMIN_PASSWORD) {
        $adminSecret = Read-Host 'Elige la contraseña del panel (mínimo 12 caracteres)' -AsSecureString
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecret)
        $env:ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        $passwordPointer = [IntPtr]::Zero
    }
    if ($env:ADMIN_PASSWORD.Length -lt 12) {
        throw 'La contraseña debe tener al menos 12 caracteres.'
    }

    # Java usa la confianza de Windows para las descargas Maven en este proceso.
    $env:MAVEN_OPTS = "$previousMavenOptions -Djavax.net.ssl.trustStoreType=Windows-ROOT".Trim()
    Write-Host "Aplicación: http://localhost:8080 | Panel: http://localhost:8080/admin | Usuario: $env:ADMIN_USERNAME"
    Write-Host 'Para detenerla, pulsa Ctrl+C.'
    if ($Perfil -eq 'dev') {
        & '.\mvnw.cmd' '-Dspring-boot.run.profiles=dev' spring-boot:run
    } else {
        & '.\mvnw.cmd' spring-boot:run
    }
    if ($LASTEXITCODE -ne 0) { throw "La aplicación finalizó con código $LASTEXITCODE." }
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $env:ADMIN_PASSWORD = $previousPassword
    $env:ADMIN_USERNAME = $previousUsername
    $env:MAVEN_OPTS = $previousMavenOptions
    Set-Location -LiteralPath $locationBeforeStart.Path
}
