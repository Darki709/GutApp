package com.example.gutapp.data.drawing;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.utils.Transformer;

import java.util.List;
import java.util.Locale;

/**
 * DrawingRenderer — renders all ChartDrawing types onto the chart canvas.
 *
 * Key design: all anchor positions are timestamps. resolveIndex() maps each
 * timestamp to the nearest candle index for the current timeframe, so drawings
 * appear correctly positioned on 5m, 1h, 1d, etc. without any redrawing.
 *
 * Two-pass rendering (called separately by DrawingChart):
 *   drawLayer(canvas, chart, manager, candles, Layer.BEHIND_CANDLES)  — before MPAndroidChart
 *   drawLayer(canvas, chart, manager, candles, Layer.ABOVE_CANDLES)   — after MPAndroidChart
 */
public class DrawingRenderer {


    //buffers to reduce memory allocation in runtime
    private final float[] pointBuffer = new float[2];
    private final float[] lineBuffer = new float[4];

    private final Paint linePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path        = new Path();
    private final float[] pts2      = new float[2];

    private static final float[] FIB_ALPHAS = {1f, 0.8f, 0.7f, 0.6f, 0.7f, 0.8f, 1f};
    private static final float[] GANN_SLOPES = {8f, 4f, 3f, 2f, 1f, 0.5f, 0.333f, 0.25f, 0.125f};
    private static final String[] GANN_LABELS = {"1×8","1×4","1×3","1×2","1×1","2×1","3×1","4×1","8×1"};

