/*
 * Copyright 2013-2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package nz.ac.auckland.lablet.script;

import android.os.Bundle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Representation of the script, holds all script components.
 */
public class Script {
    private ScriptTreeNode root = null;
    private IScriptListener listener = null;
    private String lastError = "";

    private File userDataDirectory = null;

    public void setListener(IScriptListener listener) {
        this.listener = listener;
    }

    public File getUserDataDirectory() {
        return userDataDirectory;
    }

    void setUserDataDirectory(File userDataDirectory) {
        this.userDataDirectory = userDataDirectory;
    }

    /**
     * The root component tree is first page/ sheet in the script.
     *
     * @param component root component tree
     */
    void setRoot(ScriptTreeNode component) {
        root = component;
    }

    /**
     * The root component tree is first page/ sheet in the script.
     *
     * @return root component tree
     */
    public ScriptTreeNode getRoot() {
        return root;
    }

    /**
     * Check if all components are initialized correctly.
     *
     * @return true if script is ok
     */
    public boolean initCheck() {
        if (root == null)
            return false;
        java.util.Iterator<ScriptTreeNode> iterator = root.iterator();
        while (true) {
            if (!iterator.hasNext())
                break;
            ScriptTreeNode component = iterator.next();
            if (!component.initCheck()) {
                lastError = "In Component \"" + component.getName() + "\": ";
                lastError += component.getLastErrorMessage();
                return false;
            }
        }
        return true;
    }

    /**
     * If there was an error the message can be retrieved here.
     *
     * @return last error message
     */
    String getLastError() {
        return lastError;
    }

    /**
     * Returns the list of components that are currently available starting from the root tree component
     *
     * A active chain is the chain that can be created by following the SCRIPT_STATE_DONE state links.
     *
     * @return returns the active chain of the root tree component
     */
    public List<ScriptTreeNode> getActiveChain() {
        if (root == null)
            return new ArrayList<>();

        return root.getActiveChain();
    }

    /**
     * Generates a new unique id depending on the script name.
     *
     * @param scriptName a seed value for the uid, e.g., the script name
     * @return unique script id
     */
    static public String generateScriptUid(String scriptName) {
        CharSequence dateString = android.text.format.DateFormat.format("yyyy-MM-dd_HH-mm-ss", new java.util.Date());

        String newUid = "";
        newUid += dateString;
        if (!scriptName.equals("")) {
            newUid += "_";
            newUid += scriptName;
        }
        return newUid;
    }

    /**
     * This method does not do much at the moment. However, in case this changes in the future the user should call it
     * anyway.
     *
     * @return false if the root component was already in done state
     */
    public boolean start() {
        if (root == null)
            return false;
        if (root.getState() > ScriptTreeNode.SCRIPT_STATE_ONGOING)
            return false;
        root.setState(ScriptTreeNode.SCRIPT_STATE_ONGOING);
        return true;
    }

    /**
     * Is called whenever a child component changes it state.
     * @param component the component that changed its state
     * @param state new state
     */
    void onComponentStateChanged(ScriptTreeNode component, int state) {
        if (listener != null)
            listener.onComponentStateChanged(component, state);
    }

    /**
     * Save the current script state into a bundle.
     * @param bundle archive the script should be stored in
     * @return false if an error occurred
     */
    boolean saveScriptState(Bundle bundle) {
        if (root == null)
            return false;

        bundle.putString("scriptId", ScriptTreeNode.getTreeHash(root));

        if (!saveScriptComponentState(root, 0, bundle))
            return false;

        int componentId = 0;
        java.util.Iterator<ScriptTreeNode> iterator = root.iterator();
        while (true) {
            if (!iterator.hasNext())
                break;
            ScriptTreeNode component = iterator.next();
            componentId++;

            if (!saveScriptComponentState(component, componentId, bundle))
                return false;
        }
        return true;
    }

    /**
     * Restores a previously saved state of a script.
     *
     * @param bundle saved state of the script
     * @return true if the state has been restored otherwise the error message is stored in {@link #lastError}
     * @see {@link #saveScriptState(Bundle)}
     */
    boolean loadScriptState(Bundle bundle) {
        if (root == null)
            return false;

        String scriptId = ScriptTreeNode.getTreeHash(root);
        Object id = bundle.get("scriptId");
        if (id == null || !id.equals(scriptId)) {
            lastError = "Script has been updated and is now incompatible to the saved state.";
            return false;
        }

        if (!loadScriptComponentState(root, 0, bundle))
            return false;

        int componentId = 0;
        java.util.Iterator<ScriptTreeNode> iterator = root.iterator();
        while (true) {
            if (!iterator.hasNext())
                break;
            ScriptTreeNode component = iterator.next();
            componentId++;

            if (!loadScriptComponentState(component, componentId, bundle))
                return false;
        }

        return true;
    }

    private boolean saveScriptComponentState(ScriptTreeNode component, int componentId, Bundle bundle) {
        Bundle componentBundle = new Bundle();
        component.toBundle(componentBundle);

        String bundleKey = Integer.toString(componentId);
        bundle.putBundle(bundleKey, componentBundle);
        return true;
    }

    private boolean loadScriptComponentState(ScriptTreeNode component, int componentId, Bundle bundle) {
        String bundleKey = Integer.toString(componentId);
        if (!bundle.containsKey(bundleKey)) {
            lastError = "Script component state can't be restored.";
            return false;
        }
        return component.fromBundle(bundle.getBundle(bundleKey));
    }
}
