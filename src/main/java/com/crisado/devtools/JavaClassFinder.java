package com.crisado.devtools;

import java.io.File;
import java.util.List;

public class JavaClassFinder {

    private final List<File> sources;

    public JavaClassFinder(List<File> sources) {
        this.sources = sources;
    }

    public File findJavaClass(String name) {
        for (File src : sources) {
            File dir = findJavaClassRecursively(src, name);
            if (dir != null) return dir;
        }
        return null;
    }

    private File findJavaClassRecursively(File dir, String name) {
        File[] subDirs = dir.listFiles();
        if (subDirs == null) {
            return null;
        }
        for (File subdir : subDirs) {
            if (subdir.isFile() && subdir.getName().startsWith(name)) {
                return subdir;
            }
            if (subdir.isDirectory()) {
                File file = findJavaClassRecursively(subdir, name);
                if (file != null) {
                    return file;
                }
            }
        }
        return null;
    }
}
