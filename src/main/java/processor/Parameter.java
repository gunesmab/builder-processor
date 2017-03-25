package processor;


import com.squareup.javapoet.ParameterSpec;

/**
 * Created by mabocoglug on 19/03/17.
 */
public class Parameter {

    private String simpleClassName;
    private String defValueInitializer;
    private final ParameterSpec parameterSpec;

    public Parameter(final ParameterSpec parameterSpec, final String simpleClassName) {
        this.parameterSpec = parameterSpec;
        this.simpleClassName = simpleClassName;
    }

    public String getDefValueInitializer() {
        return defValueInitializer;
    }

    public ParameterSpec getParameterSpec() {
        return parameterSpec;
    }

    public String getSimpleClassName() {
        return simpleClassName;
    }

    public void setDefValueInitializer(String defValueInitializer) {
        this.defValueInitializer = defValueInitializer;
    }
}
