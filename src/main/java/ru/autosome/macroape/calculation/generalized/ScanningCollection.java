package ru.autosome.macroape.calculation.generalized;

import ru.autosome.ape.calculation.findPvalue.CanFindPvalue;
import ru.autosome.ape.calculation.findPvalue.FindPvalueAPE;
import ru.autosome.ape.calculation.findPvalue.FoundedPvalueInfo;
import ru.autosome.ape.calculation.findThreshold.CanFindThreshold;
import ru.autosome.ape.calculation.findThreshold.FindThresholdAPE;
import ru.autosome.commons.backgroundModel.GeneralizedBackgroundModel;
import ru.autosome.commons.model.BoundaryType;
import ru.autosome.commons.model.Discretizer;
import ru.autosome.commons.motifModel.Alignable;
import ru.autosome.commons.motifModel.Discretable;
import ru.autosome.commons.motifModel.ScoreDistribution;
import ru.autosome.macroape.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ScanningCollection<ModelType extends Alignable<ModelType> & Discretable<ModelType> & ScoreDistribution<BackgroundType>,
                                BackgroundType extends GeneralizedBackgroundModel> {

  protected final List<ThresholdEvaluator<ModelType>> thresholdEvaluators;

  public final ModelType queryPWM;
  public double pvalue;
  public Double queryPredefinedThreshold;
  public Discretizer roughDiscretizer, preciseDiscretizer;
  public BackgroundType background;
  public BoundaryType pvalueBoundaryType;
  public Double similarityCutoff;
  public Double preciseRecalculationCutoff; // null means that no recalculation will be performed
  public final Function<PairAligned<ModelType>, ? extends AlignedModelIntersection> calculatorOfAligned;

  public ScanningCollection(List<ThresholdEvaluator<ModelType>> thresholdEvaluators, ModelType queryPWM, Function<PairAligned<ModelType>, ? extends AlignedModelIntersection> calculatorOfAligned) {
    this.thresholdEvaluators = thresholdEvaluators;
    this.queryPWM = queryPWM;
    this.calculatorOfAligned = calculatorOfAligned;
  }

  public ComparisonSimilarityInfo comparisonInfo(CanFindPvalue queryPvalueEvaluator,
                                                 double queryThreshold,
                                                 SingleThresholdEvaluator<ModelType> knownMotifEvaluator,
                                                 Discretizer discretizer) {
    CompareModels<ModelType> calc;
    calc = new CompareModels<>(queryPWM, knownMotifEvaluator.pwm, background.volume(), discretizer, calculatorOfAligned);

    Double collectionThreshold = knownMotifEvaluator.thresholdCalculator
                                          .thresholdByPvalue(pvalue, pvalueBoundaryType).threshold;

    FoundedPvalueInfo countByThresholdQuery = queryPvalueEvaluator.pvalueByThreshold(queryThreshold);
    FoundedPvalueInfo countByThresholdKnown = knownMotifEvaluator.pvalueCalculator.pvalueByThreshold(collectionThreshold);
    return calc.jaccard(countByThresholdQuery, countByThresholdKnown);
  }


  public ScanningSimilarityInfo similarityInfo(CanFindPvalue roughQueryPvalueEvaluator,
                                               CanFindPvalue preciseQueryPvalueEvaluator,
                                               double roughQueryThreshold,
                                               double preciseQueryThreshold,
                                               ThresholdEvaluator<ModelType> knownMotifEvaluator) {
    ComparisonSimilarityInfo info;
    boolean precise = false;

    info = comparisonInfo(roughQueryPvalueEvaluator, roughQueryThreshold, knownMotifEvaluator.rough, roughDiscretizer);

    if (preciseRecalculationCutoff != null &&
            info.similarity() >= preciseRecalculationCutoff &&
            knownMotifEvaluator.precise.thresholdCalculator != null) {
      info = comparisonInfo(preciseQueryPvalueEvaluator, preciseQueryThreshold, knownMotifEvaluator.precise, preciseDiscretizer);
      precise = true;
    }
    if (similarityCutoff == null || info.similarity() >= similarityCutoff) {
      return new ScanningSimilarityInfo(knownMotifEvaluator.name, info, precise);
    } else {
      return null;
    }
  }

  public List<ScanningSimilarityInfo> similarityInfos() {
    List<ScanningSimilarityInfo> result;
    result = new ArrayList<>(thresholdEvaluators.size());

    CanFindPvalue roughQueryPvalueEvaluator = new FindPvalueAPE<>(queryPWM, background, roughDiscretizer);
    CanFindPvalue preciseQueryPvalueEvaluator = new FindPvalueAPE<>(queryPWM, background, preciseDiscretizer);

    double roughQueryThreshold = queryThreshold(roughDiscretizer);
    double preciseQueryThreshold = queryThreshold(preciseDiscretizer);

    for (ThresholdEvaluator<ModelType> knownMotifEvaluator: thresholdEvaluators) {
      ScanningSimilarityInfo info = similarityInfo(roughQueryPvalueEvaluator, preciseQueryPvalueEvaluator,
          roughQueryThreshold, preciseQueryThreshold,
          knownMotifEvaluator);
      if (info != null) {
        result.add(info);
      }
    }
    return result;
  }


  double queryThreshold(Discretizer discretizer) {
    if (queryPredefinedThreshold != null) {
      return queryPredefinedThreshold;
    } else {
      CanFindThreshold pvalue_calculator = new FindThresholdAPE<>(queryPWM, background, discretizer);
      return pvalue_calculator.thresholdByPvalue(pvalue, pvalueBoundaryType).threshold;
    }
  }

}
