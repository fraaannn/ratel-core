/**
 * Copyright (C) 2018 Ryszard Wiśniewski <brut.alll@gmail.com>
 * Copyright (C) 2018 Connor Tumbleson <connor.tumbleson@gmail.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brut.androlib.src;

import org.antlr.runtime.RecognitionException;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.FileDataStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import brut.androlib.AndrolibException;
import brut.androlib.mod.SmaliMod;
import brut.directory.DirectoryException;
import brut.directory.ExtFile;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class SmaliBuilder {
    public static void build(ExtFile smaliDir, File dexFile, int apiLevel, boolean printLog) throws AndrolibException {
        new SmaliBuilder(smaliDir, dexFile, apiLevel).build(printLog);
    }

    public static void build(ExtFile smaliDir, File dexFile) throws AndrolibException {
        new SmaliBuilder(smaliDir, dexFile, 0).build(true);
    }

    private SmaliBuilder(ExtFile smaliDir, File dexFile, int apiLevel) {
        mSmaliDir = smaliDir;
        mDexFile = dexFile;
        mApiLevel = apiLevel;
    }

    private void build(boolean printLog) throws AndrolibException {
        try {
            DexBuilder dexBuilder;
            if (mApiLevel > 0) {
                dexBuilder = new DexBuilder(Opcodes.forApi(mApiLevel));
            } else {
                dexBuilder = new DexBuilder(Opcodes.getDefault());
            }

            for (String fileName : mSmaliDir.getDirectory().getFiles(true)) {
                buildFile(fileName, dexBuilder, printLog);
            }
            dexBuilder.writeTo(new FileDataStore(new File(mDexFile.getAbsolutePath())));
        } catch (IOException | DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    private void buildFile(String fileName, DexBuilder dexBuilder, boolean printLog)
            throws AndrolibException, IOException {
        File inFile = new File(mSmaliDir, fileName);
        if (printLog) {
            System.out.println("build smali file: " + inFile.getAbsolutePath());
        }
        InputStream inStream = new FileInputStream(inFile);

        if (fileName.endsWith(".smali")) {
            try {
                if (!SmaliMod.assembleSmaliFile(inFile, dexBuilder, false, false)) {
                    throw new AndrolibException("Could not smali file: " + fileName);
                }
            } catch (IOException | RecognitionException ex) {
                throw new AndrolibException(ex);
            }
        } else {
            LOGGER.warning("Unknown file type, ignoring: " + inFile);
        }
        inStream.close();
    }

    private final ExtFile mSmaliDir;
    private final File mDexFile;
    private int mApiLevel = 0;

    private final static Logger LOGGER = Logger.getLogger(SmaliBuilder.class.getName());
}
