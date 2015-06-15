/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ml4j.nn;

import java.io.Serializable;

import org.jblas.DoubleMatrix;
import org.ml4j.nn.activationfunctions.DifferentiableActivationFunction;

/**
 * A DirectedLayer which composes input neurons and output neurons into a directed acyclic bipartite graph.
 * 
 * There are no input-input connections or output-output connections,  only input-output connections.
 * 
 * The connection between input neuron i and output neuron j is represented by an input->output weight, w(i,j).
 * 
 * Please note that the DoubleMatrix containing the weights is actually transposed from 
 * the shape that may be naturally assumed  - ie. w(i,j) = thetas.get(j,i);
 * 
 * @author Michael Lavelle
 *
 */
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
	
	/**
	 * Activates the output neurons, by forward propagating information from
	 * the input neuron activities, as specified by a double[][] array.
	 * 
	 * @param layerInputs The activations of the input units ( not including bias units). Many
	 * activations can be forward propagated in parallel using the rows of this matrix, with
	 * each column representing each input neuron.
	 * 
	 * @return The activations of the output units once information has been propagated.
	 * If multiple activation rows were input for parallel processing, the output
	 * will have a row for each parallel output activation.
	 */
	public DoubleMatrix activate(double[][] layerInputsArrays)
	{
		DoubleMatrix layerInputs= new DoubleMatrix(layerInputsArrays);
		if (hasBiasUnit())
		{
			layerInputs = DoubleMatrix.concatHorizontally(DoubleMatrix.ones(layerInputs.rows,1), layerInputs);
		}
		return forwardPropagate(layerInputs).getOutputActivations();
	}

	/**
	 * Activates the output neurons, by forward propagating information from
	 * the input neurons as specified by a DoubleMatrix
	 * 
	 * @param layerInputs The activations of the input units ( not including bias units). Many
	 * activations can be forward propagated in parallel using the rows of this matrix, with
	 * each column representing each input neuron.
	 * 
	 * @return The activations of the output units once information has been propagated.
	 * If multiple activation rows were input for parallel processing, the output
	 * will have a row for each parallel output activation.
	 */
	public DoubleMatrix activate(DoubleMatrix layerInputs)
	{
		if (hasBiasUnit())
		{
			layerInputs = DoubleMatrix.concatHorizontally(DoubleMatrix.ones(layerInputs.rows,1), layerInputs);
		}
		return forwardPropagate(layerInputs).getOutputActivations();
	}
	
	/**
	 * Propagate information through this FeedForwardLayer
	 * 
	 * @param inputActivations A matrix of inputActivations ( including
	 * the always-1 activations of the bias unit if  hasBiasUnit() == true ).
	 * 
	 * Each row is a specification of activations for all input units ( including
	 * the bias unit if hasBiasUnit() == true ), with a column for each unit.
	 * 
	 * @return A NeuralNetworkLayerActivation instance specifying how
	 * the information propagated through the layer.
	 */
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

	/**
	 * A clone of the weights matrix
	 * 
	 * The matrix dimensions are outputNeuronCount:(inputNeuronCount + hasBiasUnit() ? 1: 0)
	 *
	 * 
	 * @return A duplicated matrix of weights mapping input neurons to output neurons.
	 * Please note that the weight connecting input neuron i to output neuron j,
	 * w(i,j) = getClonedThetas().get(j,i) - ie the transpose of the shape that
	 * may be assumed.
	 * 
	 */
	public DoubleMatrix getClonedThetas() {

		DoubleMatrix ret = thetas.dup();
		return ret;
	}

	/**
	 * Untrained FeedForwardLayer constructor - sets the layer to retrainable=true
	 * 
	 * @param inputNeuronCount The number of input neurons, not including any bias unit
	 * @param outputNeuronCount The number of output neurons
	 * @param activationFunction  The activation function which is applied to the 
	 * inputs after they have been multiplied by their weights 
	 * to product the output neuron activities
	 * @param biasUnit Whether this layer contains an additional inputs bias unit, as well as the input neurons specified by inputNeuronCount
	 */
	public FeedForwardLayer(int inputNeuronCount, int outputNeuronCount, DifferentiableActivationFunction activationFunction,boolean biasUnit) {
		super(inputNeuronCount,outputNeuronCount,activationFunction,biasUnit,true);
		this.thetas = generateInitialThetas(getOutputNeuronCount(), getInputNeuronCount() + (biasUnit ? 1 : 0));
	}
	
	/**
	 * Pre-trained FeedForwardLayer constructor - initializes the weights matrix
	 * and allows retrainable flag to be custom set.
	 * 
	 * @param inputNeuronCount The number of input neurons, not including any bias unit
	 * @param outputNeuronCount The number of output neurons
	 * @param activationFunction  The activation function which is applied to the 
	 * inputs after they have been multiplied by their weights 
	 * to product the output neuron activities
	 * @param biasUnit Whether this layer contains an additional inputs bias unit, as well as the input neurons specified by inputNeuronCount
	 */
	public FeedForwardLayer(int inputNeuronCount, int outputNeuronCount, DoubleMatrix thetas,
			DifferentiableActivationFunction activationFunction, boolean biasUnit,boolean retrainable) {
		super(inputNeuronCount,outputNeuronCount,activationFunction,biasUnit,retrainable);
		if (thetas == null) throw new IllegalArgumentException("Thetas passed to layer cannot be null");
		if (thetas.getRows() != outputNeuronCount || thetas.getColumns() != (inputNeuronCount + (biasUnit ? 1 : 0))) throw new IllegalArgumentException("Thetas matrix must be of dimensions " + outputNeuronCount +  ":" + (inputNeuronCount + (hasBiasUnit ? 1 : 0)));
		this.thetas = thetas;

	}

	/**
	 * Update the weights of this layer
	 * 
	 * @param thetas The weights matrix. Please note that the weight connecting input neuron i to output neuron j,
	 * w(i,j) = getClonedThetas().get(j,i) - ie the transpose of the shape that
	 * may be assumed.
	 * @param layerIndex The index of the layer in the containing Neural Network
	 * 
	 * @param permitFurtherRetrains Whether to permit further retrains (weight updates) after updating the weights.
	 */
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

	/**
	 * Generate a set of weights, initialized to normally distributed
	 * random values.
	 * 
	 * @param r The row count of the target weight matrix : ( outputNeuronCount)
	 * @param c The columns count of the target weight matrix : ( inputNeuronCount + hasBiasUnit() ? 1 : 0 )
	 * @return An initial set of weights
	 */
	private DoubleMatrix generateInitialThetas(int r, int c) {
		DoubleMatrix initial = DoubleMatrix.randn(r, c).mul(0.05);
		return initial;
	}
	
	/**
	 * Return input activations which maximise the activation of a specified output neuron
	 * 
	 * @param outputNeuronIndex The index of the output Neuron to obtain maximising input features for
	 * @return The input features which maximise the activation of the specified output Neuron
	 * 
	 */
	public double[] getOutputNeuronActivationMaximisingInputFeatures(int outputNeuronIndex) {
		int jCount = thetas.getColumns() - 1;
		double[] maximisingInputFeatures = new double[jCount];
		for (int j = 0; j < jCount; j++) {
			double wij = getWij(outputNeuronIndex, j);
			double sum = 0;

			for (int j2 = 0; j2 < jCount; j2++) {
				sum = sum + Math.pow(getWij(outputNeuronIndex, j2), 2);
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
