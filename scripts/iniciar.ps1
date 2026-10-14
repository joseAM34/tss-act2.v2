param(
    [ValidateSet('dev', 'postgres')]
    [string]$Perfil = 'dev'
)

# Atajo opcional. Ejecutar desde la raíz del proyecto, con las variables ya configuradas.
# El comando manual equivalente está en README.md.
if ($Perfil -eq 'dev') {
    & '.\mvnw.cmd' "-Dspring-boot.run.profiles=dev" spring-boot:run
} else {
    & '.\mvnw.cmd' spring-boot:run
}

if ($LASTEXITCODE -ne 0) {
    throw "Maven finalizó con código $LASTEXITCODE. Revisa el mensaje de la terminal."
}
