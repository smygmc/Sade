param([string]$InputFile, [string]$OutputFile)
$ErrorActionPreference = 'Stop'
$request = Get-Content -Raw -Encoding utf8 -LiteralPath $InputFile | ConvertFrom-Json
$page = Invoke-WebRequest -Uri 'https://www.bing.com/translator' -SessionVariable translationSession -TimeoutSec 30
$sessionId = [regex]::Match($page.Content, 'IG:"([^"]+)"').Groups[1].Value
$formId = [regex]::Match($page.Content, 'data-iid="(translator\.[^"]+)"').Groups[1].Value
$formValues = [regex]::Match($page.Content, 'params_AbusePreventionHelper\s*=\s*(\[[^\]]+\])').Groups[1].Value | ConvertFrom-Json
if (!$sessionId -or !$formId -or !$formValues) { throw 'Translation form unavailable' }
$uri = 'https://www.bing.com/ttranslatev3?isVertical=1&IG=' + $sessionId + '&IID=' + $formId
$form = @{fromLang=$request.source; to=$request.target; text=$request.text; token=$formValues[1]; key=$formValues[0]}
$response = Invoke-RestMethod -Method Post -Uri $uri -Body $form -WebSession $translationSession -TimeoutSec 30
if (!$response[0].translations[0].text) { throw 'No translated text returned' }
Set-Content -Encoding utf8 -LiteralPath $OutputFile -Value $response[0].translations[0].text -NoNewline
