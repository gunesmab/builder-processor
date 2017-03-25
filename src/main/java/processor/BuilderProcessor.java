package processor;


import annotation.Build;
import annotation.Constructor;
import annotation.DefaultValue;
import com.google.auto.service.AutoService;
import com.google.common.base.CaseFormat;
import com.squareup.javapoet.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

/**
 * Created by mabocoglug on 19/03/17.
 */
@AutoService(Processor.class)
public class BuilderProcessor extends AbstractProcessor {

    private static final String NON_NULL_ANDROID_CLASS_NAME = "android.support.annotation.NonNull";
    private static final String NOT_NULL_SUN_CLASS_NAME = "com.sun.istack.internal.NotNull";
    private static final AnnotationSpec ANNOTATION_SPEC_NON_NULL = AnnotationSpec.builder(ClassName.bestGuess(NON_NULL_ANDROID_CLASS_NAME)).build();
    private static final AnnotationSpec ANNOTATION_SPEC_NOT_NULL = AnnotationSpec.builder(ClassName.bestGuess(NOT_NULL_SUN_CLASS_NAME)).build();
    private static final String CLASS_NOT_FOUND_EXCEPTION = "class not found";
    private static final String FORMAT_BUILDER_CLASS_NAME = "%1$sBuilder";
    private static final String FORMAT_SET_PARAMETER__JAVA_POET = "this.%1$s = %2$s";
    private static final String FORMAT_ASSINGMENT = "%1$s = %2$s";
    private static final String FORMAT_SETTER_METHOD = "set%1$s";
    private static final String FORMAT_BUILDER_RETURN = "return this";
    private static final String BUILD_METHOD_NAME = "build";
    private static final String BUILDER_RETURN_JAVA_FORMAT = "return new %1$s(";
    private static final String CLOSE_BUILDER_RETURN_STATEMENT = ")";
    private static final String COMMA = ",";
    private static final String TAG = BuilderProcessor.class.getSimpleName();

