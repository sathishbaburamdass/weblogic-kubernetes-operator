// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import oracle.kubernetes.operator.utils.DomainUpgradeUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class DomainResourceConverter {

  private static final DomainUpgradeUtils domainUpgradeUtils = new DomainUpgradeUtils();

  String inputFileName;
  String outputDir;
  String outputFileName;

  /**
   * Entry point of the DomainResourceConverter.
   * @param args The arguments for domain resource converter.
   *
   */
  public static void main(String[] args) {
    final DomainResourceConverter drc = parseCommandLine(args);

    File inputFile = new File(drc.inputFileName);
    File outputDir = new File(drc.outputDir);

    if (!inputFile.exists()) {
      throw new RuntimeException("InputFile: " + drc.inputFileName + " does not exist or could not be read.");
    }

    if (!outputDir.exists()) {
      throw new RuntimeException("The output dir specified by '-d' does not exist or could not be read.");
    }

    convertDomain(drc);
    return;
  }

  private static void convertDomain(DomainResourceConverter drc) {
    try (Writer writer = Files.newBufferedWriter(Path.of(drc.outputDir + "/" + drc.outputFileName))) {
      writer.write(domainUpgradeUtils.convertDomain(Files.readString(Path.of(drc.inputFileName))));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Constructs an instances of Domain resource converter with given arguments.
   * @param outputDir Name of the output directory.
   * @param outputFileName Name of the output file.
   * @param inputFileName Name of the input file.
   */
  public DomainResourceConverter(
          String outputDir,
          String outputFileName,
          String inputFileName) {
    this.outputDir = Optional.ofNullable(outputDir).orElse(new File(inputFileName).getParent());
    this.outputFileName = Optional.ofNullable(outputFileName)
            .orElse("Converted_" + new File(inputFileName).getName());
    this.inputFileName = inputFileName;
  }

  private static DomainResourceConverter parseCommandLine(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();

    Option helpOpt = new Option("h", "help", false, "Print help message");
    options.addOption(helpOpt);

    Option outputDir = new Option("d", "outputDir", true, "Output directory");
    options.addOption(outputDir);

    Option outputFile = new Option("f", "outputFile", true, "Output file name");
    options.addOption(outputFile);

    try {
      CommandLine cli = parser.parse(options, args);
      if (cli.hasOption("help")) {
        printHelpAndExit(options);
      }
      if (cli.getArgs().length < 1) {
        printHelpAndExit(options);
      }
      return new DomainResourceConverter(cli.getOptionValue("d"), cli.getOptionValue("f"),
              cli.getArgs()[0]);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private static void printHelpAndExit(Options options) {
    HelpFormatter help = new HelpFormatter();
    help.printHelp(120, "java -cp <operator-jar> oracle.kubernetes.operator.DomainResourceConverter "
                    + "<input-file> [-d <output_dir>] [-f <output_file_name>] [-h --help]",
            "", options, "");
    System.exit(1);
  }
}