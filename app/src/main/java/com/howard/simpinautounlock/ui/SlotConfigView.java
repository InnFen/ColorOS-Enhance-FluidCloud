package com.howard.simpinautounlock.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import dev.howard.colorosenhance.R;

public final class SlotConfigView extends MaterialCardView {
    private final TextView title;
    private final TextView statusText;
    private final EditText pinEntry;
    private final EditText confirmationEntry;
    private final Button saveButton;
    private final MaterialSwitch toggleButton;
    private final Button clearButton;

    private int slotId;
    private boolean enabled;
    private boolean renderingToggle;

    public SlotConfigView(Context context) {
        this(context, null);
    }

    public SlotConfigView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    public SlotConfigView(Context context, AttributeSet attributes, int defaultStyleAttribute) {
        super(context, attributes, defaultStyleAttribute);
        LayoutInflater.from(getContext()).inflate(R.layout.sim_view_slot_config, this, true);
        title = findViewById(R.id.sim_slot_title);
        statusText = findViewById(R.id.sim_slot_status);
        pinEntry = findViewById(R.id.sim_pin_entry);
        confirmationEntry = findViewById(R.id.sim_pin_confirmation);
        saveButton = findViewById(R.id.sim_save_button);
        toggleButton = findViewById(R.id.sim_toggle_button);
        clearButton = findViewById(R.id.sim_clear_button);
    }

    public void bind(int slotId, Listener listener) {
        this.slotId = slotId;
        title.setText(getContext().getString(R.string.sim_slot_title, slotId + 1));
        toggleButton.setContentDescription(
                getContext().getString(R.string.sim_slot_toggle_description, slotId + 1));
        saveButton.setOnClickListener(view -> {
            String pin = pinEntry.getText().toString();
            String confirmation = confirmationEntry.getText().toString();
            try {
                listener.onSave(this.slotId, pin, confirmation);
            } finally {
                clearInputs();
            }
        });
        toggleButton.setOnCheckedChangeListener((view, requestedState) -> {
            if (renderingToggle || requestedState == enabled) {
                return;
            }
            // Handles taps, accessibility actions and thumb drags. Only the bridge result
            // changes the authoritative state; rendering must not send another request.
            renderToggleState(enabled);
            listener.onToggle(this.slotId, requestedState);
        });
        clearButton.setOnClickListener(view -> listener.onClear(this.slotId));
    }

    public void render(SlotPresentation presentation) {
        statusText.setTextAppearance(R.style.sim_TextAppearance_SimPin_Body);
        String displayText = presentation.displayText();
        if (presentation.pendingWarningVisible()) {
            displayText += "\n" + getContext().getString(R.string.sim_warning_pending_same_sim_slot);
        }
        statusText.setText(displayText);

        enabled = presentation.toggleState();
        toggleButton.setEnabled(presentation.toggleEnabled());
        renderToggleState(enabled);
        clearButton.setEnabled(presentation.clearEnabled());
    }

    public void showLoading() {
        enabled = false;
        statusText.setTextAppearance(R.style.sim_TextAppearance_SimPin_Body);
        statusText.setText(R.string.sim_status_loading);
        toggleButton.setEnabled(false);
        renderToggleState(false);
        clearButton.setEnabled(false);
    }

    public void showError(String message) {
        enabled = false;
        statusText.setTextAppearance(R.style.sim_TextAppearance_SimPin_Error);
        statusText.setText(message);
        toggleButton.setEnabled(false);
        renderToggleState(false);
        clearButton.setEnabled(false);
    }

    public void showNotice(String message) {
        statusText.setTextAppearance(R.style.sim_TextAppearance_SimPin_Body);
        statusText.setText(message);
    }

    public void clearInputs() {
        pinEntry.getText().clear();
        confirmationEntry.getText().clear();
    }

    private void renderToggleState(boolean checked) {
        renderingToggle = true;
        try {
            toggleButton.setChecked(checked);
        } finally {
            renderingToggle = false;
        }
    }

    public interface Listener {
        void onSave(int slotId, String pin, String confirmation);

        void onToggle(int slotId, boolean enabled);

        void onClear(int slotId);
    }
}
