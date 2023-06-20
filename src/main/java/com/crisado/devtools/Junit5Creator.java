package com.crisado.devtools;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

public class Junit5Creator {

    private final CtClass<?> ctClass;
    private final Map<String, CtMethod<?>> mapMethods;
    private Map<String, CtFieldReference<?>> mapMocks;
    private String classReferenceName;
    private OutputStream out;

    public Junit5Creator(File clazz) throws IOException {
        ctClass = Launcher.parseClass(new String(Files.readAllBytes(clazz.toPath())));
        setClassNameAsProperty();
        findMocks();
        mapMethods = ctClass.getMethods().stream()
                .collect(toMap(CtMethod::getSimpleName, identity(), (first, second) -> first));
    }

    private void findMocks() {
        mapMocks = ctClass.getAllFields()
                .stream()
                .filter(field -> !field.isStatic())
                .collect(toMap(CtFieldReference::getSimpleName, identity(), (first, second) -> first));
    }

    public void generateTests(OutputStream out) throws IOException {
        this.out = out;
        writePackage();
        writeImports();
        writeOpenClass();
        writeDependencyFields();
        writeClassField();
        writeTestMethods();
        writeCloseClass();
    }


    private void setClassNameAsProperty() {
        char[] className = ctClass.getSimpleName().toCharArray();
        className[0] += 32;
        classReferenceName = new String(className);
    }


    private void writeMockListeners(CtMethod<?> method) throws IOException {
        List<? extends CtInvocation<?>> invocations = getMockInvocations(method, new HashSet<>());
        for (CtInvocation<?> invocation : invocations) {
            CtElement parent = invocation.getParent();
            if (parent instanceof CtReturn || parent instanceof CtInvocation || parent instanceof CtVariable) {
                out.write(String.format("\t\twhen(%s.%s(%s)).thenReturn(null);",
                        invocation.getTarget().toString(),
                        invocation.getExecutable().getSimpleName(),
                        invocation.getArguments()
                                .stream()
                                .map(a -> "any()")
                                .collect(joining(", "))
                ).getBytes());
                out.write(System.lineSeparator().getBytes());
            }
        }
        out.write(System.lineSeparator().getBytes());
    }

    private List<? extends CtInvocation<?>> getMockInvocations(CtMethod<?> method, Set<CtInvocation<?>> skip) {
        ArrayList<CtInvocation<?>> invocations = new ArrayList<>();
        method.getElements(e -> e instanceof CtInvocation)
                .forEach(e -> {
                    CtInvocation<?> invocation = (CtInvocation<?>) e;
                    if (!skip.contains(invocation)) {
                        String target = invocation.getTarget().toString();
                        String call = invocation.getExecutable().getSimpleName();
                        if ("".equals(target)) {
                            CtMethod<?> subMethod = mapMethods.get(call);
                            if (subMethod != null) {
                                skip.add(invocation);
                                invocations.addAll(getMockInvocations(subMethod, skip));
                            }
                        } else {
                            CtFieldReference<?> mock = mapMocks.get(target);
                            if (mock != null) {
                                skip.add(invocation);
                                invocations.add(invocation);
                            }
                        }
                    }
                });
        return invocations;
    }


    private void writeDependencyFields() throws IOException {
        for (Map.Entry<String, CtFieldReference<?>> entry : mapMocks.entrySet()) {
            CtFieldReference<?> field = entry.getValue();
            out.write("\t@Mock".getBytes());
            out.write(System.lineSeparator().getBytes());
            out.write(String.format("\tprivate %s %s;", field.getType().getSimpleName(), field.getSimpleName()).getBytes());
            out.write(System.lineSeparator().getBytes());
        }
    }

    private void writeClassField() throws IOException {
        out.write("\t@InjectMocks".getBytes());
        out.write(System.lineSeparator().getBytes());
        out.write(String.format("\tprivate %s %s;", ctClass.getSimpleName(), classReferenceName).getBytes());
        out.write(System.lineSeparator().getBytes());
        out.write(System.lineSeparator().getBytes());
    }


    private void writePackage() throws IOException {
        out.write(String.format("package %s;", ctClass.getPackage().toString()).getBytes());
        out.write(System.lineSeparator().getBytes());
        out.write(System.lineSeparator().getBytes());
    }

    private void writeImports() throws IOException {
        Set<String> imports = new HashSet<>(Arrays.asList(
                "import org.junit.jupiter.api.Test",
                "import org.junit.jupiter.api.extension.ExtendWith",
                "import org.mockito.Mock",
                "import org.mockito.InjectMocks",
                "import org.mockito.junit.jupiter.MockitoExtension",
                "import static org.mockito.Mockito.when",
                "import static org.mockito.ArgumentMatchers.any",
                "import static org.assertj.core.api.Assertions.assertThat",
                "import static org.assertj.core.api.Assertions.assertThatThrownBy"
        ));
        for (Map.Entry<String, CtFieldReference<?>> entry : mapMocks.entrySet()) {
            CtTypeReference<?> type = entry.getValue().getType();
            imports.add(String.format("import %s.%s", type.getPackage(), type.getSimpleName()));
        }
        for (CtMethod<?> method : ctClass.getMethods()) {
            for (CtTypeReference<? extends Throwable> thrownType : method.getThrownTypes()) {
                imports.add(String.format("import %s.%s", thrownType.getPackage(), thrownType.getSimpleName()));
            }
            for (CtParameter<?> parameter : method.getParameters()) {
                imports.add(String.format("import %s.%s", parameter.getType().getPackage(), parameter.getType().getSimpleName()));
            }
        }
        imports.add(String.format("import %s.%s", ctClass.getPackage(), ctClass.getSimpleName()));

        for (String imp : imports.stream().sorted().collect(toList())) {
            out.write(String.format("%s;", imp).getBytes());
            out.write(System.lineSeparator().getBytes());
        }
        out.write(System.lineSeparator().getBytes());
    }

