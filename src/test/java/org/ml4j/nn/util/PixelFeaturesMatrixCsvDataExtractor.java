package org.ml4j.nn.util;

import org.ml4j.util.NumericFeaturesMatrixCsvDataExtractor;

public class PixelFeaturesMatrixCsvDataExtractor extends NumericFeaturesMatrixCsvDataExtractor {

	@Override
	public double[] createData(String[] csvAttributes) {
		double[] rawData = super.createData(csvAttributes);
		// Reverse zeros and ones in the csv file so that pixels of pen strokes
		// map
		// to active neurons
		double[] pixelActivationData = new double[rawData.length - 1];
		for (int i = 0; i < rawData.length - 1; i++) {
			//pixelActivationData[i] = rawData[i + 1] == 0 ? 1 : 0;
			pixelActivationData[i] = rawData[i + 1]/255d;
		}
		return pixelActivationData;

	}

}
