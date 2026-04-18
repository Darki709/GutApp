package com.example.gutapp.data.indicators.impl;
import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;
public class MaIndicator extends Indicator {
    public MaIndicator() {
        params.add(new Param("period","Period",Param.Type.INTEGER,2,200,20));
        setColor(Color.parseColor("#FFC107"));
    }
    @Override public String getId() { return "ma"; }
    @Override public String getDisplayName() { return "Moving Average"; }
    @Override public String getTag() { return "MA"; }
    @Override public boolean isSubChart() { return false; }
    @Override public Indicator newInstance() { return new MaIndicator(); }
    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period=(int)getParam("period");
        List<Entry> e=new ArrayList<>();
        for(int i=period-1;i<candles.size();i++){
            double s=0; for(int j=i-period+1;j<=i;j++) s+=candles.get(j).close;
            e.add(new Entry(i,(float)(s/period)));
        }
        Result r=new Result();
        r.overlayLines.add(makeLineSet(e,"MA("+period+")",getColor(),1.4f));
        return r;
    }
}