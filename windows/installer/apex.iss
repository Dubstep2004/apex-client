; Установщик APEX VPN для Windows (Inno Setup 6).
; Собирается в GitHub Actions:
;   ISCC /DAppVersion=1.3.1 /DSourceDir=<папка APEX VPN> /DOutputName=apex-vpn-3.3.0-windows apex.iss

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\APEX VPN"
#endif
#ifndef OutputName
  #define OutputName "apex-vpn-windows"
#endif

#define AppName "APEX VPN"
#define AppExe "APEX VPN.exe"

[Setup]
; Постоянный AppId: новые версии ставятся поверх старых
AppId={{8C3F5A21-6E4B-4D7A-9B12-5A7E0C4D9F31}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=APEX VPN
AppPublisherURL=https://github.com/Dubstep2004/apex-vpn
AppSupportURL=https://t.me/Apexx_support
AppUpdatesURL=https://github.com/Dubstep2004/apex-vpn/releases/latest
VersionInfoVersion={#AppVersion}
VersionInfoDescription=Установщик APEX VPN
; Для текущего пользователя: права администратора не нужны
PrivilegesRequired=lowest
DefaultDirName={localappdata}\Programs\APEX VPN
DefaultGroupName=APEX VPN
DisableProgramGroupPage=yes
DisableReadyPage=no
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir=..\build\installer
OutputBaseFilename={#OutputName}
SetupIconFile=..\icon.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
; Оформление
WizardStyle=modern
WizardSizePercent=110,110
WizardImageFile=wizard.bmp,wizard150.bmp,wizard200.bmp
WizardSmallImageFile=small.bmp,small150.bmp,small200.bmp
WizardImageStretch=yes
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
CloseApplications=force
RestartApplications=no
ShowLanguageDialog=no

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Messages]
russian.WelcomeLabel1=Добро пожаловать в APEX VPN
russian.WelcomeLabel2=Быстрый и умный VPN для Windows — та же подписка и те же серверы, что в приложении на телефоне.%n%n• Автопилот сам выбирает самый быстрый сервер%n• Умный режим: российские сайты — напрямую%n• Системный прокси или TUN для всего компьютера%n• 9 дизайнов и живой фон%n%nУстановка займёт меньше минуты и не требует прав администратора.
russian.FinishedHeadingLabel=APEX VPN установлен
russian.FinishedLabel=Готово! Откройте APEX VPN, вставьте ссылку подписки https://sub.knownhosts.ru/… и нажмите на щит.%n%nЗначок программы — в трее у часов.
russian.ClickFinish=Нажмите «Завершить», чтобы закрыть установщик.

[CustomMessages]
russian.TaskDesktop=Ярлык на рабочем столе
russian.TaskAutostart=Запускать вместе с Windows (в трее)
russian.TaskGroupExtra=Дополнительно:
russian.LaunchNow=Запустить APEX VPN

[Tasks]
Name: "desktopicon"; Description: "{cm:TaskDesktop}"; GroupDescription: "{cm:TaskGroupExtra}"
Name: "autostart"; Description: "{cm:TaskAutostart}"; GroupDescription: "{cm:TaskGroupExtra}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\APEX VPN"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\APEX VPN"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Registry]
; Тот же ключ, что включает переключатель «Запускать вместе с Windows» в программе
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "APEX VPN"; ValueData: """{app}\{#AppExe}"" --tray"; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchNow}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{sys}\taskkill.exe"; Parameters: "/F /IM ""{#AppExe}"""; Flags: runhidden; RunOnceId: "KillApp"
Filename: "{sys}\taskkill.exe"; Parameters: "/F /IM sing-box.exe"; Flags: runhidden; RunOnceId: "KillSingBox"
Filename: "{sys}\taskkill.exe"; Parameters: "/F /IM xray.exe"; Flags: runhidden; RunOnceId: "KillXray"

[Code]
// Перед установкой и обновлением закрываем APEX VPN и его ядра, иначе файлы заняты.
// Программа при закрытии сама убирает системный прокси; если её «убили», прокси уберёт следующий запуск.
procedure KillApex();
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM "APEX VPN.exe"', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM sing-box.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM xray.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  KillApex();
  Result := '';
end;

// Удаление: системный прокси APEX (если остался) выключаем, чтобы не пропал интернет
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Owned, Cur: String;
begin
  if CurUninstallStep = usUninstall then
  begin
    if RegQueryStringValue(HKCU, 'Software\APEX VPN', 'ProxyOwned', Owned) and
       RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Internet Settings', 'ProxyServer', Cur) and
       (Owned = Cur) then
      RegWriteDWordValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Internet Settings', 'ProxyEnable', 0);
    RegDeleteKeyIncludingSubkeys(HKCU, 'Software\APEX VPN');
    // Автозапуск мог включить и переключатель в программе — убираем в любом случае
    RegDeleteValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'APEX VPN');
  end;
end;
