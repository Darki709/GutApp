package com.example.gutapp.data.chart;

import android.util.Log;
import android.widget.Toast;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.ui.ChartActivity;
import com.github.mikephil.charting.charts.CombinedChart;

import java.util.HashMap; // Switched to HashMap
import java.util.Map;     // Switched to Map

public class IndicatorManager {
    private CombinedChart combinedChart;
    private Map<String, Indicator> indicators; // Using Map<String, Indicator>
    private DB_Helper db_helper;
    private String symbol; //symbol of the stock indicators will be drawn for

    private StockDataHelper.Timeframe currentTimeframe;


    private int autoincrementID; //works like the PRIMARY KEY AUTOINCREMENT in sql

    public IndicatorManager(CombinedChart combinedChart, DB_Helper db_helper, String symbol) {
        this.symbol = symbol;
        this.combinedChart = combinedChart;
        this.indicators = new HashMap<>(); // Initialize as a HashMap
        this.db_helper = db_helper;
        autoincrementID = 0;
        this.currentTimeframe = StockDataHelper.Timeframe.DAILY;
        Log.i(ChartActivity.CHART_LOG_TAG, currentTimeframe.name());// Initialize with DAILY
    }

    public void setCurrentTimeframe(StockDataHelper.Timeframe timeframe) {
        // 1. Update the manager's internal timeframe state
        this.currentTimeframe = timeframe;

        // 2. Iterate through all active indicators to update and redraw them
        if (indicators.isEmpty()) {
            return; // Nothing to do if there are no indicators
        }

        Log.d(ChartActivity.CHART_LOG_TAG, "Timeframe changed. Redrawing all active indicators for: " + timeframe.name());

        for (Indicator indicator : indicators.values()) {
            try {
                // 3. Update the timeframe of each individual indicator
                indicator.setTimeframe(timeframe);
                // 4. Redraw the indicator, which will now use the new timeframe to recalculate
                indicator.draw(combinedChart);
            } catch (Exception e) {
                Log.e(ChartActivity.CHART_LOG_TAG, "Failed to redraw indicator " + indicator.getID() + " for new timeframe.", e);
            }
        }

        // 5. Invalidate the chart once after all indicators are redrawn
        combinedChart.invalidate();
    }

    /**
     * Retrieves an indicator by its ID. Returns null if not found.
     */
    public Indicator getIndicator(String id){
        return indicators.get(id); // Direct and efficient lookup
    }

    /**
     * Creates a new indicator and adds it to the manager and chart.
     */
    public void createIndicator(Indicators type, float[] params){
        Indicator indicator = IndicatorFactory.createIndicator(type, Integer.toString(this.autoincrementID++), this.symbol, this.currentTimeframe ,params, this.db_helper);
        if (indicator != null) {
            indicators.put(indicator.getID(), indicator); //add to map
            Log.d(ChartActivity.CHART_LOG_TAG, "Successfully created indicator with ID: " + indicator.getID());
            addIndicator2Graph(indicator);
        } else {
            Log.e(ChartActivity.CHART_LOG_TAG, "IndicatorFactory returned null for type: " + type);
        }
    }

    /**
     * Adds an indicator to the internal map and draws it on the chart.
     */
    public void addIndicator2Graph(Indicator indicator) {

        if (indicator == null) return;
        try{
            indicator.draw(combinedChart);
            Log.d(ChartActivity.CHART_LOG_TAG, "Successfully added indicator with ID: " + indicator.getID());
        }
        catch (Exception e){
            Log.e(ChartActivity.CHART_LOG_TAG, "Error drawing indicator: " + indicator.getID(), e);
            // if drawing fails set visibily of indicator to false
            indicator.setVisible(false);
            Toast.makeText(this.combinedChart.getContext(), "Error drawing indicator: " + indicator.getID(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Deletes an indicator from the chart and the manager using its ID.
     */
    public void deleteIndicator(String id) {
        // 1. Check if the indicator exists in the map
        Indicator indicator = indicators.get(id);

        if (indicator != null) {
            try {
                // 2. Tell the indicator to remove its visual representation
                indicator.remove(combinedChart);

                // 3. Remove the indicator from the map
                indicators.remove(id);

                Log.d(ChartActivity.CHART_LOG_TAG, "Successfully deleted indicator with ID: " + id);
            } catch (Exception e) {
                Log.e(ChartActivity.CHART_LOG_TAG, "Error while deleting indicator with ID: " + id, e);
            }
        } else {
            Log.w(ChartActivity.CHART_LOG_TAG, "Could not find an indicator with ID '" + id + "' to delete.");
        }
    }

    /**
     * Finds an indicator by its ID and applies new settings.
     */
    public void changeSettings(String id, float[] params){
        // Direct lookup is much more efficient than iterating
        Indicator indicator = indicators.get(id);
        if (indicator != null) {
            try{
                indicator.changeSettings(params, this.combinedChart);
            }
            catch (Exception e) {
                Log.e(ChartActivity.CHART_LOG_TAG, "Error changing settings for indicator " + id, e);
                throw e;
            }
        } else {
            Log.w(ChartActivity.CHART_LOG_TAG, "Could not find an indicator with ID '" + id + "' to change settings.");
        }
    }

    //returns a map of all the indicators
    public Map<String, Indicator> getAllIndicators() {
        return this.indicators;
    }
}
