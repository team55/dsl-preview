package org.jetbrains.kotlin.android.robowrapper

import android.app.Activity
import android.app.Fragment
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.robolectric.Robolectric
import org.robolectric.util.FragmentTestUtil
import org.jetbrains.kotlin.android.robowrapper.UnsupportedClassException

public class BaseViewResolver {

    public fun getBaseView(clazz: Class<Any>): View {
        return when {
            clazz.isActivity() -> {
                [suppress("CAST_NEVER_SUCCEEDS")]
                val activity = Robolectric.buildActivity<Activity>(clazz as Class<Activity>).create().get()
                val contentView = activity.findViewById(android.R.id.content) as ViewGroup
                if (contentView.getChildCount() == 0) FrameLayout(activity) else contentView.getChildAt(0)
            }
            clazz.isFragment() -> {
                val fragment = clazz.newInstance() as Fragment
                FragmentTestUtil.startFragment(fragment)
                fragment.getView()
            }
            clazz.isSupportFragment() -> {
                val fragment = clazz.newInstance() as android.support.v4.app.Fragment
                FragmentTestUtil.startFragment(fragment)
                var baseView = fragment.getView()
                if (baseView is ViewGroup && (baseView as ViewGroup).getChildCount() > 0) {
                    baseView = (baseView as ViewGroup).getChildAt(0)
                }
                baseView
            }
            else -> throw UnsupportedClassException()
        }
    }

    private fun Class<*>.isActivity() = javaClass<Activity>().isAssignableFrom(this)
    private fun Class<*>.isFragment() = javaClass<Fragment>().isAssignableFrom(this)
    private fun Class<*>.isSupportFragment() = javaClass<android.support.v4.app.Fragment>().isAssignableFrom(this)

}
