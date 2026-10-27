using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using Microsoft.Win32;
using ZXing;
namespace RADVPN;
internal sealed partial class MainWindow
{
    Task<string?> ReadQrImage()
    {
        var file = new OpenFileDialog { Filter = "QR image (*.png;*.jpg;*.jpeg;*.bmp)|*.png;*.jpg;*.jpeg;*.bmp" };
        if (file.ShowDialog() != true)
            return Task.FromResult<string?>(null);
        if (new System.IO.FileInfo(file.FileName).Length > 10 * 1024 * 1024)
            throw new InvalidOperationException("import_too_large");
        using var source = new Bitmap(file.FileName);
        if (source.Width > 4096 || source.Height > 4096)
            throw new InvalidOperationException("import_too_large");
        using var bitmap = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);
        using (var graphics = Graphics.FromImage(bitmap))
            graphics.DrawImage(source, 0, 0);
        var data = bitmap.LockBits(new Rectangle(0, 0, bitmap.Width, bitmap.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        try
        {
            var bytes = new byte[data.Stride * data.Height];
            Marshal.Copy(data.Scan0, bytes, 0, bytes.Length);
            var reader = new BarcodeReaderGeneric();
            reader.Options.TryHarder = true;
            reader.Options.PossibleFormats = new[] { BarcodeFormat.QR_CODE };
            var result = reader.Decode(new RGBLuminanceSource(bytes, bitmap.Width, bitmap.Height, RGBLuminanceSource.BitmapFormat.BGR32));
            return result?.Text is string text ? Task.FromResult<string?>(text) : throw new InvalidOperationException("invalid_profile");
        }
        finally { bitmap.UnlockBits(data); }
    }
}
