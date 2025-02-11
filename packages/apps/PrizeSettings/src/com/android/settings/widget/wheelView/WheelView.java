/*
 *  Android Wheel Control.
 *  https://code.google.com/p/android-wheel/
 *  
 *  Copyright 2010 Yuri Kanivets
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.settings.widget.wheelView;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.Scroller;
import com.android.settings.R;

import java.text.DateFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * Numeric wheel view.
 * 
 * @author Yuri Kanivets
 */
public class WheelView extends View {
	
	protected static final String TAG = "WheelView";
	/** Scrolling duration */
	private static final int SCROLLING_DURATION = 400;

	/** Minimum delta for scrolling */
	private static final int MIN_DELTA_FOR_SCROLLING = 1;

	/** Current value & label text color */
//	private static final int VALUE_TEXT_COLOR = 0xFF4bd1c3;//0xFF146dc9 ;
	private static final int VALUE_TEXT_COLOR = 0xFF3478f6;//0xFF146dc9 ;LIJIMENG
//	0xFF146dc9
	/** Items text color */
	private static final int ITEMS_TEXT_COLOR = 0xFF646464;
	//private static final int ITEMS_TEXT_COLOR = 0xFF3478f6;

	/** Top and bottom shadows colors */
//	private static final int[] SHADOWS_COLORS = new int[] { 0xFF111111,
//			0x00AAAAAA, 0x00AAAAAA };
	
	private static final int[] SHADOWS_COLORS = new int[] { 0x80FFFFFF,
		0x80FFFFFF, 0x80FFFFFF };

	/** Additional items height (is added to standard text item height) */
	private static final int ADDITIONAL_ITEM_HEIGHT = 35;

	/*PRIZE-界面调整 wanzhijuan 2015-7-3 start */
	/** Text size dp*/
	private static final int TEXT_SIZE = 17;

	/** Top and bottom items offset (to hide that) */
	private static final int ITEM_OFFSET = 6;//TEXT_SIZE / 5;
	/*PRIZE-界面调整 wanzhijuan 2015-7-3 end */

	/** Additional width for items layout */
	private static final int ADDITIONAL_ITEMS_SPACE = 200;

	/** Label offset */
	private static final int LABEL_OFFSET = 8;

	/** Left and right padding value */
//	private static final int PADDING = 80;
	private static int PADDING = 80;
	/** Default count of visible items */
	private static final int DEF_VISIBLE_ITEMS = 5;

	// Wheel Values
	private WheelAdapter adapter = null;
	private int currentItem = 0;
	
	// Widths
	private int itemsWidth = 0;
	private int labelWidth = 0;

	// Count of visible items
	private int visibleItems = DEF_VISIBLE_ITEMS;
	
	// Item height
	private int itemHeight = 0;

	// Text paints
	private TextPaint itemsPaint;
	private TextPaint valuePaint;

	// Layouts
	private StaticLayout itemsLayout;
	private StaticLayout labelLayout;
	private StaticLayout valueLayout;

	// Label & background
	private String label;
	private Drawable centerDrawable;

	// Shadows drawables
	private GradientDrawable topShadow;
	private GradientDrawable bottomShadow;

	// Scrolling
	private boolean isScrollingPerformed; 
	private int scrollingOffset;

	// Scrolling animation
	private GestureDetector gestureDetector;
	private Scroller scroller;
	private int lastScrollY;

	// Cyclic
	boolean isCyclic = false;
	
	// Listeners
	private List<OnWheelChangedListener> changingListeners = new LinkedList<OnWheelChangedListener>();
	private List<OnWheelScrollListener> scrollingListeners = new LinkedList<OnWheelScrollListener>();
	
	/*PRIZE-界面调整 wanzhijuan 2015-7-3 start */
	private int mTextSize;//px
	private int mValueTextSize;//px
	/** 屏幕密度**/
    private float mDensity;
    private int mAdditionalItemHeight;
    private boolean mIsDrawRightLine;
    private TextPaint mLinePaint;
	private int location;//left 1,right 2

