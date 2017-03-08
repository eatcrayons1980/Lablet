package nz.ac.auckland.lablet.utility;

import static nz.ac.auckland.lablet.utility.FileHelper.isLuaFile;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test cases
 */
public class FileHelperTest {

    @Test
    public void testIsLuaFile() throws Exception {
        assertTrue("test failed: short file name", isLuaFile("t.lua"));
        assertTrue("test failed: valid file name", isLuaFile("ValidFile.lua"));
        assertTrue("test failed: multiple '.lua' matches", isLuaFile("This.is.a.lua.file.lua"));
        assertFalse("test failed: '.lua' in non-lua file", isLuaFile("This.is.not.a.lua.file.tex"));
        assertFalse("test failed: null string", isLuaFile(null));
        assertFalse("test failed: '.lua' by itself", isLuaFile(".lua"));
        assertFalse("test failed: empty string", isLuaFile(""));
        assertFalse("test failed: bad file name", isLuaFile("test_file.tex"));
    }

}
