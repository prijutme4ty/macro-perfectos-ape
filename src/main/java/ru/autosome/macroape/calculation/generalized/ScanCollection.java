package ru.autosome.macroape.calculation.generalized;

import ru.autosome.ape.calculation.findPvalue.CanFindPvalue;
import ru.autosome.ape.calculation.findPvalue.FindPvalueAPE;
import ru.autosome.ape.calculation.findThreshold.CanFindThreshold;
import ru.autosome.ape.calculation.findThreshold.FindThresholdAPE;
import ru.autosome.ape.model.exception.HashOverflowException;
import ru.autosome.commons.backgroundModel.GeneralizedBackgroundModel;
import ru.autosome.commons.model.BoundaryType;
import ru.autosome.commons.motifModel.*;
import ru.autosome.macroape.model.PairAligned;

import java.util.ArrayList;
import java.util.List;

public abstract class ScanCollection <ModelType extends Alignable<ModelType> & Named & ScoringModel & Discretable<ModelType> &ScoreDistribution<BackgroundType>, BackgroundType extends GeneralizedBackgroundModel> {

  protected final List<ru.autosome.macroape.cli.generalized.ScanCollection<ModelType,BackgroundType>.ThresholdEvaluator> thresholdEvaluators;

  public final ModelType queryPWM;
  public double pvalue;
  public Double queryPredefinedThreshold;
  public Double roughDiscretization, preciseDiscretization;
  public BackgroundType queryBackground, collectionBackground;
  public BoundaryType pvalueBoundaryType;
  public Integer maxHashSize, maxPairHashSize;
  public Double similarityCutoff;
  public Double preciseRecalculationCutoff; // null means that no recalculation will be performed


  public ScanCollection(List<ru.autosome.macroape.cli.generalized.ScanCollection<ModelType,BackgroundType>.ThresholdEvaluator> thresholdEvaluators, ModelType queryPWM) {
    this.thresholdEvaluators = thresholdEvaluators;
    this.queryPWM = queryPWM;
  }

  abstract protected CompareModels<ModelType, BackgroundType> calculation(ModelType firstMotif, ModelType secondMotif,
                                                                          BackgroundType firstBackground, BackgroundType secondBackground,
                                                                          CanFindPvalue firstPvalueCalculator, CanFindPvalue secondPvalueCalculator,
                                                                          Double discretization, Integer maxHashSize);

  public List<SimilarityInfo> similarityInfos() throws HashOverflowException {
    List<SimilarityInfo> result;
    result = new ArrayList<SimilarityInfo>(thresholdEvaluators.size());

    FindPvalueAPE roughQueryPvalueEvaluator = new FindPvalueAPE<ModelType, BackgroundType>(queryPWM, queryBackground, roughDiscretization, maxHashSize);
    FindPvalueAPE preciseQueryPvalueEvaluator = new FindPvalueAPE<ModelType, BackgroundType>(queryPWM, queryBackground, preciseDiscretization, maxHashSize);

    double roughQueryThreshold = queryThreshold(roughDiscretization);
    double preciseQueryThreshold = queryThreshold(preciseDiscretization);


    for (ru.autosome.macroape.cli.generalized.ScanCollection<ModelType,BackgroundType>.ThresholdEvaluator knownMotifEvaluator: thresholdEvaluators) {
      CompareModelsCountsGiven.SimilarityInfo info;
      boolean precise = false;
      CompareModels roughCalculation = calculation(queryPWM, knownMotifEvaluator.pwm,
                                                   queryBackground, collectionBackground,
                                                   roughQueryPvalueEvaluator,
                                                   knownMotifEvaluator.roughPvalueCalculator,
                                                   roughDiscretization, maxPairHashSize);

      Double roughCollectionThreshold = knownMotifEvaluator.roughThresholdCalculator
                                         .thresholdByPvalue(pvalue, pvalueBoundaryType).threshold;

      info = roughCalculation.jaccard(roughQueryThreshold,
                                      roughCollectionThreshold);

      if (preciseRecalculationCutoff != null &&
           info.similarity() >= preciseRecalculationCutoff &&
           knownMotifEvaluator.preciseThresholdCalculator != null) {
        CompareModels preciseCalculation = calculation(queryPWM, knownMotifEvaluator.pwm,
                                                       queryBackground, collectionBackground,
                                                       preciseQueryPvalueEvaluator,
                                                       knownMotifEvaluator.precisePvalueCalculator,
                                                       preciseDiscretization, maxPairHashSize);

        Double preciseCollectionThreshold = knownMotifEvaluator.preciseThresholdCalculator
                                             .thresholdByPvalue(pvalue, pvalueBoundaryType).threshold;

        info = preciseCalculation.jaccard(preciseQueryThreshold,
                                          preciseCollectionThreshold);
        precise = true;
      }
      if (similarityCutoff == null || info.similarity() >= similarityCutoff) {
        result.add(new SimilarityInfo(knownMotifEvaluator.pwm, info, precise));
      }
    }
    return result;
  }


  double queryThreshold(Double discretization) throws HashOverflowException {
    if (queryPredefinedThreshold != null) {
      return queryPredefinedThreshold;
    } else {
      CanFindThreshold pvalue_calculator = new FindThresholdAPE<ModelType, BackgroundType>(queryPWM, queryBackground, discretization, maxHashSize);
      return pvalue_calculator.thresholdByPvalue(pvalue, pvalueBoundaryType).threshold;
    }
  }

  public class SimilarityInfo extends CompareModelsCountsGiven.SimilarityInfo {
    public final ModelType collectionPWM;
    public final boolean precise;

    public SimilarityInfo(ModelType collectionPWM, PairAligned<ModelType> alignment,
                          double recognizedByBoth, double recognizedByFirst, double recognizedBySecond,
                          boolean precise) {
      super(alignment, recognizedByBoth, recognizedByFirst, recognizedBySecond);
      this.collectionPWM = collectionPWM;
      this.precise = precise;
    }
    public SimilarityInfo(ModelType collectionPWM, CompareModelsCountsGiven.SimilarityInfo similarityInfo, boolean precise) {
      super(similarityInfo.alignment,
            similarityInfo.recognizedByBoth,
            similarityInfo.recognizedByFirst,
            similarityInfo.recognizedBySecond);
      this.collectionPWM = collectionPWM;
      this.precise = precise;
    }
    public String name() {
      return collectionPWM.getName();
    }
  }
}
