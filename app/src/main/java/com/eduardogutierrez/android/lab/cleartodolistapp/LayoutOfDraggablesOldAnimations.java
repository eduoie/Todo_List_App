package com.eduardogutierrez.android.lab.cleartodolistapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsoluteLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LayoutOfDraggablesOldAnimations extends AbsoluteLayout implements DraggableItemView.NoteEventsCallback, Handler.Callback {
    private static final String TAG = LayoutOfDraggables.class.getSimpleName();

    // Size of the box in DP
    public static final int BOX_HEIGHT = 60;
    float layoutHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DraggableItemView.BOX_HEIGHT, getResources().getDisplayMetrics());

    private static final int ANIMATION_FINISHED = 1;

    private GestureDetector gestureDetector;
    /* The child view that is currently being dragged */
    private DraggableItemView currentDraggableView;
    private int currentDraggableViewPosition;

    private boolean isBeingDragged;
    /* hold the views below and above when it is dragging, these change dynamically */
    private DraggableItemView viewBelow;
    private DraggableItemView viewAbove;

    private boolean blockSwitchAbove;
    private boolean blockSwitchBelow;
    private boolean isAnimatingView;
    // will handle animation status
    private Handler handler;

    private int pendingDeleteAnimations;

    private boolean noteIsBeingEdited;

    public LayoutOfDraggablesOldAnimations(Context context) {
        this(context, null);
    }

    public LayoutOfDraggablesOldAnimations(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                // do not let the parent scroll while we are busy here
                getParent().requestDisallowInterceptTouchEvent(true);
                if (currentDraggableView != null) {
                    if (!currentDraggableView.isDraggingHorizontal() && !noteIsBeingEdited) {
                        isBeingDragged = true;
                        currentDraggableView.setAllowedForRelocation();
                        // puts view on top so it doesn't scroll below others
                        currentDraggableView.bringToFront();
                    }
                }
                super.onLongPress(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // TODO: bug in detecting double tap after editing a text. Perhaps due to the hack to open keyboard?
                if (currentDraggableView != null && !noteIsBeingEdited) {
                    noteIsBeingEdited = true;
                    currentDraggableView.initEditMode(e);
                }
                return super.onDoubleTap(e);
            }
        });

        handler = new Handler(this);

    }

    @Override
    protected void onAttachedToWindow() {

        // // TODO: 2/8/16   requires adjust this: android:windowSoftInputMode="adjustResize"
        super.onAttachedToWindow();
        final View activityRootView = (View) getParent();
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Log.d(TAG, "onGlobalLayout: this.getHeight() = " + getHeight() +
                        ", rootView.getHeight()=" + activityRootView.getRootView().getHeight());
                int heightDiff = getHeight() - activityRootView.getRootView().getHeight();
                if (heightDiff > dpToPx(getResources(), 200)) { // if more than 200 dp, it's probably a keyboard...
                    // ... do something here
                }
            }
        });
    }

    public static float dpToPx(Resources resources, float valueInDp) {
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (currentDraggableView != null) {
                    if (currentDraggableView.isDraggingHorizontal()) {
                        // do not let the parent scroll while we are busy here
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }

                    final int yView = (int) currentDraggableView.getY();
                    if (viewAbove != null) {
                        float switchPoint = viewAbove.getY() + viewAbove.getHeight() / 4;
//                        Log.d(TAG, "yView = " + yView + ", ViewABOVE SWITCH POINT = " + switchPoint);
                        if (yView < switchPoint && blockSwitchAbove == false) {
                            // prevent from animating the view more than once, i.e. view has crossed the threshold and
                            // now the view relocation and animation is irreversible
                            blockSwitchAbove = true;
                            isAnimatingView = true;
                            Log.d(TAG, "ANIMATING VIEW ABOVE");
                            viewAbove.animate().yBy(viewAbove.getHeight()).setDuration(50L).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    blockSwitchAbove = false;
                                    isAnimatingView = false;
                                    currentDraggableViewPosition--;
                                    viewAbove = findViewAbove(yView);
                                    viewBelow = findViewBelow(yView);
                                    handler.sendEmptyMessage(ANIMATION_FINISHED);
                                    helperLogViews();
                                }
                            }).start();
                        }
                    }
                    if (viewBelow != null) {
                        float switchPoint = (float) (viewBelow.getY() + viewBelow.getHeight() * 0.75);
//                        Log.d(TAG, "yView = " + yView + ", ViewBELOW SWITCH POINT = " + switchPoint);
                        if (yView + currentDraggableView.getHeight() > switchPoint && blockSwitchBelow == false) {
                            // blockSwitchBelow = prevent from animating the view more than once, i.e. view has crossed
                            // the threshold and now the view relocation and animation is irreversible
                            blockSwitchBelow = true;
                            isAnimatingView = true;
                            Log.d(TAG, "ANIMATING VIEW BELOW");
                            viewBelow.animate().yBy(-viewBelow.getHeight()).setDuration(50L).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    blockSwitchBelow = false;
                                    isAnimatingView = false;
                                    currentDraggableViewPosition++;
                                    viewAbove = findViewAbove(yView);
                                    viewBelow = findViewBelow(yView);
                                    handler.sendEmptyMessage(ANIMATION_FINISHED);
                                    helperLogViews();
                                }
                            }).start();
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // in case that one view is animating, the animation will be processed
                // when the handler callback is received.
                if (!isAnimatingView && isBeingDragged) {
                    animateDraggableView();
                }
                isBeingDragged = false;
        }
        // don't interrupt the flow
        return super.onInterceptTouchEvent(event);
    }

    private void animateDraggableView() {
        // put the dragging view into its slot.
        if (currentDraggableView != null) {
            final float yDestination = currentDraggableView.getHeight() * currentDraggableViewPosition + getPaddingTop();
            Log.d(TAG, "animateDraggableView: LIFTING UP, dragPosition=" + currentDraggableViewPosition +
                    ", y=" + currentDraggableView.getY() + ", yDest =" + yDestination);

            float yTranslation = yDestination - currentDraggableView.getY();
            currentDraggableView.animate().yBy(yTranslation).setDuration(100L).start();
            helperLogViews();
            // do an internal reordering
            reorderViews();
            Log.d(TAG, "animateDraggableView: AFTER INTERNAL REORDERING");
            helperLogViews();
        }
    }

    /**
     * Internal reorder views
     */
    private void reorderViews() {
        List<DraggableItemView> draggableItemViews = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            draggableItemViews.add((DraggableItemView) getChildAt(i));
        }

        Collections.sort(draggableItemViews, new Comparator<DraggableItemView>() {
            @Override
            public int compare(DraggableItemView lhs, DraggableItemView rhs) {
                return (int) (lhs.getY() - rhs.getY());
            }
        });

        for (int i = 0; i < getChildCount(); i++) {
            draggableItemViews.get(i).setOrder(i);
            // TEMP necessary once layout is measured again?
//            LayoutParams layoutParams = (LayoutParams) draggableItemViews.get(i).getLayoutParams();
//            layoutParams.y = (int) (i * layoutHeight);
        }

