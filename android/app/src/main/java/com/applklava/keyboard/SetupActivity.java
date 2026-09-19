package com.applklava.keyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SetupActivity extends AppCompatActivity {

    private TextView statusText;
    private TextView zoneVal;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        prefs = getSharedPreferences("applklava", MODE_PRIVATE);
        statusText = findViewById(R.id.statusText);
        zoneVal = findViewById(R.id.zoneVal);
        EditText testField = findViewById(R.id.testField);
        Button btnEnable = findViewById(R.id.btnEnable);
        Button btnChoose = findViewById(R.id.btnChoose);
        Button btnPicker = findViewById(R.id.btnPicker);
        SeekBar zoneBar = findViewById(R.id.zoneBar);

        float zone = prefs.getFloat("zoneWidth", 1.45f);
        zoneBar.setMax(120);
        zoneBar.setProgress(Math.round((zone - 1.0f) * 100f));
        zoneVal.setText(String.format("%.2f×", zone));

        zoneBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = 1.0f + progress / 100f;
                zoneVal.setText(String.format("%.2f×", v));
                if (fromUser) prefs.edit().putFloat("zoneWidth", v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnEnable.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Открой Настройки → Язык и ввод → Клавиатура", Toast.LENGTH_LONG).show();
            }
        });

        btnChoose.setOnClickListener(v -> showPicker());
        btnPicker.setOnClickListener(v -> showPicker());

        testField.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(testField, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void refreshStatus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        if (imm != null) {
            String id = getPackageName() + "/.KlavaImeService";
            String id2 = getPackageName() + "/" + KlavaImeService.class.getName();
            for (android.view.inputmethod.InputMethodInfo info : imm.getEnabledInputMethodList()) {
                String iid = info.getId();
                if (iid.equals(id) || iid.equals(id2) || iid.contains("KlavaImeService")) {
                    enabled = true;
                    break;
                }
            }
        }

        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = current != null && current.contains("KlavaImeService");

        if (!enabled) {
            statusText.setText("1) Включи «ApplKlava» в списке клавиатур\n2) Затем выбери её как текущую");
        } else if (!selected) {
            statusText.setText("Клавиатура включена. Нажми «Выбрать ApplKlava» и укажи её.");
        } else {
            statusText.setText("Готово: ApplKlava — текущая клавиатура.\nКнопка «Обычная» на клаве вернёт системную.");
        }
    }
}
