/**
 * This file is part of riak-java-pb-client 
 *
 * Copyright (c) 2010 by Trifork
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.trifork.riak;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import com.google.protobuf.MessageLite;
import com.trifork.riak.RPB.RpbErrorResp;

public class RiakConnection {

	static final int DEFAULT_RIAK_PB_PORT = 8087;

	 private Socket sock;
	 private DataOutputStream dout;
	 private DataInputStream din;

	public RiakConnection(String host) throws IOException {
		this(host, DEFAULT_RIAK_PB_PORT);
	}

	public RiakConnection(String host, int port) throws IOException {
		this(InetAddress.getByName(host), port);
	}

	public RiakConnection(InetAddress addr, int port) throws IOException {
		sock = new Socket(addr, port);
		
		sock.setSendBufferSize(1024 * 200);
		
		dout = new DataOutputStream(new BufferedOutputStream(sock
				.getOutputStream(), 1024 * 200));
		din = new DataInputStream(
				new BufferedInputStream(sock.getInputStream(), 1024 * 200));
	}

	///////////////////////

	void send(int code, MessageLite req) throws IOException {
		int len = req.getSerializedSize();
		dout.writeInt(len + 1);
		dout.write(code);
		req.writeTo(dout);
		dout.flush();
	}

	void send(int code) throws IOException {
		dout.writeInt(1);
		dout.write(code);
		dout.flush();
	}

	byte[] receive(int code) throws IOException {
		int len = din.readInt();
		int get_code = din.read();

		if (code == RiakClient.MSG_ErrorResp) {
			RpbErrorResp err = com.trifork.riak.RPB.RpbErrorResp.parseFrom(din);
			throw new RiakError(err);
		}

		byte[] data = null;
		if (len > 1) {
			data = new byte[len - 1];
			din.readFully(data);
		}

		if (code != get_code) {
			throw new IOException("bad message code");
		}

		return data;
	}

	void receive_code(int code) throws IOException, RiakError {
		int len = din.readInt();
		int get_code = din.read();
		if (code == RiakClient.MSG_ErrorResp) {
			RpbErrorResp err = com.trifork.riak.RPB.RpbErrorResp.parseFrom(din);
			throw new RiakError(err);
		}
		if (len != 1 || code != get_code) {
			throw new IOException("bad message code");
		}
	}

	static Timer idle_timer = new Timer();
	TimerTask idle_timeout;
	
	public void beginIdle() {
		idle_timeout = new TimerTask() {
			
			@Override
			public void run() {
				RiakConnection.this.timer_fired(this);
			}
		};
		
		idle_timer.schedule(idle_timeout, 1000);
	}

	synchronized void timer_fired(TimerTask fired_timer) {
		if (idle_timeout != fired_timer) {
			// if it is not our current timer, then ignore
			return;
		}
		
		try {
			sock.close();
			din = null;
			dout = null;
			sock = null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	synchronized boolean endIdleAndCheckValid() {
		TimerTask tt = idle_timeout;
		if (tt != null) { tt.cancel(); }
		idle_timeout = null;
		
		if (sock == null) {
			return false;
		} else {
			return true;
		}
	}

	public DataOutputStream getOutputStream() {
		return dout;
	}
	
	
}
