package com.android.prizefloatwindow.view;

import com.android.prizefloatwindow.R;
import com.android.prizefloatwindow.config.Config;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.RotateAnimation;



public class ArcLayout extends ViewGroup {
    private int mChildSize; // 子菜单项大小相同
    private int mChildPadding = 0;
    public static final float DEFAULT_FROM_DEGREES = 270.0f;
    public static final float DEFAULT_TO_DEGREES = 360.0f;
    private float mFromDegrees = DEFAULT_FROM_DEGREES;
    private float mToDegrees = DEFAULT_TO_DEGREES;
    private static final int DEFAULT_MIN_RADIUS = 90;
    private int mRadius;// 中心菜单圆点到子菜单中心的距离
    private boolean mExpanded = false;

    private int position = PathMenu.LEFT_TOP;
    private int centerX = 0;
    private int centerY = 0;

    public void computeCenterXY(int position) {
        switch (position) {
            case PathMenu.LEFT_TOP://左上
                centerX = getWidth() / 2 - getRadiusAndPadding();
                centerY = getHeight() / 2 - getRadiusAndPadding();
                break;
            case PathMenu.LEFT_CENTER://左中
                centerX = getWidth() / 2 - getRadiusAndPadding();
                centerY = getHeight() / 2;
                break;
            case PathMenu.LEFT_BOTTOM://左下
                centerX = getWidth() / 2 - getRadiusAndPadding();
                centerY = getHeight() / 2 + getRadiusAndPadding();
                break;
            case PathMenu.CENTER_TOP://上中
                centerX = getWidth() / 2;
                centerY = getHeight() / 2 - getRadiusAndPadding();
                break;
            case PathMenu.CENTER_BOTTOM://下中
                centerX = getWidth() / 2;
                centerY = getHeight() / 2 + getRadiusAndPadding();
                break;
            case PathMenu.RIGHT_TOP://右上
                centerX = getWidth() / 2 + getRadiusAndPadding();
                centerY = getHeight() / 2 - getRadiusAndPadding();
                break;
            case PathMenu.RIGHT_CENTER://右中
                centerX = getWidth() / 2 + getRadiusAndPadding();
                centerY = getHeight() / 2;
                break;
            case PathMenu.RIGHT_BOTTOM://右下
                centerX = getWidth() / 2 + getRadiusAndPadding();
                centerY = getHeight() / 2 + getRadiusAndPadding();
                break;

            case PathMenu.CENTER:
                centerX = getWidth() / 2;
                centerY = getHeight() / 2;
                break;
        }
    }

    private int getRadiusAndPadding() {
        return mRadius + (mChildPadding * 2);
    }

