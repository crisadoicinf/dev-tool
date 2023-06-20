package com.crisado.devtools.goal;

import com.crisado.devtools.JavaClassFinder;
import com.crisado.devtools.Junit5Creator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Mojo(name = "junit5")
public class Junit5TestGenerator extends AbstractMojo {
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(property = "class")
    String clazz;

    @Override
    public void execute() {
        Log log = getLog();
        List<File> sources = project.getCompileSourceRoots()
                .stream()
                .map(File::new)
                .collect(toList());
        JavaClassFinder finder = new JavaClassFinder(sources);
        File javaClass = finder.findJavaClass(clazz);
        if (javaClass == null) {
            log.error(String.format("Java Class '%s' Not Found", clazz));
        } else {
            try {
                Junit5Creator junit5Creator = new Junit5Creator(javaClass);
                junit5Creator.generateTests(System.out);
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

}