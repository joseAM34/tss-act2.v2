param(
    [string]$Jar = (Join-Path $PSScriptRoot '../target/estudio-mercado-0.1.0-SNAPSHOT.jar')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packagePath = (Resolve-Path -LiteralPath $Jar).Path
$archive = [IO.Compression.ZipFile]::OpenRead($packagePath)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
    foreach ($resource in @(
        'BOOT-INF/classes/static/vendor/echarts-6.0.0.min.js',
        'BOOT-INF/classes/static/vendor/ECHARTS-LICENSE.txt',
        'BOOT-INF/classes/static/vendor/ECHARTS-NOTICE.txt',
        'BOOT-INF/classes/static/css/app.css',
        'BOOT-INF/classes/static/js/app.js',
        'BOOT-INF/classes/templates/survey.html'
    )) {
        if ($entries -notcontains $resource) { throw "Falta el recurso local: $resource" }
    }
    foreach ($dependency in @('spring-boot-', 'spring-security-core-', 'thymeleaf-', 'postgresql-', 'flyway-core-', 'commons-statistics-inference-')) {
        if (-not ($entries | Where-Object { $_.StartsWith("BOOT-INF/lib/$dependency") })) {
            throw "Falta la dependencia empaquetada: $dependency"
        }
    }
    $libraries = @($entries | Where-Object { $_ -like 'BOOT-INF/lib/*.jar' })
    Write-Output "Paquete verificado: $packagePath"
    Write-Output "$($libraries.Count) bibliotecas incluidas; ECharts, estilos, scripts y plantillas locales presentes."
    Write-Output 'Esta comprobación no conecta a la base de datos ni valida un despliegue.'
} finally {
    $archive.Dispose()
}
