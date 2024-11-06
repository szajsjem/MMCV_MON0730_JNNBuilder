package pl.szajsjem.jni;

public class BeeDnnLib {//studs for now

    public static String[] getLayerType() {
        return new String[]{"pActivation"};
    }

    static String[] getActivationFunctions() {
        return new String[]{"ReLU"};
    }

    static String[] getInitializers() {
        return new String[]{"Uniform"};
    }

    static String[] getOptimizers() {
        return new String[]{"Adam"};
    }

    static String[] getLossFunctions() {
        return new String[]{"MSE"};
    }

    static String[] getRegularizers() {
        return new String[]{"L2"};
    }


}
