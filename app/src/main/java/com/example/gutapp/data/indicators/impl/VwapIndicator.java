package com.example.gutapp.data.indicators.impl;
import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;
public class VwapIndicator extends Indicator {
    public VwapIndicator() { setColor(Color.parseColor("#AB47BC")); }
    @Override public String getId() { return "vwap"; }
    @Override public String getDisplayName() { return "VWAP"; }
    @Override public String getTag() { return "VWAP"; }
    @Override public boolean isSubChart() { return false; }
    @Override public Indicator newInstance() { return new VwapIndicator(); }
    @Override
    public Result compute(ArrayList<Candle> candles) {
        List<Entry> e=new ArrayList<>();
        double cv=0,cV=0;
        for(int i=0;i<candles.size();i++){
            Candle c=candles.get(i); double tp=(c.high+c.low+c.close)/3.0;
            cv+=tp*c.volume; cV+=c.volume;
            if(cV>0) e.add(new Entry(i,(float)(cv/cV)));
        }
        Result r=new Result();
        r.overlayLines.add(makeLineSet(e,"VWAP",getColor(),1.6f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> candles) {
        Result res = compute(candles);
        if (res.overlayLines.isEmpty()) return 50;

        float lastPrice = (float) candles.get(candles.size() - 1).close;
        float vwapVal = res.overlayLines.get(0).getValues().get(res.overlayLines.get(0).getEntryCount() - 1).getY();

        if (lastPrice > vwapVal) return 75; // Strong intraday bullish
        if (lastPrice < vwapVal) return 25; // Strong intraday bearish
        return 50;
    }
}