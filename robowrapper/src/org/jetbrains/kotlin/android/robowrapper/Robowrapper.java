package org.jetbrains.kotlin.android.robowrapper;

public class Robowrapper {

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        System.setProperty("robolectric.offline", "true");

        org.junit.runner.JUnitCore.main("org.jetbrains.kotlin.android.robowrapper.ParserTest");
    }

}
