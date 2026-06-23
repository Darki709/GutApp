package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;
public class RsiIndicator extends Indicator {
    public RsiIndicator() {
        params.add(new Param("period","Period",Param.Type.INTEGER,2,50,14));
        params.add(new Param("overbought","Overbought",Param.Type.INTEGER,50,95,80));
        params.add(new Param("oversold","Oversold",Param.Type.INTEGER,5,50,20));
        setColor(Color.parseColor("#7C4DFF"));
    }
    @Override public String getId() { return "rsi"; }
    @Override public String getDisplayName() { return "RSI"; }
    @Override public String getTag() { return "RSI"; }
    @Override public boolean isSubChart() { return true; }
    @Override public Indicator newInstance() { return new RsiIndicator(); }
    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period=(int)getParam("period");
        if(candles.size()<period+1) return new Result();
        double ag=0,al=0;
        for(int i=1;i<=period;i++){
            double ch=candles.get(i).close-candles.get(i-1).close;
            if(ch>0) ag+=ch; else al+=Math.abs(ch);
        }
        ag/=period; al/=period;
        List<Entry> e=new ArrayList<>();
        for(int i=period+1;i<candles.size();i++){
            double ch=candles.get(i).close-candles.get(i-1).close;
            double g=ch>0?ch:0, l=ch<0?Math.abs(ch):0;
            ag=(ag*(period-1)+g)/period; al=(al*(period-1)+l)/period;
            double rs=al==0?100:ag/al; double rsi=100-(100/(1+rs));
            e.add(new Entry(i,(float)rsi));
        }
        float ob=getParam("overbought"),os=getParam("oversold");
        List<Entry> obE=new ArrayList<>(),osE=new ArrayList<>();
        for(Entry en:e){obE.add(new Entry(en.getX(),ob)); osE.add(new Entry(en.getX(),os));}
        Result r=new Result(); r.subChartMin=0f; r.subChartMax=100f;
        LineDataSet rsiSet=makeLineSet(e,"RSI("+period+")",getColor(),1.4f);
        LineDataSet obSet=makeLineSet(obE,"OB",Color.argb(100,239,83,80), 1);
        LineDataSet osSet=makeLineSet(osE,"OS",Color.argb(100,38,166,154), 1);
        rsiSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        obSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        osSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(rsiSet); r.subChartLines.add(obSet); r.subChartLines.add(osSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> candles) {
        if (candles.size() < 2) return 50;

        // Compute the current RSI value
        Result res = compute(candles);
        if (res.subChartLines.isEmpty() || res.subChartLines.get(0).getEntryCount() == 0) return 50;

        float currentRsi = res.subChartLines.get(0).getYMax(); // Simplified for latest value

        // Logic: RSI > 70 is overbought (Bearish reversal potential)
        // RSI < 30 is oversold (Bullish reversal potential)
        if (currentRsi >= 70) return 20; // Strong Bearish (Look to sell)
        if (currentRsi <= 30) return 80; // Strong Bullish (Look to buy)

        // Linear mapping for middle ranges
        return (int) currentRsi;
    }
}