package tskill.jskills.trueskill.factors;

import static tskill.jskills.numerics.GaussianDistribution.logProductNormalization;
import tskill.jskills.factorgraphs.Factor;
import tskill.jskills.factorgraphs.Message;
import tskill.jskills.factorgraphs.Variable;
import tskill.jskills.numerics.GaussianDistribution;

public abstract class GaussianFactor extends Factor<GaussianDistribution> {

    GaussianFactor(String name) { super(name); }

    /** Sends the factor-graph message with and returns the log-normalization constant **/
    @Override
    protected double SendMessage(Message<GaussianDistribution> message,
            Variable<GaussianDistribution> variable) {
        GaussianDistribution marginal = variable.getValue();
        GaussianDistribution messageValue = message.getValue();
        double logZ = logProductNormalization(marginal, messageValue);
        variable.setValue(marginal.mult(messageValue));
        return logZ;
    }

    @Override
    public Message<GaussianDistribution> CreateVariableToMessageBinding(
            Variable<GaussianDistribution> variable) {
        return CreateVariableToMessageBinding(variable,
                new Message<GaussianDistribution>(GaussianDistribution
                        .fromPrecisionMean(0, 0), "message from %s to %s",
                        this, variable));
    }
}