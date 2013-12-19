package ru.autosome.perfectosape.calculations;

import gnu.trove.iterator.TDoubleDoubleIterator;
import gnu.trove.iterator.TDoubleObjectIterator;
import gnu.trove.map.hash.TDoubleDoubleHashMap;
import gnu.trove.map.hash.TDoubleObjectHashMap;
import ru.autosome.perfectosape.*;

public class CompareAligned {
  public static class SimilarityInfo extends ResultInfo {
    public final double recognizedByBoth;
    public final double recognizedByFirst;
    public final double recognizedBySecond;

    public Double firstVocabularyVolume;
    public Double secondVocabularyVolume;

    public SimilarityInfo(double recognizedByBoth, double recognizedByFirst, double recognizedBySecond,
                          Double firstVocabularyVolume, Double secondVocabularyVolume) {
      this.recognizedByFirst = recognizedByFirst;
      this.recognizedBySecond = recognizedBySecond;
      this.recognizedByBoth = recognizedByBoth;

      this.firstVocabularyVolume = firstVocabularyVolume;
      this.secondVocabularyVolume = secondVocabularyVolume;
    }

    public Double similarity() {
      if (recognizedByFirst == 0 || recognizedBySecond == 0) {
        return null;
      }
      double union = recognizedByFirst + recognizedBySecond - recognizedByBoth;
      return recognizedByBoth / union;
    }

    public Double distance() {
      Double similarity = similarity();
      if (similarity == null) {
        return null;
      } else {
        return 1.0 - similarity;
      }
    }

    public Double realPvalueFirst() {
      if (firstVocabularyVolume == null) {
        return null;
      } else {
        return recognizedByFirst / firstVocabularyVolume;
      }
    }
    public Double realPvalueSecond() {
      if (secondVocabularyVolume == null) {
        return null;
      } else {
        return recognizedBySecond / secondVocabularyVolume;
      }
    }
  }

  public final CountingPWM firstPWM;
  public final CountingPWM secondPWM;
  public final Position relativePosition;

  public Double max_pair_hash_size;

  private PWMAligned cache_alignment;

  public CompareAligned(CountingPWM firstPWM, CountingPWM secondPWM, Position relativePosition) {
    this.firstPWM = firstPWM;
    this.secondPWM = secondPWM;
    this.relativePosition = relativePosition;
  }

  public SimilarityInfo jaccard(double first_threshold, double second_threshold) throws Exception {
    double f = firstPWM.count_by_threshold(first_threshold) * Math.pow(firstPWM.background.volume(),
                                                                       alignment().length() - firstPWM.pwm.length());
    double s = secondPWM.count_by_threshold(second_threshold) * Math.pow(secondPWM.background.volume(),
                                                                         alignment().length() - secondPWM.pwm.length());

    double[] intersections = counts_for_two_matrices(first_threshold, second_threshold);
    double intersect = Math.sqrt(intersections[0] * intersections[1]);

    double firstPWMVocabularyVolume = Math.pow(firstPWM.background.volume(), alignment().length());
    double secondPWMVocabularyVolume = Math.pow(secondPWM.background.volume(), alignment().length());
    return new SimilarityInfo(intersect, f, s,
                              firstPWMVocabularyVolume,
                              secondPWMVocabularyVolume);
  }

  public SimilarityInfo jaccard_by_pvalue(double pvalue) throws Exception {
    double threshold_first = firstPWM.threshold(pvalue).threshold;
    double threshold_second = secondPWM.threshold(pvalue).threshold;
    return jaccard(threshold_first, threshold_second);
  }

  public SimilarityInfo jaccard_by_weak_pvalue(double pvalue) throws Exception {
    double threshold_first = firstPWM.weak_threshold(pvalue).threshold;
    double threshold_second = secondPWM.weak_threshold(pvalue).threshold;
    return jaccard(threshold_first, threshold_second);
  }

  static public interface RecalculateScore {
    public double recalculateScore(double score, int letter) throws Exception;
  }

