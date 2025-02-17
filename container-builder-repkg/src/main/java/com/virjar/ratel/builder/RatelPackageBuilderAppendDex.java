package com.virjar.ratel.builder;


import com.virjar.ratel.builder.manifesthandler.AXmlEditorCmdHandler;
import com.virjar.ratel.builder.manifesthandler.EnableDebug;
import com.virjar.ratel.builder.manifesthandler.ReplaceApplication;
import com.virjar.ratel.builder.manifesthandler.RequestLegacyExternalStorage;
import com.virjar.ratel.buildsrc.Constants;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;
import org.jf.pxb.android.axml.AxmlReader;
import org.jf.pxb.android.axml.AxmlVisitor;
import org.jf.pxb.android.axml.AxmlWriter;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Matcher;

public class RatelPackageBuilderAppendDex {
    public static void handleTask(File workDir, Param param, BuildParamMeta buildParamMeta,
                                  CommandLine cmd, ZipOutputStream zos
    ) throws IOException {

        ZipFile originAPKZip = new ZipFile(param.originApk);
        Enumeration<ZipEntry> entries = originAPKZip.getEntries();
        while (entries.hasMoreElements()) {
            ZipEntry originEntry = entries.nextElement();
            String entryName = originEntry.getName();
            //remove sign data
            if (entryName.startsWith("META-INF/")) {
                continue;
            }
            if (Util.isRatelUnSupportArch(entryName)) {
                //过滤掉不支持的架构
                continue;
            }
            //i will edit androidManifest.xml ,so skip it now
            if (entryName.equals(Constants.manifestFileName) &&
                    !Constants.RATEL_MANAGER_PACKAGE.equals(buildParamMeta.apkMeta.getPackageName())) {
                zos.putNextEntry(new ZipEntry(originEntry));
                System.out.println("edit androidManifest.xml entry");
                zos.write(editManifestWithAXmlEditor(IOUtils.toByteArray(originAPKZip.getInputStream(originEntry)), cmd, buildParamMeta));
                continue;
            }
            zos.putNextEntry(new ZipEntry(originEntry));
            zos.write(IOUtils.toByteArray(originAPKZip.getInputStream(originEntry)));
        }
        //close
        originAPKZip.close();

        Util.copyAssets(zos, new File(workDir, Constants.RATEL_ENGINE_JAR), Constants.RATEL_ENGINE_JAR);
        System.out.println("copy ratel engine dex resources");
        appendDex(zos, param.originApk, new File(workDir, Constants.ratelMultiDexBootstrapJarPath));
        System.out.println("apk build success!!");

        if (buildParamMeta.originApplicationClass != null) {
            buildParamMeta.ratelBuildProperties.setProperty(Constants.KEY_ORIGIN_APPLICATION_NAME, buildParamMeta.originApplicationClass);
        }
    }


    private static void appendDex(ZipOutputStream zos, File originApk, File engineAPk) throws IOException {

        // classes.dex classes2.dex classes3.dex 没有 classes1.dex
        ZipFile orignApkZipFile = new ZipFile(originApk);
        Enumeration<ZipEntry> originEntries = orignApkZipFile.getEntries();
        int maxIndex = 1;
        while (originEntries.hasMoreElements()) {
            Matcher matcher = Util.classesIndexPattern.matcher(originEntries.nextElement().getName());
            if (!matcher.matches()) {
                continue;
            }
            int nowIndex = NumberUtils.toInt(matcher.group(1));
            if (nowIndex > maxIndex) {
                maxIndex = nowIndex;
            }
        }
        maxIndex++;


        // copy dex
        ZipFile driverApkZipFile = new ZipFile(engineAPk);
        Enumeration<ZipEntry> entries = driverApkZipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipEntry originEntry = entries.nextElement();
            String entryName = originEntry.getName();
            if (entryName.contains("/")) {
                continue;
            }
            if (!entryName.endsWith(".dex")) {
                continue;
            }
            if (!entryName.startsWith("classes")) {
                continue;
            }

            zos.putNextEntry(new ReNameEntry(originEntry, "classes" + maxIndex + ".dex"));
            zos.write(IOUtils.toByteArray(driverApkZipFile.getInputStream(originEntry)));
            maxIndex++;

        }
        driverApkZipFile.close();
    }

    private static byte[] editManifestWithAXmlEditor(byte[] manifestFileData, CommandLine cmd, BuildParamMeta buildParamMeta) throws IOException {

        AxmlReader rd = new AxmlReader(manifestFileData);
        AxmlWriter wr = new AxmlWriter();

        AxmlVisitor axmlVisitor = wr;
        if (cmd.hasOption('d')) {
            axmlVisitor = new EnableDebug(axmlVisitor);
        }
        if (NumberUtils.toInt(buildParamMeta.apkMeta.getTargetSdkVersion()) >= 30) {
            // axmlVisitor = new AddQueriesForPackage(axmlVisitor, Constants.RATEL_MANAGER_PACKAGE);
            // android11 包可见性，导致插件没有办法通过PMS获取，所以先使用query_all_package
            if (!buildParamMeta.axmlEditorCommand.contains("add_permission_android.permission.QUERY_ALL_PACKAGES")) {
                buildParamMeta.axmlEditorCommand.add("add_permission_android.permission.QUERY_ALL_PACKAGES");
            }
        }

        axmlVisitor = AXmlEditorCmdHandler.handleCmd(axmlVisitor, buildParamMeta.axmlEditorCommand);

        axmlVisitor = new ReplaceApplication(axmlVisitor, Constants.containerMultiDexBootstrapClass);
        if (NumberUtils.toInt(buildParamMeta.apkMeta.getTargetSdkVersion()) >= 29) {
            // android 10,无法访问内存卡，临时放开
            axmlVisitor = new RequestLegacyExternalStorage(axmlVisitor);
        }


        rd.accept(axmlVisitor);
        return wr.toByteArray();
    }
}
