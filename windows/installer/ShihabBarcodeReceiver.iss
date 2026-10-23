; Barcode Receiver (Windows) — installer (per user, no administrator rights)
; Built by scripts\build-all.ps1, which publishes the app to releases\_publish and runs:
;   "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DMyVersion=<VERSION> installer\ShihabBarcodeReceiver.iss

#ifndef MyVersion
  #define MyVersion "0.0.0"
#endif
#define Root ".."

[Setup]
AppId={{6B0E2F8A-3C41-4D7B-9E25-1A7C4F0B8D63}
AppName=Barcode Receiver
AppVersion={#MyVersion}
AppVerName=Barcode Receiver {#MyVersion}
AppPublisher=Shihab App
AppPublisherURL=https://shihab.app/
AppSupportURL=https://shihab.app/
VersionInfoVersion={#MyVersion}
; Per user: installs to %LOCALAPPDATA%\Programs, no UAC prompt (the app needs no administrator rights)
PrivilegesRequired=lowest
DefaultDirName={autopf}\Barcode Receiver
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.19041
OutputDir={#Root}\releases
OutputBaseFilename=ShihabBarcodeReceiver-Setup-{#MyVersion}
SetupIconFile={#Root}\src\BarcodeReceiver\Assets\app.ico
UninstallDisplayIcon={app}\ShihabBarcodeReceiver.exe
UninstallDisplayName=Barcode Receiver
LicenseFile={#Root}\..\LICENSE
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; A running copy is closed before its files are replaced (adb is stopped with it)
AppMutex=ShihabBarcodeReceiver.SingleInstance
CloseApplications=yes

[Languages]
Name: "ar"; MessagesFile: "compiler:Languages\Arabic.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#Root}\releases\_publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Barcode Receiver"; Filename: "{app}\ShihabBarcodeReceiver.exe"
Name: "{autodesktop}\Barcode Receiver"; Filename: "{app}\ShihabBarcodeReceiver.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\ShihabBarcodeReceiver.exe"; Description: "{cm:LaunchProgram,Barcode Receiver}"; Flags: nowait postinstall skipifsilent
; In-app update (Shihab.Updater runs this installer with /SILENT /relaunch=1): open the app again.
Filename: "{app}\ShihabBarcodeReceiver.exe"; Flags: nowait; Check: RelaunchRequested

[UninstallRun]
; The bundled adb leaves its server running: stop it so no file stays locked and nothing is left behind.
Filename: "{app}\adb\adb.exe"; Parameters: "kill-server"; Flags: runhidden skipifdoesntexist; RunOnceId: "AdbKill"

[UninstallDelete]
; Nothing left behind: certificate and pairing key, settings, logs
Type: filesandordirs; Name: "{localappdata}\ShihabBarcodeReceiver"

[Registry]
; "Start with Windows" (offered in About) is removed with the app.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "ShihabBarcodeReceiver"; Flags: uninsdeletevalue dontcreatekey

[Code]
function RelaunchRequested(): Boolean;
begin
  Result := ExpandConstant('{param:relaunch|0}') = '1';
end;
