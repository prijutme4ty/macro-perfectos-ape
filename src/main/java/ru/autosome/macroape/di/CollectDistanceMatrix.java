package ru.autosome.macroape.di;

import ru.autosome.commons.backgroundModel.di.DiBackground;
import ru.autosome.commons.backgroundModel.di.DiBackgroundModel;
import ru.autosome.commons.backgroundModel.di.DiWordwiseBackground;
import ru.autosome.commons.backgroundModel.mono.Background;
import ru.autosome.commons.backgroundModel.mono.BackgroundModel;
import ru.autosome.commons.backgroundModel.mono.WordwiseBackground;
import ru.autosome.commons.cli.Helper;
import ru.autosome.commons.importer.DiPWMImporter;
import ru.autosome.commons.importer.MotifImporter;
import ru.autosome.commons.importer.PWMImporter;
import ru.autosome.commons.model.BoundaryType;
import ru.autosome.commons.model.Discretizer;
import ru.autosome.commons.motifModel.di.DiPWM;
import ru.autosome.commons.motifModel.mono.PWM;
import ru.autosome.commons.motifModel.types.DataModel;
import ru.autosome.macroape.calculation.di.CompareModelsCountsGiven;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectDistanceMatrix extends ru.autosome.macroape.cli.generalized.CollectDistanceMatrix<DiPWM, DiBackgroundModel> {
  @Override
  protected String DOC_background_option() {
    return "ACGT - 16 numbers, comma-delimited(spaces not allowed), sum should be equal to 1, like 0.02,0.03,0.03,0.02,0.08,0.12,0.12,0.08,0.08,0.12,0.12,0.08,0.02,0.03,0.03,0.02";
  }
  @Override
  protected String DOC_run_string() {
    return "java ru.autosome.macroape.di.CollectDistanceMatrix";
  }

  @Override
  protected String DOC_additional_options() {
    return "These options can be used for PWM vs DiPWM comparison:\n" +
           "  [--from-mono]  - obtain DiPWMs from mononucleotide PWM/PCM/PPMs.\n" +
           "  [--mono-background <background>]  - ACGT - 4 numbers, comma-delimited(spaces not allowed), sum should be equal to 1, like 0.25,0.24,0.26,0.25\n" +
           "                                      Mononucleotide background for PCM/PPM --> PWM conversion of mono models\n";
  }

  protected boolean failed_to_recognize_additional_options(String opt, List<String> argv) {
    if (opt.equals("--from-mono")) {
      fromMononucleotide= true;
      return false;
    } else if (opt.equals("--mono-background")) {
      backgroundMononucleotide = Background.fromString(argv.remove(0));
      return false;
    } else {
      return true;
    }
  }

  boolean fromMononucleotide;
  BackgroundModel backgroundMononucleotide;

  @Override
  protected List<DiPWM> loadMotifCollection(File path_to_collection) {
    if (fromMononucleotide) {
      PWMImporter importer = new PWMImporter(backgroundMononucleotide, dataModel, effectiveCount, transpose);
      List<PWM> monoCollection = importer.loadMotifCollection(path_to_collection);
      pwmCollection = new ArrayList<DiPWM>(monoCollection.size());
      for(PWM monoPWM: monoCollection) {
        pwmCollection.add(DiPWM.fromPWM(monoPWM));
      }
      return pwmCollection;
    } else {
      DiPWMImporter importer = new DiPWMImporter(background, dataModel, effectiveCount, transpose);
      return importer.loadMotifCollection(path_to_collection);
    }
  }

  @Override
  protected void initialize_defaults() {
    super.initialize_defaults();
    fromMononucleotide = false;
    backgroundMononucleotide = new WordwiseBackground();
  }

  @Override
  protected void initialize_default_background() {
    background = new DiWordwiseBackground();
  }

  @Override
  protected DiBackgroundModel extract_background(String str) {
    return DiBackground.fromString(str);
  }

  private CollectDistanceMatrix() {
    initialize_defaults();
  }

  private static CollectDistanceMatrix from_arglist(List<String> argv) {
    CollectDistanceMatrix result = new CollectDistanceMatrix();
    Helper.print_help_if_requested(argv, new CollectDistanceMatrix().documentString());
    result.setup_from_arglist(argv);
    return result;
  }

  private static CollectDistanceMatrix from_arglist(String[] args) {
    ArrayList<String> argv = new ArrayList<String>();
    Collections.addAll(argv, args);
    return from_arglist(argv);
  }


  @Override
  protected CompareModelsCountsGiven calculator(DiPWM firstModel, DiPWM secondModel) {
    return new CompareModelsCountsGiven(firstModel, secondModel,
                                        background, background,
                                        roughDiscretizer, maxPairHashSize);
  }



  public static void main(String[] args) {
    try {
      CollectDistanceMatrix cli = CollectDistanceMatrix.from_arglist(args);
      cli.process();
    } catch (Exception err) {
      System.err.println("\n" + err.getMessage() + "\n--------------------------------------\n");
      err.printStackTrace();
      System.err.println("\n--------------------------------------\nUse --help option for help\n\n" + new CollectDistanceMatrix().documentString());
      System.exit(1);
    }
  }
}