  // unoptimized version of this and related methods
  private double[] counts_for_two_matrices(double threshold_first, double threshold_second) throws Exception {
    // just not to call method each time
    final BackgroundModel first_background = firstPWM.background;
    final BackgroundModel second_background = secondPWM.background;
    if (first_background.equals(second_background)) {
      if (firstPWM.background.is_wordwise()) {
//        double result = get_counts(threshold_first, threshold_second,  new RecalculateScore() {
//          public double recalculateScore(double score, int letter) { return score; }
//        });

        double result = get_counts_wordwise(threshold_first, threshold_second);
        return new double[] {result, result};
      } else {
        final BackgroundModel background = first_background;
        double result = get_counts(threshold_first, threshold_second, new RecalculateScore() {
          public double recalculateScore(double score, int letter) { return background.count(letter) * score; }
        });

        return new double[] {result, result};
      }
    } else {
      double first_result = get_counts(threshold_first, threshold_second, new RecalculateScore() {
        public double recalculateScore(double score, int letter) { return first_background.count(letter) * score; }
      });

      double second_result = get_counts(threshold_first, threshold_second, new RecalculateScore() {
        public double recalculateScore(double score, int letter) { return second_background.count(letter) * score; }
      });

      return new double[] {first_result, second_result};
    }
  }