    private Messager messager;
    private Types typesUtil;
    private Elements elementsUtil;
    private Filer filer;
    private Trees treeUtil;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        typesUtil = processingEnv.getTypeUtils();
        elementsUtil = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        Util.init(processingEnv);
        treeUtil = Trees.instance(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        final HashSet<String> strings = new HashSet<>();
        strings.add(Build.class.getCanonicalName());
        return strings;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        final Iterator<? extends Element> iterator = roundEnv.getElementsAnnotatedWith(Constructor.class).iterator();
        if (!iterator.hasNext()) {
            return false;
        }
        final ExecutableElement constructorElement = (ExecutableElement) iterator.next();
        final TypeElement typeElementClassToBuild = (TypeElement) roundEnv.getElementsAnnotatedWith(Build.class).iterator().next();
        final String builderClassName = getBuilderClassName(constructorElement);
        final String builderClassPackageName = elementsUtil.getPackageOf(constructorElement).asType().toString();
        final ClassName builderClassJavaPoetName = ClassName.get(builderClassPackageName, builderClassName);
        if (iterator.hasNext()) {
            messager.printMessage(ERROR, "Constructor could be one");
        }
        final HashMap<String, Parameter> parameterHashMap = new HashMap<String, Parameter>();

        final List<? extends VariableElement> variableElements = constructorElement.getParameters();
        for (int i = 0; i < variableElements.size(); i++) {
            final VariableElement variableElement = variableElements.get(i);
            final ParameterSpec parameterSpec = getParameterSpec(variableElement);
            parameterHashMap.put(
                    parameterSpec.name, new Parameter(parameterSpec, variableElement.getSimpleName().toString())
            );
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DefaultValue.class)) {
            final VariableElement defValueElement = (VariableElement) element;
            final DefaultValue defaultValueAnnotation = defValueElement.getAnnotation(DefaultValue.class);
            final String parameterName = defaultValueAnnotation.value();
            if (parameterHashMap.containsKey(parameterName)) {
                final Object consValue = defValueElement.getConstantValue();
                final String fieldName = defValueElement.getSimpleName().toString();
                final CodeAnalyzerTreeScanner codeScanner = new CodeAnalyzerTreeScanner(messager);
                final TreePath tp = this.treeUtil.getPath(defValueElement.getEnclosingElement());
                codeScanner.setFieldName(fieldName);
                codeScanner.scan(tp, treeUtil);
                final String fieldInitializer = codeScanner.getFieldInitializer();
                if (fieldInitializer == null) {
                    messager.printMessage(ERROR, parameterName + " could not be null");
                    throw new NullPointerException(parameterName + " could not be null");
                }
                parameterHashMap.get(parameterName).setDefValueInitializer(fieldInitializer);
            }
        }
        final Collection<Parameter> parameters = parameterHashMap.values();
        final ArrayList<Parameter> builderConsParameters = findConsParameters(parameters);
        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(builderClassName);
        final MethodSpec cons = createConstructor(classBuilder, builderConsParameters, parameters);
        classBuilder.addModifiers(Modifier.PUBLIC);
        classBuilder.addMethod(cons);
        final ArrayList<Parameter> parametersForSetterMethods = new ArrayList<Parameter>(parameters);
        parametersForSetterMethods.removeAll(builderConsParameters);
        addFieldsAndSetterMethods(classBuilder, builderClassJavaPoetName, parametersForSetterMethods);
        addBuildMethod(classBuilder, ClassName.get(typeElementClassToBuild), parameters);
        classBuilder.addModifiers(Modifier.FINAL, Modifier.PUBLIC);
        final TypeSpec builderClass = classBuilder.build();
        final JavaFile javaFile = JavaFile.builder(builderClassPackageName, builderClass).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            messager.printMessage(ERROR, "NO NO NOOO " + e.toString());
            e.printStackTrace();
        }
        return true;
    }

    private void addBuildMethod(final TypeSpec.Builder classBuilder,
                                final ClassName classNameForBuildObject,
                                final Collection<Parameter> parameters) {
        final MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(BUILD_METHOD_NAME);
        final StringBuilder stringBuilder = new StringBuilder(
                String.format(BUILDER_RETURN_JAVA_FORMAT, classNameForBuildObject.reflectionName())
        );
        boolean first = true;
        for (Parameter parameter : parameters) {
            if (!first) {
                stringBuilder.append(COMMA);
            }
            stringBuilder.append(parameter.getParameterSpec().name);
            first = false;
        }
        stringBuilder.append(CLOSE_BUILDER_RETURN_STATEMENT);
        methodSpec.addStatement(stringBuilder.toString());
        methodSpec.returns(classNameForBuildObject);
        methodSpec.addModifiers(Modifier.PUBLIC);
        classBuilder.addMethod(methodSpec.build());
    }

    private void addFieldsAndSetterMethods(final TypeSpec.Builder classBuilder,
                                           final ClassName builderClassName,
                                           final Collection<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            final FieldSpec fieldSpec = createField(parameter, Modifier.PRIVATE);
            classBuilder.addField(fieldSpec);
            final MethodSpec methodSpec = createSetterMethod(parameter, builderClassName);
            classBuilder.addMethod(methodSpec);
        }
    }

    private FieldSpec createField(final Parameter parameter, final Modifier... modifiers) {
        return FieldSpec.builder(
                parameter.getParameterSpec().type, parameter.getParameterSpec().name, modifiers
        ).build();
    }

    private MethodSpec createSetterMethod(final Parameter parameter, final ClassName builderClassName) {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(getSetterMethodName(parameter));
        builder.addModifiers(Modifier.PUBLIC);
        builder.returns(builderClassName);
        builder.addParameter(parameter.getParameterSpec());
        builder.addStatement(
                String.format(FORMAT_SET_PARAMETER__JAVA_POET, parameter.getParameterSpec().name, parameter.getParameterSpec().name)
        );
        builder.addStatement(FORMAT_BUILDER_RETURN);
        return builder.build();
    }

    private String getSetterMethodName(Parameter parameter) {
        return String.format(FORMAT_SETTER_METHOD, CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, parameter.getSimpleClassName()));
    }

    private ParameterSpec getParameterSpec(VariableElement variableElement) {
        final TypeMirror typeMirror = variableElement.asType();
        final TypeName typeName;
        if (typeMirror.getKind().isPrimitive()) {
            typeName = TypeName.get(typeMirror).box();
        } else {
            typeName = TypeName.get(typeMirror);
        }
        return ParameterSpec.builder(
                typeName,
                variableElement.getSimpleName().toString(),
                new Modifier[0])
                .addModifiers(variableElement.getModifiers()
                ).build();
    }

    private MethodSpec createConstructor(final TypeSpec.Builder typeSpecBuilder,
                                         final ArrayList<Parameter> builderConsParameters,
                                         final Collection<Parameter> allParameters) {
        final ArrayList<Parameter> paramsWithoutBuilderParams = new ArrayList<Parameter>(allParameters);
        paramsWithoutBuilderParams.removeAll(builderConsParameters);
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        final int size = builderConsParameters.size();
        for (int i = 0; i < size; i++) {
            final Parameter parameter = builderConsParameters.get(i);
            final FieldSpec fieldSpec = createField(parameter, Modifier.PRIVATE, Modifier.FINAL);
            typeSpecBuilder.addField(fieldSpec);
            builder.addParameter(parameter.getParameterSpec());
            builder.addStatement(
                    String.format(
                            FORMAT_SET_PARAMETER__JAVA_POET,
                            parameter.getParameterSpec().name,
                            parameter.getParameterSpec().name
                    )
            );
        }
        final int sizeWithoutParams = paramsWithoutBuilderParams.size();
        for (int i = 0; i < sizeWithoutParams; i++) {
            final Parameter parameter = paramsWithoutBuilderParams.get(i);
            if (parameter.getDefValueInitializer() != null) {
                builder.addStatement(
                        String.format(FORMAT_ASSINGMENT, parameter.getParameterSpec().name, parameter.getDefValueInitializer())
                );
            }
        }
        return builder.build();
    }

    private ArrayList<Parameter> findConsParameters(final Collection<Parameter> parameters) {
        final ArrayList<Parameter> params = new ArrayList<Parameter>(parameters);
        final int parametersSize = params.size();
        final ArrayList<Parameter> consParameters = new ArrayList<Parameter>(parametersSize);
        final Iterator<Parameter> parameterIterator = params.iterator();
        while (parameterIterator.hasNext()) {
            final Parameter parameter = parameterIterator.next();
            final Iterator<AnnotationSpec> annotationSpecIterator = parameter.getParameterSpec().annotations.iterator();
            boolean found = false;
            while (!found && annotationSpecIterator.hasNext()) {
                final AnnotationSpec annotationSpec = annotationSpecIterator.next();
                if (annotationSpec.equals(ANNOTATION_SPEC_NON_NULL)
                        || annotationSpec.equals(ANNOTATION_SPEC_NOT_NULL)) {
                    consParameters.add(parameter);
                    found = true;
                }
            }
            parameterIterator.remove();
        }
        return consParameters;
    }

    private String getBuilderClassName(ExecutableElement constructorElement) {
        return String.format(FORMAT_BUILDER_CLASS_NAME, constructorElement.getEnclosingElement().asType().toString());
    }


    private void printClassNotFoundException(final String className) {
        printException(CLASS_NOT_FOUND_EXCEPTION, className);
    }

    private void printException(final String exceptionName, final String className) {
        printException(NOTE, exceptionName, className);
    }

    private void printException(Diagnostic.Kind kind,
                                final String exceptionName,
                                final String className) {
        messager.printMessage(kind, exceptionName + className);
    }
}
