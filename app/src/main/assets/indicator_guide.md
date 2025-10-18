# Complete Guide: Adding Technical Indicators to Your Stock Chart App

This comprehensive guide explains how to add new technical indicators to your stock charting application. The architecture is modular, allowing you to add indicators without modifying core application logic.

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Required Components](#required-components)
3. [Step-by-Step Implementation](#step-by-step-implementation)
4. [Code Examples from SMA](#code-examples-from-sma)
5. [Testing Your Indicator](#testing-your-indicator)
6. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

### Core Components

Your indicator system consists of four main layers:

1. **Indicator Class** - The indicator implementation itself
2. **Database Helper** - Caches calculated values for performance
3. **IndicatorUtil** - Contains calculation logic
4. **Factory & Enum** - Registration and instantiation

### Data Flow

```
User Action → IndicatorManager → IndicatorFactory → Indicator Class
                                                           ↓
                                                    IndicatorUtil
                                                           ↓
                                                    DB Helper (Cache)
                                                           ↓
                                                      CombinedChart
```

---

## Required Components

### 1. Abstract Base Class: `Indicator`

All indicators extend this class located at:
`app/src/main/java/com/example/gutapp/data/chart/Indicator.java`

**Key fields:**
- `id` - Unique identifier for the indicator instance
- `type` - The indicator type (from `Indicators` enum)
- `timeframe` - Current chart timeframe (DAILY, HOURLY, etc.)
- `isOverlay` - Whether indicator draws on the main chart (true) or separate panel (false)
- `color` - Display color
- `symbol` - Stock symbol

**Required methods to implement:**
- `draw(CombinedChart chart)` - Renders the indicator
- `remove(CombinedChart chart)` - Removes the indicator from display
- `changeSettings(float[] params, CombinedChart chart)` - Updates indicator parameters
- `getParams()` - Returns colon-separated parameter string for persistence

---

## Step-by-Step Implementation

### Step 1: Create the Database Helper

Create a new helper class in:
`app/src/main/java/com/example/gutapp/database/indicatorHelpers/`

**Template Based on SMA_DBHelper Pattern:**

```java
package com.example.gutapp.database.indicatorHelpers;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.Table;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

public class YourIndicator_DBHelper implements Table {
    private static final String TABLE_NAME = "your_indicator_data";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_PERIOD = "period"; // Or your parameter name
    public static final String COLUMN_TIMEFRAME = "timeframe";

    private SQLiteDatabase db;

    // Constructor
    public YourIndicator_DBHelper(DB_Helper db_helper) {
        this.db = db_helper.getWritableDatabase();
    }

    // Static insert method - called from IndicatorUtil during calculation
    public static void insertYourIndicator(SQLiteDatabase db, String symbol, 
                                          float date, float value, 
                                          int period, // Your parameters
                                          StockDataHelper.Timeframe timeframe) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYMBOL, symbol);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_VALUE, value);
        values.put(COLUMN_PERIOD, period);
        values.put(COLUMN_TIMEFRAME, timeframe.getValue());
        
        try {
            db.insert(TABLE_NAME, null, values);
            Log.i(DB_Helper.DB_LOG_TAG, "Inserted indicator data for symbol " + symbol);
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting indicator data: " + e.getMessage());
            throw e;
        }
    }

    // Fetch method - returns cached data if available
    public List<Entry> fetchYourIndicator(String symbol, int period, 
                                         StockDataHelper.Timeframe timeframe) {
        String query = "SELECT " + COLUMN_DATE + ", " + COLUMN_VALUE + 
                      " FROM " + TABLE_NAME +
                      " WHERE " + COLUMN_SYMBOL + " = ? AND " + 
                      COLUMN_PERIOD + " = ? AND " + 
                      COLUMN_TIMEFRAME + " = ?";
        String[] args = {symbol, String.valueOf(period), timeframe.getValue()};
        List<Entry> data = new ArrayList<>();

        try (Cursor cursor = db.rawQuery(query, args)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = period - 1; // Starting index after warmup period
                while (!cursor.isAfterLast()) {
                    data.add(new Entry(i++, cursor.getFloat(1)));
                    cursor.moveToNext();
                }
            }
            Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + data.size() + " entries");
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching indicator data: " + e.getMessage(), e);
        }
        return data;
    }

    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SYMBOL + " TEXT NOT NULL, " +
                COLUMN_DATE + " REAL NOT NULL, " +
                COLUMN_VALUE + " REAL NOT NULL, " +
                COLUMN_PERIOD + " INTEGER NOT NULL, " +
                COLUMN_TIMEFRAME + " TEXT NOT NULL" +
                ");";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }
}
```

**Critical Steps:**
1. Implement the `Table` interface with `createTable()` and `getName()`
2. Include `COLUMN_TIMEFRAME` to support multiple timeframes
3. Make the `insert` method **static** and accept `SQLiteDatabase` as parameter
4. Use try-with-resources for cursor management

**Register Your Table:**

After creating your helper, add it to `DB_Helper.java` constructor:

```java
public DB_Helper(@Nullable Context context) {
    super(context, DB_NAME, null, DB_VERSION);
    
    // Existing tables
    StockDataHelper stockDataHelper = new StockDataHelper(context, this);
    tables.add(stockDataHelper);
    UserTableHelper userTableHelper = new UserTableHelper(this);
    tables.add(userTableHelper);
    SymbolsTableHelper symbolsTableHelper = new SymbolsTableHelper(this);
    tables.add(symbolsTableHelper);
    ChartPresetHelper chartPresetHelper = new ChartPresetHelper(this);
    tables.add(chartPresetHelper);
    
    // Add your new table helper
    YourIndicator_DBHelper yourIndicatorHelper = new YourIndicator_DBHelper(this);
    tables.add(yourIndicatorHelper);
}
```

---

### Step 2: Add Calculation Logic to IndicatorUtil

Add a static calculation method to:
`app/src/main/java/com/example/gutapp/data/chart/IndicatorUtil.java`

**Pattern Based on `movingAverageDataSet`:**

```java
public static LineDataSet yourIndicatorDataSet(SQLiteDatabase db, 
                                              List<float[]> prices, 
                                              int period, // Your parameters
                                              String symbol, 
                                              String id, 
                                              StockDataHelper.Timeframe timeframe) {
    List<Entry> entries = new ArrayList<>();
    
    // Validate we have enough data
    if (prices == null || prices.size() < period) {
        return new LineDataSet(entries, id);
    }

    // Use transaction for performance - CRITICAL!
    db.beginTransaction();
    try {
        // Start after warmup period
        for (int i = period - 1; i < prices.size(); i++) {
            
            // YOUR CALCULATION LOGIC HERE
            // Example: Calculate average over period
            float sum = 0f;
            for (int j = i - period + 1; j <= i; j++) {
                sum += prices.get(j)[1]; // prices[j][1] is the close price
            }
            float calculatedValue = sum / period;
            
            // Cache the result
            YourIndicator_DBHelper.insertYourIndicator(
                db, 
                symbol, 
                prices.get(i)[0], // timestamp at index 0
                calculatedValue, 
                period,
                timeframe
            );
            
            // Add to entries list
            entries.add(new Entry(prices.get(i)[0], calculatedValue));
        }
        
        db.setTransactionSuccessful();
    } finally {
        db.endTransaction();
    }

    return new LineDataSet(entries, id);
}
```

**Key Points:**
- `prices.get(i)[0]` = timestamp (x-axis value)
- `prices.get(i)[1]` = close price (for calculations)
- Always wrap database inserts in a transaction
- Start loop at `period - 1` to allow for warmup
- Call your static insert method inside the transaction

---

### Step 3: Create the Indicator Class

Create your indicator in:
`app/src/main/java/com/example/gutapp/data/chart/indicators/`

**Complete Template Based on SMA.java:**

```java
package com.example.gutapp.data.chart.indicators;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.data.chart.Indicator;
import com.example.gutapp.data.chart.IndicatorUtil;
import com.example.gutapp.data.chart.Indicators;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.indicatorHelpers.YourIndicator_DBHelper;
import com.example.gutapp.ui.ChartActivity;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

public class YourIndicator extends Indicator {

    // Your indicator's specific parameters
    private int period;
    private float width;
    private YourIndicator_DBHelper dbHelper;
    private DB_Helper db_helper;

    /**
     * Constructor following the standard pattern
     * @param db_helper Database helper instance
     * @param color Display color (int)
     * @param period Calculation period
     * @param width Line width for display
     * @param id Unique identifier for this instance
     * @param type Indicator type enum
     * @param symbol Stock symbol
     * @param timeframe Current chart timeframe
     */
    public YourIndicator(DB_Helper db_helper, int color, int period, 
                        float width, String id, Indicators type, 
                        String symbol, StockDataHelper.Timeframe timeframe) {
        // Call parent constructor with isOverlay=true for overlay indicators
        super(id, type, timeframe, true, symbol, color);
        this.period = period;
        this.width = width;
        this.db_helper = db_helper;
        this.dbHelper = new YourIndicator_DBHelper(db_helper);
    }

    /**
     * Calculate or fetch cached indicator values
     */
    private LineDataSet calculateIndicator(String symbol, int period, 
                                          StockDataHelper.Timeframe timeframe) {
        // Try to fetch cached data first
        List<Entry> entries = dbHelper.fetchYourIndicator(symbol, period, timeframe);
        
        if (entries.isEmpty()) {
            Log.i(ChartActivity.CHART_LOG_TAG, 
                 "Calculating indicator for " + symbol + " " + timeframe.name());
            
            List<float[]> prices = new ArrayList<>();
            
            // Fetch price data from database
            try (Cursor cursor = ((StockDataHelper) db_helper.getHelper(DB_Index.STOCK_TABLE))
                    .readFromDB(
                        new String[]{"close"},
                        "symbol = ? AND timeframe = ?",
                        new String[]{symbol, timeframe.getValue()},
                        "date ASC",
                        null)) {
                
                int i = 0;
                if (cursor != null && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        float date = i++;
                        float close = cursor.getFloat(0);
                        prices.add(new float[]{date, close});
                        cursor.moveToNext();
                    }
                    Log.i(DB_Helper.DB_LOG_TAG, 
                         "Fetched " + prices.size() + " entries for symbol " + symbol);
                    
                    // Calculate using IndicatorUtil
                    return IndicatorUtil.yourIndicatorDataSet(
                        db_helper.getWritableDatabase(), 
                        prices, 
                        period, 
                        symbol, 
                        getID(), 
                        timeframe
                    );
                }
            } catch (Exception e) {
                Log.e(DB_Helper.DB_LOG_TAG, 
                     "Error fetching stock data for indicator: " + e.getMessage(), e);
                return new LineDataSet(new ArrayList<>(), getID() + "_error");
            }
        }
        
        // Return cached data
        LineDataSet data = new LineDataSet(entries, getID());
        Log.i(ChartActivity.CHART_LOG_TAG, 
             "Returning cached data for " + symbol + " " + timeframe.name() + 
             " size: " + data.getEntryCount());
        return data;
    }

    @Override
    public void draw(CombinedChart combinedChart) {
        // CRITICAL: Always remove old data first to prevent duplicates
        remove(combinedChart);

        // Calculate or fetch indicator data
        LineDataSet indicatorDataSet = calculateIndicator(
            this.symbol, 
            this.period, 
            this.timeframe
        );

        // Configure visual properties
        indicatorDataSet.setColor(this.color);
        indicatorDataSet.setLineWidth(this.width);
        indicatorDataSet.setDrawCircles(false);  // Essential for performance
        indicatorDataSet.setDrawValues(false);   // Don't show value labels
        indicatorDataSet.setHighlightEnabled(false); // Optional: disable highlights

        // Add to chart
        CombinedData combinedData = combinedChart.getData();
        if (combinedData == null) {
            Log.e(ChartActivity.CHART_LOG_TAG, "CombinedData is null. Cannot draw indicator.");
            return;
        }
        
        LineData lineData = combinedData.getLineData();
        if (lineData == null) {
            lineData = new LineData();
        }
        lineData.addDataSet(indicatorDataSet);
        combinedData.setData(lineData);
        combinedChart.setData(combinedData);

        // Refresh the chart
        combinedChart.notifyDataSetChanged();
        combinedChart.invalidate();
    }

    @Override
    public void remove(CombinedChart combinedChart) {
        CombinedData data = combinedChart.getData();
        if (data == null) return;

        LineData lineData = data.getLineData();
        if (lineData == null) return;

        // Find the dataset by its label (which is the indicator's ID)
        ILineDataSet set = lineData.getDataSetByLabel(getID(), false);

        if (set != null) {
            lineData.removeDataSet(set);
            combinedChart.notifyDataSetChanged();
            combinedChart.invalidate();
        }
    }

    @Override
    public void changeSettings(float[] params, CombinedChart combinedChart) {
        // Remove old visualization
        this.remove(combinedChart);
        
        // Update parameters
        this.color = (int) params[0];
        this.period = (int) params[1];
        this.width = params[2];
        
        // Redraw with new settings
        this.draw(combinedChart);
    }

    @Override
    public String getParams() {
        // Return colon-separated string for persistence
        return Integer.toString(this.color) + ":" + 
               Integer.toString(this.period) + ":" + 
               Float.toString(this.width);
    }
}
```

**Implementation Checklist:**
- ✅ Call `super()` with correct `isOverlay` parameter
- ✅ Store DB helper reference
- ✅ Check cache before calculating
- ✅ Use try-with-resources for cursor
- ✅ Call `remove()` at start of `draw()`
- ✅ Set `setDrawCircles(false)` and `setDrawValues(false)`
- ✅ Return colon-separated params string

---

### Step 4: Register in Indicators Enum

Edit: `app/src/main/java/com/example/gutapp/data/chart/Indicators.java`

```java
public enum Indicators {
    SMA,
    YOUR_INDICATOR; // Add your new indicator constant

    public static Indicators fromInt(int i) {
        Indicators[] values = Indicators.values();
        if (i >= 0 && i < values.length) {
            return values[i];
        }
        throw new RuntimeException("Invalid indicator index:");
    }
}
```

**Note:** The enum order matters for persistence! Once deployed, don't reorder existing values.

---

### Step 5: Update IndicatorFactory

Edit: `app/src/main/java/com/example/gutapp/data/chart/IndicatorFactory.java`

```java
public static Indicator createIndicator(Indicators type, String id, 
                                       String symbol, 
                                       StockDataHelper.Timeframe timeframe, 
                                       float[] params, DB_Helper db_helper) {
    switch (type) {
        case SMA:
            return new SMA(db_helper, (int)params[0], (int)params[1], 
                          params[2], id, type, symbol, timeframe);
        
        case YOUR_INDICATOR:
            // Map params array to constructor arguments
            // params[0] = color, params[1] = period, params[2] = width
            return new YourIndicator(db_helper, (int)params[0], (int)params[1], 
                                    params[2], id, type, symbol, timeframe);
    }
    return null;
}
```

**Parameter Mapping:**
Match the params array indices to your constructor parameters. Standard pattern:
- `params[0]` → color (cast to int)
- `params[1]` → primary parameter (period, window, etc.)
- `params[2]` → secondary parameter or width
- Continue as needed for your indicator

---

## Code Examples from SMA

### SMA Database Helper

From `SMA_DBHelper.java`, key patterns:

```java
// Static insert method used during calculation
public static void insertSMA(SQLiteDatabase db, String symbol, 
                            float date, float sma, int smaPeriod, 
                            StockDataHelper.Timeframe timeframe) {
    ContentValues values = new ContentValues();
    values.put(COLUMN_SYMBOL, symbol);
    values.put(COLUMN_DATE, date);
    values.put(COLUMN_SMA, sma);
    values.put(COLUMN_SMA_PERIOD, smaPeriod);
    values.put(COLUMN_TIMEFRAME, timeframe.getValue());
    db.insert(TABLE_NAME, null, values);
}

// Instance fetch method with timeframe filtering
public List<Entry> fetchSMA(String symbol, int smaPeriod, 
                           StockDataHelper.Timeframe timeframe) {
    String query = "SELECT " + COLUMN_DATE + ", " + COLUMN_SMA + 
                  " FROM " + TABLE_NAME +
                  " WHERE " + COLUMN_SYMBOL + " = ? AND " + 
                  COLUMN_SMA_PERIOD + " = ? AND " + 
                  COLUMN_TIMEFRAME + " = ?";
    // ... implementation
}
```

### SMA Calculation in IndicatorUtil

From `IndicatorUtil.java`:

```java
public static LineDataSet movingAverageDataSet(SQLiteDatabase db, 
                                              List<float[]> prices, 
                                              int period, String symbol, 
                                              String id, 
                                              StockDataHelper.Timeframe timeframe) {
    List<Entry> entries = new ArrayList<>();
    if (prices == null || prices.size() < period) 
        return new LineDataSet(entries, symbol);

    db.beginTransaction();
    try {
        for (int i = period - 1; i < prices.size(); i++) {
            float sum = 0f;
            for (int j = i - period + 1; j <= i; j++) {
                sum += prices.get(j)[1]; // Close price
            }
            float avg = sum / period;

            SMA_DBHelper.insertSMA(db, symbol, i, avg, period, timeframe);
            entries.add(new Entry(prices.get(i)[0], avg));
        }
        db.setTransactionSuccessful();
    } finally {
        db.endTransaction();
    }

    return new LineDataSet(entries, id);
}
```

**Key Patterns:**
1. Transaction wrapping for performance
2. Start at `period - 1` index
3. Use `prices.get(j)[1]` for close price
4. Use `prices.get(i)[0]` for timestamp
5. Insert to cache inside loop
6. Return populated LineDataSet

### SMA Indicator Class Structure

From `SMA.java`:

```java
// Constructor pattern
public SMA(DB_Helper db_helper, int color, int period, float width,
          String id, Indicators type, String symbol, 
          StockDataHelper.Timeframe timeframe) {
    super(id, type, timeframe, true, symbol, color);
    this.period = period;
    this.width = width;
    this.db_helper = db_helper;
    this.sma_dbHelper = new SMA_DBHelper(db_helper);
}

// Calculate with cache check
public LineDataSet calculateSMA(String symbol, int period, 
                               StockDataHelper.Timeframe timeframe) {
    List<Entry> entries = sma_dbHelper.fetchSMA(symbol, period, timeframe);
    if (entries.isEmpty()) {
        // Fetch prices and calculate
        // ... calculation logic
    }
    return new LineDataSet(entries, getID());
}

// Draw with remove first
@Override
public void draw(CombinedChart combinedChart) {
    remove(combinedChart);  // CRITICAL!
    LineDataSet indicatorDataSet = calculateSMA(this.symbol, this.period, this.timeframe);
    // ... configure and add to chart
}
```

---

## Testing Your Indicator

### 1. Verify Table Creation

After first run, check that your table exists:
```java
Log.i(DB_Helper.DB_LOG_TAG, "Tables created: " + tables.size());
```

### 2. Test Cache Behavior

**First calculation:**
- Should log "Calculating indicator for [symbol]"
- Should populate database

**Subsequent loads:**
- Should log "Returning cached data"
- Should be significantly faster

### 3. Test Timeframe Changes

Click through all timeframe buttons (5m, 15m, 1h, 1d):
- Indicator should recalculate for each
- Each timeframe should have separate cached data
- No visual glitches or leftover lines

### 4. Test Parameter Changes

Use the settings dialog to modify parameters:
- Old indicator should disappear
- New indicator should appear immediately
- Changed values should persist (check with app restart)

### 5. Test Preset Persistence

1. Add your indicator to the chart
2. Close the app (triggers `onStop()` → `storePresets()`)
3. Reopen the app
4. Navigate to the same symbol
5. Verify indicator loads with same parameters

---

## Troubleshooting

### Problem: Indicator Not Appearing

**Checklist:**
1. ✅ Added enum value to `Indicators.java`?
2. ✅ Added case to `IndicatorFactory.java`?
3. ✅ Constructor calls `super()` with correct parameters?
4. ✅ `isOverlay` parameter set correctly (true/false)?
5. ✅ Calling `remove()` at start of `draw()`?

**Debug Steps:**
```java
Log.i(ChartActivity.CHART_LOG_TAG, "Factory creating: " + type.name());
Log.i(ChartActivity.CHART_LOG_TAG, "Drawing indicator: " + getID());
Log.i(ChartActivity.CHART_LOG_TAG, "Dataset entries: " + dataSet.getEntryCount());
```

### Problem: Duplicate Lines

**Cause:** Not calling `remove()` before `draw()`

**Solution:**
```java
@Override
public void draw(CombinedChart combinedChart) {
    remove(combinedChart); // Must be first!
    // ... rest of draw logic
}
```

### Problem: Data Not Caching

**Check:**
1. Transaction committed? (`db.setTransactionSuccessful()`)
2. Table registered in `DB_Helper` constructor?
3. Using static insert method correctly?
4. Timeframe included in WHERE clause?

**Debug:**
```java
Log.i(DB_Helper.DB_LOG_TAG, "Inserted " + count + " entries");
List<Entry> cached = dbHelper.fetchYourIndicator(...);
Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + cached.size() + " cached entries");
```

### Problem: Wrong Data on Timeframe Change

**Cause:** Not filtering by timeframe in database query

**Solution:** Always include timeframe in WHERE clause:
```java
"WHERE symbol = ? AND period = ? AND timeframe = ?"
new String[]{symbol, String.valueOf(period), timeframe.getValue()}
```

### Problem: App Crashes on Indicator Creation

**Common Causes:**
1. Null pointer - DB helper not initialized
2. Cursor not closed - use try-with-resources
3. Array index out of bounds - verify params array size

**Debug:**
```java
try {
    // Your code
} catch (Exception e) {
    Log.e(ChartActivity.CHART_LOG_TAG, "Error: " + e.getMessage(), e);
    e.printStackTrace();
}
```

### Problem: Preset Not Loading

**Check:**
1. `getParams()` returns correct format (colon-separated)
2. Parameter order matches factory constructor
3. Enum order hasn't changed since last save

**Debug PresetManager:**
```java
Log.i(ChartActivity.CHART_LOG_TAG, "Loading preset params: " + params);
Log.i(ChartActivity.CHART_LOG_TAG, "Parsed params: " + Arrays.toString(paramsArray));
```

---

## Parameter Conventions

### Standard Parameter Order

Follow this pattern consistently:

```java
float[] params = {
    (float) color,           // params[0] - Always first
    (float) primaryParam,    // params[1] - Main calculation parameter
    (float) secondaryParam,  // params[2] - Additional parameter (if needed)
    lineWidth               // params[last] - Visual width
};
```

### Constructor Parameter Order

```java
public YourIndicator(
    DB_Helper db_helper,              // Always first
    int color,                        // Always second
    int primaryParam,                 // Third
    float secondaryParam,             // Additional params...
    float width,                      // Width last field param
    String id,                        // Framework params start
    Indicators type,
    String symbol,
    StockDataHelper.Timeframe timeframe
)
```

### getParams() Format

Must return colon-separated string:
```java
@Override
public String getParams() {
    return color + ":" + primaryParam + ":" + secondaryParam + ":" + width;
}
```

**Important:** Order must match factory's params array mapping!

---

## Performance Best Practices

### 1. Always Use Transactions

```java
db.beginTransaction();
try {
    // Bulk inserts here
    db.setTransactionSuccessful();
} finally {
    db.endTransaction();
}
```

**Why:** Without transactions, each insert is a separate disk write. Transactions batch them, providing 100x+ speedup.

### 2. Check Cache First

```java
List<Entry> cached = dbHelper.fetch(...);
if (cached.isEmpty()) {
    // Only calculate if not cached
}
```

### 3. Use Try-With-Resources

```java
try (Cursor cursor = db.query(...)) {
    // Cursor automatically closed
}
```

### 4. Disable Visual Overhead

```java
dataSet.setDrawCircles(false);
dataSet.setDrawValues(false);
dataSet.setHighlightEnabled(false);
```

### 5. Log Strategically

```java
// Good: Log cache hits/misses
Log.i(TAG, "Cache hit: " + entries.size() + " entries");

// Bad: Don't log in tight loops
for (int i = 0; i < 1000; i++) {
    // Log.i(TAG, "Processing " + i); // ❌ Too much
}
```

---

## Summary Checklist

Before submitting your indicator:

- [ ] Created database helper class implementing `Table`
- [ ] Registered helper in `DB_Helper` constructor
- [ ] Added calculation method to `IndicatorUtil`
- [ ] Used transactions for database inserts
- [ ] Created indicator class extending `Indicator`
- [ ] Implemented all abstract methods correctly
- [ ] Called `remove()` at start of `draw()`
- [ ] Set `setDrawCircles(false)` and `setDrawValues(false)`
- [ ] Added enum value to `Indicators`
- [ ] Added factory case to `IndicatorFactory`
- [ ] Tested with all timeframes (5m, 15m, 1h, 1d)
- [ ] Tested parameter changes work
- [ ] Verified caching works (check logs)
- [ ] Tested preset persistence (restart app)
- [ ] Added appropriate logging for debugging

---

## Additional Resources

### Key Files to Study

1. **`SMA.java`** - Complete working example of an overlay indicator
2. **`SMA_DBHelper.java`** - Database caching pattern
3. **`IndicatorUtil.java`** - Calculation methodology
4. **`IndicatorManager.java`** - How indicators are managed
5. **`PresetManager.java`** - How indicators are persisted

### Architecture Components

- **`Indicator.java`** - Abstract base class
- **`IndicatorFactory.java`** - Instantiation logic
- **`ChartActivity.java`** - UI integration and user interactions
- **`DB_Helper.java`** - Main database coordinator

### Price Data Structure

When fetching prices from the database, understand the array structure:

```java
List<float[]> prices = // fetched from database
// Each float[] has structure:
// [0] = timestamp/index (x-axis)
// [1] = close price (for calculations)
```

### Common Indicator Types

**Overlay Indicators** (`isOverlay = true`):
- Draw on the main price chart
- Examples: SMA, EMA, Bollinger Bands
- Share Y-axis with price data

**Oscillator Indicators** (`isOverlay = false`):
- Draw in separate panel below chart
- Examples: RSI, MACD, Stochastic
- Independent Y-axis scaling

---

## Advanced Topics

### Multi-Line Indicators

Some indicators display multiple lines (e.g., Bollinger Bands with upper/middle/lower bands).

**Pattern:**

```java
public class MultiLineIndicator extends Indicator {
    private String line1Id;
    private String line2Id;
    private String line3Id;
    
    public MultiLineIndicator(...) {
        super(id, type, timeframe, true, symbol, color);
        // Create unique IDs for each line
        this.line1Id = id + "_line1";
        this.line2Id = id + "_line2";
        this.line3Id = id + "_line3";
    }
    
    @Override
    public void draw(CombinedChart chart) {
        remove(chart);
        
        // Calculate all lines
        LineDataSet[] lines = calculateAllLines();
        
        // Configure each line
        for (LineDataSet line : lines) {
            line.setColor(this.color);
            line.setDrawCircles(false);
            line.setDrawValues(false);
        }
        
        // Add all to chart
        CombinedData data = chart.getData();
        LineData lineData = data.getLineData();
        for (LineDataSet line : lines) {
            lineData.addDataSet(line);
        }
        
        chart.notifyDataSetChanged();
        chart.invalidate();
    }
    
    @Override
    public void remove(CombinedChart chart) {
        CombinedData data = chart.getData();
        if (data == null) return;
        
        LineData lineData = data.getLineData();
        if (lineData == null) return;
        
        // Remove all lines
        ILineDataSet set1 = lineData.getDataSetByLabel(line1Id, false);
        ILineDataSet set2 = lineData.getDataSetByLabel(line2Id, false);
        ILineDataSet set3 = lineData.getDataSetByLabel(line3Id, false);
        
        if (set1 != null) lineData.removeDataSet(set1);
        if (set2 != null) lineData.removeDataSet(set2);
        if (set3 != null) lineData.removeDataSet(set3);
        
        chart.notifyDataSetChanged();
        chart.invalidate();
    }
}
```

**Database Strategy:**
- Store a `line_type` column to distinguish between lines
- Query separately for each line type
- Or return array of LineDataSet from calculation method

### Custom Visual Styling

Beyond basic line configuration:

```java
// Dashed lines
dataSet.enableDashedLine(10f, 5f, 0f); // lineLength, spaceLength, phase

// Fill area under line
dataSet.setDrawFilled(true);
dataSet.setFillColor(Color.argb(50, 255, 0, 0)); // Semi-transparent

// Line style
dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Smooth curves
dataSet.setMode(LineDataSet.Mode.STEPPED); // Step chart

// Gradient fill
Drawable drawable = ContextCompat.getDrawable(context, R.drawable.fade_gradient);
dataSet.setFillDrawable(drawable);
```

### Handling Missing Data

Some indicators may have gaps in data:

```java
public static LineDataSet yourIndicatorDataSet(...) {
    List<Entry> entries = new ArrayList<>();
    
    for (int i = period - 1; i < prices.size(); i++) {
        float value = calculateValue(prices, i, period);
        
        // Skip invalid values
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            Log.w(TAG, "Skipping invalid value at index " + i);
            continue;
        }
        
        entries.add(new Entry(prices.get(i)[0], value));
    }
    
    return new LineDataSet(entries, id);
}
```

### Parameter Validation

Add validation in your indicator constructor:

```java
public YourIndicator(DB_Helper db_helper, int color, int period, 
                    float width, String id, Indicators type, 
                    String symbol, StockDataHelper.Timeframe timeframe) {
    super(id, type, timeframe, true, symbol, color);
    
    // Validate parameters
    if (period < 1) {
        throw new IllegalArgumentException("Period must be positive");
    }
    if (width <= 0) {
        throw new IllegalArgumentException("Width must be positive");
    }
    
    this.period = period;
    this.width = width;
    this.db_helper = db_helper;
    this.dbHelper = new YourIndicator_DBHelper(db_helper);
}
```

### Optimizing Calculation Performance

For computationally expensive indicators:

```java
// Use appropriate data types
float sum = 0f; // Not double unless precision required

// Minimize object creation in loops
List<Entry> entries = new ArrayList<>(expectedSize);

// Reuse calculation results
float previousValue = 0f;
for (int i = period - 1; i < prices.size(); i++) {
    // Use previousValue in calculation if possible
    float value = alpha * prices.get(i)[1] + (1 - alpha) * previousValue;
    previousValue = value;
    entries.add(new Entry(prices.get(i)[0], value));
}

// Consider caching intermediate results
Map<Integer, Float> intermediateCache = new HashMap<>();
```

---

## Integration with UI

### Adding Your Indicator to the UI

The indicator appears in `ChartActivity`'s popup menu automatically once registered. Users can:

1. **Add Indicator:** Click "Indicators" button → Select your indicator → Click "Add"
2. **Change Settings:** Select active indicator → Click "Settings"
3. **Remove:** Select active indicator → Click "Remove"

### Default Parameters for UI

When user clicks "Add" without configuring settings:

```java
// In ChartActivity's AvailableIndicatorsAdapter
holder.buttonAction1.setOnClickListener(v -> {
    float[] defaultParams = {
        Color.YELLOW,  // Choose appropriate default color
        20,            // Standard period (adjust for your indicator)
        1f             // Standard line width
    };
    indicatorManager.createIndicator(indicatorType, defaultParams);
});
```

Consider what makes sense as defaults for your indicator type.

### Settings Dialog (Future Enhancement)

Current implementation uses placeholder dialogs. To create a proper settings dialog:

```java
private void showSettingsDialog(Indicators type) {
    View dialogView = LayoutInflater.from(this)
        .inflate(R.layout.dialog_indicator_settings, null);
    
    EditText periodInput = dialogView.findViewById(R.id.periodInput);
    // Add more input fields...
    
    new AlertDialog.Builder(this)
        .setTitle("Settings for " + type.name())
        .setView(dialogView)
        .setPositiveButton("Add", (dialog, which) -> {
            int period = Integer.parseInt(periodInput.getText().toString());
            float[] params = {Color.CYAN, period, 2f};
            indicatorManager.createIndicator(type, params);
        })
        .setNegativeButton("Cancel", null)
        .show();
}
```

---

## Common Indicator Formulas

### Exponential Moving Average (EMA)

```java
public static LineDataSet emaDataSet(SQLiteDatabase db, List<float[]> prices, 
                                    int period, String symbol, String id, 
                                    StockDataHelper.Timeframe timeframe) {
    List<Entry> entries = new ArrayList<>();
    if (prices == null || prices.size() < period) {
        return new LineDataSet(entries, id);
    }
    
    float multiplier = 2.0f / (period + 1);
    
    // Calculate initial SMA
    float sum = 0f;
    for (int i = 0; i < period; i++) {
        sum += prices.get(i)[1];
    }
    float ema = sum / period;
    
    db.beginTransaction();
    try {
        entries.add(new Entry(prices.get(period - 1)[0], ema));
        YourIndicator_DBHelper.insert(db, symbol, prices.get(period - 1)[0], 
                                     ema, period, timeframe);
        
        // Calculate EMA for remaining points
        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i)[1] - ema) * multiplier + ema;
            entries.add(new Entry(prices.get(i)[0], ema));
            YourIndicator_DBHelper.insert(db, symbol, prices.get(i)[0], 
                                         ema, period, timeframe);
        }
        
        db.setTransactionSuccessful();
    } finally {
        db.endTransaction();
    }
    
    return new LineDataSet(entries, id);
}
```

### Relative Strength Index (RSI)

```java
public static LineDataSet rsiDataSet(SQLiteDatabase db, List<float[]> prices, 
                                    int period, String symbol, String id, 
                                    StockDataHelper.Timeframe timeframe) {
    List<Entry> entries = new ArrayList<>();
    if (prices == null || prices.size() < period + 1) {
        return new LineDataSet(entries, id);
    }
    
    db.beginTransaction();
    try {
        // Calculate initial average gain/loss
        float gainSum = 0f, lossSum = 0f;
        for (int i = 1; i <= period; i++) {
            float change = prices.get(i)[1] - prices.get(i - 1)[1];
            if (change > 0) gainSum += change;
            else lossSum += Math.abs(change);
        }
        
        float avgGain = gainSum / period;
        float avgLoss = lossSum / period;
        
        // Calculate RSI for each point
        for (int i = period; i < prices.size(); i++) {
            float change = prices.get(i)[1] - prices.get(i - 1)[1];
            float gain = change > 0 ? change : 0;
            float loss = change < 0 ? Math.abs(change) : 0;
            
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            
            float rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            float rsi = 100 - (100 / (1 + rs));
            
            entries.add(new Entry(prices.get(i)[0], rsi));
            YourIndicator_DBHelper.insert(db, symbol, prices.get(i)[0], 
                                         rsi, period, timeframe);
        }
        
        db.setTransactionSuccessful();
    } finally {
        db.endTransaction();
    }
    
    return new LineDataSet(entries, id);
}
```

**Note:** RSI should have `isOverlay = false` to display in separate panel.

---

## Database Schema Best Practices

### Indexing for Performance

After creating many indicators, add indexes:

```java
@Override
public String createTable() {
    return "CREATE TABLE " + TABLE_NAME + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_SYMBOL + " TEXT NOT NULL, " +
            COLUMN_DATE + " REAL NOT NULL, " +
            COLUMN_VALUE + " REAL NOT NULL, " +
            COLUMN_PERIOD + " INTEGER NOT NULL, " +
            COLUMN_TIMEFRAME + " TEXT NOT NULL" +
            "); " +
            // Add index for common query pattern
            "CREATE INDEX idx_indicator_lookup ON " + TABLE_NAME + 
            " (" + COLUMN_SYMBOL + ", " + COLUMN_PERIOD + ", " + 
            COLUMN_TIMEFRAME + ");";
}
```

### Cache Invalidation

If you need to clear cached data (e.g., after data refresh):

```java
public void clearCache(String symbol, StockDataHelper.Timeframe timeframe) {
    SQLiteDatabase db = this.db;
    String whereClause = COLUMN_SYMBOL + " = ? AND " + COLUMN_TIMEFRAME + " = ?";
    String[] whereArgs = {symbol, timeframe.getValue()};
    
    int deleted = db.delete(TABLE_NAME, whereClause, whereArgs);
    Log.i(DB_Helper.DB_LOG_TAG, "Cleared " + deleted + " cached entries");
}
```

### Migration Strategy

If you need to change your table schema after deployment:

```java
// In DB_Helper.java
private static final int DB_VERSION = 2; // Increment version

@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2) {
        // Add new column to existing table
        db.execSQL("ALTER TABLE your_indicator_data ADD COLUMN new_param REAL DEFAULT 2.0");
    }
}
```

---

## Debugging Tips

### Enable Verbose Logging

Add detailed logs during development:

```java
private static final boolean DEBUG = true;

private void debugLog(String message) {
    if (DEBUG) {
        Log.d(ChartActivity.CHART_LOG_TAG, "[YourIndicator] " + message);
    }
}

// Use throughout your code
debugLog("Fetching prices for " + symbol + " " + timeframe.name());
debugLog("Calculated " + entries.size() + " values");
debugLog("Drawing with color: " + Integer.toHexString(color));
```

### Verify Data Integrity

Add validation after calculations:

```java
for (Entry entry : entries) {
    if (Float.isNaN(entry.getY()) || Float.isInfinite(entry.getY())) {
        Log.e(TAG, "Invalid value at x=" + entry.getX() + ": " + entry.getY());
    }
}
```

### Test Edge Cases

```java
// Test with insufficient data
List<float[]> smallPrices = new ArrayList<>();
for (int i = 0; i < 5; i++) {
    smallPrices.add(new float[]{i, 100f});
}
LineDataSet result = yourIndicatorDataSet(db, smallPrices, 20, ...);
// Should return empty or handle gracefully

// Test with extreme values
List<float[]> extremePrices = new ArrayList<>();
extremePrices.add(new float[]{0, Float.MAX_VALUE});
extremePrices.add(new float[]{1, Float.MIN_VALUE});
// Should not crash
```

### Monitor Memory Usage

For indicators with large datasets:

```java
Runtime runtime = Runtime.getRuntime();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
Log.d(TAG, "Memory usage: " + (usedMemory / 1024 / 1024) + " MB");
```

---

## Final Notes

### Code Style Consistency

Match the existing codebase style:
- Use camelCase for variables and methods
- Use PascalCase for class names
- Use UPPER_CASE for constants
- Follow the parameter order conventions
- Use meaningful variable names (avoid single letters except in loops)

### Documentation

Add JavaDoc comments to your public methods:

```java
/**
 * Calculates the Your Indicator values for the given price data.
 * 
 * @param db Database instance for caching results
 * @param prices List of price data where each entry is [timestamp, close]
 * @param period Number of periods to use in calculation
 * @param symbol Stock symbol being analyzed
 * @param id Unique identifier for this indicator instance
 * @param timeframe Current chart timeframe
 * @return LineDataSet containing calculated indicator values
 */
public static LineDataSet yourIndicatorDataSet(...)
```

### Version Control

When committing your indicator:
1. Create a feature branch
2. Commit database helper, calculation, and indicator class separately
3. Write descriptive commit messages
4. Test thoroughly before merging

### Testing Checklist

Before considering your indicator complete:

- [ ] Works with all timeframes (5m, 15m, 1h, 1d)
- [ ] Caching works correctly
- [ ] No memory leaks (test with multiple adds/removes)
- [ ] Handles insufficient data gracefully
- [ ] Visual appearance is clear and professional
- [ ] Parameters persist correctly
- [ ] No crashes with extreme values
- [ ] Performance is acceptable (< 1 second calculation)
- [ ] Works with multiple instances simultaneously
- [ ] Settings changes apply correctly

---

## Quick Reference

### File Locations
```
app/src/main/java/com/example/gutapp/
├── data/
│   └── chart/
│       ├── Indicator.java (base class)
│       ├── Indicators.java (enum - add here)
│       ├── IndicatorFactory.java (factory - add case here)
│       ├── IndicatorUtil.java (calculations - add method here)
│       └── indicators/
│           └── YourIndicator.java (your class here)
└── database/
    ├── DB_Helper.java (register table here)
    └── indicatorHelpers/
        └── YourIndicator_DBHelper.java (db helper here)
```

### Implementation Order
1. Database Helper → Register in DB_Helper
2. IndicatorUtil method
3. Indicator class
4. Enum value
5. Factory case
6. Test

### Key Methods to Override
```java
draw(CombinedChart)        // Render indicator
remove(CombinedChart)      // Remove from chart
changeSettings(float[])    // Update parameters
getParams()                // Serialize for storage
```

### Common Pitfalls
- ❌ Forgetting to call `remove()` in `draw()`
- ❌ Not using transactions for database inserts
- ❌ Missing timeframe in database queries
- ❌ Not setting `setDrawCircles(false)`
- ❌ Incorrect parameter order in factory
- ❌ Not registering table in DB_Helper

---

## Conclusion

You now have a complete guide to adding technical indicators to your stock chart app. The modular architecture ensures that:

- New indicators don't affect existing code
- Caching provides excellent performance
- Multiple timeframes work automatically
- User settings persist across sessions
- The system scales to many indicators

Follow the SMA implementation as your reference, use this guide for patterns and best practices, and test thoroughly. Happy coding!

For questions or issues, review the troubleshooting section and check your implementation against the SMA example.