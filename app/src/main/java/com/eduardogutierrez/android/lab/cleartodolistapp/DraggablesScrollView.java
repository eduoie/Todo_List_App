package com.eduardogutierrez.android.lab.cleartodolistapp;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

public class DraggablesScrollView extends ScrollView {

    private static final String TAG = DraggablesScrollView.class.getSimpleName();
    private float originX;
    private float originY;
    private int scaledTouchSlop;
    private boolean handlingScroll;

    public DraggablesScrollView(Context context) {
        super(context);
        init(context);
    }

    public DraggablesScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DraggablesScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                originX = event.getX();
                originY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getY() - originY;
                if (Math.abs(deltaY) > scaledTouchSlop) {
                    // it is time for the scrollView to handle things
                    handlingScroll = true;
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
//                Log.d(TAG, "ScrollView onTouchEvent: handling ACTION_MOVE");
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                handlingScroll = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    //    @Override
//    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
//
//        // let overscroll the top: it is the gesture to add a new note item.
//        float maxOverScroll = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());
//
////        Log.d(TAG, String.format("overScrollBy: deltaY=%d scrollY=%d scrollRangeY=%d maxOverScrollY=%d", deltaY, scrollY, scrollRangeY, maxOverScrollY));
//        if (deltaY < 0) maxOverScrollY = (int) maxOverScroll;
//        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
//    }
}
