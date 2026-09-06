package net.ancientloader.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class MinecraftPatcher {

    private static final String RUBYDUNG =
            "com/mojang/rubydung/RubyDung.class";

    private static final String BOOTSTRAP =
            "net/ancientloader/bootstrap/AncientLoaderBootstrap";

    private MinecraftPatcher() {
    }

    /*
     * Usage:
     *
     *   MinecraftPatcher <client.jar> <ancientloader.jar> <output.jar>
     *
     * The output is NOT a complete Minecraft JAR.
     * It is a Prism-compatible overlay containing only:
     *
     *   - classes/files added by AncientLoader
     *   - RubyDung.class with the bootstrap injection
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected: <client.jar> <ancientloader.jar> <output.jar>"
            );
        }

        patch(
                new File(args[0]),
                new File(args[1]),
                new File(args[2])
        );
    }

    static void patch(
            File client,
            File loader,
            File output
    ) throws IOException {

        /*
         * Read the original client so that we know which files
         * are actually changed.
         */
        Map<String, byte[]> original = readJar(client);

        /*
         * Build the set of files that should exist in the overlay.
         *
         * This starts with the loader's own classes.
         */
        Map<String, byte[]> patch = new TreeMap<>();

        byte[] terrain = original.get("terrain.png");

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain.png was not found in the client jar"
            );
        }

        patch.put("terrain.png", terrain);


        Map<String, byte[]> loaderEntries = readJar(loader);

        for (Map.Entry<String, byte[]> entry : loaderEntries.entrySet()) {
            String name = entry.getKey();

            /*
             * Never copy JAR metadata/signatures into the overlay.
             */
            if (name.startsWith("META-INF/")) {
                continue;
            }

            patch.put(name, entry.getValue());
        }

        /*
         * Read RubyDung.class from the ORIGINAL client and inject
         * the AncientLoader bootstrap call.
         */
        byte[] originalRubyDung = original.get(RUBYDUNG);

        if (originalRubyDung == null) {
            throw new IllegalArgumentException(
                    "RubyDung.class was not found; " +
                            "this is not the expected client jar"
            );
        }

        byte[] patchedRubyDung = inject(originalRubyDung);

        /*
         * Only put RubyDung.class into the overlay if it actually
         * differs from the original.
         */
        if (!Arrays.equals(originalRubyDung, patchedRubyDung)) {
            patch.put(RUBYDUNG, patchedRubyDung);
        }

        /*
         * Write the overlay.
         */
        writeJar(output, patch);

        System.out.println(
                "Generated Prism Minecraft JAR mod:"
        );
        System.out.println(
                "  " + output.getAbsolutePath()
        );
        System.out.println(
                "Entries: " + patch.size()
        );

        for (String name : patch.keySet()) {
            System.out.println("  + " + name);
        }
    }

    /*
     * Reads all usable entries from a JAR.
     */
    private static Map<String, byte[]> readJar(File file)
            throws IOException {

        Map<String, byte[]> result = new HashMap<>();

        try (JarFile jar = new JarFile(file)) {

            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {

                JarEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();

                /*
                 * Ignore signatures and other JAR metadata.
                 */
                if (name.startsWith("META-INF/")) {
                    continue;
                }

                try (InputStream stream =
                             jar.getInputStream(entry)) {

                    result.put(name, read(stream));
                }
            }
        }

        return result;
    }

    /*
     * Inject:
     *
     *   AncientLoaderBootstrap.initialize();
     *
     * at the beginning of RubyDung.init().
     */
    private static byte[] inject(byte[] input) {

        ClassNode node = new ClassNode();

        new ClassReader(input).accept(node, 0);

        for (MethodNode method : node.methods) {

            if ("init".equals(method.name)
                    && "()V".equals(method.desc)) {

                InsnList call = new InsnList();

                call.add(
                        new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                BOOTSTRAP,
                                "initialize",
                                "()V",
                                false
                        )
                );

                method.instructions.insert(call);
            }

            /*
             * Modify RubyDung.run().
             */
            /*
             * Modify RubyDung.run().
             */
            if ("run".equals(method.name)
                    && "()V".equals(method.desc)) {

                for (TryCatchBlockNode block : method.tryCatchBlocks) {

                    /*
                     * We want the:
                     *
                     * catch (Exception var9)
                     *
                     * around init().
                     */
                    if (!"java/lang/Exception".equals(block.type)) {
                        continue;
                    }

                    /*
                     * Walk forward from the exception handler.
                     */
                    AbstractInsnNode current = block.handler;

                    while (current != null) {

                        if (current instanceof MethodInsnNode) {

                            MethodInsnNode insn =
                                    (MethodInsnNode) current;

                            /*
                             * Find:
                             *
                             *     var9.toString()
                             *
                             * The owner may be java/lang/Throwable rather
                             * than java/lang/Exception.
                             */
                            if ("toString".equals(insn.name)
                                    && "()Ljava/lang/String;".equals(insn.desc)
                                    && ("java/lang/Throwable".equals(insn.owner)
                                    || "java/lang/Exception".equals(insn.owner)
                                    || "java/lang/Object".equals(insn.owner))) {

                                System.out.println(
                                        "MinecraftPatcher: Found exception "
                                                + "toString() in RubyDung.run()"
                                );

                                /*
                                 * Replace:
                                 *
                                 *     INVOKEVIRTUAL Throwable.toString()
                                 *
                                 * with:
                                 *
                                 *     INVOKESTATIC
                                 *     AncientLoaderBootstrap.getStackTrace(Throwable)
                                 *
                                 * The ALOAD of var9 remains untouched.
                                 */
                                MethodInsnNode replacement =
                                        new MethodInsnNode(
                                                Opcodes.INVOKESTATIC,
                                                BOOTSTRAP,
                                                "getStackTrace",
                                                "(Ljava/lang/Throwable;)"
                                                        + "Ljava/lang/String;",
                                                false
                                        );

                                method.instructions.set(
                                        current,
                                        replacement
                                );

                                System.out.println(
                                        "MinecraftPatcher: Replaced exception "
                                                + "toString() with full stack trace."
                                );

                                break;
                            }
                        }

                        current = current.getNext();
                    }
                }
            }
        }

        ClassWriter writer =
                new ClassWriter(ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        return writer.toByteArray();
    }


    /*
     * Writes the overlay JAR.
     */
    private static void writeJar(
            File file,
            Map<String, byte[]> entries
    ) throws IOException {

        File parent = file.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        try (JarOutputStream output =
                     new JarOutputStream(
                             new FileOutputStream(file))) {

            for (Map.Entry<String, byte[]> entry :
                    entries.entrySet()) {

                JarEntry jarEntry =
                        new JarEntry(entry.getKey());

                output.putNextEntry(jarEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static byte[] read(InputStream stream)
            throws IOException {

        ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];

        int amount;

        while ((amount = stream.read(buffer)) != -1) {
            bytes.write(buffer, 0, amount);
        }

        return bytes.toByteArray();
    }
}
