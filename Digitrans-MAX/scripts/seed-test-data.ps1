$previousSeedUri = $env:SEED_MONGODB_URI
$secureUri = Read-Host "URI de MongoDB para la base de pruebas (_test o -test)" -AsSecureString
$uriPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureUri)
try {
    $env:SEED_MONGODB_URI = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($uriPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($uriPointer)
    $secureUri.Dispose()
}

try {
    & python .\scripts\seed-test-data.py
    $exitCode = $LASTEXITCODE
}
finally {
    $env:SEED_MONGODB_URI = $previousSeedUri
}
exit $exitCode
