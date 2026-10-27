using System;
using System.Linq;
using System.Text;
namespace RADVPN;
internal static class DisplayNames
{
    static DisplayNames() => Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
    static int Score(string text) => text.Count(c => "ÃÂðØÙâ".Contains(c));
    internal static string Repair(string text)
    {
        var current = text;
        for (int pass = 0; pass < 3 && Score(current) > 0; pass++)
        {
            var best = current;
            foreach (var codepage in new[] { 1252, 28591 })
            {
                try
                {
                    var legacy = Encoding.GetEncoding(codepage, EncoderFallback.ExceptionFallback, DecoderFallback.ExceptionFallback);
                    var candidate = new UTF8Encoding(false, true).GetString(legacy.GetBytes(current));
                    if (Score(candidate) < Score(best) && !candidate.Any(c => char.IsControl(c) && c != '\t'))
                        best = candidate;
                }
                catch (ArgumentException) { }
            }
            if (best == current)
                break;
            current = best;
        }
        return current;
    }
}
