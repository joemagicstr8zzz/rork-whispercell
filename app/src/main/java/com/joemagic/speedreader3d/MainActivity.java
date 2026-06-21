package com.joemagic.speedreader3d;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "speed_reader_3d_prefs";
    private static final String KEY_TEXT = "text";
    private static final String KEY_WPM = "wpm";
    private static final String KEY_CHUNK_SIZE = "chunk_size";
    private static final String KEY_PUNCTUATION = "punctuation";
    private static final String KEY_VISUAL_MODE = "visual_mode";

    private static final String MODE_TUNNEL = "tunnel";
    private static final String MODE_GLASS = "glass";
    private static final String MODE_PLAIN = "plain";

    private static final int COLOR_BG = Color.rgb(7, 9, 19);
    private static final int COLOR_PANEL = Color.rgb(18, 23, 43);
    private static final int COLOR_PANEL_SOFT = Color.rgb(14, 18, 33);
    private static final int COLOR_TEXT = Color.rgb(247, 249, 255);
    private static final int COLOR_MUTED = Color.rgb(155, 168, 200);
    private static final int COLOR_MUTED_DARK = Color.rgb(104, 114, 145);
    private static final int COLOR_ACCENT = Color.rgb(124, 247, 255);
    private static final int COLOR_ACCENT_2 = Color.rgb(173, 124, 255);
    private static final int COLOR_WARNING = Color.rgb(255, 242, 124);

    private static final String SAMPLE_TEXT = "Speed reading is not about bullying your eyes into panic. " +
            "It is about removing drag. A good reader builds rhythm, trusts focus, and lets each phrase arrive cleanly. " +
            "This little machine is designed to help with that. Start slow, find the pace where the words still feel meaningful, " +
            "then increase speed in small steps. The goal is not to win a number. The goal is to read with momentum.";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Chunk> chunks = new ArrayList<>();

    private SpeedReaderView readerView;
    private TextView wordsValue;
    private TextView wpmValue;
    private TextView remainingValue;
    private TextView sessionValue;
    private TextView speedLabel;
    private TextView chunkLabel;
    private TextView helperText;
    private EditText textInput;
    private Button playButton;
    private Button backButton;
    private Button nextButton;
    private Button resetButton;
    private SeekBar speedSeekBar;
    private SeekBar chunkSeekBar;
    private Switch punctuationSwitch;
    private RadioGroup modeGroup;

    private int tunnelId;
    private int glassId;
    private int plainId;

    private String rawText = SAMPLE_TEXT;
    private int wpm = 320;
    private int chunkSize = 1;
    private boolean punctuationBoost = true;
    private String visualMode = MODE_TUNNEL;
    private boolean isPlaying = false;
    private int activeIndex = 0;

    private final Runnable advanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying || chunks.isEmpty()) {
                return;
            }

            if (activeIndex >= chunks.size() - 1) {
                isPlaying = false;
                updateUi();
                return;
            }

            activeIndex++;
            updateUi();
            scheduleNextChunk();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        loadPrefs();
        rebuildChunks();
        buildInterface();
        updateUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePrefs();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(advanceRunnable);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        View focus = getCurrentFocus();
        if (focus instanceof EditText) {
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            togglePlayPause();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            stepBy(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            stepBy(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_R) {
            resetSession();
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void buildInterface() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(makePageBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(makeHeroPanel());

        readerView = new SpeedReaderView(this);
        LinearLayout.LayoutParams readerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(380)
        );
        readerParams.setMargins(0, dp(16), 0, dp(12));
        root.addView(readerView, readerParams);

        root.addView(makeTransportControls());
        root.addView(makeInputPanel());
        root.addView(makeSettingsPanel());

        setContentView(scrollView);
    }

    private View makeHeroPanel() {
        LinearLayout panel = makePanel();

        TextView eyebrow = makeText("Speed Reader 3D", 12, COLOR_ACCENT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        panel.addView(eyebrow);

        TextView title = makeText("Read inside a focus tunnel.", 34, COLOR_TEXT, Typeface.BOLD);
        title.setLineSpacing(0, 0.92f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(6), 0, dp(10));
        panel.addView(title, titleParams);

        TextView description = makeText(
                "Paste text, pick your pace, and let each word land cleanly in the center.",
                15,
                COLOR_MUTED,
                Typeface.NORMAL
        );
        description.setLineSpacing(dp(2), 1.1f);
        panel.addView(description);

        LinearLayout statRowOne = makeHorizontalRow();
        LinearLayout statRowTwo = makeHorizontalRow();
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(16), 0, 0);
        panel.addView(statRowOne, rowParams);
        panel.addView(statRowTwo);

        wordsValue = makeStatCard(statRowOne, "Words");
        wpmValue = makeStatCard(statRowOne, "WPM");
        remainingValue = makeStatCard(statRowTwo, "Remaining");
        sessionValue = makeStatCard(statRowTwo, "Session");

        return panel;
    }

    private LinearLayout makeTransportControls() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);

        LinearLayout rowOne = makeHorizontalRow();
        LinearLayout rowTwo = makeHorizontalRow();
        shell.addView(rowOne);
        shell.addView(rowTwo);

        backButton = makeButton("Back", false);
        playButton = makeButton("Play", true);
        nextButton = makeButton("Next", false);
        resetButton = makeButton("Reset", false);

        rowOne.addView(backButton, equalButtonParams(true));
        rowOne.addView(playButton, equalButtonParams(false));
        rowTwo.addView(nextButton, equalButtonParams(true));
        rowTwo.addView(resetButton, equalButtonParams(false));

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stepBy(-1);
            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlayPause();
            }
        });
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stepBy(1);
            }
        });
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetSession();
            }
        });

        return shell;
    }

    private View makeInputPanel() {
        LinearLayout panel = makePanel();
        LinearLayout.LayoutParams panelParams = (LinearLayout.LayoutParams) panel.getLayoutParams();
        if (panelParams != null) {
            panelParams.setMargins(0, dp(16), 0, 0);
        }

        LinearLayout header = makeHorizontalRow();
        TextView title = makeText("Your text", 24, COLOR_TEXT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button sampleButton = makeMiniButton("Sample");
        Button clearButton = makeMiniButton("Clear");
        header.addView(sampleButton);
        header.addView(clearButton);
        panel.addView(header);

        textInput = new EditText(this);
        textInput.setText(rawText);
        textInput.setTextColor(COLOR_TEXT);
        textInput.setHintTextColor(COLOR_MUTED_DARK);
        textInput.setTextSize(15);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setMinLines(8);
        textInput.setSingleLine(false);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textInput.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        textInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        textInput.setHint("Paste something here, then tap Play.");
        textInput.setBackground(makeRoundedDrawable(Color.rgb(5, 8, 18), dp(18), Color.argb(48, 255, 255, 255), dp(1)));

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, dp(14), 0, 0);
        panel.addView(textInput, inputParams);

        textInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                rawText = charSequence.toString();
                activeIndex = 0;
                isPlaying = false;
                handler.removeCallbacks(advanceRunnable);
                rebuildChunks();
                savePrefs();
                updateUi();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        sampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textInput.setText(SAMPLE_TEXT);
            }
        });
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textInput.setText("");
            }
        });

        return panel;
    }

    private View makeSettingsPanel() {
        LinearLayout panel = makePanel();
        LinearLayout.LayoutParams panelParams = (LinearLayout.LayoutParams) panel.getLayoutParams();
        if (panelParams != null) {
            panelParams.setMargins(0, dp(16), 0, 0);
        }

        TextView title = makeText("Reading engine", 24, COLOR_TEXT, Typeface.BOLD);
        panel.addView(title);

        speedLabel = makeText("", 15, COLOR_MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams speedLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        speedLabelParams.setMargins(0, dp(18), 0, dp(4));
        panel.addView(speedLabel, speedLabelParams);

        speedSeekBar = new SeekBar(this);
        speedSeekBar.setMax(80);
        speedSeekBar.setProgress((wpm - 100) / 10);
        panel.addView(speedSeekBar);
        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                wpm = 100 + (progress * 10);
                savePrefs();
                updateUi();
                if (isPlaying) {
                    scheduleNextChunk();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        chunkLabel = makeText("", 15, COLOR_MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams chunkLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        chunkLabelParams.setMargins(0, dp(18), 0, dp(4));
        panel.addView(chunkLabel, chunkLabelParams);

        chunkSeekBar = new SeekBar(this);
        chunkSeekBar.setMax(3);
        chunkSeekBar.setProgress(chunkSize - 1);
        panel.addView(chunkSeekBar);
        chunkSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                chunkSize = progress + 1;
                activeIndex = 0;
                isPlaying = false;
                handler.removeCallbacks(advanceRunnable);
                rebuildChunks();
                savePrefs();
                updateUi();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        punctuationSwitch = new Switch(this);
        punctuationSwitch.setText("Punctuation pauses");
        punctuationSwitch.setTextColor(COLOR_TEXT);
        punctuationSwitch.setTextSize(15);
        punctuationSwitch.setChecked(punctuationBoost);
        punctuationSwitch.setPadding(0, dp(12), 0, dp(12));
        panel.addView(punctuationSwitch);
        punctuationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                punctuationBoost = checked;
                savePrefs();
                updateUi();
                if (isPlaying) {
                    scheduleNextChunk();
                }
            }
        });

        TextView modeLabel = makeText("Visual mode", 15, COLOR_MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams modeLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        modeLabelParams.setMargins(0, dp(8), 0, dp(8));
        panel.addView(modeLabel, modeLabelParams);

        tunnelId = View.generateViewId();
        glassId = View.generateViewId();
        plainId = View.generateViewId();

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        modeGroup.addView(makeRadioButton("Tunnel", tunnelId));
        modeGroup.addView(makeRadioButton("Glass", glassId));
        modeGroup.addView(makeRadioButton("Plain", plainId));
        panel.addView(modeGroup);

        if (MODE_GLASS.equals(visualMode)) {
            modeGroup.check(glassId);
        } else if (MODE_PLAIN.equals(visualMode)) {
            modeGroup.check(plainId);
        } else {
            modeGroup.check(tunnelId);
        }

        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                if (checkedId == glassId) {
                    visualMode = MODE_GLASS;
                } else if (checkedId == plainId) {
                    visualMode = MODE_PLAIN;
                } else {
                    visualMode = MODE_TUNNEL;
                }
                savePrefs();
                updateUi();
            }
        });

        helperText = makeText("Space bar plays or pauses if you use a keyboard. D-pad left/right steps through words. R resets.", 13, COLOR_MUTED_DARK, Typeface.NORMAL);
        helperText.setLineSpacing(dp(2), 1.05f);
        LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        helperParams.setMargins(0, dp(16), 0, 0);
        panel.addView(helperText, helperParams);

        return panel;
    }

    private RadioButton makeRadioButton(String label, int id) {
        RadioButton radioButton = new RadioButton(this);
        radioButton.setId(id);
        radioButton.setText(label);
        radioButton.setTextColor(COLOR_TEXT);
        radioButton.setTextSize(14);
        radioButton.setPadding(0, 0, dp(12), 0);
        return radioButton;
    }

    private void togglePlayPause() {
        if (chunks.isEmpty()) {
            isPlaying = false;
            updateUi();
            return;
        }

        if (!isPlaying && activeIndex >= chunks.size() - 1) {
            activeIndex = 0;
        }

        isPlaying = !isPlaying;
        updateUi();

        if (isPlaying) {
            scheduleNextChunk();
        } else {
            handler.removeCallbacks(advanceRunnable);
        }
    }

    private void stepBy(int amount) {
        isPlaying = false;
        handler.removeCallbacks(advanceRunnable);
        if (!chunks.isEmpty()) {
            activeIndex = clamp(activeIndex + amount, 0, chunks.size() - 1);
        } else {
            activeIndex = 0;
        }
        updateUi();
    }

    private void resetSession() {
        isPlaying = false;
        handler.removeCallbacks(advanceRunnable);
        activeIndex = 0;
        updateUi();
    }

    private void scheduleNextChunk() {
        handler.removeCallbacks(advanceRunnable);
        if (!isPlaying || chunks.isEmpty()) {
            return;
        }
        Chunk chunk = chunks.get(activeIndex);
        handler.postDelayed(advanceRunnable, getDelayForChunk(chunk));
    }

    private long getDelayForChunk(Chunk chunk) {
        double baseWordDelay = 60000.0 / Math.max(100, wpm);
        double pauseWeight = punctuationBoost ? chunk.pauseWeight : 1.0;
        return Math.max(90L, Math.round(baseWordDelay * chunk.wordCount * pauseWeight));
    }

    private void updateUi() {
        int totalWords = getTotalWords();
        Chunk activeChunk = chunks.isEmpty() ? null : chunks.get(clamp(activeIndex, 0, chunks.size() - 1));
        Chunk previousChunk = activeIndex > 0 && !chunks.isEmpty() ? chunks.get(activeIndex - 1) : null;
        Chunk nextChunk = activeIndex + 1 < chunks.size() ? chunks.get(activeIndex + 1) : null;

        int wordsRead = activeChunk == null ? 0 : activeChunk.endWord;
        int wordsRemaining = Math.max(0, totalWords - wordsRead);
        long remainingMillis = Math.round((wordsRemaining / (double) Math.max(100, wpm)) * 60000.0);
        long sessionMillis = Math.round((totalWords / (double) Math.max(100, wpm)) * 60000.0);
        float progress = chunks.size() <= 1 ? 0f : activeIndex / (float) Math.max(1, chunks.size() - 1);

        if (wordsValue != null) {
            wordsValue.setText(String.format(Locale.US, "%d", totalWords));
        }
        if (wpmValue != null) {
            wpmValue.setText(String.format(Locale.US, "%d", wpm));
        }
        if (remainingValue != null) {
            remainingValue.setText(formatTime(remainingMillis));
        }
        if (sessionValue != null) {
            sessionValue.setText(formatTime(sessionMillis));
        }
        if (speedLabel != null) {
            speedLabel.setText(String.format(Locale.US, "Speed: %d WPM", wpm));
        }
        if (chunkLabel != null) {
            String wordLabel = chunkSize == 1 ? "word" : "words";
            chunkLabel.setText(String.format(Locale.US, "Chunk size: %d %s", chunkSize, wordLabel));
        }
        if (playButton != null) {
            playButton.setText(isPlaying ? "Pause" : "Play");
            styleButton(playButton, isPlaying);
        }
        if (readerView != null) {
            readerView.setReaderState(
                    previousChunk == null ? "Ready" : previousChunk.text,
                    activeChunk == null ? "Paste text to begin" : activeChunk.text,
                    nextChunk == null ? "Complete" : nextChunk.text,
                    activeChunk == null ? "0" : activeChunk.startWord + "-" + activeChunk.endWord,
                    Math.round(progress * 100f) + "%",
                    progress,
                    visualMode
            );
        }
    }

    private void rebuildChunks() {
        chunks.clear();
        String normalized = normalize(rawText);
        if (normalized.length() == 0) {
            activeIndex = 0;
            return;
        }

        String[] words = normalized.split("\\s+");
        for (int index = 0; index < words.length; index += chunkSize) {
            int end = Math.min(words.length, index + chunkSize);
            StringBuilder builder = new StringBuilder();
            for (int wordIndex = index; wordIndex < end; wordIndex++) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(words[wordIndex]);
            }

            String lastWord = words[end - 1];
            double pauseWeight = 1.0;
            if (lastWord.matches(".*[.!?][\\]})\"'”’]*$")) {
                pauseWeight = 1.85;
            } else if (lastWord.matches(".*[,;:][\\]})\"'”’]*$")) {
                pauseWeight = 1.32;
            }

            chunks.add(new Chunk(builder.toString(), end - index, index + 1, end, pauseWeight));
        }

        if (activeIndex >= chunks.size()) {
            activeIndex = Math.max(0, chunks.size() - 1);
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private int getTotalWords() {
        int total = 0;
        for (Chunk chunk : chunks) {
            total += chunk.wordCount;
        }
        return total;
    }

    private String formatTime(long millis) {
        if (millis <= 0) {
            return "0:00";
        }
        long seconds = (long) Math.ceil(millis / 1000.0);
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds);
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rawText = prefs.getString(KEY_TEXT, SAMPLE_TEXT);
        wpm = clamp(prefs.getInt(KEY_WPM, 320), 100, 900);
        chunkSize = clamp(prefs.getInt(KEY_CHUNK_SIZE, 1), 1, 4);
        punctuationBoost = prefs.getBoolean(KEY_PUNCTUATION, true);
        visualMode = prefs.getString(KEY_VISUAL_MODE, MODE_TUNNEL);
        if (!MODE_TUNNEL.equals(visualMode) && !MODE_GLASS.equals(visualMode) && !MODE_PLAIN.equals(visualMode)) {
            visualMode = MODE_TUNNEL;
        }
    }

    private void savePrefs() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_TEXT, rawText);
        editor.putInt(KEY_WPM, wpm);
        editor.putInt(KEY_CHUNK_SIZE, chunkSize);
        editor.putBoolean(KEY_PUNCTUATION, punctuationBoost);
        editor.putString(KEY_VISUAL_MODE, visualMode);
        editor.apply();
    }

    private LinearLayout makePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(makeRoundedDrawable(COLOR_PANEL, dp(24), Color.argb(34, 255, 255, 255), dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout makeHorizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView makeText(String text, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(color);
        textView.setTextSize(sp);
        textView.setTypeface(Typeface.DEFAULT, style);
        return textView;
    }

    private TextView makeStatCard(LinearLayout row, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(makeRoundedDrawable(COLOR_PANEL_SOFT, dp(18), Color.argb(34, 255, 255, 255), dp(1)));

        TextView labelView = makeText(label.toUpperCase(Locale.US), 11, COLOR_MUTED, Typeface.BOLD);
        labelView.setLetterSpacing(0.08f);
        TextView valueView = makeText("0", 24, COLOR_TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        valueParams.setMargins(0, dp(5), 0, 0);
        card.addView(labelView);
        card.addView(valueView, valueParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.addView(card, params);
        return valueView;
    }

    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(COLOR_TEXT);
        button.setAllCaps(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        styleButton(button, primary);
        return button;
    }

    private Button makeMiniButton(String label) {
        Button button = makeButton(label, false);
        button.setTextSize(13);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        return button;
    }

    private LinearLayout.LayoutParams equalButtonParams(boolean rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(0, dp(4), rightMargin ? dp(8) : 0, dp(4));
        return params;
    }

    private void styleButton(Button button, boolean primary) {
        int fill = primary ? Color.rgb(31, 55, 68) : Color.rgb(18, 23, 43);
        int stroke = primary ? Color.argb(170, 124, 247, 255) : Color.argb(54, 255, 255, 255);
        button.setBackground(makeRoundedDrawable(fill, dp(18), stroke, dp(1)));
    }

    private GradientDrawable makeRoundedDrawable(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable makePageBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(6, 7, 17), Color.rgb(16, 19, 35), Color.rgb(5, 6, 12)}
        );
        return drawable;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Chunk {
        final String text;
        final int wordCount;
        final int startWord;
        final int endWord;
        final double pauseWeight;

        Chunk(String text, int wordCount, int startWord, int endWord, double pauseWeight) {
            this.text = text;
            this.wordCount = wordCount;
            this.startWord = startWord;
            this.endWord = endWord;
            this.pauseWeight = pauseWeight;
        }
    }

    public static class SpeedReaderView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String previous = "Ready";
        private String current = "Paste text to begin";
        private String next = "Complete";
        private String wordRange = "0";
        private String percent = "0%";
        private float progress = 0f;
        private String mode = MODE_TUNNEL;

        public SpeedReaderView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setReaderState(String previous, String current, String next, String wordRange, String percent, float progress, String mode) {
            this.previous = previous;
            this.current = current;
            this.next = next;
            this.wordRange = wordRange;
            this.percent = percent;
            this.progress = Math.max(0f, Math.min(1f, progress));
            this.mode = mode;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            float radius = dpLocal(28);

            paint.setShader(new LinearGradient(
                    0,
                    0,
                    width,
                    height,
                    new int[]{Color.rgb(10, 13, 28), Color.rgb(5, 7, 15), Color.rgb(18, 15, 35)},
                    null,
                    Shader.TileMode.CLAMP
            ));
            RectF full = new RectF(dpLocal(1), dpLocal(1), width - dpLocal(1), height - dpLocal(1));
            canvas.drawRoundRect(full, radius, radius, paint);
            paint.setShader(null);

            if (MODE_TUNNEL.equals(mode)) {
                drawTunnel(canvas, width, height, 1f);
            } else if (MODE_GLASS.equals(mode)) {
                drawTunnel(canvas, width, height, 0.25f);
                drawGlassGlow(canvas, width, height);
            }

            drawReaderCard(canvas, width, height);
        }

        private void drawTunnel(Canvas canvas, int width, int height, float strength) {
            float centerX = width / 2f;
            float centerY = height * 0.48f;
            int[] alphas = new int[]{52, 78, 105, 66};
            float[] sizes = new float[]{0.46f, 0.64f, 0.84f, 1.08f};

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(1.4f));
            for (int i = 0; i < sizes.length; i++) {
                int alpha = Math.round(alphas[i] * strength);
                paint.setColor(Color.argb(alpha, 124, 247, 255));
                float ovalWidth = width * sizes[i];
                float ovalHeight = height * sizes[i] * 0.34f;
                RectF oval = new RectF(
                        centerX - ovalWidth / 2f,
                        centerY - ovalHeight / 2f,
                        centerX + ovalWidth / 2f,
                        centerY + ovalHeight / 2f
                );
                canvas.save();
                canvas.rotate(45f, centerX, centerY);
                canvas.drawOval(oval, paint);
                canvas.restore();
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(90 * strength), 124, 247, 255));
            RectF horizon = new RectF(width * 0.12f, height * 0.62f, width * 0.88f, height * 0.62f + dpLocal(1));
            canvas.drawRoundRect(horizon, dpLocal(1), dpLocal(1), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawGlassGlow(Canvas canvas, int width, int height) {
            paint.setShader(new LinearGradient(
                    0,
                    height * 0.25f,
                    width,
                    height * 0.75f,
                    new int[]{Color.argb(30, 255, 255, 255), Color.argb(10, 124, 247, 255), Color.TRANSPARENT},
                    null,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(width * 0.5f, height * 0.42f, Math.min(width, height) * 0.38f, paint);
            paint.setShader(null);
        }

        private void drawReaderCard(Canvas canvas, int width, int height) {
            RectF card = new RectF(dpLocal(14), height * 0.15f, width - dpLocal(14), height * 0.86f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(188, 6, 8, 17));
            canvas.drawRoundRect(card, dpLocal(26), dpLocal(26), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpLocal(1));
            paint.setColor(Color.argb(58, 255, 255, 255));
            canvas.drawRoundRect(card, dpLocal(26), dpLocal(26), paint);
            paint.setStyle(Paint.Style.FILL);

            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setColor(COLOR_MUTED);
            textPaint.setTextSize(spLocal(12));
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(wordRange, card.left + dpLocal(18), card.top + dpLocal(27), textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(percent, card.right - dpLocal(18), card.top + dpLocal(27), textPaint);

            drawSingleLine(canvas, previous, card.centerX(), card.top + dpLocal(72), card.width() - dpLocal(42), spLocal(18), COLOR_MUTED_DARK, true);
            drawFocusText(canvas, current, card.centerX(), card.centerY(), card.width() - dpLocal(42), card.height() * 0.36f);
            drawSingleLine(canvas, next, card.centerX(), card.bottom - dpLocal(66), card.width() - dpLocal(42), spLocal(18), COLOR_MUTED_DARK, true);

            float barHeight = dpLocal(10);
            RectF barBg = new RectF(card.left + dpLocal(18), card.bottom - dpLocal(28), card.right - dpLocal(18), card.bottom - dpLocal(28) + barHeight);
            paint.setColor(Color.argb(36, 255, 255, 255));
            canvas.drawRoundRect(barBg, barHeight / 2f, barHeight / 2f, paint);

            RectF barFill = new RectF(barBg.left, barBg.top, barBg.left + (barBg.width() * progress), barBg.bottom);
            paint.setShader(new LinearGradient(
                    barBg.left,
                    0,
                    barBg.right,
                    0,
                    new int[]{COLOR_ACCENT, COLOR_ACCENT_2, COLOR_WARNING},
                    null,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(barFill, barHeight / 2f, barHeight / 2f, paint);
            paint.setShader(null);
        }

        private void drawFocusText(Canvas canvas, String text, float centerX, float centerY, float maxWidth, float maxHeight) {
            float textSize = chooseFocusSize(text);
            StaticLayout layout = makeLayout(text, textSize, maxWidth, COLOR_TEXT, Typeface.BOLD);

            while (layout.getHeight() > maxHeight && textSize > spLocal(26)) {
                textSize -= spLocal(2);
                layout = makeLayout(text, textSize, maxWidth, COLOR_TEXT, Typeface.BOLD);
            }

            canvas.save();
            canvas.translate(centerX - maxWidth / 2f, centerY - layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
        }

        private float chooseFocusSize(String text) {
            int length = text == null ? 0 : text.length();
            if (length <= 5) {
                return spLocal(72);
            }
            if (length <= 12) {
                return spLocal(58);
            }
            if (length <= 22) {
                return spLocal(44);
            }
            return spLocal(34);
        }

        private StaticLayout makeLayout(String text, float textSize, float width, int color, int style) {
            textPaint.setTextSize(textSize);
            textPaint.setColor(color);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, style));
            textPaint.setTextAlign(Paint.Align.LEFT);
            return StaticLayout.Builder
                    .obtain(text, 0, text.length(), textPaint, (int) width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 0.95f)
                    .setIncludePad(false)
                    .build();
        }

        private void drawSingleLine(Canvas canvas, String text, float centerX, float baseline, float maxWidth, float textSize, int color, boolean bold) {
            textPaint.setTextSize(textSize);
            textPaint.setColor(color);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.CENTER);
            CharSequence trimmed = TextUtils.ellipsize(text, textPaint, maxWidth, TextUtils.TruncateAt.END);
            canvas.drawText(trimmed.toString(), centerX, baseline, textPaint);
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private float spLocal(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }
    }
}
