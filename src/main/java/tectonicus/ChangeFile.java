/*
 * Copyright (c) 2024 Tectonicus contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

@Slf4j
public class ChangeFile
{
	private FileOutputStream fileOut;
	private PrintWriter writer;
	
	public ChangeFile(File outFile)
	{
		if (outFile.exists())
			outFile.delete();
	
		try
		{
			fileOut = new FileOutputStream(outFile);
			writer = new PrintWriter(fileOut);
		}
		catch (Exception e)
		{
			log.error("Couldn't create changed file: "+e);
		}
	}
	
	public synchronized void writeLine(String line)
	{
		if (writer != null)
		{
			writer.write(line);
			writer.write('\n');
		}
	}
	
	public void close()
	{
		if (writer != null)
		{
			writer.close();
		}
		if (fileOut != null)
		{
			try
			{
				fileOut.close();
			}
			catch (Exception e) {}
		}
	}
}