//        isLayoutRequested()
    }

    /**
     * Given the Y coordinate, find the first view below these coordinates
     * that is not the current draggableView
     */
    private DraggableItemView findViewBelow(int yView) {
        View chosenView = null;
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getY() > yView && view != currentDraggableView) {
                if (chosenView == null) {
                    chosenView = view;
                } else if (view.getY() < chosenView.getY()) {
                    chosenView = view;
                }
            }
        }

        return (DraggableItemView) chosenView;
    }

    /**
     * Given the Y coordinate, find the first view above these coordinates
     * that is not the current draggableView
     */
    private DraggableItemView findViewAbove(int yView) {
        View chosenView = null;
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getY() < yView && view != currentDraggableView) {
                if (chosenView == null) {
                    chosenView = view;
                } else if (view.getY() > chosenView.getY()) {
                    chosenView = view;
                }
            }
        }

        return (DraggableItemView) chosenView;
    }

    /**
     * Called by the child view to indicate that is the view that can be dragged vertically
     *
     * @param view the child view
     */
    void setCurrentDraggable(DraggableItemView view) {
        Log.d(TAG, "setCurrentDraggable: ");
        currentDraggableView = view;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) == currentDraggableView) {
                currentDraggableViewPosition = currentDraggableView.getOrder();
                viewAbove = findViewAbove((int) currentDraggableView.getY());
                viewBelow = findViewBelow((int) currentDraggableView.getY());
                blockSwitchBelow = false;
                blockSwitchAbove = false;
                helperLogViews();
                break;
            }
        }
    }

    private void helperLogViews() {
        Log.d(TAG, "helperLogViews: Listing of views:");
        List<DraggableItemView> draggableItemViews = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            draggableItemViews.add((DraggableItemView) getChildAt(i));
        }
        Collections.sort(draggableItemViews, new Comparator<DraggableItemView>() {
            @Override
            public int compare(DraggableItemView lhs, DraggableItemView rhs) {
                return (int) (lhs.getY() - rhs.getY());
            }
        });

