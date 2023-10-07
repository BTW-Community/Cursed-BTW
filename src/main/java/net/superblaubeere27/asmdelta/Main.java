/*
 * Copyright (c) 2019 superblaubeere27
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.superblaubeere27.asmdelta;

import joptsimple.*;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import net.superblaubeere27.asmdelta.difference.VerificationException;
import net.superblaubeere27.asmdelta.difference.clazz.AddClassDifference;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main {
    private static final String VERSION = "ASMDelta v1.0.0";


    public static void main(String[] args) throws IOException, VerificationException {
        OptionParser parser = new OptionParser();

        ArgumentAcceptingOptionSpec<File> jar1Option = parser.accepts("jar1", "The original file").withRequiredArg().ofType(File.class).required();
        ArgumentAcceptingOptionSpec<File> jar2Option = parser.accepts("jar2", "The new file").withRequiredArg().ofType(File.class).required();

        ArgumentAcceptingOptionSpec<File> patchFile = parser.accepts("o", "The generated patch location").withRequiredArg().ofType(File.class).required();

        ArgumentAcceptingOptionSpec<String> forJavaAgent = parser.accepts("forAgent", "Creates a patch for a java agent & dumps the additional classes needed at runtime.").withOptionalArg();
        ArgumentAcceptingOptionSpec<File> additionalClasses = parser.accepts("added-classes", "Where to output additional classes").requiredIf(forJavaAgent).withRequiredArg().ofType(File.class);

        ArgumentAcceptingOptionSpec<String> patchName = parser.accepts("name", "The patch name").withOptionalArg().defaultsTo("N/A").ofType(String.class);

        OptionSpec<Void> help = parser.accepts("help", "Prints a help page").forHelp();
        OptionSpec<Void> version = parser.accepts("version", "Prints the version on sysout").forHelp();

        OptionSet parse;

        try {
            parse = parser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage() + " (try --help)");
            return;
        }

        if (parse.has(help)) {
            parser.printHelpOn(System.err);
            return;
        }
        if (parse.has(version)) {
            System.err.println(VERSION);
            return;
        }

        File jar1 = parse.valueOf(jar1Option);
        File jar2 = parse.valueOf(jar2Option);
        File outputFile = parse.valueOf(patchFile);

        if (jar1 == null || !jar1.exists()) {
            System.err.println("Jar 1 doesn't exist");
            return;
        }
        if (jar2 == null || !jar2.exists()) {
            System.err.println("Jar 2 doesn't exist");
            return;
        }

        System.out.println("Calculating delta...");

        long l = System.currentTimeMillis();

        List<AbstractDifference> differences = ASMDelta.calculateDifference(1,
                jar1,
                jar2);

        System.out.println("Finished in " + (System.currentTimeMillis() - l) + "ms");

        if (parse.has(forJavaAgent)) {
            Set<AbstractDifference> illegalChanges = differences.stream().filter(e -> !e.canBeAppliedAtRuntime()).collect(Collectors.toSet());

            if (illegalChanges.size() > 0) {
                System.err.println("There are " + illegalChanges.size() + " illegal patches (for java agent):");

                for (AbstractDifference abstractDifference : illegalChanges) {
                    System.err.println(abstractDifference);
                }
            } else {
                System.out.println("Patch is valid (for Java agent).");
            }

            System.out.println("Writing added classes...");

            Set<AddClassDifference> newClasses = differences.stream().filter(e -> e instanceof AddClassDifference).map(e -> (AddClassDifference) e).collect(Collectors.toSet());

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(parse.valueOf(additionalClasses)))) {
                for (AddClassDifference newClass : newClasses) {
                    zipOutputStream.putNextEntry(new ZipEntry(newClass.getNewNode().name + ".class"));

                    ClassWriter classWriter = new ClassWriter(0);

                    newClass.getNewNode().accept(classWriter);

                    zipOutputStream.write(classWriter.toByteArray());

                    zipOutputStream.closeEntry();
                }
            }

            differences.removeAll(newClasses);

        }

        ASMDeltaPatch patch = new ASMDeltaPatch(parse.valueOf(patchName), 1, differences);

        outputFile.getParentFile().mkdirs();

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            patch.write(outputStream);
        }

        System.out.println("Wrote patch to " + outputFile.getAbsolutePath());

    }

}
