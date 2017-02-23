package nz.ac.auckland.lablet.script.components;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import nz.ac.auckland.lablet.R;
import nz.ac.auckland.lablet.script.Script;
import nz.ac.auckland.lablet.script.ScriptComponentViewHolder;
import nz.ac.auckland.lablet.script.ScriptTreeNode;

/**
 * View holder for a view that has a question and a text input view.
 * <p>
 * The question is considered as answered as soon as the text input view contains some text. It is not checked if the
 * question is answered correctly.
 * </p>
 */
class TextQuestion extends ScriptComponentViewHolder {
    private String text = "";
    private Integer question_num = 0;
    private String answer = "";
    private boolean optional = false;
    private ScriptTreeNodeSheetBase component;

    public TextQuestion(Script script, String text, ScriptTreeNodeSheetBase component) {
        super(script);
        this.text = text;
        this.component = component;

        setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
        update();
    }

    @Override
    public View createView(Context context, Fragment parent) {
        ScriptTreeNodeSheetBase.Counter counter = this.component.getCounter("QuestionCounter");

        // Note: we have to do this programmatically because findViewById would find the wrong child
        // items if there is more than one text question.

        LinearLayout layout = new LinearLayout(context);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.setOrientation(LinearLayout.VERTICAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color, null));
        } else {
            layout.setBackgroundColor(context.getResources().getColor(R.color.sc_question_background_color));
        }

        TextView textView = new TextView(context);
        textView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.setTextAppearance(android.R.style.TextAppearance_Medium);
        } else {
            textView.setTextAppearance(context, android.R.style.TextAppearance_Medium);
        }
        question_num = counter.increaseValue();
        textView.setText("Q" + question_num + ": " + text);

        EditText editText = new EditText(context);
        editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        editText.setText(answer);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                answer = editable.toString();
                update();
            }
        });

        layout.addView(textView);
        layout.addView(editText);
        return layout;
    }

    @Override
    public boolean initCheck() {
        return true;
    }

    /**
     * Put the object data into a bundle.
     *
     * This information is saved to the user data for the app. To better identify the data, the
     * question text and number is also saved in this bundle, even though it is not needed in the
     * associated fromBundle method.
     *
     * If the answer is blank, the question text and number is not saved.
     *
     * @param bundle to store the component state in
     */
    public void toBundle(Bundle bundle) {
        if (!answer.equals("")) {
            bundle.putString("question", text);
            bundle.putInt("number", question_num);
        }
        bundle.putString("answer", answer);
        super.toBundle(bundle);
    }

    /**
     * Get the object data from a bundle.
     *
     * Note that even though the question, number, and answer are all stored in the bundle, the
     * answer is all that is needed.
     *
     * @param bundle that contains the component state
     * @return true if the state was restored
     */
    public boolean fromBundle(Bundle bundle) {
        answer = bundle.getString("answer", "");
        return super.fromBundle(bundle);
    }

    private void update() {
        if (optional)
            setState(ScriptTreeNode.SCRIPT_STATE_DONE);
        else if (!answer.equals(""))
            setState(ScriptTreeNode.SCRIPT_STATE_DONE);
        else
            setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
    }
}