    public DrawingRenderer() {
        linePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
        textPaint.setStyle(Paint.Style.FILL);
        handlePaint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    /** Draw only drawings in the specified layer. */
    public void drawLayer(Canvas canvas, CombinedChart chart,
                          DrawingManager manager, List<Candle> candles,
                          ChartDrawing.Layer layer) {
        if (manager.isEmpty() || candles == null || candles.isEmpty()) return;

        canvas.save();
        canvas.clipRect(chart.getContentRect());

        Transformer tf  = chart.getTransformer(YAxis.AxisDependency.RIGHT);
        RectF       rect = chart.getContentRect();
        float textSz = chart.getResources().getDisplayMetrics().density * 9f;
        textPaint.setTextSize(textSz);

        for (ChartDrawing d : manager.getAll()) {
            if (d.style == null || d.layer != layer) continue;
            drawSingle(canvas, d, tf, rect, candles);
        }

        canvas.restore();
    }

    /** Draw all drawings regardless of layer (used for preview managers). */
    public void draw(Canvas canvas, CombinedChart chart,
                     DrawingManager manager, List<Candle> candles) {
        drawLayer(canvas, chart, manager, candles, ChartDrawing.Layer.BEHIND_CANDLES);
        drawLayer(canvas, chart, manager, candles, ChartDrawing.Layer.ABOVE_CANDLES);
    }

    private void drawSingle(Canvas canvas, ChartDrawing d,
                            Transformer tf, RectF rect, List<Candle> candles) {
        switch (d.getType()) {
            case HORIZONTAL_LINE:    drawHorizontalLine  (canvas,(ChartDrawing.HorizontalLine)d,   tf,rect,candles); break;
            case TREND_LINE:         drawTrendLine        (canvas,(ChartDrawing.TrendLine)d,        tf,rect,candles); break;
            case RAY_LINE:           drawRayLine          (canvas,(ChartDrawing.RayLine)d,          tf,rect,candles); break;
            case EXTENDED_LINE:      drawExtendedLine     (canvas,(ChartDrawing.ExtendedLine)d,     tf,rect,candles); break;
            case VERTICAL_LINE:      drawVerticalLine     (canvas,(ChartDrawing.VerticalLine)d,     tf,rect,candles); break;
            case LINEAR_REGRESSION:  drawLinearRegression (canvas,(ChartDrawing.LinearRegression)d, tf,rect,candles); break;
            case FIB_RETRACEMENT:    drawFibRetracement   (canvas,(ChartDrawing.FibRetracement)d,   tf,rect,candles); break;
            case PRICE_RANGE:        drawPriceRange       (canvas,(ChartDrawing.PriceRange)d,       tf,rect);         break;
            case RECTANGLE:          drawRectangle        (canvas,(ChartDrawing.Rectangle)d,        tf,rect,candles); break;
            case ELLIPSE:            drawEllipse          (canvas,(ChartDrawing.Ellipse)d,          tf,rect,candles); break;
            case TEXT_ANNOTATION:    drawTextAnnotation   (canvas,(ChartDrawing.TextAnnotation)d,   tf,rect,candles); break;
            case ARROW:              drawArrow            (canvas,(ChartDrawing.Arrow)d,            tf,rect,candles); break;
            case PARALLEL_CHANNEL:   drawParallelChannel  (canvas,(ChartDrawing.ParallelChannel)d,  tf,rect,candles); break;
            case PITCHFORK:          drawPitchfork        (canvas,(ChartDrawing.Pitchfork)d,        tf,rect,candles); break;
            case GANN_FAN:           drawGannFan          (canvas,(ChartDrawing.GannFan)d,          tf,rect,candles); break;
        }
    }

    // ── Horizontal line ───────────────────────────────────────────────
    private void drawHorizontalLine(Canvas canvas, ChartDrawing.HorizontalLine d,
                                    Transformer tf, RectF rect, List<Candle> candles) {
        float y = priceToY(d.price, tf);
        if (y < rect.top - 10 || y > rect.bottom + 10) return;
        applyLine(d.style);
        canvas.drawLine(rect.left, y, rect.right, y, linePaint);
        String lbl = (d.label != null && !d.label.isEmpty()) ? d.label
                : String.format(Locale.US, "%.4f", d.price);
        drawYLabel(canvas, lbl, d.style.color, rect.right - 4, y);
        if (d.selected) drawSelectionHandle(canvas, rect.left + 40, y);
    }

    // ── Trend line ────────────────────────────────────────────────────
    private void drawTrendLine(Canvas canvas, ChartDrawing.TrendLine d,
                               Transformer tf, RectF rect, List<Candle> candles) {
        float x1 = tsToX(d.startTs, candles, tf);
        float y1 = priceToY(d.startPrice, tf);
        float x2 = tsToX(d.endTs, candles, tf);
        float y2 = priceToY(d.endPrice, tf);
        if (d.extendLeft || d.extendRight) {
            float[] ext = extendLine(x1,y1,x2,y2,rect,d.extendLeft,d.extendRight);
            x1=ext[0]; y1=ext[1]; x2=ext[2]; y2=ext[3];
        }
        applyLine(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);
        if (!d.extendLeft)  drawHandle(canvas, x1, y1, d.style.color, d.selected);
        if (!d.extendRight) drawHandle(canvas, x2, y2, d.style.color, d.selected);
    }

    // ── Ray line ──────────────────────────────────────────────────────
    private void drawRayLine(Canvas canvas, ChartDrawing.RayLine d,
                             Transformer tf, RectF rect, List<Candle> candles) {
        float x1 = tsToX(d.startTs, candles, tf);
        float y1 = priceToY(d.startPrice, tf);
        float x2 = tsToX(d.anchorTs, candles, tf);
        float y2 = priceToY(d.anchorPrice, tf);
        float dx = x2-x1, dy = y2-y1;
        float xEnd = rect.right + 200;
        float yEnd = dx==0 ? rect.bottom : y1 + (xEnd-x1)*(dy/dx);
        applyLine(d.style);
        canvas.drawLine(x1, y1, xEnd, yEnd, linePaint);
        drawHandle(canvas, x1, y1, d.style.color, d.selected);
    }

    // ── Extended line ─────────────────────────────────────────────────
    private void drawExtendedLine(Canvas canvas, ChartDrawing.ExtendedLine d,
                                  Transformer tf, RectF rect, List<Candle> candles) {
        float x1 = tsToX(d.startTs, candles, tf);
        float y1 = priceToY(d.startPrice, tf);
        float x2 = tsToX(d.endTs, candles, tf);
        float y2 = priceToY(d.endPrice, tf);
        float[] ext = extendLine(x1,y1,x2,y2,rect,true,true);
        applyLine(d.style);
        canvas.drawLine(ext[0],ext[1],ext[2],ext[3], linePaint);
        drawHandle(canvas, x1, y1, d.style.color, d.selected);
        drawHandle(canvas, x2, y2, d.style.color, d.selected);
    }

    // ── Vertical line ─────────────────────────────────────────────────
    private void drawVerticalLine(Canvas canvas, ChartDrawing.VerticalLine d,
                                  Transformer tf, RectF rect, List<Candle> candles) {
        float x = tsToX(d.candleTs, candles, tf);
        if (x < rect.left - 10 || x > rect.right + 10) return;
        applyLine(d.style);
        canvas.drawLine(x, rect.top, x, rect.bottom, linePaint);
        if (d.label != null && !d.label.isEmpty()) {
            textPaint.setColor(d.style.color);
            canvas.drawText(d.label, x + 4, rect.top + textPaint.getTextSize() + 4, textPaint);
        }
        if (d.selected) drawSelectionHandle(canvas, x, rect.top + 40);
    }

    // ── Linear regression ─────────────────────────────────────────────
    private void drawLinearRegression(Canvas canvas, ChartDrawing.LinearRegression d,
                                      Transformer tf, RectF rect, List<Candle> candles) {
        int si = ChartDrawing.resolveIndex(d.startTs, candles);
        int ei = ChartDrawing.resolveIndex(d.endTs,   candles);
        int start = Math.max(0, Math.min(si, ei));
        int end   = Math.min(candles.size()-1, Math.max(si, ei));
        if (end <= start) return;
        int n = end-start+1;
        double sumX=0,sumY=0,sumXY=0,sumXX=0;
        for (int i=0;i<n;i++) {
            double x=i, y=candles.get(start+i).close;
            sumX+=x; sumY+=y; sumXY+=x*y; sumXX+=x*x;
        }
        double slope     = (n*sumXY - sumX*sumY) / (n*sumXX - sumX*sumX);
        double intercept = (sumY - slope*sumX) / n;
        float rx1=indexToX(start,tf), ry1=priceToY(intercept,tf);
        float rx2=indexToX(end,tf),   ry2=priceToY(intercept+slope*(n-1),tf);
        applyLine(d.style);
        canvas.drawLine(rx1,ry1,rx2,ry2,linePaint);
        if (d.drawChannel) {
            double ss=0;
            for (int i=0;i<n;i++) { double res=candles.get(start+i).close-(intercept+slope*i); ss+=res*res; }
            double std=Math.sqrt(ss/n) * d.channelDeviation;
            Paint ch=new Paint(linePaint); ch.setAlpha(120);
            ch.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,3},0));
            canvas.drawLine(rx1,priceToY(intercept+std,tf),rx2,priceToY(intercept+slope*(n-1)+std,tf),ch);
            canvas.drawLine(rx1,priceToY(intercept-std,tf),rx2,priceToY(intercept+slope*(n-1)-std,tf),ch);
            path.reset();
            path.moveTo(rx1,priceToY(intercept+std,tf)); path.lineTo(rx2,priceToY(intercept+slope*(n-1)+std,tf));
            path.lineTo(rx2,priceToY(intercept+slope*(n-1)-std,tf)); path.lineTo(rx1,priceToY(intercept-std,tf));
            path.close(); fillPaint.setColor(d.style.fillColor); canvas.drawPath(path,fillPaint);
        }
    }

    // ── Fibonacci retracement ─────────────────────────────────────────
    private void drawFibRetracement(Canvas canvas, ChartDrawing.FibRetracement d,
                                    Transformer tf, RectF rect, List<Candle> candles) {
        if (d.levels==null||d.levels.length==0) return;
        float x1=tsToX(d.startTs, candles, tf);
        float x2=tsToX(d.endTs, candles, tf);
        float xL=Math.min(x1,x2), xR=Math.max(x1,x2);
        if (xR < rect.left-10 || xL > rect.right+10) return;
        float drawL=Math.max(xL,rect.left), drawR=Math.min(xR,rect.right);
        double range=d.highPrice-d.lowPrice;
        int base=d.style.color;

        // Band fills
        for (int i=0;i<d.levels.length-1;i++) {
            double p1=d.highPrice-d.levels[i]*range, p2=d.highPrice-d.levels[i+1]*range;
            float fy1=priceToY(p1,tf), fy2=priceToY(p2,tf);
            fillPaint.setColor(base); fillPaint.setAlpha(i%2==0?25:12);
            canvas.drawRect(drawL,Math.min(fy1,fy2),drawR,Math.max(fy1,fy2),fillPaint);
        }
        // Vertical anchor borders
        float yTop=priceToY(d.highPrice,tf), yBot=priceToY(d.lowPrice,tf);
        linePaint.setColor(base); linePaint.setAlpha(60);
        linePaint.setStrokeWidth(d.style.strokeWidth*0.6f); linePaint.setPathEffect(null);
        canvas.drawLine(xL,yTop,xL,yBot,linePaint);
        canvas.drawLine(xR,yTop,xR,yBot,linePaint);
        // Level lines
        for (int i=0;i<d.levels.length;i++) {
            double price=d.highPrice-d.levels[i]*range;
            float y=priceToY(price,tf);
            if (y<rect.top-20||y>rect.bottom+20) continue;
            int alpha=(int)(FIB_ALPHAS[Math.min(i,FIB_ALPHAS.length-1)]*220);
            linePaint.setColor(base); linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(d.style.strokeWidth);
            linePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,3},0));
            canvas.drawLine(drawL,y,drawR,y,linePaint);
            textPaint.setColor(base); textPaint.setAlpha(alpha);
            String lbl=String.format(Locale.US,"%.3f  %.5f",d.levels[i],price);
            float lx=Math.min(drawR,rect.right)-textPaint.measureText(lbl)-4;
            if (lx>drawL) canvas.drawText(lbl,lx,y-3,textPaint);
        }
        linePaint.setPathEffect(null); linePaint.setAlpha(255);
        drawHandle(canvas,x1,yTop,base,d.selected);
        drawHandle(canvas,x2,yBot,base,d.selected);
    }

    // ── Price range ───────────────────────────────────────────────────
    private void drawPriceRange(Canvas canvas, ChartDrawing.PriceRange d,
                                Transformer tf, RectF rect) {
        float yH=priceToY(d.priceHigh,tf), yL=priceToY(d.priceLow,tf);
        if (yH>rect.bottom&&yL>rect.bottom) return;
        if (yH<rect.top  &&yL<rect.top)    return;
        yH=clamp(yH,rect.top,rect.bottom); yL=clamp(yL,rect.top,rect.bottom);
        fillPaint.setColor(d.style.fillColor);
        canvas.drawRect(rect.left,yH,rect.right,yL,fillPaint);
        applyLine(d.style);
        canvas.drawLine(rect.left,yH,rect.right,yH,linePaint);
        canvas.drawLine(rect.left,yL,rect.right,yL,linePaint);
        if (d.selected) { drawSelectionHandle(canvas,rect.left+40,yH); drawSelectionHandle(canvas,rect.left+40,yL); }
    }

    // ── Rectangle ─────────────────────────────────────────────────────
    private void drawRectangle(Canvas canvas, ChartDrawing.Rectangle d,
                               Transformer tf, RectF rect, List<Candle> candles) {
        float x1=tsToX(d.startTs, candles, tf), y1=priceToY(d.startPrice,tf);
        float x2=tsToX(d.endTs, candles, tf), y2=priceToY(d.endPrice,  tf);
        RectF r=new RectF(Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2),Math.max(y1,y2));
        if (d.style.filled) { fillPaint.setColor(d.style.fillColor); canvas.drawRect(r,fillPaint); }
        applyLine(d.style); canvas.drawRect(r,linePaint);
        if (d.selected) { drawHandle(canvas,x1,y1,d.style.color,true); drawHandle(canvas,x2,y2,d.style.color,true); }
    }

    // ── Ellipse ───────────────────────────────────────────────────────
    private void drawEllipse(Canvas canvas, ChartDrawing.Ellipse d,
                             Transformer tf, RectF rect, List<Candle> candles) {
        float x1=tsToX(d.startTs, candles, tf), y1=priceToY(d.startPrice,tf);
        float x2=tsToX(d.endTs, candles, tf), y2=priceToY(d.endPrice,  tf);
        RectF r=new RectF(Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2),Math.max(y1,y2));
        if (d.style.filled) { fillPaint.setColor(d.style.fillColor); canvas.drawOval(r,fillPaint); }
        applyLine(d.style); canvas.drawOval(r,linePaint);
        if (d.selected) { drawHandle(canvas,x1,y1,d.style.color,true); drawHandle(canvas,x2,y2,d.style.color,true); }
    }

    // ── Text annotation ───────────────────────────────────────────────
    private void drawTextAnnotation(Canvas canvas, ChartDrawing.TextAnnotation d,
                                    Transformer tf, RectF rect, List<Candle> candles) {
        float x=tsToX(d.candleTs, candles, tf);
        float y=priceToY(d.price,tf);
        if (x<rect.left-200||x>rect.right+200) return;
        textPaint.setTextSize(d.textSizeSp * 3f); // approx sp→px
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(d.style.color);
        String txt=d.text!=null?d.text:"";
        float tw=textPaint.measureText(txt), th=textPaint.getTextSize(), pad=8f;
        bgPaint.setColor(Color.argb(200,20,20,25));
        canvas.drawRoundRect(new RectF(x-pad,y-th-pad,x+tw+pad,y+pad),6,6,bgPaint);
        linePaint.setColor(d.style.color); linePaint.setStrokeWidth(1f); linePaint.setPathEffect(null);
        canvas.drawRoundRect(new RectF(x-pad,y-th-pad,x+tw+pad,y+pad),6,6,linePaint);
        canvas.drawText(txt,x,y,textPaint);
        textPaint.setTypeface(Typeface.DEFAULT);
        if (d.selected) drawSelectionHandle(canvas,x+tw/2,y-th/2);
    }

    // ── Arrow ─────────────────────────────────────────────────────────
    private void drawArrow(Canvas canvas, ChartDrawing.Arrow d,
                           Transformer tf, RectF rect, List<Candle> candles) {
        float x1=tsToX(d.startTs, candles, tf), y1=priceToY(d.startPrice,tf);
        float x2=tsToX(d.endTs, candles, tf), y2=priceToY(d.endPrice,  tf);
        applyLine(d.style); canvas.drawLine(x1,y1,x2,y2,linePaint);
        float dx=x2-x1, dy=y2-y1, len=(float)Math.sqrt(dx*dx+dy*dy);
        if (len<1) return;
        float ux=dx/len, uy=dy/len, headLen=20f+d.style.strokeWidth*4, headW=8f+d.style.strokeWidth*2;
        path.reset();
        path.moveTo(x2,y2);
        path.lineTo(x2-ux*headLen-uy*headW, y2-uy*headLen+ux*headW);
        path.lineTo(x2-ux*headLen+uy*headW, y2-uy*headLen-ux*headW);
        path.close(); fillPaint.setColor(d.style.color); canvas.drawPath(path,fillPaint);
        drawHandle(canvas,x1,y1,d.style.color,d.selected);
    }

    // ── Parallel channel ──────────────────────────────────────────────
    private void drawParallelChannel(Canvas canvas, ChartDrawing.ParallelChannel d,
                                     Transformer tf, RectF rect, List<Candle> candles) {
        float x1=tsToX(d.startTs, candles, tf), y1=priceToY(d.startPrice,tf);
        float x2=tsToX(d.endTs, candles, tf), y2=priceToY(d.endPrice,  tf);
        float ym=priceToY(d.midPrice,tf);
        float offset=ym-y1;
        applyLine(d.style);
        canvas.drawLine(x1,y1,x2,y2,linePaint);
        canvas.drawLine(x1,y1+offset,x2,y2+offset,linePaint);
        Paint mid=new Paint(linePaint); mid.setAlpha(100);
        mid.setPathEffect(new android.graphics.DashPathEffect(new float[]{5,5},0));
        canvas.drawLine(x1,y1+offset/2,x2,y2+offset/2,mid);
        path.reset(); path.moveTo(x1,y1); path.lineTo(x2,y2);
        path.lineTo(x2,y2+offset); path.lineTo(x1,y1+offset); path.close();
        fillPaint.setColor(d.style.fillColor); canvas.drawPath(path,fillPaint);
        drawHandle(canvas,x1,y1,d.style.color,d.selected);
        drawHandle(canvas,x2,y2,d.style.color,d.selected);
        drawHandle(canvas,x1,y1+offset,d.style.color,d.selected);
    }

    // ── Pitchfork ─────────────────────────────────────────────────────
    private void drawPitchfork(Canvas canvas, ChartDrawing.Pitchfork d,
                               Transformer tf, RectF rect, List<Candle> candles) {
        float x0=tsToX(d.p0Ts, candles, tf), y0=priceToY(d.p0Price,tf);
        float x1=tsToX(d.p1Ts, candles, tf), y1=priceToY(d.p1Price,tf);
        float x2=tsToX(d.p2Ts, candles, tf), y2=priceToY(d.p2Price,tf);
        float mx=(x1+x2)/2, my=(y1+y2)/2;
        applyLine(d.style);
        float[] med=extendLineRight(x0,y0,mx,my,rect);
        canvas.drawLine(x0,y0,med[0],med[1],linePaint);
        Paint prong=new Paint(linePaint); prong.setAlpha(180);
        float[] u=extendLineRight(x1,y1,x1+(mx-x0),y1+(my-y0),rect);
        canvas.drawLine(x1,y1,u[0],u[1],prong);
        float[] l=extendLineRight(x2,y2,x2+(mx-x0),y2+(my-y0),rect);
        canvas.drawLine(x2,y2,l[0],l[1],prong);
        Paint h=new Paint(linePaint); h.setAlpha(120);
        h.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,4},0));
        canvas.drawLine(x1,y1,x2,y2,h);
        drawHandle(canvas,x0,y0,d.style.color,d.selected);
        drawHandle(canvas,x1,y1,d.style.color,d.selected);
        drawHandle(canvas,x2,y2,d.style.color,d.selected);
    }

    // ── Gann Fan ──────────────────────────────────────────────────────
    private void drawGannFan(Canvas canvas, ChartDrawing.GannFan d,
                             Transformer tf, RectF rect, List<Candle> candles) {
        float x0=tsToX(d.startTs, candles, tf), y0=priceToY(d.startPrice,tf);
        float x1=tsToX(d.endTs, candles, tf), y1=priceToY(d.endPrice,  tf);
        float dx=x1-x0; if (Math.abs(dx)<1) return;
        float unit=(y1-y0)/dx;
        for (int i=0;i<GANN_SLOPES.length;i++) {
            float slope=unit*GANN_SLOPES[i], xEnd=rect.right+100, yEnd=y0+(xEnd-x0)*slope;
            int alpha=i==4?255:140;
            linePaint.setColor(d.style.color); linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(i==4?d.style.strokeWidth*1.5f:d.style.strokeWidth);
            linePaint.setPathEffect(null);
            canvas.drawLine(x0,y0,xEnd,yEnd,linePaint);
            float labelX=Math.min(x0+60,rect.right-40);
            float labelY=y0+(labelX-x0)*slope-4;
            if (labelY>rect.top&&labelY<rect.bottom) {
                textPaint.setColor(d.style.color); textPaint.setAlpha(alpha);
                canvas.drawText(GANN_LABELS[i],labelX,labelY,textPaint);
            }
        }
        linePaint.setAlpha(255);
        drawHandle(canvas,x0,y0,d.style.color,d.selected);
    }

    // ── Handles ───────────────────────────────────────────────────────
    private void drawHandle(Canvas canvas, float x, float y, int color, boolean selected) {
        if (selected) drawSelectionHandle(canvas,x,y);
        handlePaint.setColor(Color.parseColor("#1A1818")); canvas.drawCircle(x,y,6f,handlePaint);
        handlePaint.setColor(color); handlePaint.setAlpha(220); canvas.drawCircle(x,y,4f,handlePaint);
        handlePaint.setAlpha(255);
    }
    private void drawSelectionHandle(Canvas canvas, float x, float y) {
        handlePaint.setColor(Color.argb(60,38,166,154));   canvas.drawCircle(x,y,16f,handlePaint);
        handlePaint.setColor(Color.argb(180,38,166,154));
        handlePaint.setStyle(Paint.Style.STROKE); handlePaint.setStrokeWidth(1.5f);
        canvas.drawCircle(x,y,16f,handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
    }
    private void drawYLabel(Canvas canvas, String label, int color, float x, float y) {
        textPaint.setColor(color); textPaint.setAlpha(220);
        float tw=textPaint.measureText(label), th=textPaint.getTextSize(), pad=4f;
        bgPaint.setColor(Color.argb(200,20,20,25));
        canvas.drawRoundRect(new RectF(x-tw-pad*2,y-th,x,y+pad),3,3,bgPaint);
        canvas.drawText(label,x-tw-pad,y,textPaint); textPaint.setAlpha(255);
    }

    // ── Coordinate helpers ────────────────────────────────────────────
    private float indexToX(float index, Transformer tf) {
        pts2[0]=index; pts2[1]=0; tf.pointValuesToPixel(pts2); return pts2[0];
    }
    private float priceToY(double price, Transformer tf) {
        pts2[0]=0; pts2[1]=(float)price; tf.pointValuesToPixel(pts2); return pts2[1];
    }

    /**
     * Convert a timestamp to pixel X — extrapolates past the last candle so
     * drawings never snap/jump when they extend into future empty space.
     * Uses fractional candle indices, bypassing resolveIndex() which clamps.
     */
    private float tsToX(long ts, List<Candle> candles, Transformer tf) {
        if (candles == null || candles.isEmpty()) return 0;
        if (candles == null || candles.isEmpty()) return 0;
        int n = candles.size();
        if (n == 1) return indexToX(0, tf);

        long t0  = candles.get(0).timestamp;
        long tN  = candles.get(n - 1).timestamp;
        long avg = (tN - t0) / Math.max(1, n - 1);

        float fi;
        if (ts <= t0) {
            fi = avg > 0 ? (float)(ts - t0) / avg : 0;
        } else if (ts >= tN) {
            fi = (n - 1) + (avg > 0 ? (float)(ts - tN) / avg : 0);
        } else {
            // Binary search for surrounding candles
            int lo = 0, hi = n - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) / 2;
                if (candles.get(mid).timestamp <= ts) lo = mid; else hi = mid;
            }
            long tLo = candles.get(lo).timestamp, tHi = candles.get(hi).timestamp;
            fi = tHi == tLo ? lo : lo + (float)(ts - tLo) / (tHi - tLo);
        }
        return indexToX(fi, tf);
    }
    private void applyLine(ChartDrawing.DrawingStyle style) {
        linePaint.setColor(style.color); linePaint.setStrokeWidth(style.strokeWidth);
        linePaint.setAlpha(Math.max(0,Math.min(255,Math.round(255*style.opacity))));
        linePaint.setPathEffect(style.dashed
                ? new android.graphics.DashPathEffect(new float[]{style.dashOn,style.dashOff},0) : null);
    }
    private float clamp(float v, float lo, float hi) { return Math.max(lo,Math.min(hi,v)); }
    private float[] extendLine(float x1,float y1,float x2,float y2,RectF rect,boolean extL,boolean extR) {
        float dx=x2-x1, dy=y2-y1;
        if (Math.abs(dx)<0.01f) {
            lineBuffer[0] = x1;
            lineBuffer[1] = rect.top;
            lineBuffer[2] = x2;
            lineBuffer[3] = rect.bottom;
            return lineBuffer; // recycle the same allocated array
        }
        float slope=dy/dx, rx1=x1,ry1=y1,rx2=x2,ry2=y2;
        if (extL) { rx1=rect.left-200; ry1=y1+(rx1-x1)*slope; }
        if (extR) { rx2=rect.right+200; ry2=y1+(rx2-x1)*slope; }
        lineBuffer[0] = rx1; lineBuffer[1] = ry1;
        lineBuffer[2] = rx2; lineBuffer[3] = ry2;
        return lineBuffer;
    }
    private float[] extendLineRight(float x1,float y1,float x2,float y2,RectF rect) {
        float dx=x2-x1, dy=y2-y1;
        if (Math.abs(dx)<0.01f)
        {
            pointBuffer[0] = x2;
            pointBuffer[1] = rect.bottom;
            return pointBuffer;
        }
        float xEnd=rect.right+200;
        pointBuffer[0] = xEnd; pointBuffer[1] = y1+(xEnd-x1)*(dy/dx);
        return pointBuffer;
    }
}
