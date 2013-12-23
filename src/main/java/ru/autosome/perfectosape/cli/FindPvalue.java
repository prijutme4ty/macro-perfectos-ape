package ru.autosome.perfectosape.cli;

import ru.autosome.perfectosape.*;
import ru.autosome.perfectosape.calculations.CanFindPvalue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class FindPvalue {
  private static final String DOC =
   "Command-line format:\n" +
    "java ru.autosome.perfectosape.cli.FindPvalue <pat-file> <threshold list>... [options]\n" +
    "\n" +
    "Options:\n" +
    "  [-d <discretization level>]\n" +
    "  [--pcm] - treat the input file as Position Count Matrix. PCM-to-PWM transformation to be done internally.\n" +
    "  [--ppm] or [--pfm] - treat the input file as Position Frequency Matrix. PPM-to-PWM transformation to be done internally.\n" +
    "  [--effective-count <count>] - effective samples set size for PPM-to-PWM conversion (default: 100). \n" +
    "  [-b <background probabilities] ACGT - 4 numbers, comma-delimited(spaces not allowed), sum should be equal to 1, like 0.25,0.24,0.26,0.25\n" +
    "  [--precalc <folder>] - specify folder with thresholds for PWM collection (for fast-and-rough calculation).\n" +
    "\n" +
    "examples:\n" +
    "  java ru.autosome.perfectosape.cli.FindPvalue motifs/KLF4_f2.pat 7.32\n" +
    "  java ru.autosome.perfectosape.cli.FindPvalue motifs/KLF4_f2.pat 7.32 4.31 5.42 -d 1000 -b 0.2,0.3,0.3,0.2\n";

  private String pm_filename; // file with PM
  private Double discretization;
  private BackgroundModel background;
  private double[] thresholds;
  private Integer max_hash_size;
  private DataModel data_model;
  private double effective_count;

  private String thresholds_folder;
  private PWM pwm;

  private ru.autosome.perfectosape.calculations.FindPvalueAPE ape_calculation() {
    return new ru.autosome.perfectosape.calculations.FindPvalueAPE(pwm, discretization, background, max_hash_size);
  }

  private ru.autosome.perfectosape.calculations.FindPvalueBsearch bsearch_calculation() {
    String thresholds_filename = thresholds_folder + File.separator + "thresholds_" + (new File(pm_filename)).getName();
    PvalueBsearchList bsearchList = PvalueBsearchList.load_from_file(thresholds_filename);
    return new ru.autosome.perfectosape.calculations.FindPvalueBsearch(pwm, background, bsearchList);
  }

  private CanFindPvalue calculator() {
    CanFindPvalue result;
    if (thresholds_folder != null) {
      result = bsearch_calculation();
    } else {
      result = ape_calculation();
    }
    return result;
  }

  /*ArrayList<PvalueInfo> pvalues_by_thresholds() {
    return calculator().pvalues_by_thresholds(thresholds);
  }*/

  private void initialize_defaults() {
    discretization = 10000.0;
    background = new WordwiseBackground();
    thresholds = new double[0];
    max_hash_size = 10000000;
    data_model = DataModel.PWM;
    thresholds_folder = null;
    effective_count = 100;
  }

  private void extract_pm_filename(ArrayList<String> argv) {
    if (argv.isEmpty()) {
      throw new IllegalArgumentException("No input. You should specify input file");
    }
    pm_filename = argv.remove(0);
  }

  private void extract_threshold_lists(ArrayList<String> argv) {
    ArrayList<Double> thresholds_list = new ArrayList<Double>();
    try {
      while (!argv.isEmpty()) {
        thresholds_list.add(Double.valueOf(argv.get(0)));
        argv.remove(0);
      }
    } catch (NumberFormatException e) {
    }
    if (thresholds_list.isEmpty()) {
      throw new IllegalArgumentException("You should specify at least one threshold");
    }
    thresholds = ArrayExtensions.toPrimitiveArray(thresholds_list);
  }

  private void extract_option(ArrayList<String> argv) {
    String opt = argv.remove(0);
    if (opt.equals("-b")) {
      background = Background.fromString(argv.remove(0));
    } else if (opt.equals("--max-hash-size")) {
      max_hash_size = Integer.valueOf(argv.remove(0));
    } else if (opt.equals("-d")) {
      discretization = Double.valueOf(argv.remove(0));
    } else if (opt.equals("--pcm")) {
      data_model = DataModel.PCM;
    } else if (opt.equals("--ppm") || opt.equals("--pfm")) {
      data_model = DataModel.PPM;
    } else if (opt.equals("--effective-count")) {
      effective_count = Double.valueOf(argv.remove(0));
    } else if (opt.equals("--precalc")) {
      thresholds_folder = argv.remove(0);
    } else {
      throw new IllegalArgumentException("Unknown option '" + opt + "'");
    }
  }

  void setup_from_arglist(ArrayList<String> argv) {
    extract_pm_filename(argv);
    extract_threshold_lists(argv);
    while (argv.size() > 0) {
      extract_option(argv);
    }
    pwm = Helper.load_pwm(PMParser.from_file_or_stdin(pm_filename), data_model, background, effective_count);
  }

  private FindPvalue() {
    initialize_defaults();
  }

  private static FindPvalue from_arglist(ArrayList<String> argv) {
    FindPvalue result = new FindPvalue();
    ru.autosome.perfectosape.cli.Helper.print_help_if_requested(argv, DOC);
    result.setup_from_arglist(argv);
    return result;
  }

  private static FindPvalue from_arglist(String[] args) {
    ArrayList<String> argv = new ArrayList<String>();
    Collections.addAll(argv, args);
    return from_arglist(argv);
  }

  OutputInformation report_table_layout() {
    return calculator().report_table_layout();
  }

  OutputInformation report_table(ArrayList<? extends ResultInfo> data) {
    OutputInformation result = report_table_layout();
    result.data = data;
    return result;
  }

  <R extends ResultInfo> OutputInformation report_table(R[] data) {
    ArrayList<R> data_list = new ArrayList<R>(data.length);
    Collections.addAll(data_list, data);
    return report_table(data_list);
  }

  OutputInformation report_table() {
    CanFindPvalue.PvalueInfo[] results = calculator().pvalues_by_thresholds(thresholds);
    return report_table(results);
  }

  public static void main(String[] args) {
    try {
      FindPvalue cli = FindPvalue.from_arglist(args);
      System.out.println(cli.report_table().report());
    } catch (Exception err) {
      System.err.println("\n" + err.getMessage() + "\n--------------------------------------\n");
      err.printStackTrace();
      System.err.println("\n--------------------------------------\nUse --help option for help\n\n" + DOC);
      System.exit(1);
    }
  }

}
