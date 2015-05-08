package org.ml4j.nn.algorithms;

import java.util.List;

import org.jblas.DoubleMatrix;
import org.ml4j.nn.AutoEncoder;
import org.ml4j.nn.NeuralNetworkLayer;
import org.ml4j.nn.StackedAutoEncoder;
import org.ml4j.nn.costfunctions.CostFunction;

public class AutoEncoderAlgorithm {

	private AutoEncoder autoEncoder;
	private boolean layerwiseTraining;
	private StackedAutoEncoder stackedAutoEncoder;

	public AutoEncoderAlgorithm(StackedAutoEncoder autoEncoder,boolean layerwiseTraining) {
		this.autoEncoder = autoEncoder;
		this.layerwiseTraining = layerwiseTraining;
		this.stackedAutoEncoder = autoEncoder;

	}
	
	public AutoEncoderAlgorithm(AutoEncoder autoEncoder) {
		this.autoEncoder = autoEncoder;
		this.layerwiseTraining = false;

	}
	public AutoEncoder getAutoEncoder() {
		return autoEncoder;
	}

	private CostFunction getCostFunction(NeuralNetworkAlgorithmTrainingContext context) {
		if (context.getCostFunction() == null) {
			List<NeuralNetworkLayer> layers = autoEncoder.getLayers();
			NeuralNetworkLayer outerLayer = layers.get(layers.size() - 1);
			return outerLayer.getActivationFunction().getDefaultCostFunction();
		} else {
			return context.getCostFunction();
		}
	}

	private double[] createLayerRegularisations(double regularisationLamdba) {
		double[] layerRegularisations = new double[autoEncoder.getLayers().size()];
		for (int i = 0; i < layerRegularisations.length; i++) {
			layerRegularisations[i] = regularisationLamdba;
		}
		return layerRegularisations;
	}

	public AutoEncoderHypothesisFunction getHypothesisFunction(double[][] inputs,
			NeuralNetworkAlgorithmTrainingContext context) {

		if (layerwiseTraining)
		{
			stackedAutoEncoder.trainGreedilyLayerwise(new DoubleMatrix(inputs),
					context.getRegularizationLambda(),
					context.getMaxIterations());
		}
		else
		{
			autoEncoder.train(new DoubleMatrix(inputs),
				createLayerRegularisations(context.getRegularizationLambda()), getCostFunction(context),
				context.getMaxIterations());
		}

		return new AutoEncoderHypothesisFunction(autoEncoder);
	}

}
