package com.example.AndroidPhysicsTracker;


import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

public class CalibrationView extends AlertDialog {
    private LengthCalibrationSetter calibrationSetter;
    private ExperimentAnalysis experimentAnalysis;
    private EditText lengthEditText;

    protected CalibrationView(Context context, LengthCalibrationSetter calibrationSetter, ExperimentAnalysis analysis) {
        super(context);

        this.calibrationSetter = calibrationSetter;
        this.experimentAnalysis = analysis;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View contentView = inflater.inflate(R.layout.calibration_settings, null);
        setTitle("Calibration");
        addContentView(contentView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        lengthEditText = (EditText)contentView.findViewById(R.id.lengthEditText);
        String text = new String();
        text += calibrationSetter.getCalibrationValue();
        lengthEditText.setText(text);

        Button cancelButton = (Button)contentView.findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        Button applyButton = (Button)contentView.findViewById(R.id.applyButton);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                float calibrationValue = Float.parseFloat(lengthEditText.getText().toString());
                calibrationSetter.setCalibrationValue(calibrationValue);
                dismiss();
            }
        });

        Spinner spinnerUnit = (Spinner)contentView.findViewById(R.id.spinnerUnit);
        List<String> list = new ArrayList<String>();
        list.add("[m]");
        list.add("[mm]");
        ArrayAdapter<String> unitsAdapter = new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item, list);
        spinnerUnit.setAdapter(unitsAdapter);
        if (experimentAnalysis.getXUnitPrefix().equals("m"))
            spinnerUnit.setSelection(1);

        spinnerUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (position == 0) {
                    experimentAnalysis.setXUnitPrefix("");
                    experimentAnalysis.setYUnitPrefix("");
                } else if (position == 1) {
                    experimentAnalysis.setXUnitPrefix("m");
                    experimentAnalysis.setYUnitPrefix("m");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }
}