//        for (int i = 0; i < getChildCount(); i++) {
//            Log.d(TAG, i + ": " + draggableItemViews.get(i).noteEditText.getText() + ", y = " + draggableItemViews.get(i).getY()
//                + "LayoutParams.y = " + ((LayoutParams)draggableItemViews.get(i).getLayoutParams()).y);
//
//        }
//        Log.d(TAG, "currentDraggableViewPosition = " + currentDraggableViewPosition);
//        if (viewAbove != null)
//            Log.d(TAG, "current ViewABOVE = " + viewAbove.noteEditText.getText().toString() + ", y = " + viewAbove.getY());
//        if (viewBelow != null)
//            Log.d(TAG, "current ViewBELOW = " + viewBelow.noteEditText.getText().toString() + ", y = " + viewBelow.getY());
    }

    @Override
    public void addView(View child) {
        float height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DraggableItemView.BOX_HEIGHT, getResources().getDisplayMetrics());
        float totalHeight = height * getChildCount();
        AbsoluteLayout.LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) height, 0, (int) totalHeight);
        addView(child, layoutParams);
        DraggableItemView draggableItemView = (DraggableItemView) child;
        draggableItemView.setOrder(getChildCount() - 1);
        draggableItemView.setNoteEventCallback(this);
    }

    @Override
    public void onNoteEditFinished() {
        noteIsBeingEdited = false;
    }

    @Override
    public void onActionNoteMarkedAsDone(int position) {

    }

    @Override
    public void onActionNoteDelete(int position) {
        // layout view position is not the same as 'position' (-> order)
        for (int i = 0; i < getChildCount(); i++) {
            DraggableItemView view = (DraggableItemView) getChildAt(i);
            if (view.getOrder() == position) {
                this.removeViewAt(i);
                break;
            }
        }
        // animate all views below
        // NOTE: a race condition may happen if a new delete is performed by the user before
        // the pending animations are finished.
//        if (pendingDeleteAnimations > 0) {
//            try {
//                throw new Exception("Race condition happened in pendingDeleteAnimations!!!");
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
        pendingDeleteAnimations = getChildCount() - position;
        for (int i = position; i < getChildCount(); i++) {
            final float yDestination = getChildAt(i).getHeight() * i + getPaddingTop();
            float yTranslation = yDestination - getChildAt(i).getY();
            getChildAt(i).animate().yBy(yTranslation).setDuration(100L).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pendingDeleteAnimations--;
                    if (pendingDeleteAnimations == 0) {
                        reorderViews();
                        // TODO: test this
                        post(new Runnable() {
                            @Override
                            public void run() {
                                requestLayout();
                            }
                        });
                    }
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                    pendingDeleteAnimations--;
                    if (pendingDeleteAnimations == 0) {
                        reorderViews();
                        // TODO: test this
                        post(new Runnable() {
                            @Override
                            public void run() {
                                requestLayout();
                            }
                        });
                    }
                }
            }).start();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == ANIMATION_FINISHED) {
            if (!isBeingDragged) {
                animateDraggableView();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();

        int maxHeight = 0;
        int maxWidth = 0;

        // Find out how big everyone wants to be
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        // Find rightmost and bottom-most child
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childRight;
                int childBottom;

                AbsoluteLayout.LayoutParams lp
                        = (AbsoluteLayout.LayoutParams) child.getLayoutParams();

                childRight = lp.x + child.getMeasuredWidth();
                childBottom = lp.y + child.getMeasuredHeight();

                maxWidth = Math.max(maxWidth, childRight);
            }
        }

        // TODO: fix the views LP coordinates or fix this by removing the +1 in the final measure.
        // +1 so it doesn't clip the bottom view while animating
        maxHeight = (int) ((getChildCount()+1) * layoutHeight);
        // Account for padding too
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();

        // Check against minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
                resolveSizeAndState(maxHeight, heightMeasureSpec, 0));

        Log.d(TAG, "onMeasure: measuredHeight = " + getMeasuredHeight());

    }

    //    @Override
//    protected void onLayout(boolean changed, int l, int t, int r, int b) {
//        int count = getChildCount();
//
//        float layoutHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DraggableItemView.BOX_HEIGHT, getResources().getDisplayMetrics());
//        for (int i = 0; i < count; i++) {
//            View child = getChildAt(i);
//
//
//            int childLeft = getPaddingLeft();
//            child.layout(childLeft, getPaddingTop() + (int) (layoutHeight * i), r, getPaddingTop() + (int) layoutHeight * (i + 1));
//        }
//    }
}
