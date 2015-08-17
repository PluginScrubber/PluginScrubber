import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class Scrubber {
    private static final byte[] BUFFER = new byte[4096 * 1024];

    public static void main(String[] args) {
        System.out.println("-----------------------------------------");
        System.out.println("|            PLUGIN SCRUBBER            |");
        System.out.println("|                                       |");
        System.out.println("|   Removes maliciously injected code   |");
        System.out.println("|           from your plugins           |");
        System.out.println("-----------------------------------------");
        System.out.println();
        System.out.println();

        File tmp = new File("scrubtemp");
        if (tmp.exists() && !tmp.isDirectory()) {
            System.out.print("scrubtemp exists, but is not a folder! Delete the file 'scrubtemp' and try again.");
            return;
        }
        if (!tmp.exists()) {
            if (!tmp.mkdir()) {
                System.out.println("Could not create scrubtemp folder! Cannot continue.");
                return;
            }
        }

        File folder = new File("plugins");
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Plugins folder not found. Run Plugin Scrubber from your main folder.");
            return;
        }

        File[] files = folder.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".jar");
            }
        });

        if (files.length == 0) {
            System.out.println("No plugins found in your plugins folder.");
            return;
        }

        System.out.println("Found " + files.length + " plugins. Let's begin.");
        System.out.println();

        for (File file : files) {
            System.out.println("* Processing " + file.getName());
            try {
                jar(file);
            } catch (Throwable thrown) {
                System.out.println("* Failed to process:");
                thrown.printStackTrace();
            }
        }

        try {
            deleteDirectory(tmp);
        } catch (IOException ignored) {
            System.out.println("Failed to remove 'scrubtemp' folder. Sorry.");
        }
    }

    private static void jar(File file) throws IOException {
        JarFile jarFile = new JarFile(file);

        if (jarFile.getJarEntry("scrubbed_clean") != null) {
            print(":) This plugin has already been scrubbed.");
            return;
        }

        JarEntry pluginYml = jarFile.getJarEntry("plugin.yml");
        if (pluginYml == null) {
            print("No plugin.yml found! Skipping!");
            return;
        }

        InputStream is = jarFile.getInputStream(pluginYml);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        String mainClassName = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("main:")) {
                mainClassName = line.substring("main:".length()).trim();
                break;
            }
        }
        if (mainClassName == null) {
            print("Found plugin.yml but it listed no main class. Skipping!");
            return;
        }

        String mainClassPath = mainClassName.replaceAll("\\.", "/") + ".class";
        JarEntry mainClassEntry = jarFile.getJarEntry(mainClassPath);
        if (mainClassEntry == null) {
            print("Main class not found! Skipping!");
            return;
        }
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass(jarFile.getInputStream(mainClassEntry));

        CtMethod loadConfig;
        try {
            loadConfig = ctClass.getMethod("loadConfig0", "()V");
        } catch (NotFoundException ignored) {
            print(":) This plugin is not infected!");
            return;
        }

        print("!! Found malicious code, attempting to remove.");
        try {
            loadConfig.setBody("return;");
        } catch (CannotCompileException ignored) {
            print("Could not overwrite the evil method! File still infected.");
            return;
        }

        try {
            ctClass.writeFile("scrubtemp");
        } catch (CannotCompileException ignored) {
            print("Could not write new, clean plugin class file! File still infected");
            return;
        }

        jarFile.close();
        createSafeJar(file, mainClassPath);
    }

    private static void print(String s) {
        System.out.println("    " + s);
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        int bytesRead;
        while ((bytesRead = input.read(BUFFER)) != -1) {
            output.write(BUFFER, 0, bytesRead);
        }
    }

    private static void createSafeJar(File jarFile, String mainClass) throws IOException {
        ZipFile jar = new ZipFile(jarFile);
        print("Creating new jar file");
        File newJarFile = new File("scrubtemp/" + jarFile.getName());
        ZipOutputStream newJar = new ZipOutputStream(new FileOutputStream(newJarFile));

        Enumeration<? extends ZipEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.getName().equals(mainClass)) {
                continue;
            }
            newJar.putNextEntry(e);
            if (!e.isDirectory()) {
                copy(jar.getInputStream(e), newJar);
            }
            newJar.closeEntry();
        }

        ZipEntry e = new ZipEntry(mainClass);
        print("Adding fixed class file: " + e.getName());
        newJar.putNextEntry(e);
        InputStream inClass = new FileInputStream(new File("scrubtemp/" + mainClass));
        copy(inClass, newJar);
        inClass.close();
        newJar.closeEntry();
        newJar.putNextEntry(new ZipEntry("scrubbed_clean"));
        newJar.write("Cleaned by Plugin Scrubber!\n".getBytes());
        newJar.closeEntry();

        jar.close();
        newJar.close();

        print("Moving infected jar to " + jarFile.getName() + ".infected");
        File oldFile = new File(jarFile.getParentFile(), jarFile.getName() + ".infected");
        Files.move(jarFile.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.move(newJarFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        print(":) Replaced fixed jar!");
    }

    private static void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (null != files) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        Files.delete(file.toPath());
                    }
                }
            }
        }
        Files.delete(directory.toPath());
    }
}
