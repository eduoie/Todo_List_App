package com.eduardogutierrez.android.lab.cleartodolistapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsoluteLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This class contains the editable note text and two icons.
 */
public class DraggableItemView extends AbsoluteLayout {
    private static final String TAG = DraggableItemView.class.getSimpleName();

    /**
     * Overwritten EditText to detect key backs to determine the keyboard is closed
     */
    private class ExtendedEditText extends EditText {
        public ExtendedEditText(Context context) {
            super(context);
        }
        public ExtendedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        public ExtendedEditText(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }
        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                dispatchKeyEvent(event);
                finishEditMode();
                return false;
            }
            return super.onKeyPreIme(keyCode, event);
        }
    }

    // Size of the box in DP
    public static final int BOX_HEIGHT = 60;
    float layoutHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DraggableItemView.BOX_HEIGHT, getResources().getDisplayMetrics());

    // editable text where to store the note text
    protected EditText noteEditText;

    private Context activity;

    // internal ordering, will differ from layout ordering
    private int order;
    // icon to display when a task is being set to done
    private ImageView imageDone;
    // icon to display when a task is going to be deleted
    private ImageView imageDelete;
    // flag to indicate that the note is set to done (user will see a visible strikethrough)
    private boolean markedAsDone;

    // color of the view
    private int backgroundColor;
    // handling of events and animations
    private LayoutOfDraggables parentView;

    private float rawOriginX;
    private float originX;
    private float originY;
    private int scaledTouchSlop;
    private boolean dragHorizontalFlag;
    private boolean dragVerticalFlag;
    private boolean allowedRelocation;
    private boolean viewIsAnimated;
    private boolean editMode;
    private boolean passedDoneThreshold;
    private boolean passedDeleteThreshold;
    // handling of callbacks
    private NoteEventsCallback callback;
    public DraggableItemView(Context context) {
        super(context);
        init(context, null);
    }

    public DraggableItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DraggableItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void setNoteText(String text) {
        this.noteEditText.setText(text);
    }

    private void init(Context context, AttributeSet attrs) {
        activity = context;

        scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.DraggableItemView);
        try {
            backgroundColor = typedArray.getColor(R.styleable.DraggableItemView_backgroundColor, Color.RED);
            setBackgroundColor(backgroundColor);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            typedArray.recycle();
        }

        noteEditText = (EditText) new ExtendedEditText(context);
        noteEditText.setTextColor(Color.WHITE);
        // remove underline
        noteEditText.setBackground(getResources().getDrawable(android.R.color.transparent));
        noteEditText.setSingleLine();
        noteEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