	public void setDrawRightLine(boolean isDrawRightLine) {
		this.mIsDrawRightLine = isDrawRightLine;
	}
	/*PRIZE-界面调整 wanzhijuan 2015-7-3 end */

	/**
	 * Constructor
	 */
	public WheelView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray typedArray = context.obtainStyledAttributes(attrs,R.styleable.PrizeWheekView);
		location = typedArray.getInteger(R.styleable.PrizeWheekView_location,0);
		typedArray.recycle();
		initData(context);
	}

	/**
	 * Constructor
	 */
	public WheelView(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray typedArray = context.obtainStyledAttributes(attrs,R.styleable.PrizeWheekView);
		location = typedArray.getInteger(R.styleable.PrizeWheekView_location,0);
		typedArray.recycle();
		initData(context);
	}

	/**
	 * Constructor
	 */
	public WheelView(Context context) {
		super(context);
		initData(context);
	}
	
	/**
	 * Initializes class data
	 * @param context the context
	 */
	private void initData(Context context) {
		if(!android.text.format.DateFormat.is24HourFormat(getContext())){
			PADDING = 0;
		}else{
			PADDING = 80;
		}
		gestureDetector = new GestureDetector(context, gestureListener);
		gestureDetector.setIsLongpressEnabled(false);
		
		scroller = new Scroller(context);
		
		// 获取屏幕密度
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mDensity = metrics.density;
        
        mTextSize = (int) (mDensity * TEXT_SIZE);
		mValueTextSize = (int) (mDensity * 20);
        int height = (int) context.getResources().getDimension(R.dimen.prize_hours_select_wheel_height);//215dp
        mAdditionalItemHeight = (height - mTextSize * visibleItems) / visibleItems;
        Log.i(TAG, "initData() mTextSize=" + mTextSize);
	}
	
	/**
	 * Gets wheel adapter
	 * @return the adapter
	 */
	public WheelAdapter getAdapter() {
		return adapter;
	}
	
	/**
	 * Sets wheel adapter
	 * @param adapter the new wheel adapter
	 */
	public void setAdapter(WheelAdapter adapter) {
		this.adapter = adapter;
		invalidateLayouts();
		invalidate();
	}
	
	/**
	 * Set the the specified scrolling interpolator
	 * @param interpolator the interpolator
	 */
	public void setInterpolator(Interpolator interpolator) {
		scroller.forceFinished(true);
		scroller = new Scroller(getContext(), interpolator);
	}
	
	/**
	 * Gets count of visible items
	 * 
	 * @return the count of visible items
	 */
	public int getVisibleItems() {
		return visibleItems;
	}

	/**
	 * Sets count of visible items
	 * 
	 * @param count
	 *            the new count
	 */
	public void setVisibleItems(int count) {
		visibleItems = count;
		invalidate();
	}

	/**
	 * Gets label
	 * 
	 * @return the label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Sets label
	 * 
	 * @param newLabel
	 *            the label to set
	 */
	public void setLabel(String newLabel) {
		if (label == null || !label.equals(newLabel)) {
			label = newLabel;
			labelLayout = null;
			invalidate();
		}
	}
	
	/**
	 * Adds wheel changing listener
	 * @param listener the listener 
	 */
	public void addChangingListener(OnWheelChangedListener listener) {
		changingListeners.add(listener);
	}

	/**
	 * Removes wheel changing listener
	 * @param listener the listener
	 */
	public void removeChangingListener(OnWheelChangedListener listener) {
		changingListeners.remove(listener);
	}
	
	/**
	 * Notifies changing listeners
	 * @param oldValue the old wheel value
	 * @param newValue the new wheel value
	 */
	protected void notifyChangingListeners(int oldValue, int newValue) {
		for (OnWheelChangedListener listener : changingListeners) {
			listener.onChanged(this, oldValue, newValue);
		}
	}

	/**
	 * Adds wheel scrolling listener
	 * @param listener the listener 
	 */
	public void addScrollingListener(OnWheelScrollListener listener) {
		scrollingListeners.add(listener);
	}

	/**
	 * Removes wheel scrolling listener
	 * @param listener the listener
	 */
	public void removeScrollingListener(OnWheelScrollListener listener) {
		scrollingListeners.remove(listener);
	}
	
	/**
	 * Notifies listeners about starting scrolling
	 */
	protected void notifyScrollingListenersAboutStart() {
		for (OnWheelScrollListener listener : scrollingListeners) {
			listener.onScrollingStarted(this);
		}
	}

	/**
	 * Notifies listeners about ending scrolling
	 */
	protected void notifyScrollingListenersAboutEnd() {
		for (OnWheelScrollListener listener : scrollingListeners) {
			listener.onScrollingFinished(this);
		}
	}

	/**
	 * Gets current value
	 * 
	 * @return the current value
	 */
	public int getCurrentItem() {
		return currentItem;
	}

	/**
	 * Sets the current item. Does nothing when index is wrong.
	 * 
	 * @param index the item index
	 * @param animated the animation flag
	 */
	public void setCurrentItem(int index, boolean animated) {
		if (adapter == null || adapter.getItemsCount() == 0) {
			return; // throw?
		}
		if (index < 0 || index >= adapter.getItemsCount()) {
			if (isCyclic) {
				while (index < 0) {
					index += adapter.getItemsCount();
				}
				index %= adapter.getItemsCount();
			} else{
				return; // throw?
			}
		}
		if (index != currentItem) {
			if (animated) {
				scroll(index - currentItem, SCROLLING_DURATION);
			} else {
				invalidateLayouts();
			
				int old = currentItem;
				currentItem = index;
			
				notifyChangingListeners(old, currentItem);
			
				invalidate();
			}
		}
	}

	/**
	 * Sets the current item w/o animation. Does nothing when index is wrong.
	 * 
	 * @param index the item index
	 */
	public void setCurrentItem(int index) {
		setCurrentItem(index, false);
	}	
	
	/**
	 * Tests if wheel is cyclic. That means before the 1st item there is shown the last one
	 * @return true if wheel is cyclic
	 */
	public boolean isCyclic() {
		return isCyclic;
	}

	/**
	 * Set wheel cyclic flag
	 * @param isCyclic the flag to set
	 */
	public void setCyclic(boolean isCyclic) {
		this.isCyclic = isCyclic;
		
		invalidate();
		invalidateLayouts();
	}

	/**
	 * Invalidates layouts
	 */
	private void invalidateLayouts() {
		itemsLayout = null;
		valueLayout = null;
		scrollingOffset = 0;
	}

	/**
	 * Initializes resources
	 */
	private void initResourcesIfNecessary() {
		if (itemsPaint == null) {
			itemsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG
					| Paint.FAKE_BOLD_TEXT_FLAG);
			//itemsPaint.density = getResources().getDisplayMetrics().density;
			itemsPaint.setStrokeWidth(0);
			itemsPaint.setTextSize(mTextSize);
		}

		if (valuePaint == null) {
			valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG
					| Paint.FAKE_BOLD_TEXT_FLAG | Paint.DITHER_FLAG);
			//valuePaint.density = getResources().getDisplayMetrics().density;
			valuePaint.setTextSize(mValueTextSize);
			valuePaint.setShadowLayer(0.1f, 0, 0.1f, 0xFFFfffff);  /*prize-添加阴影区域-lixing-2015-6-17-start*/
		}
		
		/*PRIZE-界面调整 wanzhijuan 2015-7-3 start */
		if (mLinePaint == null) {
			mLinePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG
					| Paint.FAKE_BOLD_TEXT_FLAG | Paint.DITHER_FLAG);
			mLinePaint.setColor(getResources().getColor(R.color.dashboard_category_title_textcolor));
			mLinePaint.setAntiAlias(true);
			mLinePaint.setStrokeWidth(0);
		}
		/*PRIZE-界面调整 wanzhijuan 2015-7-3 end */

		if (centerDrawable == null) {
			centerDrawable = getContext().getResources().getDrawable(R.drawable.wheel_val);
		}

		if (topShadow == null) {
			topShadow = new GradientDrawable(Orientation.TOP_BOTTOM, SHADOWS_COLORS);
		}

		if (bottomShadow == null) {
			bottomShadow = new GradientDrawable(Orientation.BOTTOM_TOP, SHADOWS_COLORS);
		}

		setBackgroundResource(R.drawable.wheel_bg);
	}

	/**
	 * Calculates desired height for layout
	 * 
	 * @param layout
	 *            the source layout
	 * @return the desired layout height
	 */
	private int getDesiredHeight(Layout layout) {
		if (layout == null) {
			return 0;
		}
		int height = getItemHeight();
		int desired = height * visibleItems - ITEM_OFFSET * 2
				- mAdditionalItemHeight;
		Log.i(TAG, "getDesiredHeight() itemHeight=" + height + " visibleItems=" + visibleItems + " desired=" + desired);
		// Check against our minimum height
		desired = Math.max(desired, getSuggestedMinimumHeight());
		Log.i(TAG, "getDesiredHeight() after desired=" + desired);
		return desired;
	}

	/**
	 * Returns text item by index
	 * @param index the item index
	 * @return the item or null
	 */
	private String getTextItem(int index) {
		if (adapter == null || adapter.getItemsCount() == 0) {
			return null;
		}
		int count = adapter.getItemsCount();
		if ((index < 0 || index >= count) && !isCyclic) {
			return null;
		} else {
			while (index < 0) {
				index = count + index;
			}
		}
		
		index %= count;
		return adapter.getItem(index);
	}
	
	/**
	 * Builds text depending on current value
	 * 
	 * @param useCurrentValue
	 * @return the text
	 */
	private String buildText(boolean useCurrentValue) {
		StringBuilder itemsText = new StringBuilder();
		int addItems = visibleItems / 2 + 1;

		for (int i = currentItem - addItems; i <= currentItem + addItems; i++) {
			if (useCurrentValue || i != currentItem) {
				String text = getTextItem(i);
				Log.i(TAG, "buildText() text=" + text);
				if (text != null) {
					itemsText.append(text);
				}
			}
			if (i < currentItem + addItems) {
				itemsText.append("\n");
			}
		}
		String itemsTextStr = itemsText.toString();
		Log.i(TAG, "buildText() itemsTextStr=" + itemsTextStr);
		return itemsTextStr;
	}

	/**
	 * Returns the max item length that can be present
	 * @return the max length
	 */
	private int getMaxTextLength() {
		WheelAdapter adapter = getAdapter();
		if (adapter == null) {
			return 0;
		}
		
		int adapterLength = adapter.getMaximumLength();
		if (adapterLength > 0) {
			return adapterLength;
		}

		String maxText = null;
		int addItems = visibleItems / 2;
		for (int i = Math.max(currentItem - addItems, 0);
				i < Math.min(currentItem + visibleItems, adapter.getItemsCount()); i++) {
			String text = adapter.getItem(i);
			if (text != null && (maxText == null || maxText.length() < text.length())) {
				maxText = text;
			}
		}

		return maxText != null ? maxText.length() : 0;
	}

	/**
	 * Returns height of wheel item
	 * @return the item height
	 */
	private int getItemHeight() {
		
		if (itemHeight != 0) {
			return itemHeight;
		} else if (itemsLayout != null && itemsLayout.getLineCount() > 2) {
			itemHeight = itemsLayout.getLineTop(2) - itemsLayout.getLineTop(1);

			return itemHeight;
		}
		
		return getHeight() / visibleItems;
	}

	/**
	 * Calculates control width and creates text layouts
	 * @param widthSize the input layout width
	 * @param mode the layout mode
	 * @return the calculated control width
	 */
	private int calculateLayoutWidth(int widthSize, int mode) {
		initResourcesIfNecessary();

		int width = widthSize;

		int maxLength = getMaxTextLength();
		if (maxLength > 0) {
			float textWidth = FloatMath.ceil(Layout.getDesiredWidth("0", itemsPaint));
			itemsWidth = (int) (maxLength * textWidth);
		} else {
			itemsWidth = 0;
		}
		itemsWidth += ADDITIONAL_ITEMS_SPACE; // make it some more

		labelWidth = 0;
		if (label != null && label.length() > 0) {
			labelWidth = (int) FloatMath.ceil(Layout.getDesiredWidth(label, valuePaint));
		}

		boolean recalculate = false;
		if (mode == MeasureSpec.EXACTLY) {
			width = widthSize;
			recalculate = true;
		} else {
			width = itemsWidth + labelWidth + 2 * PADDING;
			if (labelWidth > 0) {
				width += LABEL_OFFSET;
			}

			// Check against our minimum width
			width = Math.max(width, getSuggestedMinimumWidth());

			if (mode == MeasureSpec.AT_MOST && widthSize < width) {
				width = widthSize;
				recalculate = true;
			}
		}

		if (recalculate) {
			// recalculate width
			int pureWidth = width - LABEL_OFFSET - 2 * PADDING;
			if (pureWidth <= 0) {
				itemsWidth = labelWidth = 0;
			}
			if (labelWidth > 0) {
				double newWidthItems = (double) itemsWidth * pureWidth
						/ (itemsWidth + labelWidth);
				itemsWidth = (int) newWidthItems;
				labelWidth = pureWidth - itemsWidth;
			} else {
				itemsWidth = pureWidth + LABEL_OFFSET; // no label
			}
		}

		if (itemsWidth > 0) {
			createLayouts(itemsWidth, labelWidth);
		}

		return width;
	}

	/**
	 * Creates layouts
	 * @param widthItems width of items layout
	 * @param widthLabel width of label layout
	 */
	private void createLayouts(int widthItems, int widthLabel) {
		Layout.Alignment alignment;
		if(location == 1){
			alignment = Layout.Alignment.ALIGN_LEFT;
		}else if(location == 2){
			alignment = Layout.Alignment.ALIGN_RIGHT;
		}else{
			alignment = Layout.Alignment.ALIGN_CENTER;
		}
		if(!android.text.format.DateFormat.is24HourFormat(getContext())){
			if(location == 1){
				alignment = Layout.Alignment.ALIGN_LEFT;
			}else if(location == 2){
				alignment = Layout.Alignment.ALIGN_CENTER;
			}else{
				alignment = Layout.Alignment.ALIGN_RIGHT;
			}
		}
		if (itemsLayout == null || itemsLayout.getWidth() > widthItems) {
			itemsLayout = new StaticLayout(buildText(isScrollingPerformed), itemsPaint, widthItems,
					/*widthLabel > 0 ? Layout.Alignment.ALIGN_OPPOSITE : */alignment,
					1, mAdditionalItemHeight, false);
			Rect rect = new Rect();
			itemsPaint.getTextBounds(buildText(isScrollingPerformed), 0, 1, rect);
			Log.i(TAG, "createLayouts() rect=" + rect);
		} else {
			itemsLayout.increaseWidthTo(widthItems);
		}

		if (!isScrollingPerformed && (valueLayout == null || valueLayout.getWidth() > widthItems)) {
			String text = getAdapter() != null ? getAdapter().getItem(currentItem) : null;
			valueLayout = new StaticLayout(text != null ? text : "",
					valuePaint, widthItems, /*widthLabel > 0 ?
							Layout.Alignment.ALIGN_OPPOSITE : */alignment,
							1, mAdditionalItemHeight, false);
		} else if (isScrollingPerformed) {
			valueLayout = null;
		} else {
			valueLayout.increaseWidthTo(widthItems);
		}

		if (widthLabel > 0) {
			if (labelLayout == null || labelLayout.getWidth() > widthLabel) {
				labelLayout = new StaticLayout(label, valuePaint,
						widthLabel, Layout.Alignment.ALIGN_CENTER, 1,
						mAdditionalItemHeight, false);
			} else {
				labelLayout.increaseWidthTo(widthLabel);
			}
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int width = calculateLayoutWidth(widthSize, widthMode);

		int height;
		if (heightMode == MeasureSpec.EXACTLY) {
			height = heightSize;
		} else {
			height = getDesiredHeight(itemsLayout);

			if (heightMode == MeasureSpec.AT_MOST) {
				height = Math.min(height, heightSize);
			}

		}
		Log.i(TAG, "onMeasure() width=" + width + " height=" + height);
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		if (itemsLayout == null) {
			if (itemsWidth == 0) {
				calculateLayoutWidth(getWidth(), MeasureSpec.EXACTLY);
			} else {
				createLayouts(itemsWidth, labelWidth);
			}
		}

		/*PRIZE-界面调整 wanzhijuan 2015-7-3 start */
		drawCenterRect(canvas);
		if (itemsWidth > 0) {
			canvas.save();
			// Skip padding space and hide a part of top and bottom items
			canvas.translate(PADDING, mAdditionalItemHeight / 4);
			drawItems(canvas);
			
			drawValue(canvas);
			canvas.restore();
		}

		drawShadows(canvas);
		drawLines(canvas);
		/*PRIZE-界面调整 wanzhijuan 2015-7-3 end */
	}

	/**
	 * Draws shadows on top and bottom of control
	 * @param canvas the canvas for drawing
	 */
	private void drawShadows(Canvas canvas) {
		topShadow.setBounds(0, 0, getWidth(), getHeight() / visibleItems);
		topShadow.draw(canvas);

		bottomShadow.setBounds(0, getHeight() - getHeight() / visibleItems,
				getWidth(), getHeight());
		bottomShadow.draw(canvas);
	}

	/**
	 * Draws value and label layout
	 * @param canvas the canvas for drawing
	 */
	
//	VALUE_TEXT_COLOR
	private void drawValue(Canvas canvas) {
		valuePaint.setColor(VALUE_TEXT_COLOR);
		
		valuePaint.drawableState = getDrawableState();

		Rect bounds = new Rect();
		itemsLayout.getLineBounds(visibleItems / 2, bounds);

		// draw label
		if (labelLayout != null) {
			canvas.save();
			canvas.translate(itemsLayout.getWidth() + LABEL_OFFSET, bounds.top);
			labelLayout.draw(canvas);
			canvas.restore();
		}

		// draw current value
		if (valueLayout != null) {
			canvas.save();
			canvas.translate(0, bounds.top + scrollingOffset);
			valueLayout.draw(canvas);
			canvas.restore();
		}
	}

	/**
	 * Draws items
	 * @param canvas the canvas for drawing
	 */
	
	int  item_bg = 0xFFF9F9F9 ;
	private void drawItems(Canvas canvas) {
		canvas.save();
		
		int top = itemsLayout.getLineTop(1);
		canvas.translate(0, - top + scrollingOffset);
//		ITEMS_TEXT_COLOR;
		itemsPaint.setStrokeWidth(0);
		itemsPaint.setColor(ITEMS_TEXT_COLOR);
		itemsPaint.drawableState = getDrawableState();
		itemsLayout.draw(canvas);
		
		canvas.restore();
	}

	/**
	 * Draws rect for current value
	 * @param canvas the canvas for drawing
	 */
	private void drawCenterRect(Canvas canvas) {
		int Ycenter = getHeight() / 2;
		int Xcenter = getWidth() / 2 ;
		int offset = getCenterHeight() / 2;
		int topHeight = (int) getResources().getDimension(R.dimen.prize_draw_current_values) + 1;//80dp
		centerDrawable.setBounds(0, topHeight, getWidth(), getHeight() - topHeight);
		centerDrawable.draw(canvas);
		Log.i(TAG, "drawCenterRect() height=" + getHeight());
//		canvas.drawLine(-Xcenter,-Ycenter,-Xcenter,Ycenter,itemsPaint);

//		canvas.drawLine(0, Ycenter - offset, getWidth(), Ycenter - offset+1, mLinePaint);
//		canvas.drawLine(0, Ycenter + offset, getWidth(), Ycenter + offset+1, mLinePaint);lijimeng
		canvas.drawLine(0, Ycenter - offset, getWidth(), Ycenter - offset, mLinePaint);
		canvas.drawLine(0, Ycenter + offset, getWidth(), Ycenter + offset, mLinePaint);
	}
	
	/*PRIZE-界面调整 wanzhijuan 2015-7-3 start */
	private void drawLines(Canvas canvas) {
//		if (mIsDrawRightLine) {
//			canvas.drawLine(getWidth()-1, 0, getWidth(), getHeight(), mLinePaint);
//		}
//		canvas.drawLine(0, 0, 1, getHeight(), mLinePaint);
//		canvas.drawLine(0, 0, getWidth(), 1, mLinePaint);
//		canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight(), mLinePaint);
	}
	/*PRIZE-界面调整 wanzhijuan 2015-7-3 end */
	
	/**
	 * 
	 * 方法描述：获取中心框的高度
	 * @param 参数名 说明
	 * @return 返回类型 说明
	 * @see 类名/完整类名/完整类名#方法名
	 */
	private int getCenterHeight() {
		return getHeight() * 11 / 43;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		WheelAdapter adapter = getAdapter();
		if (adapter == null) {
			return true;
		}
		
		if (!gestureDetector.onTouchEvent(event) && event.getAction() == MotionEvent.ACTION_UP) {
			justify();
		}

		return true;
	}
	
	/**
	 * Scrolls the wheel
	 * @param delta the scrolling value
	 */
	private void doScroll(int delta) {
		scrollingOffset += delta;
		
		int count = scrollingOffset / getItemHeight();
		int pos = currentItem - count;
		if (isCyclic && adapter.getItemsCount() > 0) {
			// fix position by rotating
			while (pos < 0) {
				pos += adapter.getItemsCount();
			}
			pos %= adapter.getItemsCount();
		} else if (isScrollingPerformed) {
			// 
			if (pos < 0) {
				count = currentItem;
				pos = 0;
			} else if (pos >= adapter.getItemsCount()) {
				count = currentItem - adapter.getItemsCount() + 1;
				pos = adapter.getItemsCount() - 1;
			}
		} else {
			// fix position
			pos = Math.max(pos, 0);
			pos = Math.min(pos, adapter.getItemsCount() - 1);
		}
		
		int offset = scrollingOffset;
		if (pos != currentItem) {
			setCurrentItem(pos, false);
		} else {
			invalidate();
		}
		
		// update offset
		scrollingOffset = offset - count * getItemHeight();
		if (scrollingOffset > getHeight()) {
			scrollingOffset = scrollingOffset % getHeight() + getHeight();
		}
	}
	
	// gesture listener
	private SimpleOnGestureListener gestureListener = new SimpleOnGestureListener() {
		public boolean onDown(MotionEvent e) {
			if (isScrollingPerformed) {
				scroller.forceFinished(true);
				clearMessages();
				return true;
			}
			return false;
		}
		
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			Log.v("zwl", "Calendar WheelView SimpleOnGestureListener onScroll()");
			startScrolling();
			doScroll((int)-distanceY);
			return true;
		}
		
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			Log.v("zwl", "Calendar WheelView SimpleOnGestureListener OnFling() velocityY == " + velocityY);
			lastScrollY = currentItem * getItemHeight() + scrollingOffset;
			int maxY = isCyclic ? 0x7FFFFFFF : adapter.getItemsCount() * getItemHeight();
			int minY = isCyclic ? -maxY : 0;
			if(velocityY > 4000){
				velocityY = 4000;
			}
			if(velocityY < -4000){
				velocityY = -4000;
			}
			scroller.fling(0, lastScrollY, 0, (int) -velocityY / 2, 0, 0, minY, maxY);
			setNextMessage(MESSAGE_SCROLL);
			finishScrolling();
			return true;
		}
	};

	// Messages
	private final int MESSAGE_SCROLL = 0;
	private final int MESSAGE_JUSTIFY = 1;
	
	/**
	 * Set next message to queue. Clears queue before.
	 * 
	 * @param message the message to set
	 */
	private void setNextMessage(int message) {
		clearMessages();
		animationHandler.sendEmptyMessage(message);
	}

	/**
	 * Clears messages from queue
	 */
	private void clearMessages() {
		animationHandler.removeMessages(MESSAGE_SCROLL);
		animationHandler.removeMessages(MESSAGE_JUSTIFY);
	}
	
	// animation handler
	private Handler animationHandler = new Handler() {
		public void handleMessage(Message msg) {
			scroller.computeScrollOffset();
			int currY = scroller.getCurrY();
			int delta = lastScrollY - currY;
			lastScrollY = currY;
			if (delta != 0) {
				doScroll(delta);
			}
			
			// scrolling is not finished when it comes to final Y
			// so, finish it manually 
			if (Math.abs(currY - scroller.getFinalY()) < MIN_DELTA_FOR_SCROLLING) {
				currY = scroller.getFinalY();
				scroller.forceFinished(true);
			}
			if (!scroller.isFinished()) {
				animationHandler.sendEmptyMessage(msg.what);
			} else if (msg.what == MESSAGE_SCROLL) {
				justify();
			} else {
				finishScrolling();
			}
		}
	};
	
	/**
	 * Justifies wheel
	 */
	private void justify() {
		if (adapter == null) {
			return;
		}
		
		lastScrollY = 0;
		int offset = scrollingOffset;
		int itemHeight = getItemHeight();
		boolean needToIncrease = offset > 0 ? currentItem < adapter.getItemsCount() : currentItem > 0; 
		if ((isCyclic || needToIncrease) && Math.abs((float) offset) > (float) itemHeight / 2) {
			if (offset < 0)
				offset += itemHeight + MIN_DELTA_FOR_SCROLLING;
			else
				offset -= itemHeight + MIN_DELTA_FOR_SCROLLING;
		}
		if (Math.abs(offset) > MIN_DELTA_FOR_SCROLLING) {
			scroller.startScroll(0, 0, 0, offset, SCROLLING_DURATION);
			setNextMessage(MESSAGE_JUSTIFY);
		} else {
			finishScrolling();
		}
	}
	
	/**
	 * Starts scrolling
	 */
	private void startScrolling() {
		if (!isScrollingPerformed) {
			isScrollingPerformed = true;
			notifyScrollingListenersAboutStart();
		}
	}

	/**
	 * Finishes scrolling
	 */
	void finishScrolling() {
		if (isScrollingPerformed) {
			notifyScrollingListenersAboutEnd();
			isScrollingPerformed = false;
		}
		invalidateLayouts();
		invalidate();
	}
	
	
	/**
	 * Scroll the wheel
	 * @param itemsToSkip items to scroll
	 * @param time scrolling duration
	 */
	public void scroll(int itemsToScroll, int time) {
		scroller.forceFinished(true);

		lastScrollY = scrollingOffset;
		int offset = itemsToScroll * getItemHeight();
		
		scroller.startScroll(0, lastScrollY, 0, offset - lastScrollY, time);
		setNextMessage(MESSAGE_SCROLL);
		
		startScrolling();
	}

	
	
	
	
	
	
	
	
	
	
	
}




