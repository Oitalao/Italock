#Requires -RunAsAdministrator
<# Remove o Ítalock por completo: módulo da tela de bloqueio, agente, firewall, arquivos e (opcional) pareamentos. #>
param([switch]$ManterPareamentos)
$ErrorActionPreference = 'Continue'
$guid = '{4937B1C9-EBE0-47EF-9E77-8C41E6CEFFFB}'

Stop-ScheduledTask -TaskName 'Italock Agent' -ErrorAction SilentlyContinue
Stop-ScheduledTask -TaskName 'Italock Bandeja' -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName 'Italock Agent' -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName 'Italock Bandeja' -Confirm:$false -ErrorAction SilentlyContinue
Get-Process Italock, italock-agent -ErrorAction SilentlyContinue | Stop-Process -Force
# Restos da versão antiga com "continuidade": adb/scrcpy da pasta do Ítalock (os de outros programas ficam) e o atalho
# "Celular (Ítalock)" do menu Enviar para de cada perfil. A pasta Downloads\Ítalock fica (arquivos da pessoa).
$pastaScrcpy = Join-Path $env:ProgramFiles 'Italock\scrcpy'
Get-Process adb, scrcpy -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$pastaScrcpy\*" } | Stop-Process -Force
$perfis = @(Get-CimInstance Win32_UserProfile -ErrorAction SilentlyContinue | ForEach-Object { $_.LocalPath }) + @(Get-ChildItem 'C:\Users' -Directory -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
foreach ($p in ($perfis | Where-Object { $_ } | Sort-Object -Unique)) {
    Remove-Item -LiteralPath (Join-Path $p 'AppData\Roaming\Microsoft\Windows\SendTo\Celular (Ítalock).lnk') -Force -ErrorAction SilentlyContinue
}

# Módulo da tela de bloqueio
Remove-Item "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\$guid" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "HKLM:\SOFTWARE\Classes\CLSID\$guid" -Recurse -Force -ErrorAction SilentlyContinue
$ut = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\LogonUI\UserTile'
(Get-Item $ut -ErrorAction SilentlyContinue).Property | ForEach-Object {
    if ((Get-ItemProperty $ut).$_ -eq $guid) { Remove-ItemProperty $ut -Name $_ -Force }
}
Remove-Item "$env:WINDIR\System32\italock-cp.dll" -Force -ErrorAction SilentlyContinue
# Versões antigas renomeadas na atualização (a tela de bloqueio segurava a dll). Se ainda estiverem em uso, o Windows
# apaga no próximo reinício.
Get-ChildItem "$env:WINDIR\System32\italock-cp.dll.antigo-*" -ErrorAction SilentlyContinue | ForEach-Object {
    try { Remove-Item $_.FullName -Force -ErrorAction Stop }
    catch {
        Add-Type -Namespace Win32 -Name Mv -MemberDefinition '[DllImport("kernel32.dll", CharSet=CharSet.Unicode)] public static extern bool MoveFileEx(string a, string b, int f);'
        [Win32.Mv]::MoveFileEx($_.FullName, $null, 4) | Out-Null
    }
}

Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'Italock*' -or $_.DisplayName -eq 'LogonUI (Italock)' } | Remove-NetFirewallRule
Remove-Item (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\Ítalock.lnk') -Force -ErrorAction SilentlyContinue

if (-not $ManterPareamentos) {
    $dados = Join-Path $env:ProgramData 'Italock'
    if (Test-Path $dados) {
        icacls $dados /setowner '*S-1-5-32-544' /t /c *> $null
        icacls $dados /reset /t /c *> $null
        Remove-Item $dados -Recurse -Force
    }
}
$destino = Join-Path $env:ProgramFiles 'Italock'
Set-Location $env:TEMP
Remove-Item $destino -Recurse -Force -ErrorAction SilentlyContinue
Write-Host 'Ítalock removido.'
