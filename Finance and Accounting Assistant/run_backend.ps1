$envFile = "..\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match "^[^#]" -and $_ -match "=" } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
    }
}
.\mvnw.cmd clean spring-boot:run
