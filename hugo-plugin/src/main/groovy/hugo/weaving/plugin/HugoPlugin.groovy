package hugo.weaving.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class HugoPlugin implements Plugin<Project> {
  @Override void apply(Project project) {
    def hasApp = project.plugins.withType(AppPlugin)
    def hasLib = project.plugins.withType(LibraryPlugin)
    if (!hasApp && !hasLib) {
      throw new IllegalStateException("'android' or 'android-library' plugin required.")
    }

    final def log = project.logger
    final def variants
    if (hasApp) {
      variants = project.android.applicationVariants
    } else {
      variants = project.android.libraryVariants
    }

    project.dependencies {
      debugImplementation 'com.jakewharton.hugo:hugo-runtime:1.3.1-T2-SNAPSHOT'
      // TODO this should come transitively
      debugImplementation 'org.aspectj:aspectjrt:1.9.1'
      implementation 'com.jakewharton.hugo:hugo-annotations:1.3.0-SNAPSHOT'
    }

    project.extensions.create('hugo', HugoExtension)

    variants.all { variant ->
      if (!variant.buildType.isDebuggable()) {
        log.debug("Skipping non-debuggable build type '${variant.buildType.name}'.")
        return;
      } else if (!project.hugo.enabled) {
        log.debug("Hugo is not disabled.")
        return;
      }

      def fullName = variant.buildType.name

      JavaCompile javaCompile = variant.javaCompile
      javaCompile.doLast {
        String[] javaArgs = [
            "-showWeaveInfo",
            "-1.8",
            "-inpath", javaCompile.destinationDir.toString(),
            "-aspectpath", javaCompile.classpath.asPath,
            "-d", javaCompile.destinationDir.toString(),
            "-classpath", javaCompile.classpath.asPath,
            "-bootclasspath", project.android.bootClasspath.join(File.pathSeparator)
        ]
        log.debug "ajc args(Java): " + Arrays.toString(javaArgs)

        String[] kotlinArgs = [
                "-showWeaveInfo",
                 "-1.8",
                 "-inpath", project.buildDir.path + "/tmp/kotlin-classes/" + fullName,
                 "-aspectpath", javaCompile.classpath.asPath,
                 "-d", project.buildDir.path + "/tmp/kotlin-classes/" + fullName,
                 "-classpath", javaCompile.classpath.asPath,
                 "-bootclasspath", project.android.bootClasspath.join(File.pathSeparator)]
        log.debug "ajc args(Kotlin): " + Arrays.toString(kotlinArgs)

        log.debug "Full name: " + fullName
        log.debug kotlinArgs[2]

        MessageHandler handler = new MessageHandler(true);

        new Main().run(javaArgs, handler);
        new Main().run(kotlinArgs, handler);

        for (IMessage message : handler.getMessages(null, true)) {
          switch (message.getKind()) {
            case IMessage.ABORT:
            case IMessage.ERROR:
            case IMessage.FAIL:
              log.error message.message, message.thrown
              break;
            case IMessage.WARNING:
              log.warn message.message, message.thrown
              break;
            case IMessage.INFO:
              log.info message.message, message.thrown
              break;
            case IMessage.DEBUG:
              log.debug message.message, message.thrown
              break;
          }
        }
      }
    }
  }
}