  private int summarySize(TDoubleObjectHashMap<TDoubleDoubleHashMap> scores) {
    int sum = 0;
    TDoubleObjectIterator<TDoubleDoubleHashMap> iterator = scores.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      sum += iterator.value().size();
    }
    return sum;
  }

  public PWMAligned alignment() {
    if (cache_alignment == null) {
      cache_alignment = new PWMAligned(firstPWM.pwm, secondPWM.pwm, relativePosition);
    }
    return cache_alignment;
  }

  // block has form: {|score,letter| contribution to count by `letter` with `score` }
  private double get_counts(double threshold_first, double threshold_second, RecalculateScore count_contribution_block) throws Exception {
    // scores_on_first_pwm, scores_on_second_pwm --> count
    TDoubleObjectHashMap<TDoubleDoubleHashMap> scores = new TDoubleObjectHashMap<TDoubleDoubleHashMap>();
    scores.put(0.0, new TDoubleDoubleHashMap(new double[] {0},
                                             new double[] {1}) );

    for (int pos = 0; pos < alignment().length(); ++pos) {
      scores = recalc_score_hash(scores,
                                 alignment().first_pwm.matrix[pos], alignment().second_pwm.matrix[pos],
                                 threshold_first - alignment().first_pwm.best_suffix(pos + 1),
                                 threshold_second - alignment().second_pwm.best_suffix(pos + 1),
                                 count_contribution_block);
      if (max_pair_hash_size != null && summarySize(scores) > max_pair_hash_size) {
        throw new Exception("Hash overflow in Macroape::AlignedPairIntersection#counts_for_two_matrices_with_different_probabilities");
      }
    }

    return combine_scores(scores);
  }

  // block has form: {|score,letter| contribution to count by `letter` with `score` }
  private double get_counts_wordwise(double threshold_first, double threshold_second) throws Exception {
    // scores_on_first_pwm, scores_on_second_pwm --> count
    TDoubleObjectHashMap<TDoubleDoubleHashMap> scores = new TDoubleObjectHashMap<TDoubleDoubleHashMap>();
    scores.put(0.0, new TDoubleDoubleHashMap(new double[] {0},
                                             new double[] {1}) );

    for (int pos = 0; pos < alignment().length(); ++pos) {
      scores = recalc_score_hash_wordwise(scores,
                                         alignment().first_pwm.matrix[pos], alignment().second_pwm.matrix[pos],
                                         threshold_first - alignment().first_pwm.best_suffix(pos + 1),
                                         threshold_second - alignment().second_pwm.best_suffix(pos + 1));
      if (max_pair_hash_size != null && summarySize(scores) > max_pair_hash_size) {
        throw new Exception("Hash overflow in Macroape::AlignedPairIntersection#counts_for_two_matrices_with_different_probabilities");
      }
    }

    return combine_scores(scores);
  }

  double combine_scores(TDoubleObjectHashMap<TDoubleDoubleHashMap> scores) {
    double sum = 0;
    TDoubleObjectIterator<TDoubleDoubleHashMap> iterator = scores.iterator();
    while (iterator.hasNext()){
      iterator.advance();
      TDoubleDoubleIterator second_iterator = iterator.value().iterator();
      while (second_iterator.hasNext()) {
        second_iterator.advance();
        sum += second_iterator.value();
      }
    }

    return sum;
  }

  TDoubleObjectHashMap<TDoubleDoubleHashMap> initial2DHash(TDoubleObjectHashMap<TDoubleDoubleHashMap> scores, double[] first_column, double least_sufficient_first) {
    TDoubleObjectHashMap<TDoubleDoubleHashMap> result = new TDoubleObjectHashMap<TDoubleDoubleHashMap>();

    TDoubleObjectIterator<TDoubleDoubleHashMap> iterator = scores.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      double score_first = iterator.key();
      for (int letter = 0; letter < PWM.ALPHABET_SIZE; ++letter) {
        double new_score_first = score_first + first_column[letter];
        if (new_score_first >= least_sufficient_first) {
          result.put(new_score_first, new TDoubleDoubleHashMap());
        }
      }
    }
    return result;
  }


  TDoubleObjectHashMap<TDoubleDoubleHashMap> recalc_score_hash(TDoubleObjectHashMap<TDoubleDoubleHashMap> scores,
                                                             double[] first_column, double[] second_column,
                                                             double least_sufficient_first, double least_sufficient_second,
                                                             RecalculateScore count_contribution_block) throws Exception {
    TDoubleObjectHashMap<TDoubleDoubleHashMap> new_scores = initial2DHash(scores,first_column,least_sufficient_first);

    TDoubleObjectIterator<TDoubleDoubleHashMap> iterator = scores.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      double score_first = iterator.key();

      TDoubleDoubleHashMap second_scores = iterator.value();

      TDoubleDoubleIterator second_iterator = second_scores.iterator();
      while (second_iterator.hasNext()) {
        second_iterator.advance();
        double score_second = second_iterator.key();
        double count = second_iterator.value();

        for (int letter = 0; letter < PWM.ALPHABET_SIZE; ++letter) {
          double new_score_first = score_first + first_column[letter];

          if (new_score_first >= least_sufficient_first) {
            double new_score_second = score_second + second_column[letter];

            if (new_score_second >= least_sufficient_second) {
              double add = count_contribution_block.recalculateScore(count, letter);
              new_scores.get(new_score_first).adjustOrPutValue(new_score_second, add, add);
            }
          }
        }

      }
    }
    return new_scores;
  }

  TDoubleObjectHashMap<TDoubleDoubleHashMap> recalc_score_hash_wordwise(TDoubleObjectHashMap<TDoubleDoubleHashMap> scores,
                                                               double[] first_column, double[] second_column,
                                                               double least_sufficient_first, double least_sufficient_second) throws Exception {
    TDoubleObjectHashMap<TDoubleDoubleHashMap> new_scores = initial2DHash(scores,first_column,least_sufficient_first);

    TDoubleObjectIterator<TDoubleDoubleHashMap> iterator = scores.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      double score_first = iterator.key();

      TDoubleDoubleHashMap second_scores = iterator.value();

      TDoubleDoubleIterator second_iterator = second_scores.iterator();
      while (second_iterator.hasNext()) {
        second_iterator.advance();
        double score_second = second_iterator.key();
        double count = second_iterator.value();

        for (int letter = 0; letter < PWM.ALPHABET_SIZE; ++letter) {
          double new_score_first = score_first + first_column[letter];

          if (new_score_first >= least_sufficient_first) {
            double new_score_second = score_second + second_column[letter];

            if (new_score_second >= least_sufficient_second) {
              new_scores.get(new_score_first).adjustOrPutValue(new_score_second, count, count);
            }
          }
        }

      }
    }
    return new_scores;
  }

}
