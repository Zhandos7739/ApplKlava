package com.applklava.keyboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

public class KlavaImeService extends InputMethodService implements KlavaKeyboardView.Listener {

    private KlavaKeyboardView keyboardView;

    @Override
    public View onCreateInputView() {
        keyboardView = new KlavaKeyboardView(this);
        keyboardView.setListener(this);
        return keyboardView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboardView != null) {
            keyboardView.reloadPrefs();
            keyboardView.refreshSuggestions();
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (keyboardView != null) {
            keyboardView.refreshSuggestions();
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public String currentWordPrefix() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence before = ic.getTextBeforeCursor(48, 0);
        if (before == null || before.length() == 0) return "";
        String s = before.toString();
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (Character.isLetter(c) || c == '\'' || c == '-' || c == 'ё' || c == 'Ё') {
                i--;
            } else {
                break;
            }
        }
        return s.substring(i + 1);
    }

    @Override
    public void onKey(String label) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if ("⌫".equals(label)) {
            CharSequence selected = ic.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                ic.commitText("", 1);
            } else {
                ic.deleteSurroundingText(1, 0);
            }
            if (keyboardView != null) keyboardView.refreshSuggestions();
            return;
        }

        if ("\n".equals(label)) {
            EditorInfo ei = getCurrentInputEditorInfo();
            int ime = ei != null ? ei.imeOptions : 0;
            int action = ime & EditorInfo.IME_MASK_ACTION;
            if (action == EditorInfo.IME_ACTION_GO
                    || action == EditorInfo.IME_ACTION_SEARCH
                    || action == EditorInfo.IME_ACTION_SEND
                    || action == EditorInfo.IME_ACTION_DONE
                    || action == EditorInfo.IME_ACTION_NEXT) {
                ic.performEditorAction(action);
            } else {
                ic.commitText("\n", 1);
            }
            if (keyboardView != null) keyboardView.refreshSuggestions();
            return;
        }

        ic.commitText(label, 1);
        if (keyboardView != null) keyboardView.refreshSuggestions();
    }

    @Override
    public void onSwitchToSystem() {
        boolean switched = false;
        try {
            switched = switchToPreviousInputMethod();
        } catch (Throwable ignored) {
            switched = false;
        }
        if (!switched) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        }
    }

    @Override
    public void onOpenSettings() {
        // reserved
    }
}
