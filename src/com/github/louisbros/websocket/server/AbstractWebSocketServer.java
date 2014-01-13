package com.github.louisbros.websocket.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.github.louisbros.websocket.exception.UnsupportedCodeException;
import com.github.louisbros.websocket.server.ProtocolUtils.Code;

public abstract class AbstractWebSocketServer implements WebSocketServer{

	private transient Thread thread;
	private int port = -1;
	protected List<Peer> peers;
	
	public AbstractWebSocketServer(){
		thread = new Thread(this);
		peers = new ArrayList<Peer>();
	}
	
	public void start(){
		if(!thread.isAlive()){
			thread.start();
		}
	}
	
	public void stop(){
		thread.interrupt();
	}
	
	public int getPort(){
		return port;
	}
	
	//@Override
	public void run() {
		
		ServerSocketChannel serverSocketChannel = null;
		Selector selector = null;
		
		try{
			
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking( false );
			ServerSocket socket = serverSocketChannel.socket();
			socket.bind(new InetSocketAddress(0));
			port = socket.getLocalPort();
			selector = Selector.open();
			serverSocketChannel.register( selector, SelectionKey.OP_ACCEPT );
			
			while(!thread.isInterrupted()){
				
				selector.select(500);
				
				Iterator<SelectionKey> i = selector.selectedKeys().iterator();
				while(i.hasNext()){
					
					SelectionKey selectionKey = i.next();
					i.remove();
					
					if(selectionKey.isAcceptable()){
						
						SocketChannel channel = serverSocketChannel.accept();
						channel.configureBlocking( false );
						Peer peer = new Peer(channel);
						peers.add(peer);
						channel.register( selector, SelectionKey.OP_READ, peer);
					}
					else if(selectionKey.isReadable() && selectionKey.attachment() instanceof Peer){
						
						Peer peer = (Peer)selectionKey.attachment();
						ByteBuffer buffer = ByteBuffer.allocate(1024);
						peer.getChannel().read(buffer);
						buffer.flip();
						
						if(!peer.isHandshakeComplete()){
							peer.setHandshakeProperties(ProtocolUtils.readHandshake(buffer));
							selectionKey.interestOps(SelectionKey.OP_WRITE);
						}
						else{
							String message = ProtocolUtils.decodeMaskedFrame(buffer);
							if(message.equals(Code.CLOSE.name())){
								peers.remove(peer);
								ByteBuffer closeBuffer = ProtocolUtils.encodeUnmaskedFrame(Code.CLOSE, "");
								closeBuffer.flip();
								while(closeBuffer.hasRemaining()){
									peer.getChannel().write(closeBuffer);
								}
								peer.getChannel().close();
								broadcast("peer disconnected");
								continue;
							}
							else{
								broadcast(message);
							}
						}
					}
					else if(selectionKey.isWritable() && selectionKey.attachment() instanceof Peer){

						Peer peer = (Peer)selectionKey.attachment();
						
						if(!peer.isHandshakeComplete()){
							peer.setHandshakeComplete(true);
							ProtocolUtils.writeHandshake(peer.getChannel(), peer.getHandshakeProperties().getProperty("Sec-WebSocket-Key"));
							broadcast("peer connected");
						}

						selectionKey.interestOps(SelectionKey.OP_READ);
					}
				}
			}
		}
		catch(IOException e){
			e.printStackTrace();
		}
		catch(NoSuchAlgorithmException e){
			e.printStackTrace();
		}
		catch(UnsupportedCodeException e){
			e.printStackTrace();
		}
		finally{
			
			try{
				
				if(serverSocketChannel != null ){
					serverSocketChannel.close();
				}
				if(selector != null ){
					selector.close();
				}
			}
			catch(IOException e){
				e.printStackTrace();
			}
		}
	}
}
