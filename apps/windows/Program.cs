using System;
using System.Linq;
using System.Threading;
using System.Windows;

namespace RADVPN;
internal static class Program
{
    [STAThread]
    public static void Main()
    {
        var args = Environment.GetCommandLineArgs().Skip(1).ToArray();
        if (args.Contains("--quit"))
        {
            WindowSignals.RequestQuit();
            return;
        }
        if (args.Length >= 2 && args[0] == "--self-test")
        {
            Environment.ExitCode = NativeSmoke.Checks(args[1]).GetAwaiter().GetResult();
            return;
        }
        using var mutex = new Mutex(false, @"Local\RADVPN.Desktop");
        bool acquired;
        try
        {
            acquired = mutex.WaitOne(0);
        }
        catch (AbandonedMutexException) { acquired = true; }
        if (!acquired)
            return;
        try
        {
            var app = new Application();
            app.Run(new MainWindow(args));
        }
        finally { mutex.ReleaseMutex(); }
    }
}

internal static class WindowSignals
{
    internal const int QuitMessage = 0x8050;
    [System.Runtime.InteropServices.DllImport("user32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)] static extern IntPtr FindWindow(string? className, string title);
    [System.Runtime.InteropServices.DllImport("user32.dll")] static extern bool PostMessage(IntPtr handle, int message, IntPtr wParam, IntPtr lParam);
    internal static void RequestQuit()
    {
        var hwnd = FindWindow(null, "RADVPN 2.0.0");
        if (hwnd != IntPtr.Zero)
            PostMessage(hwnd, QuitMessage, IntPtr.Zero, IntPtr.Zero);
    }
}
