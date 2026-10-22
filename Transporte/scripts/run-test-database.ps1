$previousMongoUri = $env:SPRING_DATA_MONGODB_URI
$secureUri = Read-Host "URI de MongoDB para la base de pruebas (_test o -test)" -AsSecureString
$uriPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureUri)
try {
    $mongoUri = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($uriPointer).Trim()
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($uriPointer)
    $secureUri.Dispose()
}

if ($mongoUri.Contains("<") -or $mongoUri.Contains(">") -or
    $mongoUri -notmatch '^mongodb(?:\+srv)?://[^/?#]+/([^/?#]+)(?:\?.*)?$') {
    throw "URI invalida o incompleta. Usa la URI real de Atlas e incluye el nombre de la base despues del host."
}

$databaseName = $Matches[1]
if ($databaseName -ne "test" -and $databaseName -notmatch '[_-]test$') {
    throw "Conexión cancelada: la base debe llamarse 'test' o terminar en '_test'/'-test'."
}

try {
    $env:SPRING_DATA_MONGODB_URI = $mongoUri
    & .\mvnw.cmd spring-boot:run
    $exitCode = $LASTEXITCODE
}
finally {
    $env:SPRING_DATA_MONGODB_URI = $previousMongoUri
    $mongoUri = $null
}
exit $exitCode
