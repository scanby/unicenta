; Inno Setup script for the uniCenta oPOS Windows installer.
; Generated payload: build/installer/stage (Ant target: installer.windows)

#define MyAppName "uniCenta oPOS"
#define MyAppVersion "3.82"
#define MyAppPublisher "uniCenta / scanby build"
#define MyAppURL "https://github.com/scanby/unicenta"
#define StageDir "..\build\installerpos\stage"

[Setup]
AppId={{4B1E4F0C-9F2A-4E7B-9D31-6C6A0E9B3A11}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}/releases
DefaultDirName={localappdata}\uniCenta oPOS
DefaultGroupName={#MyAppName}
; No admin rights needed: the POS stores its configuration in user.home.
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\unicentaopos.png
OutputDir=..\build\dist
OutputBaseFilename=unicentaopos_{#MyAppVersion}_windows_setup
Compression=lzma2
SolidCompression=yes
LicenseFile=..\LICENSE.md
ArchitecturesAllowed=x86 x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop icon"; GroupDescription: "Additional icons:"

[Files]
Source: "{#StageDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\uniCenta oPOS"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; IconFilename: "{app}\unicentaopos.png"
Name: "{group}\uniCenta oPOS Configuration"; Filename: "{app}\configure.bat"; WorkingDir: "{app}"
Name: "{autodesktop}\uniCenta oPOS"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\configure.bat"; Description: "Run configuration before first use"; Flags: postinstall skipifsilent
