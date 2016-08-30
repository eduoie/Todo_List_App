package com.eduardogutierrez.android.lab.cleartodolistapp;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView);
//        scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        LayoutOfDraggables layoutOfDraggables = (LayoutOfDraggables) findViewById(R.id.draggableLayout);

//        DraggableItemView draggableItemView1 = new DraggableItemView(this);
//        draggableItemView1.setBackgroundColor(Color.RED);
//        draggableItemView1.setNoteText("Note 1");
//        DraggableItemView draggableItemView2 = new DraggableItemView(this);
//        draggableItemView2.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
//        draggableItemView2.setNoteText("Note 2");
//        DraggableItemView draggableItemView3 = new DraggableItemView(this);
//        draggableItemView3.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
//        draggableItemView3.setNoteText("Note 3");
//        DraggableItemView draggableItemView4 = new DraggableItemView(this);
//        draggableItemView4.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
//        draggableItemView4.setNoteText("Note 4");

//        layoutOfDraggables.addView(draggableItemView1);
//        layoutOfDraggables.addView(draggableItemView2);
//        layoutOfDraggables.addView(draggableItemView3);
//        layoutOfDraggables.addView(draggableItemView4);

//        for (int i = 0; i < 10; i++) {
//            DraggableItemView draggableItemView = new DraggableItemView(this);
//            draggableItemView.setNoteText("Note " + (i+1));
//            if (i % 3 == 0) draggableItemView.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
//            if (i % 3 == 1) draggableItemView.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
//            if (i % 3 == 2)
//                draggableItemView.setBackgroundColor(Color.argb(255, 100, 110, 60));
////                draggableItemView.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
//
//            layoutOfDraggables.addView(draggableItemView);
//        }

        for (int i = 0; i < 10; i++) {
            DraggableItemView draggableItemView = new DraggableItemView(this);
            draggableItemView.setNoteText("Note " + (i+1));
            draggableItemView.setBackgroundColor(Color.rgb(0, 200 - i * 10, 100 - i * 10));

            layoutOfDraggables.addView(draggableItemView);

        }
    }
}
