package org.ml4j.nn;

import java.io.Serializable;

import org.jblas.DoubleMatrix;
import org.ml4j.nn.activationfunctions.DifferentiableActivationFunction;

public class FeedForwardLayer extends DirectedLayer<FeedForwardLayer> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private DoubleMatrix thetas;



	public FeedForwardLayer dup(boolean retrainable) {
		FeedForwardLayer dup = new FeedForwardLayer(inputNeuronCount, outputNeuronCount, this.getClonedThetas(),
				activationFunction, hasBiasUnit(),retrainable);
		return dup;
	}
	
	public DoubleMatrix activate(double[][] layerInputsArrays)
	{
		DoubleMatrix layerInputs= new DoubleMatrix(layerInputsArrays);
		if (hasBiasUnit())
		{
			layerInputs = DoubleMatrix.concatHorizontally(DoubleMatrix.ones(layerInputs.rows,1), layerInputs);
		}
		return forwardPropagate(layerInputs).getOutputActivations();
	}

	public DoubleMatrix activate(DoubleMatrix layerInputs)
	{
		if (hasBiasUnit())
		{
			layerInputs = DoubleMatrix.concatHorizontally(DoubleMatrix.ones(layerInputs.rows,1), layerInputs);
		}
		return forwardPropagate(layerInputs).getOutputActivations();
	}
	
	
	protected NeuralNetworkLayerActivation forwardPropagate(DoubleMatrix layerInputsWithIntercept) {
		
		if (layerInputsWithIntercept.getColumns() != getInputNeuronCount() + (hasBiasUnit() ? 1 : 0))
		{
			throw new IllegalArgumentException("Layer forward propogation requires inputs matrix with intercepts with number of columns = " + (getInputNeuronCount() + 1));
		}
		
		DoubleMatrix Z = layerInputsWithIntercept.mmul(thetas.transpose());

		DoubleMatrix acts = activationFunction.activate(Z);
		NeuralNetworkLayerActivation activation = new NeuralNetworkLayerActivation(this, layerInputsWithIntercept, Z, acts);

		return activation;
	}

	public DoubleMatrix getClonedThetas() {

		DoubleMatrix ret = thetas.dup();
		return ret;
	}

	public FeedForwardLayer(int inputNeuronCount, int outputNeuronCount, DifferentiableActivationFunction activationFunction,boolean biasUnit) {
		super(biasUnit,true);
		if (activationFunction == null) throw new IllegalArgumentException("Activation function passed to layer cannot be null");
		this.inputNeuronCount = inputNeuronCount;
		this.outputNeuronCount = outputNeuronCount;
		this.activationFunction = activationFunction;
		this.thetas = generateInitialThetas(getOutputNeuronCount(), getInputNeuronCount() + (biasUnit ? 1 : 0));
	}
	

	public FeedForwardLayer(int inputNeuronCount, int outputNeuronCount, DoubleMatrix thetas,
			DifferentiableActivationFunction activationFunction, boolean biasUnit,boolean retrainable) {
		super(biasUnit,retrainable);
		if (activationFunction == null) throw new IllegalArgumentException("Activation function passed to layer cannot be null");
		if (thetas == null) throw new IllegalArgumentException("Thetas passed to layer cannot be null");
		if (thetas.getRows() != outputNeuronCount || thetas.getColumns() != (inputNeuronCount + (biasUnit ? 1 : 0))) throw new IllegalArgumentException("Thetas matrix must be of dimensions " + outputNeuronCount +  ":" + (inputNeuronCount + 1));
		this.inputNeuronCount = inputNeuronCount;
		this.outputNeuronCount = outputNeuronCount;
		this.activationFunction = activationFunction;
		this.thetas = thetas;

	}

	protected void updateThetas(DoubleMatrix thetas, int layerIndex, boolean permitFurtherRetrains) {

		if (!isRetrainable()) {
			throw new IllegalStateException("Layer " + (layerIndex + 1)
					+ " has already been trained and has not been set to retrainable");
		}
		if (layerIndex < 0)
		{
			throw new IllegalArgumentException("Neural network layer index must be zero or above");
		}
		
		if (thetas.getRows() != outputNeuronCount || thetas.getColumns() != (inputNeuronCount + (hasBiasUnit ? 1 : 0 ))) throw new IllegalArgumentException("Thetas matrix must be of dimensions " + outputNeuronCount +  ":" + (inputNeuronCount + (hasBiasUnit ? 1 : 0 )));
		this.thetas = thetas;
		if (!permitFurtherRetrains) {
			this.setRetrainable(false);
		}
	}

	private DoubleMatrix generateInitialThetas(int r, int c) {
		DoubleMatrix initial = DoubleMatrix.randn(r, c);
		return initial;
	}
	
	public double[] getNeuronActivationMaximisingInputFeatures(int hiddenUnitIndex) {
		int jCount = thetas.getColumns() - 1;
		double[] maximisingInputFeatures = new double[jCount];
		for (int j = 0; j < jCount; j++) {
			double wij = getWij(hiddenUnitIndex, j);
			double sum = 0;

			for (int j2 = 0; j2 < jCount; j2++) {
				sum = sum + Math.pow(getWij(hiddenUnitIndex, j2), 2);
			}
			sum = Math.sqrt(sum);
			maximisingInputFeatures[j] = wij / sum;
		}
		return maximisingInputFeatures;
	}
	
	private double getWij(int i, int j) {
		DoubleMatrix weights = thetas;
		int jInd = j + 1;
		return weights.get(i, jInd);
	}
	

}
