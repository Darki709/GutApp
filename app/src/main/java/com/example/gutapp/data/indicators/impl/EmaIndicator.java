package com.example.gutapp.data.indicators.impl;
import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;
public class EmaIndicator extends Indicator {
    public EmaIndicator() {
        params.add(new Param("period","Period",Param.Type.INTEGER,2,200,20));
        setColor(Color.parseColor("#E91E63"));
    }
    @Override public String getId() { return "ema"; }
    @Override public String getDisplayName() { return "Exponential MA"; }
    @Override public String getTag() { return "EMA"; }
    @Override public boolean isSubChart() { return false; }
    @Override public Indicator newInstance() { return new EmaIndicator(); }
    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period=(int)getParam("period");
        double mult=2.0/(period+1), ema=candles.get(0).close;
        List<Entry> e=new ArrayList<>();
        for(int i=1;i<candles.size();i++){
            ema=(candles.get(i).close-ema)*mult+ema;
            if(i>=period-1) e.add(new Entry(i,(float)ema));
        }
        Result r=new Result();
        r.overlayLines.add(makeLineSet(e,"EMA("+period+")",getColor(),1.4f));
        return r;
    }
}