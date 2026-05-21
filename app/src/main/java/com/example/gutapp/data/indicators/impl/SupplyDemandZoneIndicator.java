package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import java.util.ArrayList;

public class SupplyDemandZoneIndicator extends Indicator {

    public SupplyDemandZoneIndicator() {
        // Parameter 1: Sensitivity multiplier for detecting the structural imbalance
        params.add(new Param("threshold", "Threshold (x10)", Param.Type.FLOAT, 0.5f, 5.0f, 1.5f));

        // Parameter 2: Dynamic slider allowing the user to select how many active zones to render
        params.add(new Param("maxZones", "Max Active Zones", Param.Type.INTEGER, 1, 5, 2));

        setColor(Color.parseColor("#4CAF50"));
    }

    @Override public String  getId()          { return "supply_demand_zones"; }
    @Override public String  getDisplayName() { return "Supply & Demand Zones"; }
    @Override public String  getTag()         { return "S&D"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() {
        return new SupplyDemandZoneIndicator();
    }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.size() < 30) {
            return r;
        }

        float userThreshold = getParam("threshold");
        int maxZonesPerSide = (int) getParam("maxZones");
        int total = candles.size();

        // Pass 1: Calculate global baseline average candle body size
        double totalBodySize = 0;
        for (int i = 0; i < total; i++) {
            totalBodySize += Math.abs(candles.get(i).close - candles.get(i).open);
        }
        double avgBody = totalBodySize / total;
        double strongMoveTrigger = avgBody * userThreshold;

        int demandCount = 0;
        int supplyCount = 0;

        // Pass 2: Step backward through history to discover potential zone structures
        // We look back up to 100 bars to hunt for valid unmitigated levels
        int lookbackLimit = Math.max(0, total - 100);

        for (int i = total - 3; i > lookbackLimit; i--) {
            Candle current = candles.get(i);
            double body = current.close - current.open;
            double absBody = Math.abs(body);

            // Did this candle footprint represent an institutional imbalance?
            if (absBody > strongMoveTrigger) {
                int baseIndex = i - 1;
                if (baseIndex < 0) continue;

                Candle baseCandle = candles.get(baseIndex);
                long zoneStartTs = baseCandle.timestamp;
                long currentTs   = candles.get(total - 1).timestamp;

                if (body > 0 && demandCount < maxZonesPerSide) {
                    // Potential Demand Zone Metrics
                    double topPrice = baseCandle.high;
                    double botPrice = Math.min(baseCandle.low, baseCandle.close);

                    // Forward Verification: Verify if later market action destroyed this zone
                    boolean isBroken = false;
                    for (int j = i; j < total; j++) {
                        // A zone is broken if a subsequent candle close prints completely underneath it
                        if (candles.get(j).close < botPrice) {
                            isBroken = true;
                            break;
                        }
                    }

                    if (!isBroken) {
                        DrawingStyle demandStyle = new DrawingStyle(Color.parseColor("#2E7D32"), 1.0f, false);
                        demandStyle.filled = true;
                        demandStyle.fillColor = Color.argb(25, 46, 125, 50); // Translucent clean spacing
                        r.drawings.add(new ChartDrawing.Rectangle(zoneStartTs, topPrice, currentTs, botPrice, demandStyle, Source.INDICATOR));
                        demandCount++;
                    }
                }
                else if (body < 0 && supplyCount < maxZonesPerSide) {
                    // Potential Supply Zone Metrics
                    double topPrice = Math.max(baseCandle.high, baseCandle.open);
                    double botPrice = baseCandle.low;

                    // Forward Verification: Verify if later market action destroyed this zone
                    boolean isBroken = false;
                    for (int j = i; j < total; j++) {
                        // A zone is broken if a subsequent candle close prints completely above it
                        if (candles.get(j).close > topPrice) {
                            isBroken = true;
                            break;
                        }
                    }

                    if (!isBroken) {
                        DrawingStyle supplyStyle = new DrawingStyle(Color.parseColor("#C62828"), 1.0f, false);
                        supplyStyle.filled = true;
                        supplyStyle.fillColor = Color.argb(25, 198, 40, 40);

                        r.drawings.add(new ChartDrawing.Rectangle(zoneStartTs, topPrice, currentTs, botPrice, supplyStyle, Source.INDICATOR));
                        supplyCount++;
                    }
                }

                // Optimization: Exit calculation sweep early if slots are saturated
                if (demandCount >= maxZonesPerSide && supplyCount >= maxZonesPerSide) {
                    break;
                }
            }
        }

        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;

        double activeClose = data.get(data.size() - 1).close;
        double activeOpen = data.get(data.size() - 1).open;

        if (activeClose > activeOpen) return 60;
        if (activeClose < activeOpen) return 40;
        return 50;
    }
}