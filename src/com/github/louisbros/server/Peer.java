package com.github.louisbros.server;

import java.nio.channels.SocketChannel;

public class Peer {
	
	private SocketChannel channel;
	private String key;
	private boolean handshakeComplete = false;
	
	public Peer(SocketChannel channel){
		this.channel = channel;
	}
	
	public String getKey(){
		return key;
	}
	public void setKey(String key){
		this.key = key;
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
}
