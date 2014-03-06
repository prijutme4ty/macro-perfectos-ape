package ru.autosome.perfectosape.calculations.findThreshold;

import ru.autosome.perfectosape.backgroundModels.BackgroundModel;
import ru.autosome.perfectosape.calculations.CountingPWM;
import ru.autosome.perfectosape.motifModels.PWM;

public class FindThresholdAPE extends FindThresholdByDiscretization<PWM> {
  public static class Builder extends FindThresholdBuilder<PWM> {
    Double discretization;
    BackgroundModel background;
    Integer maxHashSize;
    public Builder(BackgroundModel background, Double discretization, Integer maxHashSize) {
      this.background = background;
      this.discretization = discretization;
      this.maxHashSize = maxHashSize;
    }

    @Override
    public CanFindThreshold thresholdCalculator() {
      return new FindThresholdAPE(motif, background, discretization, maxHashSize);
    }
  }

  Integer maxHashSize; // if maxHashSize is null - it's not applied
  BackgroundModel background;

  public FindThresholdAPE(PWM pwm, BackgroundModel background,
                    Double discretization, Integer max_hash_size) {
    super(pwm, discretization);
    this.background = background;
    this.maxHashSize = max_hash_size;
  }

  @Override
  CountingPWM countingPWM(PWM pwm) {
    return new CountingPWM(pwm, background, maxHashSize);
  }
}
