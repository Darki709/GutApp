package com.example.gutapp.data.indicators.impl;
import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;
public class MacdIndicator extends Indicator {
    public MacdIndicator() {
        params.add(new Param("fast","Fast EMA",Param.Type.INTEGER,2,50,12));
        params.add(new Param("slow","Slow EMA",Param.Type.INTEGER,5,200,26));
        params.add(new Param("signal","Signal EMA",Param.Type.INTEGER,2,50,9));
        setColor(Color.parseColor("#2196F3"));
    }
    @Override public String getId() { return "macd"; }
    @Override public String getDisplayName() { return "MACD"; }
    @Override public String getTag() { return "MACD"; }
    @Override public boolean isSubChart() { return true; }
    @Override public Indicator newInstance() { return new MacdIndicator(); }
    @Override
    public Result compute(ArrayList<Candle> candles) {
        int fast=(int)getParam("fast"),slow=(int)getParam("slow"),sig=(int)getParam("signal");
        if(candles.size()<slow+sig) return new Result();
        double[] fe=computeEma(candles,fast), se=computeEma(candles,slow);
        List<Entry> macdE=new ArrayList<>();
        for(int i=slow-1;i<candles.size();i++) macdE.add(new Entry(i,(float)(fe[i]-se[i])));
        double sm=2.0/(sig+1), sigEma=macdE.get(0).getY();
        List<Entry> sigE=new ArrayList<>(),histE=new ArrayList<>();
        for(int i=0;i<macdE.size();i++){
            sigEma=(macdE.get(i).getY()-sigEma)*sm+sigEma;
            if(i>=sig-1){
                float x=macdE.get(i).getX();
                sigE.add(new Entry(x,(float)sigEma));
                histE.add(new Entry(x,macdE.get(i).getY()-(float)sigEma));
            }
        }
        Result r=new Result();
        LineDataSet ms=makeLineSet(macdE,"MACD",getColor(),1.4f);
        LineDataSet ss=makeLineSet(sigE,"Signal",Color.parseColor("#FF9800"),1.2f);
        LineDataSet hs=makeLineSet(histE,"Hist",Color.parseColor("#546E7A"),1f);
        ms.setAxisDependency(YAxis.AxisDependency.RIGHT);
        ss.setAxisDependency(YAxis.AxisDependency.RIGHT);
        hs.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(ms); r.subChartLines.add(ss); r.subChartLines.add(hs);
        return r;
    }
    private double[] computeEma(ArrayList<Candle> c,int p){
        double[] e=new double[c.size()]; double m=2.0/(p+1); e[0]=c.get(0).close;
        for(int i=1;i<c.size();i++) e[i]=(c.get(i).close-e[i-1])*m+e[i-1];
        return e;
    }
}