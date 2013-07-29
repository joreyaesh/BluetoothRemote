package com.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class RemoteCommand implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5172852401L;
	/**
	 * 
	 */
	public int command = 0;
	public int parameter1 = 0; // X coordinate change/scroll amount
	public int parameter2 = 0; // Y coordinate change
	public String string1 = ""; // String for textbox

	public RemoteCommand() {
	}

	public static RemoteCommand getRemoteCommand(byte data[]) {
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
        //System.err.println("sent " + data.length + " bytes: " + data[0] + "," + data[1] + "," + data[2] + "," + data[3]);
		ObjectInput in = null;
		try {
			in = new ObjectInputStream(bis);
            RemoteCommand ret = (RemoteCommand) in.readObject();
			return ret;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				bis.close();
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;

	}

	public byte[] getByteArray() {
		byte[] retval = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
		ObjectOutput out = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(this);
			return bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return retval;
	}
}
