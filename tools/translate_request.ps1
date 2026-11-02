param([string]$InputFile, [string]$OutputFile)
$ErrorActionPreference = 'Stop'
$request = Get-Content -Raw -Encoding utf8 -LiteralPath $InputFile | ConvertFrom-Json
$uri = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=' + [uri]::EscapeDataString($request.source) + '&tl=' + [uri]::EscapeDataString($request.target) + '&dt=t&q=' + [uri]::EscapeDataString($request.text)
$result = Invoke-RestMethod -Uri $uri -TimeoutSec 30
$text = ($result[0] | ForEach-Object { $_[0] }) -join ''
Set-Content -Encoding utf8 -LiteralPath $OutputFile -Value $text -NoNewline
