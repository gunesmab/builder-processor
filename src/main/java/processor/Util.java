package processor;


import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashSet;
import java.util.List;

import static javax.tools.Diagnostic.Kind.NOTE;

/**
 * Created by mabocoglug on 20/03/17.
 */
final class Util {


    private static Util instance;
    private final ProcessingEnvironment processingEnv;
    private Messager messager;
    private Types typesUtil;
    private Elements elementsUtil;
    private Filer filer;

    private Util(final ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        messager = processingEnv.getMessager();
        typesUtil = processingEnv.getTypeUtils();
        elementsUtil = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
    }

    static void init(final ProcessingEnvironment processingEnv) {
        instance = new Util(processingEnv);
    }

    static Util getInstance() {
        if (instance == null) {
            throw new NullPointerException("call init method before getInstance");
        }
        return instance;
    }


    HashSet<String> findAnnotations(final Element element) {
        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
        final int annotationMirrorSize;
        if (annotationMirrors == null || ((annotationMirrorSize = annotationMirrors.size()) == 0)) {
            messager.printMessage(NOTE, "annotation not found " + element.getSimpleName());
            return new HashSet<String>(0);
        }
        final HashSet<String> annotationsClassNames = new HashSet<String>(annotationMirrorSize);
        for (int i = 0; i < annotationMirrorSize; i++) {
            annotationsClassNames.add(annotationMirrors.get(i).getAnnotationType().toString());
        }
        return annotationsClassNames;
    }

    public TypeMirror getTypeMirror(VariableElement variableElement) {
        final TypeMirror variableTypeMirror = variableElement.asType();
        if (variableTypeMirror.getKind().isPrimitive()) {
            return typesUtil.boxedClass((PrimitiveType) variableTypeMirror).asType();
        }
        return variableTypeMirror;
//        final TypeKind kind = variableElement.asType().getKind();
//        final String className;
//        switch (kind) {
//
//            case BOOLEAN:
//                className = Boolean.class.getName();
//                break;
//            case BYTE:
//                className = Byte.class.getName();
//                break;
//            case SHORT:
//                className = Short.class.getName();
//                break;
//            case INT:
//                className = Integer.class.getName();
//                break;
//            case LONG:
//                className = Long.class.getName();
//                break;
//            case CHAR:
//                className = Character.class.getName();
//                break;
//            case FLOAT:
//                className = Float.class.getName();
//                break;
//            case DOUBLE:
//                className = Double.class.getName();
//                break;
//            case VOID:
//            case NONE:
//            case NULL:
//            case ARRAY:
//            case DECLARED:
//            case ERROR:
//            case TYPEVAR:
//            case WILDCARD:
//            case PACKAGE:
//            case EXECUTABLE:
//            case OTHER:
//            case UNION:
//            case INTERSECTION:
//            default:
//                className = variableElement.asType().toString();
//                break;
//        }
//        return className;
    }
}
