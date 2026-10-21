; Instalador do Ítalock para Windows (Inno Setup). Sem assinatura de código.
; Compilar: ISCC.exe /DOrigem=<pasta dist\windows> /DSaida=<pasta de saída> italock.iss

[Setup]
AppId={{B3E1F0A2-6C1D-4C7B-9E55-1A7F3C9D2E10}
AppName=Ítalock
AppVersion=1.8.0
AppVerName=Ítalock 1.8.0
AppPublisher=Ítalo
DefaultDirName={autopf}\Italock
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#Saida}
OutputBaseFilename=ItalockWindows
SetupIconFile={#Icone}
UninstallDisplayName=Ítalock
UninstallDisplayIcon={app}\Italock.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
CloseApplications=force

[Languages]
Name: "ptbr"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Files]
Source: "{#Origem}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[InstallDelete]
; Restos da versão com "continuidade" (espelhamento pelo scrcpy), que saiu
Type: filesandordirs; Name: "{app}\scrcpy"

[Run]
; Agente, bandeja, firewall e atalho (o mesmo script da instalação manual)
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\Instalar-Italock.ps1"""; StatusMsg: "Configurando o Ítalock..."; Flags: runhidden waituntilterminated
Filename: "{app}\Italock.exe"; Parameters: "--janela"; Description: "Abrir o Ítalock"; Flags: postinstall nowait skipifsilent


[Code]
{ Desinstalar: pergunta se apaga também os celulares pareados (desinstalação silenciosa mantém). }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var Code: Integer; Params: String;
begin
  if CurUninstallStep = usUninstall then
  begin
    Params := '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\Desinstalar-Italock.ps1') + '"';
    if UninstallSilent() or (MsgBox('Apagar também os celulares pareados e as configurações do Ítalock?' + #13#10#13#10 +
        'Escolha Não para guardar e usar de novo se instalar outra vez.', mbConfirmation, MB_YESNO or MB_DEFBUTTON2) <> IDYES) then
      Params := Params + ' -ManterPareamentos';
    Exec('powershell.exe', Params, '', SW_HIDE, ewWaitUntilTerminated, Code);
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var Code: Integer;
begin
  { Atualização: fecha o app da bandeja e o agente para poder trocar os arquivos }
  { O agente roda como SYSTEM: o Agendador encerra; o taskkill pega o que sobrar }
  Exec(ExpandConstant('{sys}\schtasks.exe'), '/End /TN "Italock Agent"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\schtasks.exe'), '/End /TN "Italock Bandeja"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM Italock.exe /IM italock-agent.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  { adb e scrcpy da pasta do Ítalock (versão antiga com espelhamento); os de outros programas ficam }
  Exec('powershell.exe', '-NoProfile -Command "Get-Process adb,scrcpy -ErrorAction SilentlyContinue | Where-Object { $_.Path -like ''' + ExpandConstant('{app}') + '\scrcpy\*'' } | Stop-Process -Force"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Sleep(1500);
  Result := '';
end;
