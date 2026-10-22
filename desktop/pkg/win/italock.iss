; Instalador do Ítalock para Windows (Inno Setup). Sem assinatura de código.
; Compilar: ISCC.exe /DOrigem=<pasta dist\windows> /DSaida=<pasta de saída> italock.iss

[Setup]
AppId={{B3E1F0A2-6C1D-4C7B-9E55-1A7F3C9D2E10}
AppName=Ítalock
AppVersion=1.9.0
AppVerName=Ítalock 1.9.0
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
; Português do Brasil é o padrão para todo mundo (não segue o idioma do Windows); a pessoa pode escolher outro na
; primeira tela. Numa instalação nova, o idioma escolhido aqui vira o idioma do Ítalock.
LanguageDetectionMethod=none
ShowLanguageDialog=yes

[Languages]
; Os nomes (exceto ptbr) são os códigos de idioma do Ítalock. Hindi e chinês simplificado não têm tradução oficial do
; Inno Setup: o instalador não oferece esses dois (o Ítalock tem os dois nas Configurações).
Name: "ptbr"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"
Name: "de"; MessagesFile: "compiler:Languages\German.isl"
Name: "it"; MessagesFile: "compiler:Languages\Italian.isl"
Name: "ja"; MessagesFile: "compiler:Languages\Japanese.isl"
Name: "ko"; MessagesFile: "compiler:Languages\Korean.isl"
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "pl"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "ar"; MessagesFile: "compiler:Languages\Arabic.isl"

[CustomMessages]
; ITALOCK-MENSAGENS (gerado a partir das traduções do Ítalock)
ptbr.Configurando=Configurando o Ítalock...
ptbr.Abrir=Abrir o Ítalock
ptbr.Apagar=Apagar também os celulares pareados e as configurações do Ítalock?
ptbr.Apagar2=Escolha Não para guardar e usar de novo se instalar outra vez.
en.Configurando=Setting up Ítalock...
en.Abrir=Open Ítalock
en.Apagar=Also delete the paired phones and Ítalock settings?
en.Apagar2=Choose No to keep them and use them again if you reinstall.
es.Configurando=Configurando Ítalock...
es.Abrir=Abrir Ítalock
es.Apagar=¿Borrar también los teléfonos emparejados y la configuración de Ítalock?
es.Apagar2=Elige No para conservarlos y usarlos de nuevo si vuelves a instalar.
fr.Configurando=Configuration d'Ítalock...
fr.Abrir=Ouvrir Ítalock
fr.Apagar=Supprimer aussi les téléphones associés et les paramètres d'Ítalock ?
fr.Apagar2=Choisissez Non pour les conserver et les réutiliser si vous réinstallez.
de.Configurando=Ítalock wird eingerichtet...
de.Abrir=Ítalock öffnen
de.Apagar=Auch die gekoppelten Handys und die Einstellungen von Ítalock löschen?
de.Apagar2=Wählen Sie Nein, um sie zu behalten und bei einer Neuinstallation wieder zu verwenden.
it.Configurando=Configurazione di Ítalock...
it.Abrir=Apri Ítalock
it.Apagar=Eliminare anche i telefoni associati e le impostazioni di Ítalock?
it.Apagar2=Scegli No per conservarli e riusarli se lo reinstalli.
ja.Configurando=Ítalock を設定しています...
ja.Abrir=Ítalock を開く
ja.Apagar=ペアリングしたスマホと Ítalock の設定も削除しますか？
ja.Apagar2=再インストールしたときにまた使えるよう保存しておくには、「いいえ」を選んでください。
ko.Configurando=Ítalock 구성 중...
ko.Abrir=Ítalock 열기
ko.Apagar=페어링된 휴대폰과 Ítalock 설정도 삭제할까요?
ko.Apagar2=다시 설치할 때 다시 사용할 수 있게 보관하려면 '아니요'를 선택하세요.
ru.Configurando=Настройка Ítalock...
ru.Abrir=Открыть Ítalock
ru.Apagar=Удалить также связанные телефоны и настройки Ítalock?
ru.Apagar2=Выберите «Нет», чтобы сохранить их и снова использовать при повторной установке.
pl.Configurando=Konfigurowanie Ítalock...
pl.Abrir=Otwórz Ítalock
pl.Apagar=Usunąć też sparowane telefony i ustawienia Ítalock?
pl.Apagar2=Wybierz Nie, aby je zachować i użyć ponownie po kolejnej instalacji.
ar.Configurando=جارٍ إعداد Ítalock...
ar.Abrir=فتح Ítalock
ar.Apagar=هل تريد أيضًا حذف الهواتف المقترنة وإعدادات Ítalock؟
ar.Apagar2=اختر لا للاحتفاظ بها واستخدامها مجددًا إذا ثبّتّ البرنامج مرة أخرى.
; FIM-ITALOCK-MENSAGENS

[Files]
Source: "{#Origem}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[InstallDelete]
; Restos da versão com "continuidade" (espelhamento pelo scrcpy), que saiu
Type: filesandordirs; Name: "{app}\scrcpy"

[Run]
; Agente, bandeja, firewall e atalho (o mesmo script da instalação manual)
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\Instalar-Italock.ps1"""; StatusMsg: "{cm:Configurando}"; Flags: runhidden waituntilterminated
Filename: "{app}\Italock.exe"; Parameters: "--janela"; Description: "{cm:Abrir}"; Flags: postinstall nowait skipifsilent


[Code]
{ Desinstalar: pergunta se apaga também os celulares pareados (desinstalação silenciosa mantém). }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var Code: Integer; Params: String;
begin
  if CurUninstallStep = usUninstall then
  begin
    Params := '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\Desinstalar-Italock.ps1') + '"';
    if UninstallSilent() or (MsgBox(CustomMessage('Apagar') + #13#10#13#10 +
        CustomMessage('Apagar2'), mbConfirmation, MB_YESNO or MB_DEFBUTTON2) <> IDYES) then
      Params := Params + ' -ManterPareamentos';
    Exec('powershell.exe', Params, '', SW_HIDE, ewWaitUntilTerminated, Code);
  end;
end;

{ Instalação nova num idioma que não é o português do Brasil: o Ítalock já abre nesse idioma (dá para trocar nas
  Configurações). Atualização (configurações já existem) não muda nada. }
procedure CurStepChanged(CurStep: TSetupStep);
var Pasta, Arquivo: String;
begin
  if (CurStep = ssPostInstall) and (ActiveLanguage <> 'ptbr') then
  begin
    Pasta := ExpandConstant('{commonappdata}\Italock');
    Arquivo := Pasta + '\app_settings.json';
    if not FileExists(Arquivo) then
    begin
      ForceDirectories(Pasta);
      SaveStringToFile(Arquivo, '{"idioma":"' + ActiveLanguage + '"}', False);
    end;
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
