using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
namespace RADVPN;
internal static class CountryFlags
{
    static readonly Dictionary<string, System.Windows.Media.Imaging.BitmapSource> images = new();
    static readonly Dictionary<string, string> names = new() { ["NETHERLANDS"] = "NL", ["GERMANY"] = "DE", ["FRANCE"] = "FR", ["UNITED KINGDOM"] = "GB", ["UNITED STATES"] = "US", ["SWEDEN"] = "SE", ["JAPAN"] = "JP", ["CANADA"] = "CA", ["FINLAND"] = "FI", ["TURKEY"] = "TR", ["TÜRKIYE"] = "TR", ["SINGAPORE"] = "SG", ["IRAN"] = "IR", ["RUSSIA"] = "RU" };
    internal static string Code(string name)
    {
        name = DisplayNames.Repair(name);
        var runes = name.EnumerateRunes().ToArray();
        for (int i = 0; i + 1 < runes.Length; i++)
            if (runes[i].Value is >= 0x1F1E6 and <= 0x1F1FF && runes[i + 1].Value is >= 0x1F1E6 and <= 0x1F1FF)
                return new string(new[] { (char)('A' + runes[i].Value - 0x1F1E6), (char)('A' + runes[i + 1].Value - 0x1F1E6) });
        foreach (var entry in names)
            if (name.Contains(entry.Key, StringComparison.OrdinalIgnoreCase))
                return entry.Value;
        return "";
    }
    internal static string WithoutEmoji(string name) => string.Concat(DisplayNames.Repair(name).EnumerateRunes().Where(x => x.Value is < 0x1F1E6 or > 0x1F1FF).Select(x => x.ToString())).Trim();
    internal static FrameworkElement Icon(string name)
    {
        if (images.Count == 0)
        {
            var atlas = JsonSerializer.Deserialize<System.Text.Json.Nodes.JsonObject>(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "flags-atlas.json")))!;
            using var stream = new MemoryStream(Convert.FromBase64String(atlas["png"]!.ToString()));
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            var codes = atlas["codes"]!.AsArray();
            for (int i = 0; i < codes.Count; i++)
            {
                var flag = new CroppedBitmap(image, new Int32Rect((i % 16) * 32, (i / 16) * 22, 32, 22));
                flag.Freeze();
                images[codes[i]!.ToString()] = flag;
            }
        }
        var code = Code(name);
        if (images.TryGetValue(code, out var source))
            return new Image { Source = source, Width = 28, Height = 20, ToolTip = code, VerticalAlignment = VerticalAlignment.Center };
        return Glyphs.Icon("globe", System.Windows.Media.Brushes.Orange, 24);
    }
}
