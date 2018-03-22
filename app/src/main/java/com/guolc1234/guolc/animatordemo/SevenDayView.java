package com.guolc1234.guolc.animatordemo;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Created by guolc on 2018/3/20.
 */

public class SevenDayView extends View {
    public SevenDayView(Context context) {
        this(context,null);
    }
    public SevenDayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SevenDayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    private int mDirection = HORIZIONTAL;
    private Bitmap mBarBitmap;
    private Paint mPaint,mBarPaint;
    private GradientDrawable mGradientDrawable;
    private ValueAnimator mValueAnimator;
    private String[] mTitles = {"周一","周二","周三","周四","周五","周六","周日"};
    private int mWidth,mHeight, mBarWidth, mBarHeight, mInterval = 18;
    private Path mPath;
    private Rect mRect = new Rect();
    private RectF mRectF = new RectF();
    private float mRadius;
    private float[] radiusArray = { 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f };
    private float[] mBarCurrentWidth = { 0f, 0f, 0f, 0f, 0f, 0f, 0f};
    private float[] mBarWidths = { 0f, 0f, 0f, 0f, 0f, 0f, 0f};
    private Xfermode MXfermode= new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private void init(Context context) {
        mPath = new Path();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.RED);
        mGradientDrawable = (GradientDrawable) context.getResources().getDrawable(R.drawable.hot);
        setLayerType(LAYER_TYPE_SOFTWARE,null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if(mTitles == null){
            throw new RuntimeException("mTitles must be not null!");
        }
        final int length = mTitles.length;
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        switch (mDirection){
            case HORIZIONTAL:
                mRadius = mWidth/2;
                mBarHeight = (int) ((mHeight - (length-1)*1f* mInterval)/length);
                mBarWidth = (int) (mWidth*0.8f);
                break;
            case VERTICAL:
                mRadius = mHeight/2;
                mBarHeight = (int) ((mWidth - (length-1)*1f* mInterval)/length);
                mBarWidth = (int) (mWidth*0.8f);
                break;
        }
        setRadius(0,mRadius,mRadius,0);
        getChartBarSize();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        configBarBitmap();
        canvas.getClipBounds(mRect);
        Bitmap src = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Bitmap mask = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Bitmap result = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Canvas srcCanvas = new Canvas(src);
        Canvas maskCanvas = new Canvas(mask);
        Canvas resultCanvas = new Canvas(result);
        for (int i = 0; i < mTitles.length; i++) {
            final int offsetY = (mInterval +mBarHeight)*i;
            mPath.reset();
            mPath.moveTo(0,offsetY);
            mRectF.set(0,offsetY, mBarCurrentWidth[i],offsetY+mBarHeight);
            mPath.addRoundRect(mRectF,radiusArray,Path.Direction.CW);
            srcCanvas.drawBitmap(mBarBitmap,0,offsetY,mBarPaint);
            maskCanvas.drawPath(mPath,mPaint);
        }
        resultCanvas.drawBitmap(src,0,0,mBarPaint);
        mPaint.setXfermode(MXfermode);
        resultCanvas.drawBitmap(mask,0,0,mPaint);
        mPaint.setXfermode(null);
        canvas.drawBitmap(result,0,0,mBarPaint);
        canvas.restore();
    }
    private void configBarBitmap(){
        if(mBarBitmap==null){
            mBarBitmap = Bitmap.createBitmap(mBarWidth, mBarHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mBarBitmap);
            Rect rect = new Rect();
            canvas.getClipBounds(rect);
            mGradientDrawable.setBounds(rect);
            mGradientDrawable.draw(canvas);
        }
    }
    private void getChartBarSize(){
        mBarWidth = (int) (mWidth*0.8f);
        mBarWidths[0] = mBarWidth*0.5f;
        mBarWidths[1] = mBarWidth*0.6f;
        mBarWidths[2] = mBarWidth*0.9f;
        mBarWidths[3] = mBarWidth*0.7f;
        mBarWidths[4] = mBarWidth*0.4f;
        mBarWidths[5] = mBarWidth*0.7f;
        mBarWidths[6] = mBarWidth*0.8f;
    }
    public void animateStart(long duration) {
        if (mValueAnimator != null) {
            mValueAnimator.cancel();
            mValueAnimator = null;
        }
        mValueAnimator = ValueAnimator.ofFloat(1f, 100f);
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float value = (Float) animation.getAnimatedValue();
                for (int i = 0; i < mBarWidths.length; i++) {
                    mBarCurrentWidth[i] = mBarWidths[i]*value/100f;
                }
                if (value == 100) {
                    mValueAnimator.cancel();
                    mValueAnimator = null;
                }
                invalidate();
            }
        });
        mValueAnimator.setDuration(duration).start();
    }

    public void setRadius(float leftTop, float rightTop, float rightBottom, float leftBottom) {
        radiusArray[0] = leftTop;
        radiusArray[1] = leftTop;
        radiusArray[2] = rightTop;
        radiusArray[3] = rightTop;
        radiusArray[4] = rightBottom;
        radiusArray[5] = rightBottom;
        radiusArray[6] = leftBottom;
        radiusArray[7] = leftBottom;
    }


    public static final int HORIZIONTAL = 0;
    public static final int VERTICAL = 1;
    @IntDef({HORIZIONTAL,VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction{}

    public void setDirection(@Direction int mDirection){
        this.mDirection = mDirection;
    }

}
