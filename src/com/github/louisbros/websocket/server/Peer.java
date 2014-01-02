package com.github.louisbros.websocket.server;

import java.io.Serializable;
import java.nio.channels.SocketChannel;
import java.util.Properties;

public class Peer implements Serializable{
	
	private static final long serialVersionUID = 1L;
	private SocketChannel channel;
	private boolean handshakeComplete = false;
	private Properties handshakeProperties;
	
	public Peer(SocketChannel channel){
		this.channel = channel;
	}
	
	public SocketChannel getChannel(){
		return channel;
	}
	public void setChannel(SocketChannel channel){
		this.channel = channel;
	}
	
	public boolean isHandshakeComplete(){
		return handshakeComplete;
	}
	public void setHandshakeComplete(boolean handshakeComplete){
		this.handshakeComplete = handshakeComplete;
	}
	
	public Properties getHandshakeProperties(){
		return handshakeProperties;
	}
	public void setHandshakeProperties(Properties handshakeProperties){
		this.handshakeProperties = handshakeProperties;
	}
}
