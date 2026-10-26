$ErrorActionPreference = 'Stop'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class NativeDisplay {
 [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern bool EnumDisplaySettings(string name,int mode,IntPtr data);
 [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int ChangeDisplaySettings(IntPtr data,int flags);
 [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
}
'@
$mode = [Runtime.InteropServices.Marshal]::AllocHGlobal(220)
try {
 for ($i=0; $i -lt 220; $i++) { [Runtime.InteropServices.Marshal]::WriteByte($mode,$i,0) }
 [Runtime.InteropServices.Marshal]::WriteInt16($mode,68,220)
 if (-not [NativeDisplay]::EnumDisplaySettings($null,-1,$mode)) { throw 'Cannot query runner display mode' }
 [Runtime.InteropServices.Marshal]::WriteInt32($mode,72,0x001C0000)
 [Runtime.InteropServices.Marshal]::WriteInt32($mode,168,32)
 [Runtime.InteropServices.Marshal]::WriteInt32($mode,172,1920)
 [Runtime.InteropServices.Marshal]::WriteInt32($mode,176,1080)
 $result = [NativeDisplay]::ChangeDisplaySettings($mode,0)
 Start-Sleep -Seconds 2
 $text = "Runner display setup result=$result; actual=$([NativeDisplay]::GetSystemMetrics(0))x$([NativeDisplay]::GetSystemMetrics(1))"
 Write-Host "::warning::$text"
 New-Item dist -ItemType Directory -Force | Out-Null
 $text | Set-Content dist/windows-display.txt
} finally { [Runtime.InteropServices.Marshal]::FreeHGlobal($mode) }
