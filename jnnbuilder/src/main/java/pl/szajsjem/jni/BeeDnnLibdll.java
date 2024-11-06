package pl.szajsjem.jni;


//jni link
public class BeeDnnLibdll {//studs for now

    static {
        System.loadLibrary("BeeDnnLib");
    }

    public native static String[] getLayerType();

    public native static String[] getActivationFunctions();

    public native static String[] getInitializers();

    public native static String[] getOptimizers();

    public native static String[] getLossFunctions();

    public native static String[] getRegularizers();


}
