Write-Host ""
Write-Host "Configuracao da exportacao de baixas de manifesto" -ForegroundColor Cyan
Write-Host "As variaveis serao salvas no usuario atual do Windows." -ForegroundColor Cyan
Write-Host ""

$legacyUrl = "jdbc:mysql://salome.bancodedadosclientes.com:3306/salome_rp"
$legacyUser = "lucas"
$credentialsPath = "C:\dev\salome-core\google-service-account.json"

$spreadsheetId = Read-Host "Cole o ID da Google Sheet"
if ([string]::IsNullOrWhiteSpace($spreadsheetId)) {
    Write-Host "ID da planilha nao informado. Configuracao cancelada." -ForegroundColor Red
    exit 1
}

$securePassword = Read-Host "Digite a senha do usuario MySQL lucas" -AsSecureString
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
if ([string]::IsNullOrWhiteSpace($password)) {
    Write-Host "Senha nao informada. Configuracao cancelada." -ForegroundColor Red
    exit 1
}

[Environment]::SetEnvironmentVariable("SALOME_LEGACY_DB_ENABLED", "true", "User")
[Environment]::SetEnvironmentVariable("SALOME_LEGACY_DB_URL", $legacyUrl, "User")
[Environment]::SetEnvironmentVariable("SALOME_LEGACY_DB_USERNAME", $legacyUser, "User")
[Environment]::SetEnvironmentVariable("SALOME_LEGACY_DB_PASSWORD", $password, "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_ENABLED", "true", "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_CRON", "0 */15 * * * *", "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID", $spreadsheetId, "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH", $credentialsPath, "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_FILIAL_DESTINO_ID", "", "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_BATCH_SIZE", "500", "User")
[Environment]::SetEnvironmentVariable("SALOME_MANIFESTO_EXPORT_DATA_CORTE", "2026-05-01", "User")

Write-Host ""
Write-Host "Configuracao salva." -ForegroundColor Green
Write-Host "Importante: salve o JSON da conta de servico em:" -ForegroundColor Yellow
Write-Host $credentialsPath -ForegroundColor Yellow
Write-Host ""
Write-Host "Feche e abra novamente o terminal ou de duplo clique em rodar-exportacao-manifestos.bat." -ForegroundColor Cyan
Read-Host "Pressione ENTER para fechar"
