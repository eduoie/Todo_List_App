package com.eduardogutierrez.android.lab.cleartodolistapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.AbsoluteLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LayoutOfDraggables extends AbsoluteLayout implements DraggableItemView.NoteEventsCallback {
    private static final String TAG = LayoutOfDraggables.class.getSimpleName();

    // Size of the box in DP
    float layoutHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DraggableItemView.BOX_HEIGHT, getResources().getDisplayMetrics());

    private static final int ANIMATION_FINISHED = 1;

    private GestureDetector gestureDetector;
    /* The child view that is currently being dragged or being edited */
    private DraggableItemView currentTargetView;
    private int currentTargetViewPosition;

    private boolean isBeingDragged;
    /* hold the views below and above when it is dragging, these change dynamically */
    private DraggableItemView viewBelow;
    private DraggableItemView viewAbove;
    // when a note is deleted, keep track of the pending notes that need to be relocated
    private int pendingRelocateAnimations;

    private boolean noteIsBeingEdited;
    // flags to indicate that the user wants the scrollview to scroll upwards/downwards a moving note
    private boolean scrollUpwards;
    private boolean scrollDownwards;
    // handles scrolling when the note view is dragged over the top or under the bottom of the scroll.
    // It will reveal hidden noteviews by performing the scrolling.
    ScrollRunnable scrollRunnable;

    private float originY;

    public LayoutOfDraggables(Context context) {
        this(context, null);
    }

    public LayoutOfDraggables(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(final Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                // do not let the parent scroll while we are busy here
                getParent().requestDisallowInterceptTouchEvent(true);
                if (currentTargetView != null) {
                    if (!currentTargetView.isDraggingHorizontal() && !noteIsBeingEdited) {
                        isBeingDragged = true;
                        currentTargetView.setAllowedForRelocation();
                        // puts view on top so it doesn't scroll below others
                        currentTargetView.bringToFront();
                        // init the ScrollRunnable now that the view is dragging.
                        scrollRunnable = new ScrollRunnable((ScrollView) getParent());
                        Thread scrollUpThread = new Thread(scrollRunnable);
                        scrollUpThread.start();
                    }
                }
                super.onLongPress(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (currentTargetView != null && !noteIsBeingEdited) {
                    noteIsBeingEdited = true;
                    currentTargetView.initEditMode(e);
                }
                return super.onDoubleTap(e);
            }
        });

    }

    public static float dpToPx(Resources resources, float valueInDp) {
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics);
    }

    // Used as a debug helper
    private class MyRunnable implements Runnable {
        View view;

        public void setView(View v) {
            view = v;
        }

        @Override
        public void run() {
            for (int i = 0; i < 100; i++) {
                if (currentTargetView.getY() > view.getY()) {
                    System.out.println("TARGET VIEW HAS CROSSED OVER VIEW IN ANIMATION!!!!!!");
                }
                System.out.println("animating view Y: " + view.getY() +
                        ", targetView Y = " + currentTargetView.getY());
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private class ScrollRunnable implements Runnable {
        ScrollView scrollView;
        // volatile wasn't having the expected effect and the variable was keeping it's last value in the thread for a long undefined time.
        boolean keepRunning = true;

        public ScrollRunnable(ScrollView scrollView) {
            this.scrollView = scrollView;
        }

        public void stop() {
            keepRunning = false;
        }

        @Override
        public void run() {
            while (keepRunning) {
                if (scrollUpwards) {
                    scrollView.scrollBy(0, -1);
                    // update the draggable view too.
                    currentTargetView.setY(currentTargetView.getY() - 1);
                    if (scrollView.getScrollY() <= 0) {
                        return;
                    }
                    switchViewAboveIfCrossing();
                } else if (scrollDownwards) {
                    scrollView.scrollBy(0, 1);
                    // update the draggable view too.
                    currentTargetView.setY(currentTargetView.getY() + 1);
                    if (currentTargetView.getY() > scrollView.getHeight() + currentTargetView.getHeight()) {
                        return;
                    }
                    switchViewBelowIfCrossing();
                }
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                scrollUpwards = false;
                scrollDownwards = false;
                originY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (currentTargetView != null) {
                    if (currentTargetView.isDraggingHorizontal()) {
                        // do not let the parent scroll while we are busy here
                        getParent().requestDisallowInterceptTouchEvent(true);
                        // plus, we don't have anymore to do here
                        break;
                    }
                    // determine if scrolling has to be performed
                    ScrollView scrollView = (ScrollView) getParent();
//                    String str = String.format("targetY = %.1f, scrollView.scrollY = %d", currentTargetView.getY(), scrollView.getScrollY());
                    String str = String.format("targetY + height = %.1f, scrollView.height = %d", currentTargetView.getY() + currentTargetView.getHeight(), scrollView.getHeight());
                    Log.d(TAG, str);
                    if (isBeingDragged) {
                        // condition event.getY() < originY (or > for downward) applies when the note view is partially hidden.
                        scrollUpwards = currentTargetView.getY() < scrollView.getScrollY() && event.getY() < originY;
                        scrollDownwards = currentTargetView.getY() + currentTargetView.getHeight() > scrollView.getBottom() && event.getY() > originY;
                    }

                    // check and perform reordering.
                    // TODO: consider removing this from here and let it be handled exclusively by the ScrollRunnable
                    switchViewAboveIfCrossing();
                    switchViewBelowIfCrossing();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (scrollUpwards || scrollDownwards) {
                    scrollUpwards = false;
                    scrollDownwards = false;
                    if (scrollRunnable != null) {
                        scrollRunnable.stop();
                    }
                }
                if (isBeingDragged) {
                    animateDraggableViewIntoSlot();
                }
                isBeingDragged = false;
                break;
        }
        // don't interrupt the flow
        return super.onInterceptTouchEvent(event);
    }

    /**
     * Method is synchronized as it can be called from more than one thread.
     */
    synchronized private void switchViewAboveIfCrossing() {
        final int targetViewY = (int) currentTargetView.getY();
        if (viewAbove != null) {
            float switchPoint = viewAbove.getY() + viewAbove.getHeight() / 4;
            // TEMP: changed switchPoint
            switchPoint = viewAbove.getY();
            Log.d(TAG, "targetViewY = " + targetViewY + ", ViewABOVE SWITCH POINT = " + switchPoint);
            if (targetViewY < switchPoint) {
                Log.d(TAG, "ANIMATING VIEW ABOVE = " + viewAbove.noteEditText.getText().toString());
                ViewPropertyAnimator viewAnimator = viewAbove.animate();

//                            MyRunnable runnable = new MyRunnable();
//                            runnable.setView(viewAbove);
//                            Thread thread = new Thread(runnable);
//                            thread.start();

                /**
                 * viewbelow will be set to the current viewAbove. However at the moment of the call, viewAbove will
                 * start to animate, and it will take a while to be below the currentTargetView. Once it is performed
                 * the animation, viewBelow is set.
                 * There is a risk of an undefined state if the currentTargetView is displaced back faster than the time that
                 * takes the animation to complete.
                 */
                final DraggableItemView viewBelowAfter = viewAbove;
                viewAnimator.translationYBy(viewAbove.getHeight()).setDuration(100L).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewBelow = viewBelowAfter;
//                                    handler.sendEmptyMessage(ANIMATION_FINISHED);
                        helperLogViews();
                    }
                });
                currentTargetViewPosition--;
                viewBelow = null;
                viewAbove = findViewAbove((int) viewAbove.getY());
//                            Log.d(TAG, "new View Above = " + viewAbove.noteEditText.getText().toString());
            }
        }
    }

    synchronized private void switchViewBelowIfCrossing() {
        final int targetViewY = (int) currentTargetView.getY();
        if (viewBelow != null) {
            float switchPoint = (float) (viewBelow.getY() + viewBelow.getHeight() * 0.75);
            Log.d(TAG, "targetViewY = " + targetViewY + ", ViewBELOW SWITCH POINT = " + switchPoint);
            if (targetViewY + currentTargetView.getHeight() > switchPoint) {
                Log.d(TAG, "ANIMATING VIEW BELOW = " + viewBelow.noteEditText.getText().toString());
                ViewPropertyAnimator viewAnimator = viewBelow.animate();
                final DraggableItemView viewAboveAfter = viewBelow;
                viewAnimator.translationYBy(-viewBelow.getHeight()).setDuration(100L).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewAbove = viewAboveAfter;
                        helperLogViews();
                    }
                });
                currentTargetViewPosition++;
                viewAbove = null;
                viewBelow = findViewBelow((int) viewBelow.getY());
            }
        }
    }


    // puts the dragging view into the available slot.
    private void animateDraggableViewIntoSlot() {
        if (currentTargetView != null) {
            final float yDestination = currentTargetView.getHeight() * currentTargetViewPosition + getPaddingTop();
            float yTranslation = yDestination - currentTargetView.getY();
            Log.d(TAG, "animateDraggableView: LIFTING UP, dragPosition=" + currentTargetViewPosition +
                    ", y=" + currentTargetView.getY() + ", yDest =" + yDestination +
                    ", yTranslation= " + yTranslation);

            currentTargetView.animate().translationYBy(yTranslation).setDuration(100L).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // do an internal reordering
                    post(new Runnable() {
                        @Override
                        public void run() {
                            helperLogViews();
                            reorderViews();
                            Log.d(TAG, "animateDraggableView: AFTER INTERNAL REORDERING");
                            helperLogViews();
                        }
                    });
                }
            });
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

        // TEMP: ugly solution, but other solution doesn't correctly set the views coordinates and eventually they scramble all over the place
        removeAllViews();
        for (int i = 0; i < draggableItemViews.size(); i++) {
            DraggableItemView draggableItemView = new DraggableItemView(getContext());
            int color = Color.TRANSPARENT;
            Drawable background = draggableItemViews.get(i).getBackground();
            if (background instanceof ColorDrawable) color = ((ColorDrawable) background).getColor();
            draggableItemView.setBackgroundColor(color);
            draggableItemView.setNoteText(draggableItemViews.get(i).noteEditText.getText().toString());
            draggableItemView.noteEditText.setPaintFlags(draggableItemViews.get(i).noteEditText.getPaintFlags());
            draggableItemView.setMarkedAsDone(draggableItemViews.get(i).isMarkedAsDone());
            addView(draggableItemView);
        }
        post(new Runnable() {
            @Override
            public void run() {
                requestLayout();
            }
        });