    public ArcLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 获取自定义属性，设定默认值
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs,R.styleable.ArcLayout, 0, 0);
            mFromDegrees = a.getFloat(R.styleable.ArcLayout_fromDegrees,DEFAULT_FROM_DEGREES);
            mToDegrees = a.getFloat(R.styleable.ArcLayout_toDegrees,DEFAULT_TO_DEGREES);
            mChildSize = Math.max(a.getDimensionPixelSize(R.styleable.ArcLayout_childSize, 0), 0);

            a.recycle();
        }
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * 计算半径
     */
    private static int computeRadius(final float arcDegrees,final int childCount, final int childSize, final int childPadding,final int minRadius) {
        final float perDegrees = arcDegrees == 360 ? (arcDegrees) / (childCount) : (arcDegrees) / (childCount - 1);

        final float perHalfDegrees = perDegrees / 2;
        final int perSize = childSize + childPadding;
        final int radius = (int) ((perSize / 2) / Math.sin(Math.toRadians(perHalfDegrees)));
       // Log.d("snail_animal","-----------computeRadius--perDegrees=="+perDegrees+"  perHalfDegrees=="+perHalfDegrees+"  perSize=="+perSize+"  radius=="+radius+"  minRadius=="+minRadius);// 
        return Math.max(radius, minRadius);
    }

    /**
     * 计算子菜单项的范围
     */
    private static Rect computeChildFrame(final int centerX, final int centerY,
                                          final int radius, final float degrees, final int size) {
        //子菜单项中心点
        final double childCenterX = centerX + radius* Math.cos(Math.toRadians(degrees));
        final double childCenterY = centerY + radius* Math.sin(Math.toRadians(degrees));
        //子菜单项的左上角，右上角，左下角，右下角
        return new Rect((int) (childCenterX - size / 2),
                (int) (childCenterY - size / 2),
                (int) (childCenterX + size / 2),
                (int) (childCenterY + size / 2));
    }

    public int getRadius() {
        return mRadius;
    }

    /**
     * 子菜单项大小
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int radius = mRadius = computeRadius(Math.abs(mToDegrees - mFromDegrees), getChildCount(),mChildSize, mChildPadding, DEFAULT_MIN_RADIUS)+Config.dp2px(15);
        //Log.d("snail_animal","-----------onMeasure--mRadius=="+mRadius+"  radius=="+radius);// 
        int layoutPadding = 0;
        int size = radius * 2 + mChildSize + mChildPadding+ layoutPadding * 2;

        setMeasuredDimension(size, size);

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(mChildSize,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(mChildSize,MeasureSpec.EXACTLY));
        }
    }

    /**
     * 子菜单项位置
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
       // final int centerX = getWidth() / 2 - mRadius;////add
       // final int centerY = getHeight() / 2;////////add
        computeCenterXY(position);
        //当子菜单要收缩时radius=0，在ViewGroup坐标中心
        final int radius = mExpanded ? mRadius : 0;

        final int childCount = getChildCount();
//        final float perDegrees =Math.abs (mToDegrees - mFromDegrees) / (childCount - 1);
        final float perDegrees = Math.abs(mToDegrees - mFromDegrees) == 360 ? (Math.abs(mToDegrees - mFromDegrees)) / (childCount) : (Math.abs(mToDegrees - mFromDegrees)) / (childCount - 1);

       // Log.d("snail_animal","--------------onLayout-----radius=="+radius);// 
        float degrees = mFromDegrees;
        for (int i = 0; i < childCount; i++) {
            Rect frame = computeChildFrame(centerX, centerY, radius, degrees,mChildSize);
            if(position == PathMenu.LEFT_TOP || position == PathMenu.LEFT_CENTER || position == PathMenu.LEFT_BOTTOM ){
            	degrees += perDegrees;
            }else {
            	degrees -= perDegrees;
			}
            //Log.d("snail_animal","--------------onLayout-----i=="+i+"  left=="+frame.left+" right=="+frame.right+"  top=="+frame.top+"  bottom=="+frame.bottom);// 
            getChildAt(i).layout(frame.left, frame.top, frame.right,frame.bottom);
        }
    }

    /**
     * 计算动画开始时的偏移量
     */
    private static long computeStartOffset(final int childCount,
                                           final boolean expanded, final int index, final float delayPercent,
                                           final long duration, Interpolator interpolator) {
        final float delay = delayPercent * duration;
        final long viewDelay = (long) (getTransformedIndex(expanded,
                childCount, index) * delay);
        final float totalDelay = delay * childCount;

        float normalizedDelay = viewDelay / totalDelay;
        normalizedDelay = interpolator.getInterpolation(normalizedDelay);

        return (long) (normalizedDelay * totalDelay);
    }

    /**
     * 变换时的子菜单项索引
     */
    private static int getTransformedIndex(final boolean expanded,
                                           final int count, final int index) {
        if (expanded) {
            return count - 1 - index;
        }

        return index;
    }

    /**
     * 展开动画
     */
    private static Animation createExpandAnimation(float fromXDelta,
                                                   float toXDelta, float fromYDelta, float toYDelta, long startOffset,
                                                   long duration, Interpolator interpolator) {
        //Animation animation = new RotateAndTranslateAnimation(0, toXDelta, 0,toYDelta, 0, 720);
    	Animation animation = new RotateAndTranslateAnimation(fromXDelta, toXDelta, fromYDelta,toYDelta, 0, 720);
       // animation.setStartOffset(startOffset);
        animation.setDuration(duration);
       // animation.setInterpolator(interpolator);
        animation.setFillAfter(true);

        return animation;
    }

    /**
     * 收缩动画
     */
    private static Animation createShrinkAnimation(float fromXDelta,
                                                   float toXDelta, float fromYDelta, float toYDelta, long startOffset,
                                                   long duration, Interpolator interpolator) {
        AnimationSet animationSet = new AnimationSet(false);
        animationSet.setFillAfter(true);
        //收缩过程中，child 逆时针自旋转360度
       // final long preDuration = duration / 2;   
        /*Animation rotateAnimation = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        //rotateAnimation.setStartOffset(startOffset);
        rotateAnimation.setDuration(duration);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setFillAfter(true);

        animationSet.addAnimation(rotateAnimation);*/
        //收缩过程中位移，并逆时针旋转360度
        Animation translateAnimation = new RotateAndTranslateAnimation(fromXDelta,
                toXDelta, fromYDelta, toYDelta, 360, 720);
        //translateAnimation.setStartOffset(startOffset + preDuration);
        //translateAnimation.setDuration(duration-preDuration);
        translateAnimation.setDuration(duration);
        //translateAnimation.setInterpolator(interpolator);
        translateAnimation.setFillAfter(true);

        animationSet.addAnimation(translateAnimation);

        return animationSet;
    }

    /**
     * 绑定子菜单项动画
     */
    private void bindChildAnimation(final View child, final int index,final long duration) {
    	//Log.d("snail_animal","-----------switchState--bindChildAnimation---start-----mToDegrees=="+mToDegrees+"  mFromDegrees=="+mFromDegrees+"  centerX=="+centerX+"  centerY=="+centerY+"  mRadius=="+mRadius+"  index=="+index);// 
        final boolean expanded = mExpanded; 
        computeCenterXY(position);
        mRadius = computeRadius(Math.abs(mToDegrees - mFromDegrees), getChildCount(),mChildSize, mChildPadding, DEFAULT_MIN_RADIUS)+Config.dp2px(15);
        final int radius = expanded ? 0 : mRadius; 
        final int childCount = getChildCount();
        final float perDegrees = Math.abs(mToDegrees - mFromDegrees) == 360 ? (mToDegrees - mFromDegrees) / (childCount) : (mToDegrees - mFromDegrees) / (childCount - 1);
       // Rect frame = computeChildFrame(centerX, centerY, radius, mFromDegrees + index * perDegrees, mChildSize);
        //Log.d("snail_animal","--------------bindChildAnimation-----index=="+index+"  left=="+frame.left+" right=="+frame.right+"  top=="+frame.top+"  bottom=="+frame.bottom);// 
        //final int toXDelta = frame.left - child.getLeft();//展开或收缩动画,child沿X轴位移距离 
       // final int toYDelta = frame.top - child.getTop();//展开或收缩动画,child沿Y轴位移距离
        final int toXDelta = (int)(-mRadius* Math.sin(Math.toRadians(perDegrees*index)));//展开或收缩动画,child沿X轴位移距离
        final int toYDelta = (int)(mRadius* Math.cos(Math.toRadians(perDegrees*index)));//展开或收缩动画,child沿Y轴位移距离
        //Interpolator interpolator = mExpanded ? new AccelerateInterpolator(): new OvershootInterpolator(1.5f);
        Interpolator interpolator =  new LinearInterpolator();
       // final long startOffset = computeStartOffset(childCount, mExpanded,index, 0.1f, duration, interpolator);
        final long startOffset = 0;
        //TODO toXDelta toYDelta 设置为0 为什么可以
        //mExpanded为true，已经展开，收缩动画；为false,展开动画 
        //Log.d("snail_animal","-----------switchState--bindChildAnimation--  index=="+index+"  toXDelta=="+toXDelta+"  toYDelta=="+toYDelta);// 
        Animation animation = mExpanded ? createShrinkAnimation(0, toXDelta, 0, //createShrinkAnimation
        		toYDelta, startOffset, duration, interpolator)
                : createExpandAnimation(toXDelta, 0, toYDelta, 0, startOffset,
                		duration, interpolator);

        final boolean isLast = getTransformedIndex(expanded, childCount, index) == childCount - 1;
        animation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (isLast) {
                    postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            onAllAnimationsEnd();
                        }
                    }, 0);
                } 
            }
        });
        //child.setAnimation(animation);
        child.startAnimation(animation);
    }

    public boolean isExpanded() {
        return mExpanded;
    }


    /**
     * 设定弧度
     */
    public void setArc(float fromDegrees, float toDegrees, int position) {
        this.position = position;
        if (mFromDegrees == fromDegrees && mToDegrees == toDegrees) {
            return;
        }

        mFromDegrees = fromDegrees;
        mToDegrees = toDegrees;
        computeCenterXY(position);
        requestLayout();
    }

    /**
     * 设定弧度
     */
    public void setArc(float fromDegrees, float toDegrees) {
        if (mFromDegrees == fromDegrees && mToDegrees == toDegrees) {
            return;
        }

        mFromDegrees = fromDegrees;
        mToDegrees = toDegrees;
        computeCenterXY(position);
        requestLayout();
    }

    /**
     * 设定子菜单项大小
     */
    public void setChildSize(int size) {
        if (mChildSize == size || size < 0) {
            return;
        }

        mChildSize = size;

        requestLayout();
    }

    public int getChildSize() {
        return mChildSize;
    }


    /**
     * 切换中心按钮的展开缩小
     */
    public void switchState(final boolean showAnimation, int position) {
        this.position = position;
        if (showAnimation) {//
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                bindChildAnimation(getChildAt(i), i, 150); 
            }
        }
        mExpanded = !mExpanded;
        if (!showAnimation) {
            requestLayout();
        }
        invalidate();
        Log.d("snail_onTouch", "----switchState-------");
    }
    public void setExpand(boolean expand){
    	mExpanded = expand;
    }

    /**
     * 切换中心按钮的展开缩小
     */
    public void switchState(final boolean showAnimation) {
    	Log.d("snail_animal","-----------switchState-----start-showAnimation=="+showAnimation);// 
        if (showAnimation) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                bindChildAnimation(getChildAt(i), i, 150);
            }
        }

        mExpanded = !mExpanded;

        if (!showAnimation) {
            requestLayout();
        }

        invalidate();
    }

  
    /**
     * 结束所有动画
     */
    private void onAllAnimationsEnd() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).clearAnimation();
        }
        requestLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    	// TODO Auto-generated method stub
    	if (event.getKeyCode() == KeyEvent.KEYCODE_BACK){
    	} 
    	return super.dispatchKeyEvent(event);
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	// TODO Auto-generated method stub
    	if (event.getKeyCode() == KeyEvent.KEYCODE_BACK){
    	} 
    	return super.onKeyDown(keyCode, event);
    }
}