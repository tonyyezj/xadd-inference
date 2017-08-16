package hgm.poly.bayesian;

import hgm.poly.sampling.SamplerInterface;
import hgm.sampling.SamplingFailureException;

import java.util.Arrays;

/**
 * Created by Hadi Afshar.
 * Date: 11/03/14
 * Time: 9:05 PM
 */
public class MetropolisHastingGeneralBayesianSampler implements SamplerInterface {
    protected GeneralBayesianPosteriorHandler gph;
    double[] cVarMins;
    double[] cVarMaxes;
    int numVars;

    private double proposalVariance;
    protected Double[] lastSample;

    public MetropolisHastingGeneralBayesianSampler(GeneralBayesianPosteriorHandler gph/*, double[] cVarMins, double[] cVarMaxes,*/, double proposalVariance) {
        this.gph = gph;
        this.cVarMins = gph.getPriorHandler().getLowerBoundsPerDim();//cVarMins;
        this.cVarMaxes = gph.getPriorHandler().getUpperBoundsPerDim();//cVarMaxes;
        numVars = cVarMins.length;
        if (cVarMaxes.length != numVars) throw new RuntimeException("length mismatch between mins and maxes");

        if (gph.getPolynomialFactory().getAllVars().length != numVars) throw new RuntimeException("var size mismatch");
        if (proposalVariance <= 0d) throw new RuntimeException("negative variance!!!");
        this.proposalVariance = proposalVariance;

        lastSample = takeInitSample();
    }

    private Double[] takeInitSample() {
        Double[] sample = new Double[numVars];

        for (; ; ) {
            for (int i = 0; i < numVars; i++) {
                sample[i] = AbstractGeneralBayesianGibbsSampler.randomDoubleUniformBetween(cVarMins[i], cVarMaxes[i]);
            }
            if (gph.evaluate(sample) > 0) return sample;
        }

    }

    public void setProposalVariance(double proposalVariance) {
        this.proposalVariance = proposalVariance;
    }

    @Override
    public Double[] reusableSample() throws SamplingFailureException {
        Double[] proposalState = jump(lastSample, proposalVariance);

        double prCurrentState = gph.evaluate(lastSample);
        double prProposalState = gph.evaluate(proposalState);
        double acceptanceRatio = prProposalState / prCurrentState;
        if (acceptanceRatio >= 1) {
            System.arraycopy(proposalState, 0, lastSample, 0, proposalState.length);
            return proposalState;
        }

        if (acceptanceRatio > AbstractGeneralBayesianGibbsSampler.randomDoubleUniformBetween(0, 1)) {
            System.arraycopy(proposalState, 0, lastSample, 0, proposalState.length);
            return proposalState;
        } else {
            Double[] sample = new Double[lastSample.length];
            System.arraycopy(lastSample, 0, sample, 0, lastSample.length);
            return sample;
        }
    }

    protected Double[] jump(Double[] mean, double variance) {
        Double[] newState = new Double[mean.length];
        for (int i = 0; i < mean.length; i++) {
            newState[i] = AbstractGeneralBayesianGibbsSampler.randomGaussianDouble(mean[i], variance);
        }
        return newState;
    }
}