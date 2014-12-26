package org.jetbrains.kotlin.android.robowrapper;

import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@Config(manifest= Config.NONE, emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ClassLoaderManagerTest {

    @Test
    public void testClassLoaderManager() throws Exception {
        Activity a = Robolectric.setupActivity(Activity.class);
        assertNotNull(a);

        ClassLoaderManager manager = new ClassLoaderManager();
        String className = "org.jetbrains.kotlin.android.robowrapper.test.SomeClass";

        ClassLoader cl1 = getContextClassLoader();
        Object o1 = cl1.loadClass(className);

        manager.replaceClassLoader("org.jetbrains.kotlin.android.robowrapper.test.");

        ClassLoader cl2 = getContextClassLoader();
        Object o2 = cl2.loadClass(className);

        manager.replaceClassLoader("org.jetbrains.kotlin.android.robowrapper.test.somethingNotExist");
        ClassLoader cl3 = getContextClassLoader();
        Object o3 = cl3.loadClass(className);

        assertNotEquals(o1, o2);
        assertEquals(o2, o3);
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

}
