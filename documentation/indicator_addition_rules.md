# Adding New Indicators to GutApp

This guide covers how to add a new indicator — writing a Java class, registering it,
and producing chart drawings from its output.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Method 1 — Java Built-in Indicator](#method-1--java-built-in-indicator)
   - [Step 1: Create the class](#step-1-create-the-class)
   - [Step 2: Register it](#step-2-register-it)
   - [Step 3: Verify](#step-3-verify)
3. [Indicator Types Reference](#indicator-types-reference)
   - [Overlay indicator](#overlay-indicator-lines-on-the-main-chart)
   - [Sub-chart indicator](#sub-chart-indicator-separate-pane-below)
   - [Drawing-producing indicator](#drawing-producing-indicator-overlay--drawings)
4. [Parameters Reference](#parameters-reference)
5. [Result API Reference](#result-api-reference)
6. [Helper Methods](#helper-methods)
7. [Candle Fields](#candle-fields)
8. [Drawing API Reference](#drawing-api-reference)
   - [Coordinate system](#coordinate-system--timestamps-not-indices)
   - [DrawingStyle](#drawingstyle)
   - [All drawing types](#all-drawing-types)
9. [Trend Bias API](#trend-bias-api-calculatebias)
10. [Full Examples](#full-examples)
    - [Donchian Channel](#example-1-donchian-channel)
    - [Stochastic Oscillator](#example-2-stochastic-oscillator)
    - [Pivot Points (drawings)](#example-3-pivot-points-with-drawings)
    - [ATR](#example-4-atr-average-true-range)
11. [Common Mistakes](#common-mistakes)
12. [Quick Checklist](#quick-checklist)

---

## Architecture Overview

```
IndicatorRegistry          — catalog of TYPE prototypes (one per indicator class)
       │
       └─► IndicatorSession  — live list of INSTANCES active on a chart
                  │
                  └─► Indicator.compute(candles) → Indicator.Result
                                                          │
                                          ┌───────────────┼───────────────┐
                                   overlayLines    subChartLines      drawings
                                  (on main chart)  (separate pane)  (ChartDrawing)
```

- **One class = one type.** Adding a type means creating one `.java` file and one `register()` call.
- **Multiple instances** of the same type are supported out of the box (MA(20) + MA(50) simultaneously).
- Each instance stores its own `color` and `params` — no shared state between instances.
- The `Result` object is created fresh on every `compute()` call. Never cache or mutate it.

---

## Method 1 — Java Built-in Indicator

### Step 1: Create the class

Create a new file in:
```
app/src/main/java/com/example/gutapp/data/indicators/impl/MyIndicator.java
```

Use this template and fill in the sections marked `TODO`:

```java
package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

public class MyIndicator extends Indicator {

    public MyIndicator() {
        // TODO: declare parameters (remove if none needed)
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 14));
        params.add(new Param("mult",   "Mult",   Param.Type.FLOAT,   0.5f, 5f, 2f));

        // TODO: set your indicator's default color
        setColor(Color.parseColor("#FFC107"));
    }

    @Override public String  getId()          { return "my_indicator"; } // TODO: unique snake_case id
    @Override public String  getDisplayName() { return "My Indicator"; } // TODO: panel display name
    @Override public String  getTag()         { return "MYI"; }          // TODO: short tag on chart
    @Override public boolean isSubChart()     { return false; }          // TODO: true = separate pane

    @Override
    public Indicator newInstance() { return new MyIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.size() < 2) return r;

        int period = (int) getParam("period");

        List<Entry> entries = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            float value = 0f; // TODO: replace with real calculation
            entries.add(new Entry(i, value));
        }

        r.overlayLines.add(makeLineSet(entries, getTag(), getColor(), 1.4f));
        return r;
    }

    @Override public int calculateBias(ArrayList<Candle> data) { return 50; }
}
```

### Step 2: Register it

Open `IndicatorRegistry.java`:
```
app/src/main/java/com/example/gutapp/data/indicators/IndicatorRegistry.java
```

Add the import and one line in the constructor:

```java
import com.example.gutapp.data.indicators.impl.MyIndicator;

private IndicatorRegistry() {
    register(new MaIndicator());
    // ... existing indicators ...
    register(new MyIndicator());   // ← add this line
}
```

### Step 3: Verify

1. Build the project (`Ctrl+F9` / `⌘F9`).
2. Open any chart, tap **⊕ Indicators**.
3. Your indicator should appear at the bottom of the catalog list.
4. Tap **+** to add an instance and verify the line renders on the chart.

---

## Indicator Types Reference

### Overlay indicator (lines on the main chart)

```java
@Override public boolean isSubChart() { return false; }

@Override
public Result compute(ArrayList<Candle> candles) {
    Result r = new Result();
    List<Entry> line = new ArrayList<>();
    // ...
    r.overlayLines.add(makeLineSet(line, "Label", getColor(), 1.4f));
    r.overlayLines.add(makeDashedLineSet(line2, "Upper", getColor()));
    return r;
}
```

### Sub-chart indicator (separate pane below)

```java
@Override public boolean isSubChart() { return true; }

@Override
public Result compute(ArrayList<Candle> candles) {
    Result r = new Result();
    List<Entry> values = new ArrayList<>();
    // ...
    r.subChartMin = 0f;    // optional — pin Y axis
    r.subChartMax = 100f;  // leave as Float.NaN for auto-scale
    r.subChartLines.add(makeLineSet(values, "RSI", getColor(), 1.4f));
    return r;
}
```

### Drawing-producing indicator (overlay + drawings)

Indicators can emit `ChartDrawing` objects alongside or instead of line data.
These are rendered on the drawing canvas as **locked, non-editable** overlays
that are replaced automatically on each `compute()` call.

```java
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;

@Override
public Result compute(ArrayList<Candle> candles) {
    Result r = new Result();

    // Horizontal support level
    DrawingStyle style = DrawingStyle.dashed(Color.parseColor("#4CAF50"));
    r.drawings.add(new ChartDrawing.HorizontalLine(182.50, "Support", style, Source.INDICATOR));

    // Shaded supply/demand zone (full chart width — PriceRange has no X bounds)
    DrawingStyle zone = new DrawingStyle(Color.parseColor("#EF5350"), 1f, false);
    zone.filled = true;
    r.drawings.add(new ChartDrawing.PriceRange(185.0, 183.0, zone, Source.INDICATOR));

    return r;
}
```

Always pass `Source.INDICATOR`. This locks the drawing (not editable by user touch)
and ensures it is replaced on each `compute()`.

---

## Parameters Reference

```java
params.add(new Param(
    "period",           // key   — used in getParam("period")
    "Period",           // label — shown in the UI slider
    Param.Type.INTEGER, // type  — INTEGER or FLOAT
    2,                  // min
    200,                // max
    14                  // default value
));
params.add(new Param("mult", "Multiplier", Param.Type.FLOAT, 0.1f, 5.0f, 2.0f));
```

Reading in `compute()`:
```java
int   period = (int) getParam("period");
float mult   =       getParam("mult");
```

**Rules:** key must be unique within the indicator, no spaces, min < max, default within [min, max].

---

## Result API Reference

| Field | Type | Purpose |
|---|---|---|
| `r.overlayLines` | `List<LineDataSet>` | Lines on the main price chart |
| `r.subChartLines` | `List<LineDataSet>` | Lines in the sub-chart pane |
| `r.subChartMin` | `float` | Pin sub-chart Y-axis minimum (`Float.NaN` = auto) |
| `r.subChartMax` | `float` | Pin sub-chart Y-axis maximum (`Float.NaN` = auto) |
| `r.drawings` | `List<ChartDrawing>` | Overlay drawings on the drawing canvas |

---

## Helper Methods

```java
makeLineSet(entries, label, color, lineWidthDp)    // solid line
makeDashedLineSet(entries, label, color)            // dashed line
```

For sub-chart indicators, set the axis dependency explicitly:
```java
LineDataSet set = makeLineSet(entries, "MACD", getColor(), 1.4f);
set.setAxisDependency(YAxis.AxisDependency.RIGHT);
r.subChartLines.add(set);
```

---

## Candle Fields

| Field | Type | Description |
|---|---|---|
| `candle.timestamp` | `long` | Unix timestamp in **seconds** |
| `candle.open` | `double` | Open price |
| `candle.high` | `double` | High price |
| `candle.low` | `double` | Low price |
| `candle.close` | `double` | Close price |
| `candle.volume` | `float` | Volume |

Candles are sorted ascending: index `0` = oldest, `candles.size()-1` = most recent.

`Entry` X must be the **array index**, not the timestamp:
```java
entries.add(new Entry(i, (float) candles.get(i).close)); // ✓ correct
entries.add(new Entry((float) candles.get(i).timestamp, value)); // ✗ wrong
```

---

## Drawing API Reference

### Coordinate system — timestamps, not indices

> **Critical change from earlier versions.**
> All time-position anchors in `ChartDrawing` now use **Unix timestamps in seconds**,
> not candle array indices. This makes drawings timeframe-invariant — a drawing placed
> on the 1D chart appears correctly on 5m, 1H, etc. without any extra work.

To get the timestamp for a specific candle:
```java
long ts = candles.get(i).timestamp;  // seconds since epoch
```

To get the timestamp for the most recent candle:
```java
long latestTs = candles.get(candles.size() - 1).timestamp;
```

To get the timestamp for the first candle:
```java
long firstTs = candles.get(0).timestamp;
```

### DrawingStyle

`DrawingStyle` controls color, stroke width, dash pattern, fill, and opacity.

```java
// Static factory methods (most common)
DrawingStyle.solid(int color)               // solid line, width 1.5dp
DrawingStyle.solid(int color, float width)  // solid line, custom width
DrawingStyle.dashed(int color)              // dashed line, width 1dp

// Full constructor
new DrawingStyle(int color, float strokeWidth, boolean dashed)

// Fields you can set after construction
style.dashOn      = 8f;          // dash length in pixels
style.dashOff     = 4f;          // gap length in pixels
style.filled      = true;        // fill the shape (Rectangle, Ellipse, PriceRange)
style.fillColor   = Color.argb(40, r, g, b); // fill color (auto-set from color if not specified)
style.opacity     = 0.8f;        // 0.0–1.0
```

### All drawing types

Every constructor ends with `(DrawingStyle style, Source source)`.
Always pass `Source.INDICATOR` for indicator-produced drawings.

---

#### `HorizontalLine`
A full-width horizontal price level.
```java
// Price only
new ChartDrawing.HorizontalLine(double price, DrawingStyle style, Source.INDICATOR)

// Price + label shown on Y axis
new ChartDrawing.HorizontalLine(double price, String label, DrawingStyle style, Source.INDICATOR)
```

---

#### `TrendLine`
A line segment between two price/time anchors.
```java
new ChartDrawing.TrendLine(
    long startTs, double startPrice,
    long endTs,   double endPrice,
    DrawingStyle style, Source.INDICATOR)

// Optional extension flags
trendLine.extendLeft  = true;  // extends infinitely to the left
trendLine.extendRight = true;  // extends infinitely to the right
```

---

#### `RayLine`
Starts at an anchor and extends to the right forever.
```java
new ChartDrawing.RayLine(
    long startTs,  double startPrice,   // origin
    long anchorTs, double anchorPrice,  // second point defines direction
    DrawingStyle style, Source.INDICATOR)
```

---

#### `ExtendedLine`
Like a trend line but extends infinitely in both directions.
```java
new ChartDrawing.ExtendedLine(
    long startTs, double startPrice,
    long endTs,   double endPrice,
    DrawingStyle style, Source.INDICATOR)
```

---

#### `VerticalLine`
A full-height vertical line at a specific time.
```java
new ChartDrawing.VerticalLine(long candleTs, DrawingStyle style, Source.INDICATOR)
new ChartDrawing.VerticalLine(long candleTs, String label, DrawingStyle style, Source.INDICATOR)
```

---

#### `LinearRegression`
Best-fit regression line over a time range.
```java
ChartDrawing.LinearRegression lr = new ChartDrawing.LinearRegression(
    long startTs, long endTs,
    DrawingStyle style, Source.INDICATOR);

lr.drawChannel = true;  // optional: also draw ±1σ channel bands
```

---

#### `FibRetracement`
Fibonacci retracement levels between a high and low. Spans only the horizontal
range between `startTs` and `endTs` — does not extend to chart edges.
```java
ChartDrawing.FibRetracement fib = new ChartDrawing.FibRetracement(
    long startTs, double highPrice,
    long endTs,   double lowPrice,
    DrawingStyle style, Source.INDICATOR);

// Optional: override default levels (0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
fib.levels = new float[]{ 0f, 0.25f, 0.5f, 0.75f, 1f };
```

---

#### `PriceRange`
A full-width horizontal shaded band between two prices.
```java
new ChartDrawing.PriceRange(
    double priceHigh, double priceLow,
    DrawingStyle style, Source.INDICATOR)
// Note: PriceRange has no X bounds — it always spans the full chart width.
// Set style.filled = true for a shaded zone (this is done automatically by the constructor).
```

---

#### `Rectangle`
An axis-aligned box between two price/time corners.
```java
new ChartDrawing.Rectangle(
    long startTs, double startPrice,   // one corner
    long endTs,   double endPrice,     // opposite corner
    DrawingStyle style, Source.INDICATOR)
// Set style.filled = true for a filled box.
```

---

#### `Ellipse`
An oval between two price/time corners (bounding box corners).
```java
new ChartDrawing.Ellipse(
    long startTs, double startPrice,
    long endTs,   double endPrice,
    DrawingStyle style, Source.INDICATOR)
```

---

#### `TextAnnotation`
A text bubble placed at a specific price/time point.
```java
new ChartDrawing.TextAnnotation(
    long candleTs, double price,
    String text,
    DrawingStyle style, Source.INDICATOR)
```

---

#### `Arrow`
A directional arrow between two points (arrowhead at the end point).
```java
new ChartDrawing.Arrow(
    long startTs, double startPrice,   // tail
    long endTs,   double endPrice,     // head (arrowhead here)
    DrawingStyle style, Source.INDICATOR)
```

---

#### `ParallelChannel`
Two parallel trend lines with a shaded fill and a dashed midline.
```java
new ChartDrawing.ParallelChannel(
    long startTs, double startPrice,   // main line start
    long endTs,   double endPrice,     // main line end
    double midPrice,                   // sets channel width (price on the parallel line at startTs)
    DrawingStyle style, Source.INDICATOR)
```

---

#### `Pitchfork`
Andrews Pitchfork from three anchor points.
```java
new ChartDrawing.Pitchfork(
    long p0Ts, double p0Price,   // handle pivot
    long p1Ts, double p1Price,   // upper prong
    long p2Ts, double p2Price,   // lower prong
    DrawingStyle style, Source.INDICATOR)
```

---

#### `GannFan`
Nine Gann angle lines radiating from a pivot. The second point defines the 1×1 slope unit.
```java
new ChartDrawing.GannFan(
    long startTs, double startPrice,   // pivot point
    long endTs,   double endPrice,     // reference point (defines 1×1 unit)
    DrawingStyle style, Source.INDICATOR)
```

---

### Getting timestamps from candle indices

For drawings that need to span a known range of candles:

```java
// Convert any candle index to its timestamp
long tsAt(int index, ArrayList<Candle> candles) {
    return candles.get(index).timestamp;
}

// Example: draw a rectangle over the last 20 candles
int n    = candles.size();
long t1  = candles.get(n - 20).timestamp;
long t2  = candles.get(n - 1).timestamp;
double hi = /* highest high over those 20 candles */;
double lo = /* lowest  low  over those 20 candles */;

DrawingStyle style = DrawingStyle.solid(Color.parseColor("#2196F3"), 1f);
style.filled    = true;
style.fillColor = Color.argb(30, 33, 150, 243);
r.drawings.add(new ChartDrawing.Rectangle(t1, hi, t2, lo, style, Source.INDICATOR));
```

---

## Trend Bias API (`calculateBias`)

Override to expose directional state for the bias dashboard.

`calculateBias` returns an integer score from `0` (fully bearish) to `100` (fully bullish),
with `50` representing neutral. Return `50` as the safe default when there is insufficient data.

```java
@Override
public int calculateBias(ArrayList<Candle> data) {
    int period = (int) getParam("period");
    if (data == null || data.size() < period) return 50;

    int lastIdx = data.size() - 1;
    double latestClose = data.get(lastIdx).close;
    // ... calculate target ...
    if (latestClose > target) return 75;   // bullish
    if (latestClose < target) return 25;   // bearish
    return 50;                             // neutral
}
```

| Return | Sentiment |
|---|---|
| `100` | Strongly Bullish |
| `75` | Bullish |
| `50` | Neutral |
| `25` | Bearish |
| `0` | Strongly Bearish |

You may return any integer in the `0–100` range for finer-grained scoring.

---

## Full Examples

### Example 1: Donchian Channel

```java
package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class DonchianChannelIndicator extends Indicator {

    public DonchianChannelIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 20));
        setColor(Color.parseColor("#2196F3"));
    }

    @Override public String  getId()          { return "donchian"; }
    @Override public String  getDisplayName() { return "Donchian Channel"; }
    @Override public String  getTag()         { return "DC"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new DonchianChannelIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles.size() < period) return r;

        List<Entry> upperEntries = new ArrayList<>();
        List<Entry> lowerEntries = new ArrayList<>();
        List<Entry> midEntries   = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            for (int j = i - period + 1; j <= i; j++) {
                if (candles.get(j).high > highest) highest = candles.get(j).high;
                if (candles.get(j).low  < lowest)  lowest  = candles.get(j).low;
            }
            double mid = (highest + lowest) / 2.0;
            upperEntries.add(new Entry(i, (float) highest));
            lowerEntries.add(new Entry(i, (float) lowest));
            midEntries  .add(new Entry(i, (float) mid));
        }

        int c = getColor();
        int midColor = android.graphics.Color.argb(120, Color.red(c), Color.green(c), Color.blue(c));

        r.overlayLines.add(makeDashedLineSet(upperEntries, "DC Upper", c));
        r.overlayLines.add(makeDashedLineSet(lowerEntries, "DC Lower", c));
        r.overlayLines.add(makeDashedLineSet(midEntries,   "DC Mid",   midColor));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int n = data.size();
        double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
        for (int i = n - period; i < n; i++) {
            if (data.get(i).high > highest) highest = data.get(i).high;
            if (data.get(i).low  < lowest)  lowest  = data.get(i).low;
        }
        double mid   = (highest + lowest) / 2.0;
        double close = data.get(n - 1).close;
        if (close > mid) return 75;
        if (close < mid) return 25;
        return 50;
    }
}
```

---

### Example 2: Stochastic Oscillator

```java
package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class StochasticIndicator extends Indicator {

    public StochasticIndicator() {
        params.add(new Param("kPeriod", "K Period",   Param.Type.INTEGER, 2, 50, 14));
        params.add(new Param("dPeriod", "D Period",   Param.Type.INTEGER, 2, 20,  3));
        params.add(new Param("ob",      "Overbought", Param.Type.INTEGER, 50, 95, 80));
        params.add(new Param("os",      "Oversold",   Param.Type.INTEGER,  5, 50, 20));
        setColor(Color.parseColor("#FF9800"));
    }

    @Override public String  getId()          { return "stoch"; }
    @Override public String  getDisplayName() { return "Stochastic"; }
    @Override public String  getTag()         { return "STOCH"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new StochasticIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int kPeriod = (int) getParam("kPeriod");
        int dPeriod = (int) getParam("dPeriod");
        float ob = getParam("ob"), os = getParam("os");

        if (candles.size() < kPeriod + dPeriod) return r;

        float[] kValues = new float[candles.size()];
        for (int i = kPeriod - 1; i < candles.size(); i++) {
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            for (int j = i - kPeriod + 1; j <= i; j++) {
                if (candles.get(j).high > highest) highest = candles.get(j).high;
                if (candles.get(j).low  < lowest)  lowest  = candles.get(j).low;
            }
            double range = highest - lowest;
            kValues[i] = range == 0 ? 50f
                : (float) ((candles.get(i).close - lowest) / range * 100.0);
        }

        List<Entry> kEntries = new ArrayList<>();
        List<Entry> dEntries = new ArrayList<>();
        int start = kPeriod - 1;

        for (int i = start; i < candles.size(); i++)
            kEntries.add(new Entry(i, kValues[i]));

        for (int i = start + dPeriod - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - dPeriod + 1; j <= i; j++) sum += kValues[j];
            dEntries.add(new Entry(i, (float)(sum / dPeriod)));
        }

        List<Entry> obEntries = new ArrayList<>(), osEntries = new ArrayList<>();
        for (Entry e : kEntries) {
            obEntries.add(new Entry(e.getX(), ob));
            osEntries.add(new Entry(e.getX(), os));
        }

        r.subChartMin = 0f;
        r.subChartMax = 100f;

        LineDataSet kSet  = makeLineSet(kEntries, "%K", getColor(), 1.4f);
        LineDataSet dSet  = makeLineSet(dEntries, "%D", Color.parseColor("#9C27B0"), 1.2f);
        LineDataSet obSet = makeDashedLineSet(obEntries, "OB", Color.argb(140, 239, 83, 80));
        LineDataSet osSet = makeDashedLineSet(osEntries, "OS", Color.argb(140, 76, 175, 80));

        for (LineDataSet s : new LineDataSet[]{kSet, dSet, obSet, osSet})
            s.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(kSet);
        r.subChartLines.add(dSet);
        r.subChartLines.add(obSet);
        r.subChartLines.add(osSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int kPeriod = (int) getParam("kPeriod");
        int dPeriod = (int) getParam("dPeriod");
        if (data == null || data.size() < kPeriod + dPeriod) return 50;

        // Re-compute latest %K
        int i = data.size() - 1;
        double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
        for (int j = i - kPeriod + 1; j <= i; j++) {
            if (data.get(j).high > highest) highest = data.get(j).high;
            if (data.get(j).low  < lowest)  lowest  = data.get(j).low;
        }
        double range = highest - lowest;
        float k = range == 0 ? 50f : (float) ((data.get(i).close - lowest) / range * 100.0);

        // Map %K (0–100) directly to bias score
        return Math.round(k);
    }
}
```

---

### Example 3: Pivot Points (with drawings)

Demonstrates the updated drawing API using timestamps instead of indices.

```java
package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import java.util.ArrayList;

public class PivotPointsIndicator extends Indicator {

    public PivotPointsIndicator() { setColor(Color.parseColor("#ECEFF1")); }

    @Override public String  getId()          { return "pivots"; }
    @Override public String  getDisplayName() { return "Pivot Points"; }
    @Override public String  getTag()         { return "PP"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new PivotPointsIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles.size() < 2) return r;

        Candle prev  = candles.get(candles.size() - 2);
        double pivot = (prev.high + prev.low + prev.close) / 3.0;
        double r1    = 2 * pivot - prev.low;
        double s1    = 2 * pivot - prev.high;
        double r2    = pivot + (prev.high - prev.low);
        double s2    = pivot - (prev.high - prev.low);
        double r3    = prev.high + 2 * (pivot - prev.low);
        double s3    = prev.low  - 2 * (prev.high - pivot);

        // HorizontalLine takes (price, label, style, source) — no timestamps needed
        r.drawings.add(hline(pivot, "PP", DrawingStyle.solid(Color.parseColor("#ECEFF1"), 1.2f)));
        r.drawings.add(hline(r1, "R1", DrawingStyle.dashed(Color.parseColor("#EF9A9A"))));
        r.drawings.add(hline(r2, "R2", DrawingStyle.dashed(Color.parseColor("#E57373"))));
        r.drawings.add(hline(r3, "R3", DrawingStyle.dashed(Color.parseColor("#F44336"))));
        r.drawings.add(hline(s1, "S1", DrawingStyle.dashed(Color.parseColor("#A5D6A7"))));
        r.drawings.add(hline(s2, "S2", DrawingStyle.dashed(Color.parseColor("#66BB6A"))));
        r.drawings.add(hline(s3, "S3", DrawingStyle.dashed(Color.parseColor("#4CAF50"))));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        Candle prev  = data.get(data.size() - 2);
        double pivot = (prev.high + prev.low + prev.close) / 3.0;
        double close = data.get(data.size() - 1).close;
        if (close > pivot) return 75;
        if (close < pivot) return 25;
        return 50;
    }

    private ChartDrawing.HorizontalLine hline(double price, String label, DrawingStyle style) {
        return new ChartDrawing.HorizontalLine(price, label, style, Source.INDICATOR);
    }
}
```

---

### Example 4: ATR (Average True Range)

```java
package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class AtrIndicator extends Indicator {

    public AtrIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#AB47BC"));
    }

    @Override public String  getId()          { return "atr"; }
    @Override public String  getDisplayName() { return "ATR"; }
    @Override public String  getTag()         { return "ATR"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new AtrIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles.size() < period + 1) return r;

        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high - candles.get(0).low;
        for (int i = 1; i < candles.size(); i++) {
            double hl  = candles.get(i).high  - candles.get(i).low;
            double hpc = Math.abs(candles.get(i).high  - candles.get(i - 1).close);
            double lpc = Math.abs(candles.get(i).low   - candles.get(i - 1).close);
            tr[i] = Math.max(hl, Math.max(hpc, lpc));
        }

        List<Entry> entries = new ArrayList<>();
        double atr = 0;
        for (int i = 0; i < period; i++) atr += tr[i];
        atr /= period;

        for (int i = period; i < candles.size(); i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
            entries.add(new Entry(i, (float) atr));
        }

        // No subChartMin/Max — auto-scale
        r.subChartLines.add(makeLineSet(entries, "ATR(" + period + ")", getColor(), 1.4f));
        return r;
    }

    // ATR measures volatility, not direction — return neutral
    @Override public int calculateBias(ArrayList<Candle> data) { return 50; }
}
```

---

## Common Mistakes

**1. Using timestamps as Entry X coordinates**
```java
entries.add(new Entry((float) candles.get(i).timestamp, value)); // ✗ wrong
entries.add(new Entry(i, value));                                 // ✓ correct
```

**2. Using candle indices as drawing timestamps**
```java
// ✗ wrong — index 42 is not a valid timestamp
new ChartDrawing.TrendLine(42, startPrice, 100, endPrice, style, Source.INDICATOR)

// ✓ correct — use actual Unix timestamps from the candle
long t1 = candles.get(42).timestamp;
long t2 = candles.get(100).timestamp;
new ChartDrawing.TrendLine(t1, startPrice, t2, endPrice, style, Source.INDICATOR)
```

**3. Returning null instead of empty Result**
```java
if (candles.size() < period) return null;        // ✗ crashes
if (candles.size() < period) return new Result(); // ✓ correct
```

**4. `newInstance()` returning `this`**
```java
@Override public Indicator newInstance() { return this; }           // ✗ shares state
@Override public Indicator newInstance() { return new MyIndicator(); } // ✓ correct
```

**5. Duplicate `getId()` value**
Built-in reserved IDs: `"ma"`, `"ema"`, `"bb"`, `"vwap"`, `"rsi"`, `"macd"`

**6. Forgetting to register in `IndicatorRegistry`**

**7. `isSubChart()` not matching where data is added**
If you add to `r.subChartLines` but `isSubChart()` returns `false`, nothing renders.

**8. Returning `0` from `calculateBias()` instead of `50` for neutral/insufficient data**
```java
@Override public int calculateBias(ArrayList<Candle> data) { return 0; }  // ✗ reads as strongly bearish
@Override public int calculateBias(ArrayList<Candle> data) { return 50; } // ✓ correct neutral default
```

---

## Quick Checklist

- [ ] `getId()` returns a **unique** snake_case string
- [ ] `getDisplayName()` returns a human-readable name
- [ ] `getTag()` is 2–5 characters
- [ ] `isSubChart()` matches `overlayLines` vs `subChartLines`
- [ ] `newInstance()` returns `new MyIndicator()` (not `this`)
- [ ] `compute()` returns `new Result()` (never `null`) when data is insufficient
- [ ] All `Entry` objects use **array index** as X, not timestamp
- [ ] All drawing anchors use **Unix timestamps** from `candle.timestamp`, not array indices
- [ ] `params` declared in constructor are read with `getParam("key")` in `compute()`
- [ ] `calculateBias()` returns a value in the `0–100` range (`50` = neutral default)
- [ ] Import added and `register(new MyIndicator())` called in `IndicatorRegistry.java`
- [ ] Project builds without errors
- [ ] Indicator appears in panel and renders correctly on chart
