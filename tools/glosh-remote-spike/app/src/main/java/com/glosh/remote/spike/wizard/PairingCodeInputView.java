package com.glosh.remote.spike.wizard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressLint("ViewConstructor")
public final class PairingCodeInputView extends LinearLayout {
    private static final int GRAPHITE = Color.rgb(25, 27, 24);
    private static final int LINE = Color.rgb(199, 204, 191);
    private static final int LIME = Color.rgb(190, 242, 84);

    private final TextView[] cells = new TextView[6];
    private final EditText input;
    private final PairingCodeController controller;

    public PairingCodeInputView(Context context, PairingCodeController.Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        controller = new PairingCodeController(listener);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int index = 0; index < cells.length; index++) {
            TextView cell = new TextView(context);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(25);
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setTextColor(GRAPHITE);
            cell.setBackground(box(false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1f);
            if (index > 0) {
                params.setMargins(dp(5), 0, 0, 0);
            }
            row.addView(cell, params);
            cells[index] = cell;
        }
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input = new EditText(context);
        input.setContentDescription("Código de 6 números");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(6)});
        input.setTextColor(Color.TRANSPARENT);
        input.setCursorVisible(false);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setSingleLine(true);
        input.setAlpha(0.02f);
        addView(input, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        row.setOnClickListener(view -> focusKeyboard());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateCells(text == null ? "" : text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String value = editable == null ? "" : editable.toString();
                if (controller.accept(value) && controller.submitted()) {
                    input.setEnabled(false);
                    hideKeyboard();
                }
            }
        });
    }

    public void focusKeyboard() {
        input.setEnabled(true);
        input.requestFocus();
        post(() -> {
            InputMethodManager keyboard = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    public void allowRetry() {
        controller.allowRetry();
        input.setEnabled(true);
        input.setText("");
        focusKeyboard();
    }

    private void updateCells(String value) {
        for (int index = 0; index < cells.length; index++) {
            cells[index].setText(index < value.length() ? String.valueOf(value.charAt(index)) : "");
            cells[index].setBackground(box(index == Math.min(value.length(), cells.length - 1)));
        }
    }

    private GradientDrawable box(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(13));
        drawable.setStroke(dp(active ? 2 : 1), active ? LIME : LINE);
        return drawable;
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