//        noteEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        noteEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        event.getAction() == KeyEvent.KEYCODE_BACK) {
                    finishEditMode();
                    hideKeyboard((Activity)activity);
                    return true;
                }
                return false;
            }

        });

        markedAsDone = false;

        /*
            Build the layout views
         */
        addView(noteEditText, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, (int) layoutHeight, 0));
        imageDone = (ImageView) new ImageView(context);
        imageDone.setImageResource(R.drawable.ic_done_black_24dp);
        addView(imageDone, new LayoutParams((int) layoutHeight, (int) layoutHeight, 0, 0));
        imageDelete = (ImageView) new ImageView(context);
        imageDelete.setImageResource(R.drawable.ic_delete_sweep_black_24dp);
        addView(imageDelete, new LayoutParams((int) layoutHeight, (int) layoutHeight, 0, 0));

        // Displace the item to hide the done icon to the left
        setX(-layoutHeight);
    }

    public void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(
                Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        // once the size of the view is known, the delete image is placed to the right side.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(parentWidth + (int)layoutHeight * 2, parentHeight);
        imageDelete.setLayoutParams(new LayoutParams((int) layoutHeight, (int) layoutHeight, parentWidth + (int)layoutHeight, 0));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        /* get the parent view */
        parentView = (LayoutOfDraggables) getParent();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                parentView.setCurrentDraggable(this);
                rawOriginX = event.getRawX();
                originX = event.getX();
                originY = event.getY();

                Drawable background = getBackground();
                backgroundColor = ((ColorDrawable) background).getColor();
//                Log.d(TAG, String.format("Draggable DOWN: X = %.1f Y = %.1f viewX = %.1f viewY = %.1f",
//                        originX, originY, viewX, viewY));
                return true;

            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                float deltaX = x - originX;
                float deltaY = y - originY;
//                Log.d(TAG, String.format("Draggable MOVE: x = %.1f y = %.1f deltaX = %.1f deltaY = %.1f",
//                        x, y, deltaX, deltaY));
                /**
                 * Two cases:
                 * 1) drag vertical only when a long press is detected in the parent view.
                 * 2) drag horizontal otherwise
                 * Note that leeway for horizontal drag is half (0.5f) of vertical drag
                 */
                if (Math.abs(deltaY) > scaledTouchSlop && allowedRelocation) {
                    dragVerticalFlag = true;
                } else if (Math.abs(deltaX) > scaledTouchSlop * 0.5f && !allowedRelocation) {
                    dragHorizontalFlag = true;
                }

                if (dragVerticalFlag && !isInInputMode()) {
                    this.setY(getY() + deltaY);
                } else if (dragHorizontalFlag && !isInInputMode()) {
                    this.setX(getX() + deltaX);
//                    Log.d(TAG, String.format("alpha set: RawX = %.2f, originRawX =  %.2f",event.getRawX(), rawOriginX));
                    if (event.getRawX() - rawOriginX > 0) {
                        imageDone.setAlpha((event.getRawX() - rawOriginX) * 0.7f / layoutHeight);
                        // mark as done/undone
                        if ((event.getRawX() - rawOriginX) * 0.7f > layoutHeight) {
                            passedDoneThreshold = true;
                            if (markedAsDone) noteEditText.setPaintFlags(noteEditText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                            else noteEditText.setPaintFlags(noteEditText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                        } else if (passedDoneThreshold) {
                            // undo the operation
                            passedDoneThreshold = false;
                            if (markedAsDone) noteEditText.setPaintFlags(noteEditText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                            else noteEditText.setPaintFlags(noteEditText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                        }
                        // the reverse direction is applied to delete a note
                    } else if (rawOriginX - event.getRawX() > 0) {
                        imageDelete.setAlpha((rawOriginX - event.getRawX()) * 0.7f / layoutHeight);
                        if ((rawOriginX - event.getRawX()) * 0.7f > layoutHeight) {
                            passedDeleteThreshold = true;
                            setBackgroundColor(Color.RED);
                        } else {
                            passedDeleteThreshold = false;
                            setBackgroundColor(backgroundColor);
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (allowedRelocation) {
                    notifyUserViewIsRelocated();
                }
                if (dragHorizontalFlag) {
                    if (passedDoneThreshold) {
                        markedAsDone = !markedAsDone;
                    }
                    if (!passedDeleteThreshold) {
                        // go back to origin
                        animate().x(parentView.getPaddingLeft() - layoutHeight).setDuration(50L).start();
                    } else {
                        // view is removed from screen and callback is called to handle the deletion
                        imageDone.setAlpha(0f);
                        animate().x(2000).setDuration(200L).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (callback != null) {
                                    callback.onActionNoteDelete(getOrder());
                                }
                            }
                        }).start();
                    }
                }

                dragHorizontalFlag = false;
                dragVerticalFlag = false;
                allowedRelocation = false;
                viewIsAnimated = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Helper method to sensorily indicate the user that the view can be relocated.
     */
    private void notifyUserViewCanRelocate() {
        ((Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(30);
        if (!viewIsAnimated) {
            animate().scaleX(1.05f).scaleY(1.05f).setDuration(50L).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    /* Given that there may be two different animations running (scaling and translating)
                       this listener ensures that the translating listener is not called twice
                      */
                }
            }).start();
            viewIsAnimated = true;
        }
    }

    /**
     * Helper method. Restores the view original size.
     */
    private void notifyUserViewIsRelocated() {
        Log.d(TAG, "notifyUserViewIsRelocated: ");
        animate().scaleX(1.0f).scaleY(1.0f).setDuration(50L).start();
    }

    /**
     * When enabled (per requirements, when long press is detected) the cell can be relocated up and down the list.
     * Also the cell indicates through visual and sensory output that is ready to relocate
     */
    public void setAllowedForRelocation() {
        allowedRelocation = true;
        /* prepare to sensorilly indicate the user that the view is ready for relocation */
        notifyUserViewCanRelocate();
    }


    /**
     * @return true if the cell is being dragged horizontally. When a cell is being dragged horizontally
     * it is not allowed to reorder among other cells.
     */
    public boolean isDraggingHorizontal() {
        return dragHorizontalFlag;
    }

    /**
     * Starts edit mode for the EditText.
     */
    public void initEditMode(final MotionEvent event) {
        editMode = true;
        noteEditText.setCursorVisible(true);
        // show keyboard
        // Found this hack at:
        // http://stackoverflow.com/questions/5105354/how-to-show-soft-keyboard-when-edittext-is-focused
        (new Handler()).postDelayed(new Runnable() {

            public void run() {
                noteEditText.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN, event.getX(), event.getY(), 0));
                noteEditText.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP, event.getX(), event.getY(), 0));

            }
        }, 200);
    }

    public boolean isInInputMode() {
        return editMode;
    }

    public void finishEditMode() {
        editMode = false;
        noteEditText.setCursorVisible(false);

        if (callback != null) {
            callback.onNoteEditFinished();
        }
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isMarkedAsDone() {
        return markedAsDone;
    }

    public void setMarkedAsDone(boolean markedAsDone) {
        this.markedAsDone = markedAsDone;
    }

    public void setNoteEventCallback(NoteEventsCallback callback) {
        this.callback = callback;
    }

    public interface NoteEventsCallback {
        void onActionNoteMarkedAsDone(int position);
        void onActionNoteDelete(int position);
        void onNoteEditFinished();
    }
}
