using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace RADVPN;
// Consistent 24-unit line icon vocabulary, original paths inspired by common UI geometry.
internal static class Glyphs
{
    static readonly Dictionary<string, string> Paths = new()
    {
        ["home"] = "M3,10 L12,3 L21,10 M5,9 V21 H10 V15 H14 V21 H19 V9",
        ["servers"] = "M4,4 H20 V10 H4 Z M4,14 H20 V20 H4 Z M7,7 H8 M7,17 H8 M16,7 H18 M16,17 H18",
        ["subscriptions"] = "M5,3 H16 L20,7 V21 H5 Z M15,3 V8 H20 M8,12 H16 M8,16 H14",
        ["configs"] = "M5,3 H15 L20,8 V21 H5 Z M14,3 V9 H20 M8,13 L6,15 L8,17 M15,13 L17,15 L15,17",
        ["profiles"] = "M8,7 A4,4 0 1 0 16,7 A4,4 0 1 0 8,7 M4,21 V18 C4,12 20,12 20,18 V21",
        ["traffic"] = "M3,21 H22 M6,17 V12 M11,17 V5 M16,17 V9 M21,17 V2",
        ["settings"] = "M9,3 H15 L16,6 L19,7 L22,12 L19,17 L16,18 L15,21 H9 L8,18 L5,17 L2,12 L5,7 L8,6 Z M8,12 A4,4 0 1 0 16,12 A4,4 0 1 0 8,12",
        ["logs"] = "M6,3 H18 V21 H6 Z M9,7 H15 M9,11 H15 M9,15 H13",
        ["about"] = "M2,12 A10,10 0 1 0 22,12 A10,10 0 1 0 2,12 M12,10 V17 M12,6 V7",
        ["power"] = "M12,2 V12 M6,5 A9,9 0 1 0 18,5",
        ["download"] = "M12,3 V17 M6,11 L12,17 L18,11 M4,21 H20",
        ["upload"] = "M12,18 V4 M6,10 L12,4 L18,10 M4,21 H20",
        ["search"] = "M3,10 A7,7 0 1 0 17,10 A7,7 0 1 0 3,10 M15,15 L22,22",
        ["plus"] = "M12,4 V20 M4,12 H20",
        ["star"] = "M12,2 L15,9 L22,9 L17,14 L19,22 L12,17 L5,22 L7,14 L2,9 L9,9 Z",
        ["check"] = "M4,12 L9,17 L20,6",
        ["arrow"] = "M5,12 H20 M14,6 L20,12 L14,18",
        ["refresh"] = "M20,9 A8,8 0 1 0 20,16 M20,3 V9 H14",
        ["stop"] = "M5,5 H19 V19 H5 Z",
        ["globe"] = "M2,12 A10,10 0 1 0 22,12 A10,10 0 1 0 2,12 M2,12 H22 M12,2 C5,8 5,16 12,22 C19,16 19,8 12,2",
        ["clock"] = "M2,12 A10,10 0 1 0 22,12 A10,10 0 1 0 2,12 M12,6 V12 L16,15",
        ["copy"] = "M9,8 H21 V21 H9 Z M15,8 V3 H3 V16 H9",
        ["delete"] = "M4,6 H20 M9,6 V3 H15 V6 M6,6 L7,21 H17 L18,6 M10,10 V17 M14,10 V17",
        ["shield"] = "M12,2 L21,6 V12 C21,17 16,21 12,23 C8,21 3,17 3,12 V6 Z M14,6 L9,13 H13 L10,19 L17,10 H12 Z",
    };
    public static FrameworkElement Icon(string name, Brush brush, double size = 20)
    {
        var path = new Path { Data = Geometry.Parse(Paths.GetValueOrDefault(name, Paths["globe"])), Stroke = brush, StrokeThickness = 1.65, StrokeLineJoin = PenLineJoin.Round, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
        return new Viewbox { Width = size, Height = size, Child = new Canvas { Width = 24, Height = 24, Children = { path } }, FlowDirection = FlowDirection.LeftToRight };
    }
}
internal sealed class TrafficGraph : FrameworkElement
{
    public List<(double Up, double Down)> Samples { get; } = new();
    public Brush UpBrush { get; set; } = Brushes.Orange; public Brush DownBrush { get; set; } = Brushes.DodgerBlue; public Brush GridBrush { get; set; } = Brushes.DimGray;
    public void Add(double up, double down) { Samples.Add((up, down)); if (Samples.Count > 90) Samples.RemoveAt(0); InvalidateVisual(); }
    protected override void OnRender(DrawingContext dc)
    {
        base.OnRender(dc); var w = ActualWidth; var h = ActualHeight; if (w < 2 || h < 2) return;
        for (int i = 1; i < 4; i++) dc.DrawLine(new Pen(GridBrush, .6), new Point(0, h * i / 4), new Point(w, h * i / 4));
        if (Samples.Count < 2) return; var max = Math.Max(1, Samples.Max(x => Math.Max(x.Up, x.Down))) * 1.15;
        Draw(dc, Samples.Select(x => x.Down).ToArray(), DownBrush, w, h, max); Draw(dc, Samples.Select(x => x.Up).ToArray(), UpBrush, w, h, max);
    }
    static void Draw(DrawingContext dc, double[] values, Brush brush, double w, double h, double max) { var g = new StreamGeometry(); using (var c = g.Open()) { c.BeginFigure(new Point(0, h - values[0] / max * h), false, false); for (int i = 1; i < values.Length; i++) c.LineTo(new Point(i * w / (values.Length - 1), h - values[i] / max * h), true, false); } g.Freeze(); dc.DrawGeometry(null, new Pen(brush, 1.8), g); }
}
