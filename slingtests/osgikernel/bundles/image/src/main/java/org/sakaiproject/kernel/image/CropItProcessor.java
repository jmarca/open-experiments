/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.kernel.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;

import javax.imageio.ImageIO;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.sakaiproject.kernel.api.jcr.JCRConstants;
import org.sakaiproject.kernel.api.jcr.support.JCRNodeFactoryService;
import org.sakaiproject.kernel.api.jcr.support.JCRNodeFactoryServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CropItProcessor {

	public static JCRNodeFactoryService jcrNodeFactoryService;
	private static LinkedList<String> iTmpFiles;
	private static final Logger LOGGER = LoggerFactory
			.getLogger(CropItProcessor.class);
	private static final String CONVERT_PROG = "/usr/bin/convert";

	/**
	 * 
	 * @param x
	 *            Where to start cutting on the x-axis.
	 * @param y
	 *            Where to start cutting on the y-axis.
	 * @param width
	 *            The width of the image to cut out. If <=0 then the entire
	 *            image width will be used.
	 * @param height
	 *            The height of the image to cut out.If <=0 then the entire
	 *            image height will be used.
	 * @param dimensions
	 *            A JSONArray with the different dimensions.
	 * @param urlSaveIn
	 *            Where to save the new images.
	 * @param nImgToCrop
	 *            The node that contains the base image.
	 * @param jcrNodeFactoryService
	 * @return
	 * @throws ImageException
	 * @throws IOException
	 * @throws JCRNodeFactoryServiceException
	 * @throws RepositoryException
	 */
	public static String[] crop(int x, int y, int width, int height,
			JSONArray dimensions, String urlSaveIn, Node nImgToCrop,
			JCRNodeFactoryService jcrNodeFactoryService) throws ImageException,
			IOException, RepositoryException {
		iTmpFiles = new LinkedList<String>();
		InputStream in = null;

		// The array that will contain all the cropped and resized images.
		String[] arrFiles = new String[dimensions.size()];

		CropItProcessor.jcrNodeFactoryService = jcrNodeFactoryService;

		try {
			if (nImgToCrop != null) {

				String sImg = nImgToCrop.getName();

				// Read the image
				try {
					in = jcrNodeFactoryService.getInputStream(nImgToCrop
							.getPath());
				} catch (JCRNodeFactoryServiceException e) {
					LOGGER
							.error("Error opening input stream for image node",
									e);
					throw new IOException(
							"Unable to open input stream for image");
				}

				BufferedImage img = ImageIO.read(in);
				String tmpFile = convert2TmpFile(img);
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// Set the correct width & height.
				width = (width <= 0) ? img.getWidth() : width;
				height = (height <= 0) ? img.getHeight() : height;

				// Loop the dimensions and create and save an image for each
				// one.
				for (int i = 0; i < dimensions.size(); i++) {

					JSONObject o = dimensions.getJSONObject(i);

					// get dimension size
					int iWidth = Integer.parseInt(o.get("width").toString());
					int iHeight = Integer.parseInt(o.get("height").toString());

					iWidth = (iWidth <= 0) ? img.getWidth() : iWidth;
					iHeight = (iHeight <= 0) ? img.getHeight() : iHeight;

					// Create the image.
					String outFile = getTmpFile();
					convert(tmpFile, outFile, width, height, x, y, iWidth,
							iHeight);

					String sPath = urlSaveIn + iWidth + "x" + iHeight + "_"
							+ sImg;
					// Save new image to JCR.
					try {
						saveImageToJCR(sPath, outFile, "image/png");
					} catch (JCRNodeFactoryServiceException e) {
						LOGGER.error("Error saving cropped image", e);
						throw new IOException("Unable to save cropped image");
					}

					arrFiles[i] = sPath;
				}
			} else {
				throw new ImageException("No file found.");
			}

		} finally {
			// clear out temporary files
			removeTmpFiles();
			// close the streams
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return arrFiles;
	}

	/**
	 * Will save a stream of an image to the JCR.
	 * 
	 * @param sPath
	 *            The JCR path to save the image in.
	 * @param sType
	 *            The Mime type of the node that will be saved.
	 * @param out
	 *            The stream you wish to save.
	 * @throws RepositoryException
	 * @throws JCRNodeFactoryServiceException
	 * @throws IOException
	 */
	public static void saveImageToJCR(String sPath, String sType, String outFile)
			throws RepositoryException, JCRNodeFactoryServiceException,
			IOException {

		// Save image into the jcr
		Node n = jcrNodeFactoryService.getNode(sPath);

		// This node doesn't exist yet. Create it.
		if (n == null) {
			n = jcrNodeFactoryService.createFile(sPath, sType);
		}
		ByteArrayInputStream bais = (ByteArrayInputStream) ImageIO
				.createImageInputStream(new File(outFile));
		// convert stream to inputstream
		try {
			Node n2 = jcrNodeFactoryService.setInputStream(sPath, bais, sType);
			n2.setProperty(JCRConstants.JCR_MIMETYPE, sType);
      n2.save(); // according to javadoc, stream is read on node save
      n.save(); // according to javadoc, stream is read on node save
		} finally {
			bais.close();
		}
		n.getSession().save();
	}

	/**
	 * Uses a Runtime.exec()to use imagemagick to perform the given conversion
	 * operation. Returns true on success, false on failure. Does not check if
	 * either file exists.
	 * 
	 * @param in
	 *            the input file
	 * @param out
	 *            the output file
	 * @param width
	 *            the desired crop window width
	 * @param height
	 *            the desired crop window height
	 * @param top
	 *            the top of the crop window, relative to the top left of the
	 *            original image
	 * @param left
	 *            the left of the crop window, relative to the top left of the
	 *            original image
	 * @param dimx
	 *            the final width of the new image
	 * @param dimy
	 *            the final height of the image
	 * @return true if success, false if not
	 */
	private static boolean convert(String in, String out, int width,
			int height, int top, int left, int dimx, int dimy) {
		ArrayList<String> command = new ArrayList<String>(10);

		// note: CONVERT_PROG is a class variable that stores the location of
		// ImageMagick's convert command
		// it might be something like "/usr/local/magick/bin/convert" or
		// something else, depending on where you installed it.
		command.add(CONVERT_PROG);
		command.add("-crop");
		command.add(width + "x" + height + "+" + left + "+" + top);
		command.add("-resize");
		command.add(dimx + "x" + dimy);
		command.add("-trim");
		command.add("+repage");
		command.add(in);
		command.add(out);

		LOGGER.info(command.toString());

		return exec((String[]) command.toArray(new String[1]));
	}

	/**
	 * Tries to exec the command, waits for it to finsih, logs errors if exit
	 * status is nonzero, and returns true if exit status is 0 (success).
	 * 
	 * @param command
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */
	private static boolean exec(String[] command) {
		Process proc;

		try {
			// System.out.println("Trying to execute command " +
			// Arrays.asList(command));
			proc = Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			LOGGER.error("IOException while trying to execute " + command);
			return false;
		}

		// System.out.println("Got process object, waiting to return.");

		int exitStatus;

		while (true) {
			try {
				exitStatus = proc.waitFor();
				break;
			} catch (java.lang.InterruptedException e) {
				LOGGER.warn("Interrupted: Ignoring and waiting");
			}
		}
		if (exitStatus != 0) {
			LOGGER.error("Error executing command: " + exitStatus);
		}
		return (exitStatus == 0);
	}

	/**
	 * Create a temporary file.
	 */

	private static String getTmpFile() throws IOException {
		File tmpFile = File.createTempFile("im4java-", ".png");
		tmpFile.deleteOnExit();
		iTmpFiles.add(tmpFile.getAbsolutePath());
		return tmpFile.getAbsolutePath();
	}

	/**
	 * Write a BufferedImage to a temporary file.
	 */

	private static String convert2TmpFile(BufferedImage pBufferedImage)
			throws IOException {
		String tmpFile = getTmpFile();
		ImageIO.write(pBufferedImage, "PNG", new File(tmpFile));

		return tmpFile;
	}

	/**
	 * Remove all temporary files.
	 */

	private static void removeTmpFiles() {
		try {
			for (String file : iTmpFiles) {
				(new File(file)).delete();
			}
		} catch (Exception e) {
			// ignore, since if we can't delete the file, we can't do anything
			// about it
		}
	}

}
