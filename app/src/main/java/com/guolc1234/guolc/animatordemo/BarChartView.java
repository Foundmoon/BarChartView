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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
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

    private float mTextSize;
    private int mTextBarMargin;//文字和柱状图顶部的间距
    private int mTextColor;
    private int mDirection = VERTICAL;
    private Bitmap mBarBitmap;
    private Drawable mBarChartDrawable;
    private Paint mPaint;
    private ValueAnimator mValueAnimator;
    private String[] mTextArray;
    private int mHeight, mWidth, mInterval;
    private float mTextHeight,mTextWidth;//单纯根据文字字号和字数决定的文字宽度
    private float mTextSpace;//文字沿着柱状图渐长方向上的文字占据的空间;
    private int mTextOuterWidth;//柱状图上方文字 限制的宽度,超过此宽度则换行
    private int mBarValueConstant = -1, mBarMaxValue = -1;//Y值为固定X值随动画增长
    private float mBarMaxValuePercent;//horizontal时,mBarMaxValue占据View宽度的比例,vertical模式时mBarMaxValue占据View高度的比例
    private Path mPath;
    private RectF mRectF = new RectF();
    private float[] radiusArray = new float[8];
    private float[] mBarCurrentValueArray;
    private float[] mBarMaxValueArray;
    private final Xfermode mXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private Bitmap srcBitmap, maskBitmap, resultBitmap, textBitmap;
    private Canvas srcCanvas, maskCanvas, resultCanvas, textCanvas;
    private boolean autoOffset;//自动根据文字字号进行首行缩进以完全实现文字,缺省为false;
    private boolean barChartChangeable;//柱状图背景图自高与否,缺省为false;
    private float mInitOffset;
    private StaticLayout[] mStaticLayoutArray;
    private boolean hasSetMaxPercent;
    private TextPaint mTextPaint;

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.BarChartView);
        mInterval = ta.getInt(R.styleable.BarChartView_intervalWidth, -1);
        if (mInterval == -1) {
            throw new RuntimeException("must set interval value");
        }
        mDirection = ta.getInt(R.styleable.BarChartView_direction, HORIZONTAL);
        mTextSize = ta.getDimensionPixelSize(R.styleable.BarChartView_textSize, DEFAULT_TEXT_SIZE);
        final int id = ta.getResourceId(R.styleable.BarChartView_textArray, 0);
        if (id == 0) {
            throw new RuntimeException("must set text array");
        }
        mTextColor = ta.getColor(R.styleable.BarChartView_textColor, Color.BLACK);
        mTextArray = context.getResources().getStringArray(id);
        autoOffset = ta.getBoolean(R.styleable.BarChartView_autoOffset, false);
        setBarConstantValueAndTextOuterWidth(ta.getInt(R.styleable.BarChartView_columnSize, 0));
        mTextBarMargin = ta.getInt(R.styleable.BarChartView_textMargin, DEFAULT_TEXT_MARGIN);
        barChartChangeable = ta.getBoolean(R.styleable.BarChartView_barChangeable, false);
        mBarChartDrawable = ta.getDrawable(R.styleable.BarChartView_barChartDrawable);
        final float mRadius = ta.getFloat(R.styleable.BarChartView_barRadius, 0);
        if (mRadius != 0) {
            setRadius(0, mRadius, mRadius, 0);
        }
        mBarMaxValuePercent = ta.getFraction(R.styleable.BarChartView_maxValuePercent,1,1, -1f);
        ta.recycle();
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        mPath = new Path();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int len = mTextArray.length;
        mBarCurrentValueArray = new float[len];
        mBarMaxValueArray = new float[len];
        mStaticLayoutArray = new StaticLayout[len];
        hasSetMaxPercent = mBarMaxValuePercent > 0f && mBarMaxValuePercent <= 1f;
        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(mTextSize);
        mTextPaint.setColor(mTextColor);
        mTextWidth = mTextPaint.measureText(mTextArray[0]);
        Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
        mTextPaint.getFontMetrics(fontMetrics);
        mTextHeight = fontMetrics.bottom - fontMetrics.top;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTextArray == null) {
            throw new RuntimeException("mTextArray must be not null!");
        }
        final int length = mTextArray.length;
        final float offset = mInitOffset = getInitOffset();
        if (mDirection == HORIZONTAL) {
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                if (mInterval == 0 || mBarValueConstant == 0) {
                    throw new RuntimeException("intervalWidth or columnSize must not be null");
                }
                int autoHeight = Math.round(offset * 2f + mBarValueConstant * length + mInterval * (length - 1));
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(autoHeight, MeasureSpec.EXACTLY);
            }
        } else {
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
                if (mInterval == 0 || mBarValueConstant == 0) {
                    throw new RuntimeException("intervalWidth or columnSize must not be null");
                }
                int autoWidth = Math.round(offset * 2f + mBarValueConstant * length + mInterval * (length - 1));
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(autoWidth, MeasureSpec.EXACTLY);
            }
        }
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(mWidth, mHeight);
        setBarConstantValueAndTextOuterWidth(mDirection == HORIZONTAL ? Math.round((mHeight - (length - 1) * 1f * mInterval - offset * 2f) / length) : Math.round((mWidth - (length - 1) * 1f * mInterval - offset * 2f) / length));

        configOuterWidth();
        configStaticLayout();
        setBarMaxValue();
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
        final int initOffset = (int) mInitOffset;
        if (mDirection == VERTICAL) {
            canvas.rotate(-90);
            canvas.translate(-mHeight, 0);
        }
        configBarBitmap();
        configBitmapAndCanvas(canvas);
        for (int i = 0; i < mTextArray.length; i++) {
            final int offset = (mInterval + mBarValueConstant) * i + initOffset;
            final StaticLayout currentStaticLayout = mStaticLayoutArray[i];
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
            final float textX = mRectF.width() + (mDirection == VERTICAL ? 0 : mTextBarMargin);
            final float textY = mRectF.top;
            final int textHeight = currentStaticLayout.getHeight();
            textCanvas.save();
            if (mDirection == VERTICAL) {
                textCanvas.rotate(90, textX, textY);
                textCanvas.translate(textX - (mTextOuterWidth - mBarValueConstant) * 0.5f, textY - textHeight - mTextBarMargin);
            } else {
                if (textHeight > mBarValueConstant) {
                    textCanvas.translate(textX, textY - (textHeight - mBarValueConstant) * 0.5f);
                } else {
                    textCanvas.translate(textX, textY + (mBarValueConstant - textHeight) * 0.5f);
                }
            }
            currentStaticLayout.draw(textCanvas);
            textCanvas.restore();
        }
        resultCanvas.drawBitmap(srcBitmap, 0, 0, mPaint);
        mPaint.setXfermode(mXfermode);
        resultCanvas.drawBitmap(maskBitmap, 0, 0, mPaint);
        mPaint.setXfermode(null);
        resultCanvas.drawBitmap(textBitmap, 0, 0, mPaint);
        canvas.drawBitmap(resultBitmap, 0, 0, mPaint);
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

    private float getInitOffset() {
        if (autoOffset) {
            return mDirection == HORIZONTAL ? mStaticLayoutArray[0].getHeight() * 0.5f : mStaticLayoutArray[0].getWidth() * 0.5f;
        } else {
            return mDirection == HORIZONTAL ? getPaddingTop() : getPaddingLeft();
        }
    }

    private void setBarConstantValueAndTextOuterWidth(int size) {
        mBarValueConstant = size;
        mTextOuterWidth = mDirection == HORIZONTAL ? mWidth - mTextBarMargin - mBarMaxValue : (int) (mBarValueConstant * 1.2f);
    }

    private void setBarMaxValue() {
        if (hasSetMaxPercent) {
            mBarMaxValue = (int) (mDirection == HORIZONTAL ? mWidth * mBarMaxValuePercent : mHeight * mBarMaxValuePercent);
        } else {
            if (mDirection == HORIZONTAL) {
                mBarMaxValue = mWidth - mTextBarMargin - mTextOuterWidth;
            } else {
                mBarMaxValue = mHeight - mTextBarMargin - mStaticLayoutArray[0].getHeight();
            }
        }
    }

    private void configStaticLayout() {
        for (int i = 0; i < mTextArray.length; i++) {
            mStaticLayoutArray[i] = new StaticLayout(mTextArray[i], mTextPaint, mTextOuterWidth,
                    mDirection == HORIZONTAL ? Layout.Alignment.ALIGN_NORMAL:Layout.Alignment.ALIGN_CENTER,
                    1.0F, 0.0F, true);
        }
    }

    private void configOuterWidth() {
        if (mDirection == HORIZONTAL) {
            if (hasSetMaxPercent) {
                mTextOuterWidth = (int) (mWidth * (1f - mBarMaxValuePercent) - mTextBarMargin);
            } else {
                mTextOuterWidth = (int) mTextWidth;
            }
        } else {
            mTextOuterWidth = Math.round(mBarValueConstant*1.2f);
        }
    }

}
