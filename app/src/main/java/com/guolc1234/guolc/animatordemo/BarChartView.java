package com.guolc1234.guolc.animatordemo;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
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
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Created by guolc on 2018/3/20.
 */

public class BarChartView extends View {

    // FIXME: guolc 2018/3/22 BarChartView  暂时字宽只取mTextArray[0]文字宽度,最好是数组每个索引的文字字数一致

    private final int DEFAULT_TEXT_SIZE = 39;//字号缺省值
    private final int DEFAULT_TEXT_MARGIN = 20;//柱状图和文字缺省值
    private final int DEFAULT_BAR_INTERVAL = 18;//柱状图间距缺省值

    private float mTextSize;
    private int mTextBarMargin;//文字和柱状图顶部的间距
    private int mTextColor;
    private int mDirection = VERTICAL;
    private Bitmap mBarBitmap;
    private Paint mPaint, mTextPaint;
    private Drawable mBarChartDrawable;
    private ValueAnimator mValueAnimator;
    private String[] mTextArray;
    private int mHeight, mInterval;
    private float  mTextHeight, mFontOffset;
    private float mTextSpace;//文字沿着柱状图渐长方向上的文字占据的空间;
    private float mTextWidth;//单纯根据文字字号和字数决定的文字宽度
    private int mBarValueConstant, mBarMaxValue;//Y值为固定X值随动画增长
    private Path mPath;
    private RectF mRectF = new RectF();
    private float[] radiusArray  = new float[8];
    private float[] mBarCurrentValueArray;
    private float[] mBarMaxValueArray;
    private final Xfermode mXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private Bitmap srcBitmap, maskBitmap, resultBitmap, textBitmap;
    private Canvas srcCanvas, maskCanvas, resultCanvas, textCanvas;
    private boolean autoOffset;//自动根据文字字号进行首行缩进以完全实现文字,缺省为false;
    private boolean barChartChangeable;//柱状图背景图自高与否,缺省为false;
    private boolean autoRadius;//自动增加圆角,缺省为true

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.BarChartView);
        mInterval = ta.getInt(R.styleable.BarChartView_intervalWidth, DEFAULT_BAR_INTERVAL);
        mDirection = ta.getInt(R.styleable.BarChartView_direction, HORIZONTAL);
        mTextSize = ta.getDimensionPixelSize(R.styleable.BarChartView_textSize, DEFAULT_TEXT_SIZE);
        final int id = ta.getResourceId(R.styleable.BarChartView_textArray, 0);
        if (id == 0) {
            throw new RuntimeException("must set text array");
        }
        mTextColor = ta.getColor(R.styleable.BarChartView_textColor, Color.BLACK);
        mTextArray = context.getResources().getStringArray(id);
        mBarCurrentValueArray = new float[mTextArray.length];
        mBarMaxValueArray = new float[mTextArray.length];
        autoOffset = ta.getBoolean(R.styleable.BarChartView_autoOffset, false);
        mBarValueConstant = ta.getInt(R.styleable.BarChartView_columnSize, 0);
        mTextBarMargin = ta.getInt(R.styleable.BarChartView_textMargin, DEFAULT_TEXT_MARGIN);
        barChartChangeable = ta.getBoolean(R.styleable.BarChartView_barChangeable, false);
        mBarChartDrawable = ta.getDrawable(R.styleable.BarChartView_barChartDrawable);
        final float mRadius = ta.getFloat(R.styleable.BarChartView_barRadius,0);
        if(mRadius!=0){
            setRadius(0, mRadius, mRadius, 0);
        }
        autoRadius = ta.getBoolean(R.styleable.BarChartView_autoRadius,true);
        ta.recycle();
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        mPath = new Path();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(mTextSize);
        mTextPaint.setColor(mTextColor);
        mTextWidth = mTextPaint.measureText(mTextArray[0]);
        getFontParam();
        mTextSpace = mDirection == HORIZONTAL ? mTextWidth : mTextHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTextArray == null) {
            throw new RuntimeException("mTextArray must be not null!");
        }
        final int length = mTextArray.length;
        if (mDirection == HORIZONTAL) {
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                if (mInterval == 0 || mBarValueConstant == 0) {
                    throw new RuntimeException("intervalWidth or columnSize must not be null");
                }
                int autoHeight = Math.round(getInitOffset() * 2f + mBarValueConstant * length + mInterval * (length - 1));
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(autoHeight, MeasureSpec.EXACTLY);
            }
        } else {
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
                if (mInterval == 0 || mBarValueConstant == 0) {
                    throw new RuntimeException("intervalWidth or columnSize must not be null");
                }
                int autoWidth = Math.round(getInitOffset() * 2f + mBarValueConstant * length + mInterval * (length - 1));
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(autoWidth, MeasureSpec.EXACTLY);
            }
        }
        final int mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(mWidth, mHeight);
        final float offset = getInitOffset();
        switch (mDirection) {
            case HORIZONTAL:
                if(autoRadius){
                    final float mRadius = (mWidth - mTextSpace) * 0.5f;
                    setRadius(0, mRadius, mRadius, 0);
                }
                mBarValueConstant = (int) ((mHeight - (length - 1) * 1f * mInterval - offset * 2f) / length);
                mBarMaxValue = (int) (mWidth - mTextSpace - mTextBarMargin);
                break;
            case VERTICAL:
                if(autoRadius){
                    final float mRadius = (mHeight - mTextSpace) * 0.5f;
                    setRadius(0, mRadius, mRadius, 0);
                }
                mBarValueConstant = (int) ((mWidth - (length - 1) * 1f * mInterval - offset * 2f) / length);
                mBarMaxValue = (int) (mHeight - mTextSpace - mTextBarMargin);
                break;
        }

        mBarMaxValueArray[0] = mBarMaxValue * 0.2f;
        mBarMaxValueArray[1] = mBarMaxValue * 0.3f;
        mBarMaxValueArray[2] = mBarMaxValue * 0.4f;
        mBarMaxValueArray[3] = mBarMaxValue * 1f;
        mBarMaxValueArray[4] = mBarMaxValue * 0.6f;
        mBarMaxValueArray[5] = mBarMaxValue * 0.7f;
        mBarMaxValueArray[6] = mBarMaxValue * 0.8f;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        final int initOffset = (int) getInitOffset();
        if (mDirection == VERTICAL) {
            canvas.rotate(-90);
            canvas.translate(-mHeight, 0);
        }
        configBarBitmap();
        configBitmapAndCanvas(canvas);
        final float mTextOffset = mFontOffset;
        for (int i = 0; i < mTextArray.length; i++) {
            final int offset = (mInterval + mBarValueConstant) * i + initOffset;
            mPath.reset();
            mRectF.set(0, offset, mBarCurrentValueArray[i], offset + mBarValueConstant);
            mPath.addRoundRect(mRectF, radiusArray, Path.Direction.CW);
            if (barChartChangeable) {
                mBarChartDrawable.setBounds(0, offset, Math.round(mBarCurrentValueArray[i]), offset + mBarValueConstant);
                mBarChartDrawable.draw(srcCanvas);
            } else {
                srcCanvas.drawBitmap(mBarBitmap, 0, offset, null);
            }
            maskCanvas.drawPath(mPath, mPaint);
            final float textX = mRectF.width() + mTextBarMargin;
            final float textY = mRectF.centerY() + mTextOffset;
            textCanvas.save();
            if (mDirection == VERTICAL) {
                textCanvas.rotate(90, textX, textY);
                textCanvas.translate(-mTextOffset - mBarValueConstant * 0.5f, 0);
                if (mTextWidth > mBarValueConstant) {
                    textCanvas.translate(-Math.abs(mTextWidth - mBarValueConstant) * 0.5f, 0f);
                } else {
                    textCanvas.translate(Math.abs(mTextWidth - mBarValueConstant) * 0.5f, 0f);
                }
            }
            textCanvas.drawText(mTextArray[i], textX, textY, mTextPaint);
            textCanvas.restore();
        }
        resultCanvas.drawBitmap(srcBitmap, 0, 0, null);
        mPaint.setXfermode(mXfermode);
        resultCanvas.drawBitmap(maskBitmap, 0, 0, mPaint);
        mPaint.setXfermode(null);
        resultCanvas.drawBitmap(textBitmap, 0, 0, null);
        canvas.drawBitmap(resultBitmap, 0, 0, null);
        canvas.restore();
    }

    private void configBarBitmap() {
        if (!barChartChangeable && mBarBitmap == null) {
            mBarBitmap = Bitmap.createBitmap(mBarMaxValue, mBarValueConstant, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mBarBitmap);
            Rect rect = new Rect();
            canvas.getClipBounds(rect);
            mBarChartDrawable.setBounds(rect);
            mBarChartDrawable.draw(canvas);
        }
    }

    private void configBitmapAndCanvas(Canvas canvas) {
        if (srcBitmap == null || srcCanvas == null) {
            Rect mRect = new Rect();
            canvas.getClipBounds(mRect);
            srcBitmap = Bitmap.createBitmap(mRect.width(), mRect.height(), Bitmap.Config.ARGB_8888);
            maskBitmap = Bitmap.createBitmap(srcBitmap);
            resultBitmap = Bitmap.createBitmap(srcBitmap);
            textBitmap = Bitmap.createBitmap(srcBitmap);
            srcCanvas = new Canvas(srcBitmap);
            maskCanvas = new Canvas(maskBitmap);
            resultCanvas = new Canvas(resultBitmap);
            textCanvas = new Canvas(textBitmap);
        } else {
            srcCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            resultCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            textCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }

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
                for (int i = 0; i < mBarMaxValueArray.length; i++) {
                    mBarCurrentValueArray[i] = mBarMaxValueArray[i] * (value / 100f);
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


    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    @IntDef({HORIZONTAL, VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction {
    }

    public void setDirection(@Direction int mDirection) {
        this.mDirection = mDirection;
    }

    private void getFontParam() {
        if (mFontOffset == 0f || mTextHeight == 0f) {
            Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
            mTextPaint.getFontMetrics(fontMetrics);
            mFontOffset = (fontMetrics.descent - fontMetrics.ascent) * 0.5f - fontMetrics.descent;
            mTextHeight = fontMetrics.bottom - fontMetrics.top;
        }
    }

    private float getInitOffset() {
        if (autoOffset) {
            getFontParam();
            return mTextHeight * 0.5f;
        } else {
            return mDirection == HORIZONTAL ? getPaddingTop() : getPaddingLeft();
        }
    }
}
