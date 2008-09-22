/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.commons.io.FilenameUtils
import org.apache.tools.ant.BuildLogger
import org.apache.tools.ant.BuildListener
import org.gradle.logging.AntLoggingAdapter

/**
 * @author Hans Dockter
 */
class GradleUtil {
    private static Logger logger = LoggerFactory.getLogger(GradleUtil)

    static def List fileList(List fileDescriptions) {
        fileDescriptions.collect { new File(it.toString()) }
    }

    static def configure(Closure configureClosure, def delegate, int resolveStrategy) {
        if (!configureClosure) { return delegate}
        configureClosure.resolveStrategy = resolveStrategy
        configureClosure.delegate = delegate
        configureClosure.call()
        delegate
    }

    static void deleteDir(File dir) {
        assert !dir.isFile()
        if (dir.isDirectory()) {new AntBuilder().delete(dir: dir)}
    }

    static File makeNewDir(File dir) {
        deleteDir(dir)
        dir.mkdirs()
        dir.deleteOnExit()
        dir.canonicalFile
    }

    static File[] getGradleClasspath() {
        File gradleHomeLib = new File(System.properties["gradle.home"] + "/lib")
        if (gradleHomeLib.isDirectory()) {
            return gradleHomeLib.listFiles()
        }
        []
    }

    static URL[] filesToUrl(File file) {
        List files = [file]
        files.collect { it.toURL() }
    }

    static URL[] getGradleLiBClasspath() {
        File gradleHomeLib = new File(System.properties["gradle.home"] + "/lib")
        if (gradleHomeLib.isDirectory()) {
            List list = gradleHomeLib.listFiles().findAll {File file -> !file.name.startsWith('groovy-all')}.collect {File file ->
                file.toURI().toURL()
            }
            return list
        }
        []
    }

    static List getGroovyFiles() {
        gradleClasspath(['groovy-all'])
    }

    static List getAntJunitJarFiles() {
        gradleClasspath(['ant', 'ant-launcher', 'ant-junit'])
    }

    static List getAntJarFiles() {
        gradleClasspath(['ant', 'ant-launcher'])
    }

    static List gradleClasspath(List searchPatterns) {
        List path = getGradleClasspath() as List
        path.findAll {File file ->
            searchPatterns.find {String pattern ->
                file.name.startsWith(pattern)
            }
        }
    }

    static String unbackslash(def s) {
        FilenameUtils.separatorsToUnix(s.toString())
    }

    static String createIsolatedAntScript(String filling) {
        """ClassLoader loader = Thread.currentThread().contextClassLoader
AntBuilder ant = loader.loadClass('groovy.util.AntBuilder').newInstance()
// ant.project.removeBuildListener(ant.project.getBuildListeners()[0])
// ant.project.addBuildListener(loader.loadClass("org.gradle.logging.AntLoggingAdapter").newInstance())
ant.sequential {
    $filling
}
"""
    }

    static boolean isToolsJarInClasspath() {
        ClassLoader classLoader = Thread.currentThread().contextClassLoader.systemClassLoader
        // firstly check if the tools jar is already in the classpath
        boolean toolsJarAvailable = false;
        try {
            // just check whether this throws an exception
            classLoader.loadClass("com.sun.tools.javac.Main");
            toolsJarAvailable = true;
        } catch (Exception e) {
            try {
                classLoader.loadClass("sun.tools.javac.Main");
                toolsJarAvailable = true;
            } catch (Exception e2) {
                // ignore
            }
        }
        if (toolsJarAvailable) {
            return null;
        }
    }

    static void replaceBuildListener(AntBuilder antBuilder, BuildListener buildListener) {
        antBuilder.project.removeBuildListener(antBuilder.getProject().getBuildListeners()[0])
        antBuilder.project.addBuildListener(buildListener)
    }

    static File getToolsJar() {
        String javaHome = System.getProperty("java.home");
        File toolsJar = new File(javaHome + "/lib/tools.jar");
        if (toolsJar.exists()) {
            logger.debug("Found tools jar in: {}", toolsJar.getAbsolutePath())
            // Found in java.home as given
            return toolsJar;
        }
        if (javaHome.toLowerCase(Locale.US).endsWith(File.separator + "jre")) {
            javaHome = javaHome.substring(0, javaHome.length() - 4);
            toolsJar = new File(javaHome + "/lib/tools.jar");
        }
        if (!toolsJar.exists()) {
            logger.warn("Unable to locate tools.jar. "
                    + "Expected to find it in " + toolsJar.getPath());
            return null;
        }
        logger.debug("Found tools jar in: {}", toolsJar.getAbsolutePath())
        return toolsJar;
    }

    static executeIsolatedAntScript(List loaderClasspath, String filling) {
        ClassLoader oldCtx = Thread.currentThread().contextClassLoader
        URL[] taskUrlClasspath = loaderClasspath.collect {File file ->
            file.toURI().toURL()
        }
        ClassLoader newLoader = new URLClassLoader(taskUrlClasspath, oldCtx.parent)
        Thread.currentThread().contextClassLoader = newLoader
        File toolsJar = getToolsJar()
        logger.debug("Tools jar is: {}", toolsJar)
        if (toolsJar) {
            ClasspathUtil.addUrl(newLoader, [toolsJar])
        }
        String scriptText = createIsolatedAntScript(filling)
        logger.debug("Using groovyc as: {}", scriptText)
        newLoader.loadClass("groovy.lang.GroovyShell").newInstance(newLoader).evaluate(
                scriptText)
        Thread.currentThread().contextClassLoader = oldCtx
    }
}
