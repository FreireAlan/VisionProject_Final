package com.ufla.visionproject;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(244, 241, 250));
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return layout;
    }

    public static TextView title(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(26f);
        view.setTextColor(Color.rgb(38, 50, 56));
        view.setPadding(0, 0, 0, 12);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    public static TextView text(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(15f);
        view.setTextColor(Color.rgb(55, 65, 70));
        return view;
    }

    public static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        button.setLayoutParams(params);
        return button;
    }
}
