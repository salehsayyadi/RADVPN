Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "WinVer.nsh"
!include "LogicLib.nsh"
!include "WordFunc.nsh"
!ifndef REPO_ROOT
!error "Pass absolute REPO_ROOT with the supported build script"
!endif
!define PRODUCT "RADVPN"
!define VERSION "2.0.3"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\RADVPN"
!ifndef APP_DIR
!define APP_DIR "${REPO_ROOT}\dist\windows"
!endif
!ifndef REMOVE_MANIFEST
!define REMOVE_MANIFEST "${REPO_ROOT}\dist\UninstallFiles.nsh"
!endif
Name "RADVPN 2.0.3"
OutFile "${REPO_ROOT}\dist\RADVPN-Setup-2.0.3-x64.exe"
InstallDir "$LOCALAPPDATA\Programs\RADVPN"
InstallDirRegKey HKCU "${UNINSTALL_KEY}" "InstallLocation"
RequestExecutionLevel user
SetCompressor /SOLID lzma
VIProductVersion "2.0.3.0"
VIAddVersionKey /LANG=1033 "ProductName" "RADVPN"
VIAddVersionKey /LANG=1033 "FileVersion" "2.0.3.0"
VIAddVersionKey /LANG=1033 "ProductVersion" "2.0.3"
VIAddVersionKey /LANG=1033 "FileDescription" "RADVPN x64 experimental application setup"
VIAddVersionKey /LANG=1033 "LegalCopyright" "RADVPN contributors"
!define MUI_ICON "${REPO_ROOT}\assets\RADVPN.ico"
!define MUI_UNICON "${REPO_ROOT}\assets\RADVPN.ico"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "RADVPN 2.0.3"
!define MUI_WELCOMEPAGE_TEXT "Install RADVPN for the current Windows user.$\r$\n$\r$\nThis is an unsigned experimental build. No driver is installed and no network settings are changed by setup. Tunnel mode requires a later, explicit administrator Connect action.$\r$\n$\r$\nProfiles are encrypted in the user's LocalAppData folder and are preserved during upgrades and uninstall. Device-wide tunnel safety is not verified."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${REPO_ROOT}\LICENSE"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\RADVPN.exe"
!define MUI_FINISHPAGE_RUN_NOTCHECKED
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Farsi"
LangString CloseApp ${LANG_ENGLISH} "Close RADVPN completely using its tray Quit action before installing or uninstalling."
LangString CloseApp ${LANG_FARSI} "پیش از نصب یا حذف، RADVPN را با گزینه خروج در سینی سیستم کاملاً ببندید."
LangString WrongArch ${LANG_ENGLISH} "RADVPN requires Windows 10/11 x64."
LangString WrongArch ${LANG_FARSI} "RADVPN به ویندوز ۱۰ یا ۱۱ با معماری x64 نیاز دارد."
Function CheckRunning
 System::Call 'kernel32::OpenMutexW(i 0x100000, i 0, w "Local\RADVPN.Desktop") p.r0'
 ${If} $0 != 0
  System::Call 'kernel32::CloseHandle(p r0)'
  MessageBox MB_OK|MB_ICONEXCLAMATION "$(CloseApp)" /SD IDOK
  Abort
 ${EndIf}
FunctionEnd
Function .onInit
 !insertmacro MUI_LANGDLL_DISPLAY
 ${IfNot} ${RunningX64}
  MessageBox MB_OK|MB_ICONSTOP "$(WrongArch)" /SD IDOK
  Abort
 ${EndIf}
 ${IfNot} ${AtLeastWin10}
  MessageBox MB_OK|MB_ICONSTOP "$(WrongArch)" /SD IDOK
  Abort
 ${EndIf}
 SetShellVarContext current
 Call CheckRunning
 ReadRegStr $0 HKCU "${UNINSTALL_KEY}" "DisplayVersion"
 ${If} $0 != ""
  ${VersionCompare} "$0" "${VERSION}" $1
  ${If} $1 == 1
   MessageBox MB_OK|MB_ICONSTOP "A newer RADVPN version is already installed. Downgrade is blocked." /SD IDOK
   Abort
  ${EndIf}
 ${EndIf}
FunctionEnd
Section "RADVPN application (required)" Main
 SectionIn RO
 SetOutPath "$INSTDIR"
 File /r "${APP_DIR}\*.*"
 WriteUninstaller "$INSTDIR\Uninstall.exe"
 CreateDirectory "$SMPROGRAMS\RADVPN"
 CreateShortcut "$SMPROGRAMS\RADVPN\RADVPN.lnk" "$INSTDIR\RADVPN.exe" "" "$INSTDIR\RADVPN.ico"
 CreateShortcut "$SMPROGRAMS\RADVPN\Uninstall RADVPN.lnk" "$INSTDIR\Uninstall.exe"
 WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "RADVPN"
 WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${VERSION}"
 WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "RADVPN contributors"
 WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
 WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\RADVPN.exe"
 WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
 WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" '$\"$INSTDIR\Uninstall.exe$\" /S'
 WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
 WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
SectionEnd
Section /o "Desktop shortcut" Desktop
 CreateShortcut "$DESKTOP\RADVPN.lnk" "$INSTDIR\RADVPN.exe" "" "$INSTDIR\RADVPN.ico"
SectionEnd
Function un.onInit
 SetShellVarContext current
 System::Call 'kernel32::OpenMutexW(i 0x100000, i 0, w "Local\RADVPN.Desktop") p.r0'
 ${If} $0 != 0
  System::Call 'kernel32::CloseHandle(p r0)'
  MessageBox MB_OK|MB_ICONEXCLAMATION "$(CloseApp)" /SD IDOK
  Abort
 ${EndIf}
FunctionEnd
Section "Uninstall"
 !include "${REMOVE_MANIFEST}"
 Delete "$INSTDIR\Uninstall.exe"
 Delete "$SMPROGRAMS\RADVPN\RADVPN.lnk"
 Delete "$SMPROGRAMS\RADVPN\Uninstall RADVPN.lnk"
 RMDir "$SMPROGRAMS\RADVPN"
 Delete "$DESKTOP\RADVPN.lnk"
 ReadRegStr $0 HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RADVPN"
 ${If} $0 == '$\"$INSTDIR\RADVPN.exe$\" --minimized'
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RADVPN"
 ${EndIf}
 DeleteRegKey HKCU "${UNINSTALL_KEY}"
 RMDir "$INSTDIR"
 ; Never remove $LOCALAPPDATA\RADVPN: profiles, encrypted backups and totals survive.
SectionEnd
