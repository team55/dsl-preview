package org.jetbrains.kotlin.android.robowrapper

import android.view.View
import org.jetbrains.kotlin.android.dslpreview.Pack
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zeromq.ZMQ

import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.Charset
import kotlin.properties.Delegates

RunWith(javaClass<RobolectricTestRunner>())
Config(emulateSdk = 18)
public class ParserTest {

    private var myActivityClass: String by Delegates.notNull()
    private val myClassLoaderManager = ClassLoaderManager()
    private val myBaseViewResolver = BaseViewResolver()

    private val output = PrintStream(System.out, true, "UTF-8")

    Before
    public fun init() {
        myActivityClass = System.getProperty("robo.activityClass", "")
    }

    Test
    public fun testParser() {
        val first = generate(myActivityClass)
        if (!first.alive) {
            log(first)
            return
        }

        val context = ZMQ.context(1)
        val responder = context.socket(ZMQ.REP)
        val port = responder.bindToRandomPort("tcp://*", 2048, 100100)
        if (port < 0) {
            log("Failed to create a socket. Exiting")
            return
        }
        first.port = port
        log(first)

        while (!Thread.currentThread().isInterrupted()) {
            val request = responder.recv(0)
            val data = String(request, "UTF-8").trim()
            log("Got activity class name: " + data)
            val pack = generate(data)
            pack.port = port
            responder.send(log(pack).getBytes(Charset.forName("UTF-8")), 0)
            if (!pack.alive) {
                log("Terminating.")
                break
            }
        }
        responder.close()
        context.term()
    }

    private fun generate(className: String): Pack {
        val packageName = Robolectric.getShadowApplication().getPackageName() + "."

        try {
            val cl = Thread.currentThread().getContextClassLoader()
            val clazz = cl.loadClass(className)

            [suppress("UNCHECKED_CAST")]
            val baseView = myBaseViewResolver.getBaseView(clazz as Class<Any>)
            val viewNode = parseView(baseView)
            val xml = toXml(viewNode)
            myClassLoaderManager.replaceClassLoader(packageName)
            return Pack(xml, 0, "", true, -1)
        } catch (e: UnsupportedClassException) {
            myClassLoaderManager.replaceClassLoader(packageName)
            return Pack("", 1, "Unsupported class.", true, -1)
        } catch (e: Throwable) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            log(sw.toString())
            return Pack("", -1, sw.toString(), false, -1)
        }

    }

    private fun log(pack: Pack): String {
        return log(START_MARKER + pack.toJson() + END_MARKER)
    }

    private fun log(message: String): String {
        output.println(message)
        return message
    }

    class object {

        private val START_MARKER = "" + (5.toChar()) + (7.toChar()) + (5.toChar())
        private val END_MARKER = "\n" + START_MARKER + START_MARKER
    }


}
