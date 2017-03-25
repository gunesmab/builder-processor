package processor;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.ElementVisitor;
import javax.tools.Diagnostic;

/**
 * Created by mabocoglug on 24/03/17.
 */
public class CodeAnalyzerTreeScanner extends TreePathScanner<String, Trees> {

    private static final String TAG = CodeAnalyzerTreeScanner.class.getSimpleName();
    private String fieldName;

    private String fieldInitializer;

    private Messager messager;

    public CodeAnalyzerTreeScanner(Messager messager) {
        this.messager = messager;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldInitializer() {
        return this.fieldInitializer;
    }

    @Override
    public String visitVariable(VariableTree variableTree, Trees trees) {
        if (variableTree.getName().toString().equals(this.fieldName)) {
            this.fieldInitializer = variableTree.getInitializer().toString();
            messager.printMessage(
                    Diagnostic.Kind.NOTE, TAG + "\n" +
                            "\n name : " + variableTree.getName().toString()
                            + "\n kind " + variableTree.getInitializer().toString());
        }
        return super.visitVariable(variableTree, trees);
    }
}
