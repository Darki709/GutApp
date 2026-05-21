# Adding New Indicators to GutApp

This guide covers every way to add a new indicator — from writing a simple Java class,
to using the GutScript scripting engine, to producing chart drawings from an indicator's output.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Method 1 — Java Built-in Indicator](#method-1--java-built-in-indicator)
   - [Step 1: Create the class](#step-1-create-the-class)
   - [Step 2: Register it](#step-2-register-it)
   - [Step 3: Verify](#step-3-verify)
3. [Method 2 — GutScript (No Java Required)](#method-2--gutscript-no-java-required)
4. [Indicator Types Reference](#indicator-types-reference)
   - [Overlay indicator](#overlay-indicator-lines-on-the-main-chart)
   - [Sub-chart indicator](#sub-chart-indicator-separate-pane-below)
   - [Drawing-producing indicator](#drawing-producing-indicator-overlay--drawings)
5. [Parameters Reference](#parameters-reference)
6. [Result API Reference](#result-api-reference)
7. [Helper Methods](#helper-methods)
8. [Candle Fields](#candle-fields)
9. [Trend Bias API (`calculateBias`)](#trend-bias-api-calculatebias)
10. [Full Examples](#full-examples)
   - [Donchian Channel (overlay, 2 lines)](#example-1-donchian-channel)
   - [Stochastic Oscillator (sub-chart)](#example-2-stochastic-oscillator)
   - [Pivot Points (drawings)](#example-3-pivot-points-with-drawings)
   - [ATR (sub-chart, single line)](#example-4-atr-average-true-range)
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

Use this template and fill in the sections marked with `TODO`:

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

    // ── Identity ──────────────────────────────────────────────────────

    @Override public String  getId()          { return "my_indicator"; }  // TODO: unique snake_case id
    @Override public String  getDisplayName() { return "My Indicator"; }  // TODO: panel display name
    @Override public String  getTag()         { return "MYI"; }           // TODO: short tag on chart
    @Override public boolean isSubChart()     { return false; }           // TODO: true = separate pane

    // REQUIRED: must return a fresh instance with default params
    @Override
    public Indicator newInstance() {
        return new MyIndicator();
    }

    // ── Computation ───────────────────────────────────────────────────

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.size() < 2) return r;

        int period = (int) getParam("period");

        // TODO: compute your values and add them to r.overlayLines or r.subChartLines
        List<Entry> entries = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            // ... your calculation ...
            float value = 0f; // replace with real value
            entries.add(new Entry(i, value));
        }

        r.overlayLines.add(makeLineSet(entries, getTag(), getColor(), 1.4f));
        return r;
    }

    // ── Trend Bias ────────────────────────────────────────────────────
    @Override public int calculateBias(ArrayList<Candle> data) { return 0; } // TODO: Optional override

    // REQUIRED: must return a fresh instance with default params
}
```

### Step 2: Register it

Open `IndicatorRegistry.java`:

```
app/src/main/java/com/example/gutapp/data/indicators/IndicatorRegistry.java
```

Add two things:

**1. The import at the top:**
```java
import com.example.gutapp.data.indicators.impl.MyIndicator;
```

**2. One line inside the constructor:**
```java
private IndicatorRegistry() {
    register(new MaIndicator());
    register(new EmaIndicator());
    register(new BollingerBandsIndicator());
    register(new VwapIndicator());
    register(new RsiIndicator());
    register(new MacdIndicator());
    register(new MyIndicator());   // ← add this line
}
```

That is all. The indicator will now appear in the "Add Indicator" list in the indicator panel
immediately after a clean build.

### Step 3: Verify

1. Build the project (`Ctrl+F9` / `⌘F9`).
2. Open any chart, tap **⊕ Indicators**.
3. Scroll to the bottom of the catalog list — your indicator should appear.
4. Tap **+** to add an instance and verify the line renders on the chart.

---

## Method 2 — GutScript (No Java Required)

GutScript lets you write indicators directly inside the app without recompiling.
Navigate to **Home → My Scripts → + New** and use this format:

```
@name    "My Indicator"
@tag     "MYI"
@subchart false
@color   "#FFC107"

param period int 2 200 14

let closes = series(CLOSE)
let result = sma(closes, period)

plot(result, "My Line", @color, 1.5)
```

See the full language reference in `INTEGRATION_GUIDE.md` (scripting session) or
the in-app snippet bar for common patterns.

Scripts are saved and registered automatically. They appear alongside built-in
indicators in the chart's indicator panel.

---

## Indicator Types Reference

### Overlay indicator (lines on the main chart)

```java
@Override public boolean isSubChart() { return false; }

@Override
public Result compute(ArrayList<Candle> candles) {
    Result r = new Result();
    List<Entry> line = new ArrayList<>();

    // ... calculate values ...
    line.add(new Entry(i, value));

    // Solid line
    r.overlayLines.add(makeLineSet(line, "Label", getColor(), 1.4f));

    // Dashed line (e.g. for bands)
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

    // ... calculate values ...
    values.add(new Entry(i, rsiValue));

    // Pin the Y axis (e.g. RSI is always 0–100)
    r.subChartMin = 0f;
    r.subChartMax = 100f;

    r.subChartLines.add(makeLineSet(values, "RSI", getColor(), 1.4f));
    return r;
}
```

> **Tip:** If you do NOT set `subChartMin` / `subChartMax`, the Y axis auto-scales
> to fit the data. Leave them as `Float.NaN` (the default) for free-range indicators like MACD.

### Drawing-producing indicator (overlay + drawings)

Indicators can emit `ChartDrawing` objects (horizontal levels, trend lines, price zones, etc.)
in addition to or instead of line data. These appear on the `DrawingOverlayView` canvas as
locked, indicator-source drawings.

Requires the `Indicator.java` from the **drawing_v2** session which adds `Result.drawings`.

```java
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;

@Override
public Result compute(ArrayList<Candle> candles) {
    Result r = new Result();

    // Example: mark a horizontal support level
    double supportPrice = 182.50;
    DrawingStyle style = DrawingStyle.dashed(Color.parseColor("#4CAF50"));
    r.drawings.add(new ChartDrawing.HorizontalLine(
        supportPrice, "Support", style, Source.INDICATOR));

    // Example: shade a price range (supply zone)
    DrawingStyle zoneStyle = DrawingStyle.filled(Color.parseColor("#EF5350"), 30);
    r.drawings.add(new ChartDrawing.PriceRange(
        185.0, 183.0,
        0, candles.size() - 1,   // X bounds (start index, end index)
        zoneStyle, Source.INDICATOR));

    return r;
}
```

Available drawing types and their constructors:

| Type | Constructor arguments |
|---|---|
| `HorizontalLine` | `(double price, String label, DrawingStyle, Source)` |
| `TrendLine` | `(int startIdx, double startPrice, int endIdx, double endPrice, DrawingStyle, Source)` |
| `RayLine` | `(int startIdx, double startPrice, int anchorIdx, double anchorPrice, DrawingStyle, Source)` |
| `VerticalLine` | `(int candleIndex, DrawingStyle, Source)` |
| `LinearRegression` | `(int startIdx, int endIdx, DrawingStyle, Source)` |
| `FibRetracement` | `(int startIdx, double highPrice, int endIdx, double lowPrice, DrawingStyle, Source)` |
| `PriceRange` | `(double priceHigh, double priceLow, int startIdx, int endIdx, DrawingStyle, Source)` |

Always pass `Source.INDICATOR` — this makes the drawing locked (not editable or deletable
by user touch) and ensures it is replaced on each `compute()` call.

---

## Parameters Reference

Parameters are declared in the constructor. They appear as sliders in the indicator panel.

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

Reading a parameter in `compute()`:

```java
int    period = (int)   getParam("period");   // INTEGER
float  mult   =         getParam("mult");     // FLOAT
```

**Rules:**
- Key must be unique within the same indicator.
- Key must be a valid Java identifier (no spaces).
- Min must be strictly less than max.
- Default must be within [min, max].

---

## Result API Reference

`Result` is the object you return from `compute()`. It starts empty.

| Field / Method | Type | Purpose |
|---|---|---|
| `r.overlayLines` | `List<LineDataSet>` | Lines drawn on the main price chart |
| `r.subChartLines` | `List<LineDataSet>` | Lines drawn in the sub-chart pane |
| `r.subChartMin` | `float` | Pin the sub-chart Y-axis minimum. `Float.NaN` = auto |
| `r.subChartMax` | `float` | Pin the sub-chart Y-axis maximum. `Float.NaN` = auto |
| `r.drawings` | `List<ChartDrawing>` | Overlay drawings (requires drawing_v2 `Indicator.java`) |

You may populate multiple lists in one `compute()` call. For example, a VWAP indicator could
add one overlay line and several horizontal level drawings simultaneously.

---

## Helper Methods

These are defined on `Indicator` and available directly in `compute()`:

```java
// Solid line — the most common output
makeLineSet(entries, label, color, lineWidthDp)

// Dashed line — good for bands, targets, levels
makeDashedLineSet(entries, label, color)
```

Both return a `LineDataSet` ready to add to `r.overlayLines` or `r.subChartLines`.

For sub-chart lines you need to set the axis dependency explicitly if your indicator
mixes LEFT and RIGHT axis values (e.g. MACD signal vs histogram):

```java
LineDataSet macdLine = makeLineSet(macdEntries, "MACD", getColor(), 1.4f);
macdLine.setAxisDependency(YAxis.AxisDependency.RIGHT);
r.subChartLines.add(macdLine);
```

---

## Candle Fields

Every element of the `ArrayList<Candle>` passed to `compute()` has:

| Field | Type | Description |
|---|---|---|
| `candle.timestamp` | `long` | Unix timestamp in seconds |
| `candle.open` | `double` | Open price |
| `candle.high` | `double` | High price |
| `candle.low` | `double` | Low price |
| `candle.close` | `double` | Close price |
| `candle.volume` | `float` | Volume |

The candle list is **sorted ascending by timestamp** before being passed to `compute()`.
Index `0` is the oldest candle. Index `candles.size()-1` is the most recent.

The `Entry` X coordinate must match the candle's array index:
```java
entries.add(new Entry(i, (float) candles.get(i).close));
//                    ↑ array index, not timestamp
```
## Trend Bias API (`calculateBias`)

The system supports an optional bias assessment engine on indicators to track automated algorithmic positions or trend state values. If your indicator has quantitative directionality (momentum or threshold bounds), override the `calculateBias` method:

```java
@Override
public int calculateBias(ArrayList<Candle> data) {
   // Return +1 for Bullish, -1 for Bearish, or 0 for Neutral
   return 0; 
}
```

### Standard Return Ranges

| Score Range | Sentiment State | Context Example Trigger Mechanics |
| :--- | :--- | :--- |
| **`71` to `100`** | **Strong Bullish Bias** | Price breaking through structural resistance or overbought momentum expansion. |
| **`40` to `60`** | **Neutral / Balanced** | Consolidating in equilibrium ranges, or moving averages compressing. |
| **`0` to `39`** | **Strong Bearish Bias** | Breakdown cascades under major channel bands, high distribution sell volumes. |

### Structural Calculation Rule

Unlike the `compute()` method which runs full mathematical iterations over historical records to map line sequences, `calculateBias()` typically isolates only the calculations leading directly to the **most current data point** (`data.size() - 1`). 

*Always safely verify data count boundaries before extracting indices to prevent out-of-bounds exceptions:*

```java
@Override
public int calculateBias(ArrayList<Candle> data) {
   int period = (int) getParam("period");
   if (data == null || data.size() < period) return 0; // Standard safe fallback

    int lastIdx = data.size() - 1;
    double latestClose = data.get(lastIdx).close;
    
    // ... isolate baseline math structures for index 'lastIdx' ...
    
    if (latestClose > calculationTarget) return 1;
    if (latestClose < calculationTarget) return -1;
    return 0;
}
```


---

## Full Examples

### Example 1: Donchian Channel

Draws the highest high and lowest low over a rolling window. Two overlay lines.

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
            double highest = Double.MIN_VALUE;
            double lowest  = Double.MAX_VALUE;
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
        int midColor = android.graphics.Color.argb(
            120, Color.red(c), Color.green(c), Color.blue(c));

        r.overlayLines.add(makeDashedLineSet(upperEntries, "DC Upper", c));
        r.overlayLines.add(makeDashedLineSet(lowerEntries, "DC Lower", c));
        r.overlayLines.add(makeDashedLineSet(midEntries,   "DC Mid",   midColor));
        return r;
    }
}
```

---

### Example 2: Stochastic Oscillator

Classic %K and %D lines in a sub-chart pane with overbought/oversold level lines.

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
        params.add(new Param("kPeriod", "K Period", Param.Type.INTEGER, 2, 50, 14));
        params.add(new Param("dPeriod", "D Period", Param.Type.INTEGER, 2, 20,  3));
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
        float ob    = getParam("ob");
        float os    = getParam("os");

        if (candles.size() < kPeriod + dPeriod) return r;

        // Calculate %K values
        float[] kValues = new float[candles.size()];
        for (int i = kPeriod - 1; i < candles.size(); i++) {
            double highest = Double.MIN_VALUE;
            double lowest  = Double.MAX_VALUE;
            for (int j = i - kPeriod + 1; j <= i; j++) {
                if (candles.get(j).high > highest) highest = candles.get(j).high;
                if (candles.get(j).low  < lowest)  lowest  = candles.get(j).low;
            }
            double range = highest - lowest;
            kValues[i] = range == 0 ? 50f
                : (float) ((candles.get(i).close - lowest) / range * 100.0);
        }

        // Calculate %D as SMA of %K
        List<Entry> kEntries = new ArrayList<>();
        List<Entry> dEntries = new ArrayList<>();

        int start = kPeriod - 1;
        for (int i = start; i < candles.size(); i++) {
            kEntries.add(new Entry(i, kValues[i]));
        }

        for (int i = start + dPeriod - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - dPeriod + 1; j <= i; j++) sum += kValues[j];
            dEntries.add(new Entry(i, (float)(sum / dPeriod)));
        }

        // Level lines
        List<Entry> obEntries = new ArrayList<>();
        List<Entry> osEntries = new ArrayList<>();
        for (Entry e : kEntries) {
            obEntries.add(new Entry(e.getX(), ob));
            osEntries.add(new Entry(e.getX(), os));
        }

        r.subChartMin = 0f;
        r.subChartMax = 100f;

        LineDataSet kSet = makeLineSet(kEntries, "%K", getColor(), 1.4f);
        LineDataSet dSet = makeLineSet(dEntries, "%D",
            Color.parseColor("#9C27B0"), 1.2f);
        LineDataSet obSet = makeDashedLineSet(obEntries, "OB",
            Color.argb(140, 239, 83, 80));
        LineDataSet osSet = makeDashedLineSet(osEntries, "OS",
            Color.argb(140, 76, 175, 80));

        // Sub-chart uses RIGHT axis
        for (LineDataSet s : new LineDataSet[]{kSet, dSet, obSet, osSet}) {
            s.setAxisDependency(YAxis.AxisDependency.RIGHT);
        }

        r.subChartLines.add(kSet);
        r.subChartLines.add(dSet);
        r.subChartLines.add(obSet);
        r.subChartLines.add(osSet);
        return r;
    }
}
```

---

### Example 3: Pivot Points (with drawings)

Classic daily pivot points rendered as `HorizontalLine` drawings. No line data needed.
Requires the **drawing_v2** `Indicator.java` that adds `Result.drawings`.

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

    public PivotPointsIndicator() {
        // No params — pivot points use the prior candle's HLC
        setColor(Color.parseColor("#ECEFF1"));
    }

    @Override public String  getId()          { return "pivots"; }
    @Override public String  getDisplayName() { return "Pivot Points"; }
    @Override public String  getTag()         { return "PP"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new PivotPointsIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles.size() < 2) return r;

        // Use the previous candle's HLC to compute today's pivots
        Candle prev  = candles.get(candles.size() - 2);
        double pivot = (prev.high + prev.low + prev.close) / 3.0;
        double r1    = 2 * pivot - prev.low;
        double s1    = 2 * pivot - prev.high;
        double r2    = pivot + (prev.high - prev.low);
        double s2    = pivot - (prev.high - prev.low);
        double r3    = prev.high + 2 * (pivot - prev.low);
        double s3    = prev.low  - 2 * (prev.high - pivot);

        // Central pivot — white solid
        r.drawings.add(hline(pivot, "PP",
            DrawingStyle.solid(Color.parseColor("#ECEFF1"), 1.2f)));

        // Resistance levels — red shades
        r.drawings.add(hline(r1, "R1",
            DrawingStyle.dashed(Color.parseColor("#EF9A9A"))));
        r.drawings.add(hline(r2, "R2",
            DrawingStyle.dashed(Color.parseColor("#E57373"))));
        r.drawings.add(hline(r3, "R3",
            DrawingStyle.dashed(Color.parseColor("#F44336"))));

        // Support levels — green shades
        r.drawings.add(hline(s1, "S1",
            DrawingStyle.dashed(Color.parseColor("#A5D6A7"))));
        r.drawings.add(hline(s2, "S2",
            DrawingStyle.dashed(Color.parseColor("#66BB6A"))));
        r.drawings.add(hline(s3, "S3",
            DrawingStyle.dashed(Color.parseColor("#4CAF50"))));

        return r;
    }

    private ChartDrawing.HorizontalLine hline(double price, String label, DrawingStyle style) {
        ChartDrawing.HorizontalLine hl =
            new ChartDrawing.HorizontalLine(price, label, style, Source.INDICATOR);
        hl.extendLeft  = true;
        hl.extendRight = true;
        return hl;
    }
}
```

---

### Example 4: ATR (Average True Range)

Single sub-chart line with no fixed Y range.

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

        // True Range for each candle
        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high - candles.get(0).low;
        for (int i = 1; i < candles.size(); i++) {
            double hl  = candles.get(i).high  - candles.get(i).low;
            double hpc = Math.abs(candles.get(i).high  - candles.get(i - 1).close);
            double lpc = Math.abs(candles.get(i).low   - candles.get(i - 1).close);
            tr[i] = Math.max(hl, Math.max(hpc, lpc));
        }

        List<Entry> entries = new ArrayList<>();

        // Wilder's smoothing (same as the period-RMA used in TradingView)
        double atr = 0;
        for (int i = 0; i < period; i++) atr += tr[i];
        atr /= period;

        for (int i = period; i < candles.size(); i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
            entries.add(new Entry(i, (float) atr));
        }

        // No subChartMin/Max — let the chart auto-scale
        r.subChartLines.add(makeLineSet(entries, "ATR(" + period + ")", getColor(), 1.4f));
        return r;
    }
}
```

---

## Common Mistakes

### 1. X coordinate is a timestamp instead of an array index

```java
// ✗ WRONG — chart X axis is array index, not unix time
entries.add(new Entry((float) candles.get(i).timestamp, value));

// ✓ CORRECT
entries.add(new Entry(i, value));
```

### 2. Returning null instead of an empty Result

```java
// ✗ WRONG — will crash with NullPointerException
if (candles.size() < period) return null;

// ✓ CORRECT
if (candles.size() < period) return new Result();
```

### 3. Not implementing `newInstance()`

```java
// ✗ WRONG — newInstance() that returns `this` shares state between instances
@Override public Indicator newInstance() { return this; }

// ✓ CORRECT — always return a brand-new object
@Override public Indicator newInstance() { return new MyIndicator(); }
```

### 4. Duplicate `getId()` value

Every registered indicator must return a unique string from `getId()`. Reusing
an existing ID silently replaces the built-in indicator in the registry.

Built-in reserved IDs: `"ma"`, `"ema"`, `"bb"`, `"vwap"`, `"rsi"`, `"macd"`

### 5. Adding lines to both `overlayLines` and `subChartLines`

Choose one or the other per indicator. Putting data in both simultaneously is
not supported by the rendering pipeline.

### 6. Forgetting to register in IndicatorRegistry

Creating the class but not calling `register(new MyIndicator())` in
`IndicatorRegistry` means it will never appear in the panel.

### 7. Sub-chart indicator with `isSubChart() = false`

If you add data to `r.subChartLines` but `isSubChart()` returns `false`,
the sub-chart pane will not be created for that indicator and the data will
not be rendered.

---

## Quick Checklist

Use this checklist every time you add a new Java indicator:

- [ ] Created `MyIndicator.java` in `data/indicators/impl/`
- [ ] `getId()` returns a **unique** snake_case string not used by any existing indicator
- [ ] `getDisplayName()` returns a human-readable name (shown in the panel)
- [ ] `getTag()` returns a short 2–5 character tag (shown on the chart)
- [ ] `isSubChart()` matches where the lines are added (`overlayLines` vs `subChartLines`)
- [ ] `newInstance()` returns `new MyIndicator()` (not `this`)
- [ ] `compute()` returns `new Result()` (never `null`) even when there is not enough data
- [ ] All `Entry` objects use **array index** as X, not timestamp
- [ ] All `params` declared in constructor are read with `getParam("key")` in `compute()`
- [ ] Added `import` and `register(new MyIndicator())` in `IndicatorRegistry.java`
- [ ] Project builds without errors
- [ ] Indicator appears in the panel and renders correctly on a chart
- [ ] All `params` declared in constructor are read with `getParam("key")` in `compute()`
- [ ] `calculateBias()` implemented or returning safe `0` fallback default
- [ ] Added `import` and `register(new MyIndicator())` in `IndicatorRegistry.java`
