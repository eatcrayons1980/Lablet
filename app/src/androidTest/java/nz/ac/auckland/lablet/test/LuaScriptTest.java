/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.test;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import nz.ac.auckland.lablet.ScriptHomeActivity;
import nz.ac.auckland.lablet.script.LuaScriptLoader;
import nz.ac.auckland.lablet.script.Script;
import nz.ac.auckland.lablet.script.components.ScriptComponentFragmentFactory;

import java.io.*;


@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class LuaScriptTest extends ActivityInstrumentationTestCase2<ScriptHomeActivity> {
    private Activity activity;

    @TargetApi(Build.VERSION_CODES.FROYO)
    public LuaScriptTest() {
        super(ScriptHomeActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        activity = getActivity();

        /* TODO: This test is no longer working. */
        /* copyResourceScripts(true); */
    }

    /**
     * Load a script and check its initial state.
     *
     * TODO: This test is no longer working.
     */
    /*
    @SmallTest
    public void testScriptLoading() {
        File dir = ScriptHomeActivity.getScriptDirectory(activity);
        assertNotNull(dir);

        Context testContext = getInstrumentation().getContext();
        File scriptFile = new File(getScriptDirectory(testContext), "ScriptTesting.lua");
        assertTrue(scriptFile.exists());

        ScriptComponentFragmentFactory factory = new ScriptComponentFragmentFactory();
        LuaScriptLoader loader = new LuaScriptLoader(factory);
        Script script = loader.load(scriptFile);
        assertNotNull(script);

        assertTrue(script.start());

        assertEquals(1, script.getActiveChain().size());
    }
    */

    @TargetApi(Build.VERSION_CODES.FROYO)
    static public File getScriptDirectory(Context context) {
        File baseDir = context.getExternalFilesDir(null);
        File scriptDir = new File(baseDir, "scripts");
        if (!scriptDir.exists())
            scriptDir.mkdir();
        return scriptDir;
    }

    /**
     * TODO: This test is no longer working.
     */
    /*
    private void copyResourceScripts(boolean overwriteExisting) {
        File scriptDir = ScriptHomeActivity.getScriptDirectory(getInstrumentation().getContext());
        if (!scriptDir.exists()) {
            if (!scriptDir.mkdir())
                return;
        }
        try {
            String[] files = getInstrumentation().getContext().getAssets().list("");
            for (String file : files) {
                if (!isLuaFile(file))
                    continue;
                InputStream inputStream = getInstrumentation().getContext().getAssets().open(file);
                File scriptOutFile = new File(scriptDir, file);
                if (!overwriteExisting && scriptOutFile.exists())
                    continue;

                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(scriptOutFile, false));
                byte[] buffer = new byte[16384];
                while(true) {
                    int n = inputStream.read(buffer);
                    if (n <= -1)
                        break;
                    outputStream.write(buffer, 0, n);
                }

                inputStream.close();
                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */

    private boolean isLuaFile(String name) {
        return name.lastIndexOf(".lua") == name.length() - 4;
    }
}
