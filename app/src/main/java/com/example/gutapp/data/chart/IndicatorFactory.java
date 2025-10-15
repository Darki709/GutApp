package com.example.gutapp.data.chart;

import com.example.gutapp.data.chart.indicators.SMA;
import com.example.gutapp.database.DB_Helper;

public class IndicatorFactory {
    /*
    * params goes like this: [color, period (depends), more parameters an indicator might need...]
    * */

    public static Indicator createIndicator(Indicators type, String id, String symbol, float[] params, DB_Helper db_helper) {
        //determine the indicator and returns it by calling it's constructor
        switch (type) {
            case SMA:
                return new SMA(db_helper, (int)params[0], (int)params[1], params[2],id, type, symbol);
        }
        return null;
    }
}