    private void writeOpenClass() throws IOException {
        out.write("@ExtendWith(MockitoExtension.class)".getBytes());
        out.write(System.lineSeparator().getBytes());
        out.write(String.format("class %sTest{", ctClass.getSimpleName()).getBytes());
        out.write(System.lineSeparator().getBytes());
        out.write(System.lineSeparator().getBytes());
    }

    private void writeTestMethods() throws IOException {
        for (CtMethod<?> method : ctClass.getMethods()) {
            if (method.isPublic()) {
                out.write("\t@Test".getBytes());
                out.write(System.lineSeparator().getBytes());
                out.write(String.format("\tvoid %s()", method.getSimpleName()).getBytes());
                String throwsSignature = method.getThrownTypes().stream().map(CtTypeReference::getSimpleName).collect(joining(","));
                if (!"".equals(throwsSignature)) {
                    out.write(String.format(" throws %s", throwsSignature).getBytes());
                }
                out.write(String.format(" {", method.getSimpleName()).getBytes());
                out.write(System.lineSeparator().getBytes());
                writeMethodFields(method);
                writeMockListeners(method);
                boolean isVoid = "void".equals(method.getType().getSimpleName());
                if (isVoid) {
                    out.write(String.format("\t\t%s.%s(%s);", classReferenceName, method.getSimpleName(), getArgumentsDefinition(method)).getBytes());
                    out.write(System.lineSeparator().getBytes());
                } else {
                    out.write(String.format("\t\tassertThat(%s.%s(%s))", classReferenceName, method.getSimpleName(), getArgumentsDefinition(method)).getBytes());
                    out.write(System.lineSeparator().getBytes());
                    out.write("\t\t\t.isNotNull();".getBytes());
                    out.write(System.lineSeparator().getBytes());
                }
                out.write("\t}".getBytes());
                out.write(System.lineSeparator().getBytes());
                out.write(System.lineSeparator().getBytes());

                //create methods for exception assertion
                for (CtTypeReference<? extends Throwable> thrownType : method.getThrownTypes()) {
                    out.write("\t@Test".getBytes());
                    out.write(System.lineSeparator().getBytes());
                    out.write(String.format("\tvoid %sThrows%sIf___(){", method.getSimpleName(), thrownType.getSimpleName()).getBytes());
                    out.write(System.lineSeparator().getBytes());
                    writeMethodFields(method);
                    writeMockListeners(method);
                    out.write(String.format("\t\tassertThatThrownBy(() -> %s.%s(%s))", classReferenceName, method.getSimpleName(), getArgumentsDefinition(method)).getBytes());
                    out.write(System.lineSeparator().getBytes());
                    out.write(String.format("\t\t\t.isExactlyInstanceOf(%s.class)", thrownType.getSimpleName()).getBytes());
                    out.write(System.lineSeparator().getBytes());
                    out.write("\t\t\t.hasMessage(\"\");".getBytes());
                    out.write(System.lineSeparator().getBytes());
                    out.write("\t}".getBytes());
                    out.write(System.lineSeparator().getBytes());
                    out.write(System.lineSeparator().getBytes());
                }
            }
        }
    }

    private String getArgumentsDefinition(CtMethod<?> method) {
        return method.getParameters().stream().map(CtParameter::getSimpleName).collect(joining(", "));
    }

    private void writeMethodFields(CtMethod<?> method) throws IOException {
        for (CtParameter<?> parameter : method.getParameters()) {
            String type = parameter.getType().getSimpleName();
            String name = parameter.getSimpleName();
            if ("String".equals(type)) {
                out.write(String.format("\t\t%s %s = \"\";", type, name).getBytes());
            } else if ("Integer".equals(type) || "ind".equals(type)) {
                out.write(String.format("\t\t%s %s = 1;", type, name).getBytes());
            } else if ("Double".equals(type) || "double".equals(type)) {
                out.write(String.format("\t\t%s %s = 1d;", type, name).getBytes());
            } else if ("Long".equals(type) || "long".equals(type)) {
                out.write(String.format("\t\t%s %s = 1l;", type, name).getBytes());
            } else if ("Boolean".equals(type) || "boolean".equals(type)) {
                out.write(String.format("\t\t%s %s = true;", type, name).getBytes());
            } else {
                out.write(String.format("\t\t%s %s = new %s();", type, name, type).getBytes());
            }
            out.write(System.lineSeparator().getBytes());
        }
        out.write(System.lineSeparator().getBytes());
    }

    private void writeCloseClass() throws IOException {
        out.write("}".getBytes());
    }


}
