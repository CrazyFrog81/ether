/*
 * Copyright (c) 2013 - 2016 Stefan Muller Arisona, Simon Schubiger
 * Copyright (c) 2013 - 2016 FHNW & ETH Zurich
 * All rights reserved.
 *
 * Contributions by: Filip Schramka, Samuel von Stachelski
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *  Neither the name of FHNW / ETH Zurich nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ch.fhnw.ether.image;

import java.nio.ByteBuffer;

public interface IHostImage extends IImage {

	void clear();
	
	IHostImage copy();

	IHostImage allocate();
	
	IHostImage allocate(int width, int height);
	
	byte[] getPixel(int x, int y, byte[] dst);

	void setPixel(int x, int y, byte[] src);
	
	float[] getPixel(int x, int y, float[] dst);
	
	void setPixel(int x, int y, float[] src);
	
	byte getComponentByte(int x, int y, int component);
	
	void setComponentByte(int x, int y, int component, byte value);
	
	float getComponentFloat(int x, int y, int component);
	
	void setComponentFloat(int x, int y, int component, float value);
	
	IHostImage getSubImage(int x, int y, int width, int height);

	void setSubImage(int x, int y, IHostImage frame);
	
	ByteBuffer getPixels();
	
	default IGPUImage createGPUImage() {
		return IGPUImage.create(this);
	}
	
	static IHostImage create(IGPUImage image) {
		return image.createHostImage();
	}
	
	static IHostImage create(int width, int height, ComponentType componentType, ComponentFormat componentFormat) {
		return create(width, height, componentType, componentFormat, AlphaMode.POST_MULTIPLIED, null);
	}

	static IHostImage create(int width, int height, ComponentType componentType, ComponentFormat componentFormat, ByteBuffer pixels) {
		return create(width, height, componentType, componentFormat, AlphaMode.POST_MULTIPLIED, pixels);
	}

	static IHostImage create(int width, int height, ComponentType componentType, ComponentFormat componentFormat, AlphaMode alphaMode) {
		return create(width, height, componentType, componentFormat, alphaMode, null);
	}

	static IHostImage create(int width, int height, ComponentType componentType, ComponentFormat componentFormat, AlphaMode alphaMode, ByteBuffer pixels) {
		switch (componentType) {
		case BYTE:
			return new ByteImage(width, height, componentFormat, alphaMode, pixels);
		case FLOAT:
			return new FloatImage(width, height, componentFormat, alphaMode, pixels);
		}
		throw new IllegalArgumentException();
	}
	
	// XXX to be implemented
	static IHostImage convert(IHostImage image, ComponentType componentType, ComponentFormat componentFormat, AlphaMode alphaMode) {
		throw new UnsupportedOperationException();
//		if (image.getComponentType() == componentType && image.getComponentFormat() == componentFormat && image.getAlphaMode() == alphaMode)
//			return image;
//		
//		IHostImage result = create(image.getWidth(), image.getHeight(), componentType, componentFormat, alphaMode);
//		
//		return result;
	}
}