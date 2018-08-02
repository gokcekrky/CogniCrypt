/********************************************************************************
 * Copyright (c) 2015-2018 TU Darmstadt
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package de.cognicrypt.codegenerator.wizard;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.dom4j.io.XMLWriter;

import de.cognicrypt.codegenerator.question.Answer;
import de.cognicrypt.codegenerator.question.Question;
import de.cognicrypt.utils.FileHelper;

/**
 * This class is a storage for the configuration chosen by the user.
 * 
 * @author Stefan Krueger
 *
 */
public abstract class Configuration {

	final protected Map<Question, Answer> options;
	final protected String pathOnDisk;

	protected Configuration(Map<Question, Answer> constraints, String pathOnDisk) {
		this.pathOnDisk = pathOnDisk;
		this.options = constraints;
	}

	/**
	 * Writes chosen configuration to hard disk.
	 * 
	 * @return Written file.
	 * @throws IOException
	 *         see {@link FileWriter#FileWriter(String)) FileWriter} and {@link XMLWriter#write(String) XMLWriter.write()}
	 */
	public abstract File persistConf() throws IOException;

	/**
	 * Retrieves list of custom providers from configuration.
	 * 
	 * @return List of custom providers
	 */
	public abstract List<String> getProviders();

	/**
	 * Deletes config file from hard disk.
	 */
	public void deleteConfFromDisk() {
		FileHelper.deleteFile(pathOnDisk);
	}
}
