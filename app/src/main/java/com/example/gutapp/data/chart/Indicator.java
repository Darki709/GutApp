package com.example.gutapp.data.chart;

import com.github.mikephil.charting.charts.CombinedChart;

public abstract class Indicator {

    private String id;
    protected Indicators type;
    protected boolean isOverlay;
    protected boolean isVisible;
    // num 0 parameter
    protected int color;
    protected String symbol;


    //constructor
    public Indicator(String id, Indicators type, boolean isOverlay, String symbol,int color) {
        this.id = id;
        this.type = type;
        this.isOverlay = isOverlay;
        this.isVisible = true;
        this.symbol = symbol;
        this.color = color;
    }

    public void setVisible(boolean visible){
        this.isVisible = visible;
    }

    public void setColor(int color){
        this.color = color;
    }

    //draws the indicator
    public abstract void draw(CombinedChart combinedChart);

    public String getID(){
        return id;
    }

    public boolean isOverlay(){
        return this.isOverlay;
    }

    public abstract void changeSettings(float[] params);
}
