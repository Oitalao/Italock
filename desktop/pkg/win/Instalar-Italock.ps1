#Requires -RunAsAdministrator
<#
  Instala o Ítalock neste PC:
   - copia os arquivos para C:\Program Files\Italock
   - atalho no Menu Iniciar
   - agente de travar tela: tarefa agendada como SYSTEM na inicialização do PC (lê os pareamentos protegidos e
     trava a sessão ativa do console; funciona igual para usuário comum e administrador)
   - ícone da bandeja: tarefa no logon de qualquer usuário, SEM administrador (a janela pede elevação só ao abrir)
   - regras de firewall para o app e o agente (rede local só em perfil Particular/Domínio; Tailscale pela interface dele)
   - permissões da pasta de dados e dos pareamentos
  O módulo da tela de bloqueio é instalado pelo próprio app (botão "Instalar").
#>
param([string]$Origem = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$destino = Join-Path $env:ProgramFiles 'Italock'

# O agente roda como SYSTEM: quem encerra é o Agendador (Stop-Process de administrador pode não ter permissão)
Stop-ScheduledTask -TaskName 'Italock Agent' -ErrorAction SilentlyContinue
Stop-ScheduledTask -TaskName 'Italock Bandeja' -ErrorAction SilentlyContinue
Get-Process Italock, italock-agent -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
# Restos da versão com "continuidade" (espelhar, enviar arquivos), que saiu: adb/scrcpy que vieram com o Ítalock (os de
# outros programas ficam), a pasta scrcpy, a regra de firewall do adb e o atalho "Enviar para" de cada perfil.
# A pasta Downloads\Ítalock NÃO é apagada (pode ter arquivos da pessoa).
$pastaScrcpy = Join-Path $destino 'scrcpy'
Get-Process adb, scrcpy -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$pastaScrcpy\*" } | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2  # espera os processos soltarem as DLLs
Remove-Item $pastaScrcpy -Recurse -Force -ErrorAction SilentlyContinue
Get-NetFirewallRule -DisplayName 'Italock (adb)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
$perfis = @(Get-CimInstance Win32_UserProfile -ErrorAction SilentlyContinue | ForEach-Object { $_.LocalPath }) + @(Get-ChildItem 'C:\Users' -Directory -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
foreach ($p in ($perfis | Where-Object { $_ } | Sort-Object -Unique)) {
    Remove-Item -LiteralPath (Join-Path $p 'AppData\Roaming\Microsoft\Windows\SendTo\Celular (Ítalock).lnk') -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force $destino | Out-Null
# Pelo instalador (.exe) os arquivos já foram copiados para a pasta final: aí só configura.
if ((Resolve-Path $Origem).Path.TrimEnd('\') -ne (Resolve-Path $destino).Path.TrimEnd('\')) {
    Copy-Item -Path (Join-Path $Origem '*') -Destination $destino -Recurse -Force -Exclude 'Instalar-Italock.ps1','Desinstalar-Italock.ps1'
    Copy-Item (Join-Path $Origem 'Desinstalar-Italock.ps1') $destino -Force
}

$app = Join-Path $destino 'Italock.exe'

# Módulo da tela de bloqueio já instalado: troca pela versão nova. Se a tela de bloqueio estiver com a dll aberta,
# renomeia a antiga (o Windows deixa) e põe a nova no lugar; a antiga é apagada na próxima instalação.
$novoModulo = Join-Path $destino 'italock-cp.dll'
$modulo = Join-Path $env:WINDIR 'System32\italock-cp.dll'
if ((Test-Path $modulo) -and (Test-Path $novoModulo)) {
    Get-ChildItem "$modulo.antigo-*" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    try { Copy-Item $novoModulo $modulo -Force -ErrorAction Stop }
    catch {
        Rename-Item $modulo ("italock-cp.dll.antigo-" + (Get-Date -Format yyyyMMddHHmmss))
        Copy-Item $novoModulo $modulo -Force
    }
}
$agente = Join-Path $destino 'italock-agent.exe'

# Atalho
$lnk = Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\Ítalock.lnk'
$ws = New-Object -ComObject WScript.Shell
$s = $ws.CreateShortcut($lnk); $s.TargetPath = $app; $s.WorkingDirectory = $destino; $s.Save()

# Firewall (entrada) para o app (pareamento/destravar), o agente (travar) e a tela de bloqueio: só a rede local (em
# perfil Particular ou Domínio; rede pública de café/aeroporto fica de fora) e o Tailscale (pela interface dele, em
# qualquer perfil: em alguns PCs o Windows classifica a rede do Tailscale como pública).
$redesPermitidas = @('LocalSubnet', '100.64.0.0/10')
$temTailscale = [bool](Get-NetAdapter -Name 'Tailscale' -ErrorAction SilentlyContinue)
$logonUi = Join-Path $env:WINDIR 'System32\LogonUI.exe'
$regras = @(@{n='Italock (app)'; p=$app}, @{n='Italock (agente)'; p=$agente})
if (Get-NetFirewallRule -DisplayName 'LogonUI (Italock)' -ErrorAction SilentlyContinue) { $regras += @{n='LogonUI (Italock)'; p=$logonUi} }
foreach ($r in $regras) {
    Get-NetFirewallRule -DisplayName $r.n -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Get-NetFirewallRule -DisplayName ($r.n -replace '\)$', ', Tailscale)') -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName $r.n -Direction Inbound -Program $r.p -Action Allow -Profile Private,Domain -RemoteAddress $redesPermitidas | Out-Null
    if ($temTailscale) {
        New-NetFirewallRule -DisplayName ($r.n -replace '\)$', ', Tailscale)') -Direction Inbound -Program $r.p -Action Allow -Profile Any -InterfaceAlias 'Tailscale' -RemoteAddress '100.64.0.0/10' | Out-Null
    }
}
# A regra 'Italock' (criada pelo app em versões antigas) duplicava a 'Italock (app)': sai.
Get-NetFirewallRule -DisplayName 'Italock' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue

# Pasta de dados: SYSTEM e Administradores gravam; usuários comuns só leem (antes podiam criar arquivos nela)
$dados = Join-Path $env:ProgramData 'Italock'
New-Item -ItemType Directory -Force $dados | Out-Null
& {
$ErrorActionPreference = 'Continue'  # icacls/takeown escrevem no stderr; no PowerShell 5.1 isso viraria erro fatal
icacls $dados /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-32-545:(OI)(CI)RX' /c *> $null
# Pareamentos: permissão fixa só para SYSTEM e Administradores (a versão antiga punha e tirava um "negar" para
# Usuários a cada leitura - isso bloqueava até o administrador e podia apagar os pareamentos)
$pares = Join-Path $dados 'paired_devices.json'
if (Test-Path $pares) {
    takeown /F $pares /A *> $null
    icacls $pares /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' *> $null
    icacls $pares /remove '*S-1-5-32-545' *> $null
}
# Registros (IPs, nomes dos celulares, horários de uso): só SYSTEM e Administradores. Os programas refazem isso a cada
# arquivo novo; aqui acerta os que já existem (inclusive os antigos .1.log/.2.log).
Get-ChildItem $dados -Filter '*.log' -File -ErrorAction SilentlyContinue | ForEach-Object {
    icacls $_.FullName /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' *> $null
    icacls $_.FullName /remove '*S-1-5-32-545' *> $null
}
}
# Log da tela de bloqueio: a versão antiga não girava o arquivo (agora 1 MB x 3); zera o que passou de 1 MB
$logModulo = Join-Path $dados 'module.log'
if ((Test-Path $logModulo) -and (Get-Item $logModulo).Length -gt 1MB) {
    try {
        $fs = [IO.FileStream]::new($logModulo, 'Open', 'Write', 'ReadWrite, Delete')
        $fs.SetLength(0); $fs.Write([byte[]](0xEF, 0xBB, 0xBF), 0, 3); $fs.Close()
    } catch { Write-Host "Aviso: não consegui zerar $logModulo ($($_.Exception.Message))." }
}

# Agente: um só para o PC, como SYSTEM, desde a inicialização (antes: um por usuário no logon, com o token do usuário -
# para quem não é administrador ele não lia os pareamentos nem o evento de Destravar).
$acao = New-ScheduledTaskAction -Execute $agente
$gatilho = New-ScheduledTaskTrigger -AtStartup
$principalAgente = New-ScheduledTaskPrincipal -UserId 'S-1-5-18' -LogonType ServiceAccount -RunLevel Highest
$config = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName 'Italock Agent' -Action $acao -Trigger $gatilho -Principal $principalAgente -Settings $config -Force | Out-Null
Start-ScheduledTask -TaskName 'Italock Agent'
# Ícone na bandeja (perto do relógio): sobe escondido no logon de qualquer usuário, SEM administrador. Só a janela pede
# elevação (UAC) quando é aberta. "Parallel": com dois usuários logados cada um tem o seu (IgnoreNew deixava o
# segundo sem ícone).
$principal = New-ScheduledTaskPrincipal -GroupId 'S-1-5-32-545' -RunLevel Limited
$acaoBandeja = New-ScheduledTaskAction -Execute $app -Argument '--bandeja' -WorkingDirectory $destino
$configBandeja = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances Parallel
Register-ScheduledTask -TaskName 'Italock Bandeja' -Action $acaoBandeja -Trigger (New-ScheduledTaskTrigger -AtLogOn) -Principal $principal -Settings $configBandeja -Force | Out-Null
Start-ScheduledTask -TaskName 'Italock Bandeja'

Write-Host "Ítalock instalado em $destino."
Write-Host "Abra o Ítalock pelo Menu Iniciar, ligue a proteção da tela de bloqueio e pareie o celular."
# Módulos antigos renomeados que a tela de bloqueio já soltou
Get-ChildItem "$modulo.antigo-*" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