//
//        for (int i = 0; i < getChildCount(); i++) {
//            draggableItemViews.get(i).setOrder(i);
//            // TEMP necessary once layout is measured again?
//            LayoutParams layoutParams = (LayoutParams) draggableItemViews.get(i).getLayoutParams();
//            layoutParams.y = (int) (i * layoutHeight);
//            draggableItemViews.get(i).setTop(getPaddingTop() + layoutParams.y);
//            draggableItemViews.get(i).setY(getPaddingTop() + layoutParams.y);
//        }
//        post(new Runnable() {
//            @Override
//            public void run() {
//                requestLayout();
//            }
//        });
    }


    /**
     * Given the Y coordinate, find the first view below these coordinates
     * that is not the current draggableView
     */
    private DraggableItemView findViewBelow(int yView) {
        View chosenView = null;
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getY() > yView && view != currentTargetView) {
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
            if (view.getY() < yView && view != currentTargetView) {
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
        currentTargetView = view;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) == currentTargetView) {
                currentTargetViewPosition = currentTargetView.getOrder();
                viewAbove = findViewAbove((int) currentTargetView.getY());
                viewBelow = findViewBelow((int) currentTargetView.getY());
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

        for (int i = 0; i < getChildCount(); i++) {
            DraggableItemView itemView = draggableItemViews.get(i);
            Log.d(TAG, i + ": " + itemView.noteEditText.getText() + ", y = " + itemView.getY()
                    + ", LayoutParams.y = " + ((LayoutParams) itemView.getLayoutParams()).y
                    + ", top = " + itemView.getTop() + ", translationY = " + itemView.getTranslationY()
                    + ", scrollY = " + getScrollY());
        }
        Log.d(TAG, "currentTargetViewPosition = " + currentTargetViewPosition);
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
        // TODO: A possible solution is to ignore all UI operations by the user until this is finished. but is easier to make the delete animation shorter
//        if (pendingRelocateAnimations > 0) {
//            try {
//                throw new Exception("Race condition happened in pendingRelocateAnimations!!!");
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
        pendingRelocateAnimations = getChildCount() - position;
        for (int i = position; i < getChildCount(); i++) {
            final float yDestination = getChildAt(i).getHeight() * i + getPaddingTop();
            float yTranslation = yDestination - getChildAt(i).getY();
            getChildAt(i).animate().translationYBy(yTranslation).setDuration(100L).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pendingRelocateAnimations--;
                    if (pendingRelocateAnimations == 0) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                reorderViews();
                            }
                        });
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    pendingRelocateAnimations--;
                    if (pendingRelocateAnimations == 0) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                reorderViews();
                            }
                        });
                    }
                }
            }).start();
        }
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

        // TODO: fix the views LP coordinates or fix this by removing the +1 in the final measure. If LP is fixed this method override is unnecessary
        // +1 so it doesn't clip the bottom view while animating shuffling after a deleted note
        maxHeight = (int) ((getChildCount() + 1) * layoutHeight);
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

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
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
